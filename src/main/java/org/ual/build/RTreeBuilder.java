package org.ual.build;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.rtree.RTree;
import org.ual.spatialindex.spatialindex.ISpatialIndex;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.utils.main.StatisticsLogic;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

public class RTreeBuilder extends AbstractIndexBuilder<RTreeBuilder> {
    private static final Logger logger = LogManager.getLogger(RTreeBuilder.class);

    @Override
    public ISpatialIndex build() {
        validateConfiguration();

        if (bulkLoadMethod != null) {
            logger.info("Building R-Tree using bulk load method: {}", bulkLoadMethod);
            return bulkLoad();
        } else {
            logger.info("Building R-Tree using incremental load");
            return incrementalLoad();
        }
    }

    private void validateConfiguration() {
        if (datasetParameters == null || datasetParameters.locationFile == null) {
            throw new IllegalStateException("Dataset parameters and location file must be set");
        }
    }

    private RTree incrementalLoad() {
        logger.info("Starting incremental R-Tree construction");
        RTree tree;

        try (LineNumberReader reader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
            PropertySet ps = createRTreeProperties();
            IStorageManager sm = new NodeStorageManager();
            tree = new RTree(ps, sm, datasetParameters, false);

            logger.info("Building R-Tree (incremental)...");
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            int count = processDataPoints(reader, tree, false);

            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(tree, count, SpatialIndex.getTreeVariantString(treeVariant));
            validateTree(tree);

            logger.info("R-Tree construction completed successfully with {} data points", count);
            return tree;
        } catch (IOException e) {
            logger.error("Failed to build R-Tree incrementally", e);
            throw new RuntimeException("Failed to build R-Tree incrementally.", e);
        }
    }

    private RTree bulkLoad() {
        logger.info("Starting bulk load R-Tree construction with method: {}", bulkLoadMethod);
        RTree tree;

        try (LineNumberReader reader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
            PropertySet ps = createRTreeProperties();
            ps.setProperty("BulkLoadMethod", bulkLoadMethod);

            IStorageManager sm = new NodeStorageManager();
            tree = new RTree(ps, sm, datasetParameters, false);

            logger.info("Loading pseudo nodes for bulk load...");
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            int count = loadPseudoNodes(reader, tree);
            logger.debug("Loaded {} pseudo nodes", count);

            logger.info("Executing bulk load operation...");
            tree.bulkLoadRTree(bulkLoadMethod);
            tree.pseudoNodes.clear();
            logger.debug("Pseudo nodes cleared after bulk load");

            collectSpatialComponentMetrics(initMem, startTime);
            String desc = "Bulk-loaded (" + bulkLoadMethod.toString() + ")";
            logSpatialComponentStatistics(tree, count, desc);
            validateTree(tree);

            logger.info("R-Tree bulk load construction completed successfully with {} data points", count);
            return tree;
        } catch (IOException e) {
            logger.error("R-Tree bulk loading failed", e);
            throw new RuntimeException("R-Tree bulk loading failed.", e);
        }
    }

    private int loadPseudoNodes(LineNumberReader reader, RTree tree) throws IOException {
        int count = 0;
        String line;

        while ((line = reader.readLine()) != null) {
            String[] temp = line.split(",");
            if (temp.length < 3) {
                logger.warn("Skipping invalid line at {}: insufficient data", reader.getLineNumber());
                continue;
            }

            try {
                int id = Integer.parseInt(temp[0]);
                double x = Double.parseDouble(temp[1]);
                double y = Double.parseDouble(temp[2]);
                Region region = new Region(new double[]{x, y}, new double[]{x, y});
                tree.storePseudoNodes(id, region);
                count++;

                if (count % 10000 == 0) {
                    logger.debug("Loaded {} pseudo nodes...", count);
                }
            } catch (NumberFormatException e) {
                logger.warn("Skipping line {} due to parse error: {}", reader.getLineNumber(), e.getMessage());
            }
        }

        return count;
    }
}

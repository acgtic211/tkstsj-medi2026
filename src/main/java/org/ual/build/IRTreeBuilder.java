package org.ual.build;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.IDocumentIndex;
import org.ual.spatialindex.spatialindex.ISpatialIndex;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.irtree.IRTree;
import org.ual.utils.main.StatisticsLogic;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

public class IRTreeBuilder extends AbstractIndexBuilder<IRTreeBuilder> {
    private static final Logger logger = LogManager.getLogger(IRTreeBuilder.class);

    private AbstractDocumentStore dms;
    private IDocumentIndex invertedFile;

    public IRTreeBuilder setKeywordsWeightStorage(AbstractDocumentStore dms) {
        this.dms = dms;
        return this;
    }

    public IRTreeBuilder setTextualIndex(IDocumentIndex invertedFile) {
        this.invertedFile = invertedFile;
        return this;
    }

    @Override
    public ISpatialIndex build() {
        validateConfiguration();

        if (bulkLoadMethod != null) {
            logger.info("Building IR-Tree using bulk load method: {}", bulkLoadMethod);
            return bulkLoad();
        } else {
            logger.info("Building IR-Tree using incremental load");
            return incrementalLoad();
        }
    }

    private void validateConfiguration() {
        if (dms == null) {
            throw new IllegalStateException("DocumentStore must be set before building IR-Tree");
        }
        if (invertedFile == null) {
            throw new IllegalStateException("InvertedFile must be set before building IR-Tree");
        }
        if (datasetParameters == null || datasetParameters.locationFile == null) {
            throw new IllegalStateException("Dataset parameters and location file must be set");
        }
    }

    private IRTree incrementalLoad() {
        logger.info("Starting incremental IR-Tree construction");
        IRTree tree;

        try (LineNumberReader reader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
            PropertySet ps = createRTreeProperties();
            IStorageManager sm = new NodeStorageManager();
            tree = new IRTree(ps, sm, datasetParameters);

            // Build spatial component
            logger.info("Building spatial component (incremental)...");
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            int count = processDataPoints(reader, tree, false);

            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(tree, count, SpatialIndex.getTreeVariantString(treeVariant));
            validateTree(tree);
            logger.info("Spatial component built successfully with {} data points", count);

        } catch (IOException e) {
            logger.error("Failed to build IR-Tree spatial component", e);
            throw new RuntimeException("Failed to build IR-Tree spatial component.", e);
        }

        // Build textual component
        buildTextualComponent(tree);

        logger.info("IR-Tree construction completed successfully");
        return tree;
    }

    private IRTree bulkLoad() {
        logger.info("Starting bulk load IR-Tree construction with method: {}", bulkLoadMethod);
        IRTree tree;

        try (LineNumberReader reader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
            PropertySet ps = createRTreeProperties();
            ps.setProperty("BulkLoadMethod", bulkLoadMethod);

            IStorageManager sm = new NodeStorageManager();
            tree = new IRTree(ps, sm, datasetParameters);

            // Build spatial component using bulk loading
            logger.info("Building spatial component (bulk load)...");
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            int count = processDataPoints(reader, tree, true);

            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(tree, count, SpatialIndex.getTreeVariantString(treeVariant));
            validateTree(tree);
            logger.info("Spatial component bulk loaded successfully with {} data points", count);

        } catch (IOException e) {
            logger.error("Failed to bulk load IR-Tree spatial component", e);
            throw new RuntimeException("Failed to build IR-Tree spatial component.", e);
        }

        // Build textual component
        buildTextualComponent(tree);

        logger.info("IR-Tree bulk load construction completed successfully");
        return tree;
    }

    private void buildTextualComponent(IRTree tree) {
        logger.info("Building textual component...");
        logger.debug("Using DocumentStore: {}, InvertedFile: {}",
                dms.getClass().getSimpleName(),
                invertedFile.getClass().getSimpleName());

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        tree.createIRTree(dms, invertedFile);

        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("IR");
        validateTree(tree);

        logger.info("Textual component built successfully");
    }
}

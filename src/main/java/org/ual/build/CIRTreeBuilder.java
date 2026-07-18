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
import org.ual.spatiotextualindex.cirtree.CIRTree;
import org.ual.utils.main.StatisticsLogic;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;

public class CIRTreeBuilder extends AbstractIndexBuilder<CIRTreeBuilder> {
    private static final Logger logger = LogManager.getLogger(CIRTreeBuilder.class);

    private AbstractDocumentStore dms;
    private IDocumentIndex invertedFile;
    private int numOfClusters;
    private HashMap<Integer, Integer> clusterTree;

    public CIRTreeBuilder setKeywordsWeightStorage(AbstractDocumentStore dms) {
        this.dms = dms;
        return this;
    }

    public CIRTreeBuilder setTextualIndex(IDocumentIndex invertedFile) {
        this.invertedFile = invertedFile;
        return this;
    }

    public CIRTreeBuilder setNumOfClusters(int numOfClusters) {
        this.numOfClusters = numOfClusters;
        return this;
    }

    public CIRTreeBuilder setClusterTree(HashMap<Integer, Integer> clusterTree) {
        this.clusterTree = clusterTree;
        return this;
    }

    @Override
    public ISpatialIndex build() {
        validateConfiguration();

        if (bulkLoadMethod != null) {
            logger.warn("Bulk loading is not supported for CIR-Tree, falling back to incremental construction");
        }

        logger.info("Building CIR-Tree using incremental load");
        return incrementalLoad();
    }

    private void validateConfiguration() {
        if (dms == null) {
            throw new IllegalStateException("DocumentStore must be set before building CIR-Tree");
        }
        if (invertedFile == null) {
            throw new IllegalStateException("InvertedFile must be set before building CIR-Tree");
        }
        if (datasetParameters == null || datasetParameters.locationFile == null) {
            throw new IllegalStateException("Dataset parameters and location file must be set");
        }
        if (numOfClusters <= 0) {
            throw new IllegalStateException("NumOfClusters must be set to a positive value");
        }
        if (clusterTree == null || clusterTree.isEmpty()) {
            throw new IllegalStateException("ClusterTree must be set and non-empty");
        }
    }

    private CIRTree incrementalLoad() {
        logger.info("Starting incremental CIR-Tree construction");
        logger.debug("Parameters - numOfClusters: {}, clusterTree size: {}",
                numOfClusters, clusterTree.size());

        CIRTree tree;

        try (LineNumberReader reader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
            PropertySet ps = createRTreeProperties();
            ps.setProperty("NumberOfClusters", numOfClusters);

            IStorageManager sm = new NodeStorageManager();
            tree = new CIRTree(ps, sm, datasetParameters);

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
            logger.error("Failed to build CIR-Tree spatial component", e);
            throw new RuntimeException("Failed to build CIR-Tree spatial component.", e);
        }

        // Build textual component
        buildTextualComponent(tree);

        logger.info("CIR-Tree construction completed successfully");
        return tree;
    }

    private void buildTextualComponent(CIRTree tree) {
        logger.info("Building textual component...");
        logger.debug("Using DocumentStore: {}, InvertedFile: {}, Clusters: {}",
                dms.getClass().getSimpleName(),
                invertedFile.getClass().getSimpleName(),
                numOfClusters);

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        tree.createCIRTree(clusterTree, dms, invertedFile);

        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("CIR");
        validateTree(tree);

        logger.info("Textual component built successfully");
    }
}

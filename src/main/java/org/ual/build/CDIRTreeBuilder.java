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
import org.ual.spatiotextualindex.cdirtree.CDIRTree;
import org.ual.utils.main.StatisticsLogic;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashMap;

public class CDIRTreeBuilder extends AbstractIndexBuilder<CDIRTreeBuilder> {
    private static final Logger logger = LogManager.getLogger(CDIRTreeBuilder.class);

    private AbstractDocumentStore dms;
    private IDocumentIndex invertedFile;
    private double betaArea;
    private int maxWord;
    private int numOfClusters;
    private HashMap<Integer, Integer> clusterTree;

    public CDIRTreeBuilder setKeywordsWeightStorage(AbstractDocumentStore dms) {
        this.dms = dms;
        return this;
    }

    public CDIRTreeBuilder setTextualIndex(IDocumentIndex invertedFile) {
        this.invertedFile = invertedFile;
        return this;
    }

    public CDIRTreeBuilder setBetaArea(double betaArea) {
        this.betaArea = betaArea;
        return this;
    }

    public CDIRTreeBuilder setMaxWord(int maxWord) {
        this.maxWord = maxWord;
        return this;
    }

    public CDIRTreeBuilder setNumOfClusters(int numOfClusters) {
        this.numOfClusters = numOfClusters;
        return this;
    }

    public CDIRTreeBuilder setClusterTree(HashMap<Integer, Integer> clusterTree) {
        this.clusterTree = clusterTree;
        return this;
    }

    @Override
    public ISpatialIndex build() {
        validateConfiguration();

        if (bulkLoadMethod != null) {
            logger.warn("Bulk loading is not supported for CDIR-Tree, falling back to incremental construction");
        }

        logger.info("Building CDIR-Tree using incremental load");
        return incrementalLoad();
    }

    private void validateConfiguration() {
        if (dms == null) {
            throw new IllegalStateException("DocumentStore must be set before building CDIR-Tree");
        }
        if (invertedFile == null) {
            throw new IllegalStateException("InvertedFile must be set before building CDIR-Tree");
        }
        if (datasetParameters == null || datasetParameters.locationFile == null) {
            throw new IllegalStateException("Dataset parameters and location file must be set");
        }
        if (betaArea <= 0) {
            throw new IllegalStateException("BetaArea must be set to a positive value");
        }
        if (maxWord <= 0) {
            throw new IllegalStateException("MaxWord must be set to a positive value");
        }
        if (numOfClusters <= 0) {
            throw new IllegalStateException("NumOfClusters must be set to a positive value");
        }
        if (clusterTree == null || clusterTree.isEmpty()) {
            throw new IllegalStateException("ClusterTree must be set and non-empty");
        }
    }

    private CDIRTree incrementalLoad() {
        logger.info("Starting incremental CDIR-Tree construction");
        logger.debug("Parameters - betaArea: {}, maxWord: {}, numOfClusters: {}, clusterTree size: {}",
                betaArea, maxWord, numOfClusters, clusterTree.size());

        CDIRTree tree;

        try (LineNumberReader reader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
            AbstractDocumentStore.maxWord = maxWord; // TODO: Refactor to avoid global state
            logger.warn("Using global state for maxWord - needs refactoring");

            PropertySet ps = createRTreeProperties();
            ps.setProperty("BetaArea", betaArea);
            ps.setProperty("NumberOfClusters", numOfClusters);

            IStorageManager sm = new NodeStorageManager();
            tree = new CDIRTree(ps, sm, dms, datasetParameters);

            // Build spatial component
            logger.info("Building spatial component (incremental)...");
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            int count = processDataPoints(reader, tree, true);

            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(tree, count, SpatialIndex.getTreeVariantString(treeVariant));
            validateTree(tree);
            logger.info("Spatial component built successfully with {} data points", count);

        } catch (IOException e) {
            logger.error("Failed to build CDIR-Tree spatial component", e);
            throw new RuntimeException("Failed to build CDIR-Tree spatial component.", e);
        }

        // Build textual component
        buildTextualComponent(tree);

        logger.info("CDIR-Tree construction completed successfully");
        return tree;
    }

    private void buildTextualComponent(CDIRTree tree) {
        logger.info("Building textual component...");
        logger.debug("Using DocumentStore: {}, InvertedFile: {}, Clusters: {}",
                dms.getClass().getSimpleName(),
                invertedFile.getClass().getSimpleName(),
                numOfClusters);

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        tree.createCDIRTree(clusterTree, dms, invertedFile);

        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("CDIR");
        validateTree(tree);

        logger.info("Textual component built successfully");
    }
}

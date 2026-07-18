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
import org.ual.spatiotextualindex.dirtree.DIRTree;
import org.ual.utils.main.StatisticsLogic;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;

public class DIRTreeBuilder extends AbstractIndexBuilder<DIRTreeBuilder> {
    private static final Logger logger = LogManager.getLogger(DIRTreeBuilder.class);

    private AbstractDocumentStore dms;
    private IDocumentIndex invertedFile;
    private double betaArea;
    private int maxWord;

    public DIRTreeBuilder setKeywordsWeightStorage(AbstractDocumentStore dms) {
        this.dms = dms;
        return this;
    }

    public DIRTreeBuilder setTextualIndex(IDocumentIndex invertedFile) {
        this.invertedFile = invertedFile;
        return this;
    }

    public DIRTreeBuilder setBetaArea(double betaArea) {
        this.betaArea = betaArea;
        return this;
    }

    public DIRTreeBuilder setMaxWord(int maxWord) {
        this.maxWord = maxWord;
        return this;
    }

    @Override
    public ISpatialIndex build() {
        validateConfiguration();

        if (bulkLoadMethod != null) {
            logger.warn("Bulk loading is not supported for DIR-Tree, falling back to incremental construction");
        }

        logger.info("Building DIR-Tree using incremental load");
        return incrementalLoad();
    }

    private void validateConfiguration() {
        if (dms == null) {
            throw new IllegalStateException("DocumentStore must be set before building DIR-Tree");
        }
        if (invertedFile == null) {
            throw new IllegalStateException("InvertedFile must be set before building DIR-Tree");
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
    }

    private DIRTree incrementalLoad() {
        logger.info("Starting incremental DIR-Tree construction");
        logger.debug("Parameters - betaArea: {}, maxWord: {}", betaArea, maxWord);

        DIRTree tree;

        try (LineNumberReader reader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
            AbstractDocumentStore.maxWord = maxWord; // TODO: Refactor to avoid global state
            logger.warn("Using global state for maxWord - needs refactoring");

            PropertySet ps = createRTreeProperties();
            ps.setProperty("BetaArea", betaArea);

            IStorageManager sm = new NodeStorageManager();
            tree = new DIRTree(ps, sm, dms, datasetParameters);

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
            logger.error("Failed to build DIR-Tree spatial component", e);
            throw new RuntimeException("Failed to build DIR-Tree spatial component.", e);
        }

        // Build textual component
        buildTextualComponent(tree);

        logger.info("DIR-Tree construction completed successfully");
        return tree;
    }

    private void buildTextualComponent(DIRTree tree) {
        logger.info("Building textual component...");
        logger.debug("Using DocumentStore: {}, InvertedFile: {}",
                dms.getClass().getSimpleName(),
                invertedFile.getClass().getSimpleName());

        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        tree.createDIRTree(dms, invertedFile);

        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("DIR");
        validateTree(tree);

        logger.info("Textual component built successfully");
    }
}

package org.ual.utils.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.algorithm.kmean.KMean;
import org.ual.build.*;
import org.ual.document.WeightCompute;
import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.invertedlist.InvertedListIndex;
//import org.ual.documentindex.signedblocknew.SignedBlockInvertedIndexNEW;
//import org.ual.documentindex.signedinvertedlist.ClusteredSignedInvertedListIndex;
//import org.ual.documentindex.signedinvertedlist.SignedInvertedListIndex;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.ISpatialIndex;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storage.TreeMapDocumentStore;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.utils.main.StatisticsLogic;
import org.ual.utils.sampling.SamplingStrategy;

import java.util.HashMap;

public class IndexLogicNEW {

    private static final Logger logger = LogManager.getLogger(IndexLogicNEW.class);

    private AbstractDocumentStore keywordsWeightStorage;
    private ISpatialIndex spatialIndex;
    private IDocumentIndex textualIndex;
    private HashMap<Integer, Integer> clusterTree;
    private final DatasetParameters datasetParameters;
    private final StatisticsLogic statisticsLogic;
    private final double datasetUsagePercentage;
    private final SamplingStrategy.SamplingMethod samplingMethod;
    private final long samplingRandomSeed;
    private final int samplingStartLine;

    public IndexLogicNEW(StatisticsLogic statisticsLogic, DatasetParameters datasetParameters, double datasetUsagePercentage) {
        this(statisticsLogic, datasetParameters, datasetUsagePercentage,
                SamplingStrategy.SamplingMethod.RANDOMIZED, 42L, 0);
    }

    public IndexLogicNEW(StatisticsLogic statisticsLogic, DatasetParameters datasetParameters, double datasetUsagePercentage,
                         SamplingStrategy.SamplingMethod samplingMethod, long samplingRandomSeed, int samplingStartLine) {
        this.datasetParameters = datasetParameters;
        this.statisticsLogic = statisticsLogic;
        this.datasetUsagePercentage = datasetUsagePercentage;
        this.samplingMethod = samplingMethod;
        this.samplingRandomSeed = samplingRandomSeed;
        this.samplingStartLine = samplingStartLine;
    }

    // Document Store Builders
    public void createHashMapDocStore(float smoothingFactor) {
        keywordsWeightStorage = new HashMapDocumentStore();
        logger.info("Computing Term Weights in Memory using HashMap with {}% of dataset", datasetUsagePercentage * 100);
        WeightCompute.ComputeTF_IDFWeights(
                datasetParameters.keywordFile,
                keywordsWeightStorage,
                smoothingFactor,
                datasetUsagePercentage,
                samplingMethod,
                samplingRandomSeed,
                samplingStartLine
        );
        logger.info("{} Keywords computed.", keywordsWeightStorage.getSize());
    }

    public void createTreeMapDocStore(float smoothingFactor) {
        keywordsWeightStorage = new TreeMapDocumentStore();
        logger.info("Computing Term Weights in Memory using TreeMap");
        WeightCompute.ComputeTermWeights(datasetParameters.keywordFile, keywordsWeightStorage, smoothingFactor);
        logger.info("{} Keywords computed.", keywordsWeightStorage.getSize());
    }

    private void createClusterTree(int numClusters, int numMoves) {
        logger.info("Creating cluster tree with Kmean medoids...");
        clusterTree = KMean.calculateKMean(keywordsWeightStorage, numClusters, numMoves);
        logger.info("Done");
    }

    // Textual Index Builders
//    public void createHashMapTextualIndex(int numberOfClusters) {
//        logger.info("Initializing document index using HashMap");
//        //textualIndex = new HashMapInvertedFileIndex(numberOfClusters);
//        textualIndex = new OLDSignedInvertedListIndex(numberOfClusters);
//    }

//    public void createArrayListTextualIndex(int numberOfClusters) {
//        logger.error("This method was deprecated and removed because the ArrayList implementation of the inverted file index is no longer supported. Please use createHashMapTextualIndex instead.");
//        System.exit(-1);
//    }

    public void createInvertedListIndex(int numberOfClusters) {
        logger.info("Initializing Inverted List textual index using HashMap");
        textualIndex = new InvertedListIndex(numberOfClusters);
    }

    public void createSignedInvertedListIndex(int numberOfClusters) {
        logger.info("Initializing Signed Inverted List textual index (text-only)");
        //textualIndex = new OLDSignedInvertedListIndex(numberOfClusters);
        //textualIndex = new OLDSignedInvertedListIndex(numberOfClusters);
//        textualIndex = new ClusteredSignedInvertedListIndex(numberOfClusters);
        throw new UnsupportedOperationException("Experimental signet index support is not implemented yet.");
//        textualIndex = new SignedInvertedListIndex(numberOfClusters);
    }

    public void createSignedBlockTextualIndex() {
        logger.info("Initializing document index using Signed Block");

        Region datasetBounds = new Region(
                new double[]{datasetParameters.latitudeStart, datasetParameters.longitudeStart},
                new double[]{datasetParameters.latitudeEnd, datasetParameters.longitudeEnd}
        );

//        textualIndex = new SignedBlockInvertedIndex(datasetBounds);
        throw new UnsupportedOperationException("Experimental aggregate index support is not implemented yet.");
//        textualIndex = new SignedBlockInvertedIndexNEW(datasetBounds);
    }

    // Spatial Index Builders using AbstractIndexBuilder
    public void createRTree(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor) {
        logger.info("Creating R-Tree with {}% of dataset", datasetUsagePercentage * 100);

        spatialIndex = new RTreeBuilder()
                .setDatasetParameters(datasetParameters)
                .setFanout(fanout)
                .setFillFactor(fillFactor)
                .setDimension(dimension)
                .setTreeVariant(rtreeVariant)
                .setNearMinimumOverlapFactor(nearMinimumOverlapFactor)
                .setDatasetUsagePercentage(datasetUsagePercentage)
                .setSamplingMethod(samplingMethod)
                .setSamplingRandomSeed(samplingRandomSeed)
                .setSamplingStartLine(samplingStartLine)
                .build();

        logger.info("Done");
    }

    public void createRTreeWithBulkLoading(int fanout, float fillFactor, int dimension, int rtreeVariant,
                                           int nearMinimumOverlapFactor, BulkLoadMethod bulkLoadMethod) {
        logger.info("Creating R-Tree using Bulk Loading with {}% of dataset", datasetUsagePercentage * 100);

        spatialIndex = new RTreeBuilder()
                .setDatasetParameters(datasetParameters)
                .setFanout(fanout)
                .setFillFactor(fillFactor)
                .setDimension(dimension)
                .setTreeVariant(rtreeVariant)
                .setNearMinimumOverlapFactor(nearMinimumOverlapFactor)
                .setBulkLoadMethod(bulkLoadMethod)
                .setDatasetUsagePercentage(datasetUsagePercentage)
                .setSamplingMethod(samplingMethod)
                .setSamplingRandomSeed(samplingRandomSeed)
                .setSamplingStartLine(samplingStartLine)
                .build();

        logger.info("Done");
    }

    // Spatio-Textual Index Builders
    public void createIRTree(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor) {
        logger.info("Creating IR-Tree with {}% of dataset", datasetUsagePercentage * 100);

        spatialIndex = new IRTreeBuilder()
                .setDatasetParameters(datasetParameters)
                .setFanout(fanout)
                .setFillFactor(fillFactor)
                .setDimension(dimension)
                .setTreeVariant(rtreeVariant)
                .setNearMinimumOverlapFactor(nearMinimumOverlapFactor)
                .setKeywordsWeightStorage(keywordsWeightStorage)
                .setTextualIndex(textualIndex)
                .setDatasetUsagePercentage(datasetUsagePercentage)
                .setSamplingMethod(samplingMethod)
                .setSamplingRandomSeed(samplingRandomSeed)
                .setSamplingStartLine(samplingStartLine)
                .build();

        logger.info("IR-Tree created successfully using {}% of dataset", datasetUsagePercentage * 100);
    }

    public void createIRTreeWithBulkLoading(int fanout, float fillFactor, int dimension, int rtreeVariant,
                                            int nearMinimumOverlapFactor, BulkLoadMethod bulkLoadMethod) {
        logger.info("BulkLoad IR-Tree with {}% of dataset", datasetUsagePercentage * 100);

        spatialIndex = new IRTreeBuilder()
                .setDatasetParameters(datasetParameters)
                .setFanout(fanout)
                .setFillFactor(fillFactor)
                .setDimension(dimension)
                .setTreeVariant(rtreeVariant)
                .setNearMinimumOverlapFactor(nearMinimumOverlapFactor)
                .setBulkLoadMethod(bulkLoadMethod)
                .setKeywordsWeightStorage(keywordsWeightStorage)
                .setTextualIndex(textualIndex)
                .setDatasetUsagePercentage(datasetUsagePercentage)
                .setSamplingMethod(samplingMethod)
                .setSamplingRandomSeed(samplingRandomSeed)
                .setSamplingStartLine(samplingStartLine)
                .build();

        logger.info("Done");
    }

    public void createDIRTree(int fanout, float fillFactor, int dimension, int rtreeVariant,
                              int nearMinimumOverlapFactor, int maxWord, double betaArea) {
        logger.info("Creating DIR-Tree with {}% of dataset", datasetUsagePercentage * 100);

        spatialIndex = new DIRTreeBuilder()
                .setDatasetParameters(datasetParameters)
                .setFanout(fanout)
                .setFillFactor(fillFactor)
                .setDimension(dimension)
                .setTreeVariant(rtreeVariant)
                .setNearMinimumOverlapFactor(nearMinimumOverlapFactor)
                .setMaxWord(maxWord)
                .setBetaArea(betaArea)
                .setKeywordsWeightStorage(keywordsWeightStorage)
                .setTextualIndex(textualIndex)
                .setDatasetUsagePercentage(datasetUsagePercentage)
                .setSamplingMethod(samplingMethod)
                .setSamplingRandomSeed(samplingRandomSeed)
                .setSamplingStartLine(samplingStartLine)
                .build();

        logger.info("Done");
    }

    public void createCIRTree(int fanout, float fillFactor, int dimension, int rtreeVariant,
                              int nearMinimumOverlapFactor, int numClusters, int numMoves) {
        logger.info("Creating CIR-Tree with {}% of dataset", datasetUsagePercentage * 100);

        createClusterTree(numClusters, numMoves);

        spatialIndex = new CIRTreeBuilder()
                .setDatasetParameters(datasetParameters)
                .setFanout(fanout)
                .setFillFactor(fillFactor)
                .setDimension(dimension)
                .setTreeVariant(rtreeVariant)
                .setNearMinimumOverlapFactor(nearMinimumOverlapFactor)
                .setNumOfClusters(numClusters)
                .setClusterTree(clusterTree)
                .setKeywordsWeightStorage(keywordsWeightStorage)
                .setTextualIndex(textualIndex)
                .setDatasetUsagePercentage(datasetUsagePercentage)
                .setSamplingMethod(samplingMethod)
                .setSamplingRandomSeed(samplingRandomSeed)
                .setSamplingStartLine(samplingStartLine)
                .build();

        logger.info("Done");
    }

    public void createCDIRTree(int fanout, float fillFactor, int dimension, int rtreeVariant,
                               int nearMinimumOverlapFactor, int maxWord, double betaArea,
                               int numClusters, int numMoves) {
        logger.info("Creating CDIR-Tree with {}% of dataset", datasetUsagePercentage * 100);

        createClusterTree(numClusters, numMoves);

        spatialIndex = new CDIRTreeBuilder()
                .setDatasetParameters(datasetParameters)
                .setFanout(fanout)
                .setFillFactor(fillFactor)
                .setDimension(dimension)
                .setTreeVariant(rtreeVariant)
                .setNearMinimumOverlapFactor(nearMinimumOverlapFactor)
                .setMaxWord(maxWord)
                .setBetaArea(betaArea)
                .setNumOfClusters(numClusters)
                .setClusterTree(clusterTree)
                .setKeywordsWeightStorage(keywordsWeightStorage)
                .setTextualIndex(textualIndex)
                .setDatasetUsagePercentage(datasetUsagePercentage)
                .setSamplingMethod(samplingMethod)
                .setSamplingRandomSeed(samplingRandomSeed)
                .setSamplingStartLine(samplingStartLine)
                .build();

        logger.info("Done");
    }

    // Getters for compatibility with AbstractQueryExecutor
    public ISpatialIndex getSpatialIndex() {
        return spatialIndex;
    }

    public AbstractIRTree getAbstractIRTree() {
        if (spatialIndex instanceof AbstractIRTree) {
            return (AbstractIRTree) spatialIndex;
        }
        throw new IllegalStateException("Spatial index is not an AbstractIRTree instance");
    }

    public IDocumentIndex getTextualIndex() {
        return textualIndex;
    }

    public AbstractDocumentStore getKeywordsWeightStorage() {
        return keywordsWeightStorage;
    }

    public HashMap<Integer, Integer> getClusterTree() {
        return clusterTree;
    }

    public DatasetParameters getDatasetParameters() {
        return datasetParameters;
    }

    public double getDatasetUsagePercentage() {
        return datasetUsagePercentage;
    }
}

package org.ual.utils.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.algorithm.kmean.KMean;
import org.ual.build.*;
import org.ual.document.WeightCompute;
import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.spatialindex.spatialindex.ISpatialIndex;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storage.TreeMapDocumentStore;

import java.util.*;

public class IndexLogic {
    static AbstractDocumentStore keywordsWeightStorage;
    public static ISpatialIndex spatialIndex;
    static IDocumentIndex textualIndex;
    static HashMap<Integer, Integer> clusterTree;
    DatasetParameters datasetParameters;
    double datasetUsagePercentage;

    StatisticsLogic statisticsLogic;

    private static final Logger logger = LogManager.getLogger(IndexLogic.class);

    public IndexLogic(StatisticsLogic statisticsLogic, DatasetParameters datasetParameters, double datasetUsagePercentage) {
        this.datasetParameters = datasetParameters;
        this.statisticsLogic = statisticsLogic;
        this.datasetUsagePercentage = datasetUsagePercentage;
    }

    //==============================================================================================================================
    //============================================ Document Store Builders ================================================
    //==============================================================================================================================

    // TODO Clean up this method
    public void createHashMapDS(float smoothingFactor) {
        keywordsWeightStorage = new HashMapDocumentStore();
        logger.info("Computing Term Weights in Memory using HashMap");
        //WeightCompute.ComputeTermWeights(datasetParameters.keywordFile, keywordsWeightStorage, smoothingFactor);
        //WeightCompute.ComputeTF_IDFWeights(datasetParameters.keywordFile, keywordsWeightStorage, 0.0);
        WeightCompute.ComputeTF_IDFWeights(datasetParameters.keywordFile, keywordsWeightStorage, 0.0, datasetUsagePercentage);
        logger.info("{} Keywords computed.", keywordsWeightStorage.getSize());
    }

    public void createTreeMapDS(float smoothingFactor) {
        keywordsWeightStorage = new TreeMapDocumentStore();
        logger.info("Computing Term Weights in Memory using TreeMap");
        WeightCompute.ComputeTermWeights(datasetParameters.keywordFile, keywordsWeightStorage, smoothingFactor);
        logger.info("{} Keywords computed.", keywordsWeightStorage.getSize());
    }

    private void createClusterTree(int numClusters, int numMoves){
        logger.info("Creating cluster tree with Kmean medoids...");
        clusterTree = KMean.calculateKMean(keywordsWeightStorage, numClusters, numMoves);
        logger.info("Done");
    }

    //==============================================================================================================================
    //============================================ RTree Builders================================================
    //==============================================================================================================================

    private void createRtree(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor) {
        logger.info("Creating R-Tree with parameters: \nfanout:{} \nfillfactor:{} \ndimensions:{}", fanout, fillFactor, dimension);
        spatialIndex = BuildSpatialIndices.buildRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor);
        logger.info("Done");
    }

    private void createRtreeWithBulkLoading(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor, BulkLoadMethod bulkLoadMethod) {
        logger.info("Creating R-Tree using Bulk Loading with parameters: \nfanout:{} \nfillfactor:{} \ndimensions:{}", fanout, fillFactor, dimension);
        spatialIndex = BuildSpatialIndices.bulkloadRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, bulkLoadMethod);
        logger.info("Done");
    }


    //==============================================================================================================================
    //=========================================== InvertedList Index Builders ======================================================
    //==============================================================================================================================


    public void createHashMapTextualIndex(int numberOfClusters) {
        logger.info("Initializing document index using HashMap");
        //textualIndex = new HashMapInvertedFileIndex(numberOfClusters);
        textualIndex = new InvertedListIndex(numberOfClusters);
    }

    @Deprecated
    public void createArrayListTextualIndex(int numberOfClusters) {
        logger.error("This method was deprecated and removed because the ArrayList implementation of the inverted file index is no longer supported. Please use createHashMapTextualIndex instead.");
        System.exit(-1);
        //logger.warn("[TESTING] Initializing legacy HashMap document index");
        //textualIndex = new HashMapInvertedFileIndex(numberOfClusters);
    }
    
    //==============================================================================================================================
    //============================================ IRTree & Variants Builders================================================
    //==============================================================================================================================
    

//    public void createIRtreeNEW(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor) {
//        logger.info("Creating IR-Tree");
//        //spatialIndex = BuildSpatialIndices.buildIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, keywordsWeightStorage, textualIndex);
//        spatialIndex = BuildSpatialIndices.buildRefactorIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, keywordsWeightStorage, textualIndex);
//        logger.info("Done");
//    }

    public void createIRtreeNEW(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor) {
        logger.info("Creating IR-Tree with {}% of dataset", datasetUsagePercentage * 100);
        spatialIndex = BuildSpatialIndices.buildRefactorIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, keywordsWeightStorage, textualIndex, datasetUsagePercentage);
        logger.info("Done");
    }


    public void createIRtreeWithBulkLoadingNEW(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor, BulkLoadMethod bulkLoadMethod) {
        logger.info("BulkLoad IR-Tree");
        //spatialIndex = BuildSpatialIndices.bulkloadIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, bulkLoadMethod, keywordsWeightStorage, textualIndex);   // TODO REMOVE TREE VARIANT
        spatialIndex = BuildSpatialIndices.bulkloadRefactorIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, bulkLoadMethod, keywordsWeightStorage, textualIndex);   // TODO REMOVE TREE VARIANT
        logger.info("Done");
    }

    public void createDIRtreeNEW(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor, int maxWord, double betaArea) {
        logger.info("Creating DIR-Tree");

        //spatialIndex = BuildSpatialIndices.buildDIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, maxWord, betaArea, keywordsWeightStorage, textualIndex);
        spatialIndex = BuildSpatialIndices.buildRefactorDIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, maxWord, betaArea, keywordsWeightStorage, textualIndex);
        logger.info("Done");
    }

    public void createCIRtreeNEW(int fanout, float fillFactor, int dimension, int rtreeVariant, int nearMinimumOverlapFactor, int numClusters, int numMoves) {
        logger.info("Creating CIR-Tree");
        // Calculate cluster file with Kmean
        createClusterTree(numClusters, numMoves);

        // Build RTree index with location data
        //spatialIndex = BuildSpatialIndices.buildCIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, numClusters, clusterTree, keywordsWeightStorage, textualIndex);
        spatialIndex = BuildSpatialIndices.buildRefactorCIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, numClusters, clusterTree, keywordsWeightStorage, textualIndex);
        logger.info("Done");
    }

    public void createCDIRtreeNEW(int fanout, float fillFactor, int dimension, int rtreeVariant,  int nearMinimumOverlapFactor, int maxWord, double betaArea, int numClusters, int numMoves) {
        logger.info("Creating CDIR-Tree");
        // Calculate cluster file with Kmean
        createClusterTree(numClusters, numMoves);

        spatialIndex = BuildSpatialIndices.buildRefactorCDIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, maxWord, betaArea, numClusters, clusterTree, keywordsWeightStorage, textualIndex);
        //spatialIndex = BuildSpatialIndices.buildCDIRTree(datasetParameters, fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor, maxWord, betaArea, numClusters, clusterTree, keywordsWeightStorage, textualIndex);
        logger.info("Done");
    }

    //================================================================
    //==================== Helper Methods ============================
    //================================================================

//    public static List<String> filterDataByPercentage(List<String> allData, double percentage) {
//        if (percentage >= 1.0) return allData;
//
//        int targetSize = (int) (allData.size() * percentage);
//        List<String> shuffled = new ArrayList<>(allData);
//        Collections.shuffle(shuffled, new Random(42)); // Use fixed seed for reproducibility
//        return shuffled.subList(0, targetSize);
//    }
}

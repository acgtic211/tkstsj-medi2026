package org.ual.documentindex;

import org.ual.spatialindex.storage.WeightEntry;

import java.util.*;

/**
 * Interface for document index
 */
public interface IDocumentIndex {
    //********** Index management functions **********//
    void create(int nodeId);

    /**
     * Text-only ingestion capability.
     *
     * <p>Implemented by {@link ITextOnlyDocumentIndex}. Spatial/aggregate indexes may
     * rely on specialized ingestion APIs and keep this default unsupported.
     */
    default void addDocument(int nodeId, int docId, List<WeightEntry> document) {
        throw new UnsupportedOperationException(
                "Text-only ingestion is not supported by this index type. " +
                        "Use ITextOnlyDocumentIndex or an aggregate-specific addDocument overload."
        );
    }

    /**
     * Cluster-aware text-only ingestion capability.
     */
    default void addDocument(int nodeId, int docId, List<WeightEntry> document, int cluster) {
        throw new UnsupportedOperationException(
                "Cluster-aware text-only ingestion is not supported by this index type."
        );
    }

    List<WeightEntry> store(int nodeId);
    List<List<WeightEntry>> storeClusterEnhance(int nodeId);

    //********** Ranking functions **********//
    @Deprecated
    Map<Integer, Double> rankingSum(int nodeId, List<Integer> keywords);

    default Map<Integer, Double> rankingSum(int nodeId, List<Integer> keywords, List<Double> keywordWeights) {
        return rankingSum(nodeId, keywords, keywordWeights, RankingSumMode.defaultMode());
    }

    Map<Integer, Double> rankingSum(int nodeId, List<Integer> keywords, List<Double> keywordWeights, RankingSumMode scoringMode);

    default Map<Integer, Double> rankingSumClusterEnhance(int nodeId, List<Integer> keywords, List<Double> keywordWeights) {
        return rankingSumClusterEnhance(nodeId, keywords, keywordWeights, RankingSumMode.defaultMode());
    }

    Map<Integer, Double> rankingSumClusterEnhance(int nodeId, List<Integer> keywords, List<Double> keywordWeights, RankingSumMode scoringMode);

    double rankingSum(int nodeIdA, int nodeIdB);

    //********** Filtering functions **********//
    Map<Integer, Integer> booleanFilter(int nodeId, List<Integer> keywords);

    /**
     * Cluster-aware boolean filter.
     *
     * <p>Returns a map of document IDs to the number of query keywords that match in that document,
     * taking cluster structure into account.  The default delegates to {@link #booleanFilter} so
     * that implementations without cluster support (e.g. {@code InvertedListIndex}) need not
     * override it.
     */
    default Map<Integer, Integer> booleanFilterClusterEnhance(int nodeId, List<Integer> keywords) {
        return booleanFilter(nodeId, keywords);
    }

    //********** Similarity functions **********//

    Map<Integer, Double> nodesConstraintTextualSim(int nodeId1, int nodeId2, List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType);
    Map<Integer, Double> nodesConstraintTextualSimForClusters(int nodeId1, int nodeId2, List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType);

    double nodesTextualSim(int nodeId1, int nodeId2, SimilarityType similarityType);
    double nodesTextualSimForClusters(int nodeId1, int nodeId2, SimilarityType similarityType);
    default double nodesDocumentSim(int nodeIdA, int nodeIdB, int docIdA, int docIdB, SimilarityType similarityType) {
        System.err.println("Operation not implemented for this index type.");
        return 0.0;
    }

    default double crossDatasetTextualSim(int internalNode, int externalNode, IDocumentIndex externalInvList, SimilarityType similarityType, BoundLimit boundLimit) {
        System.err.println("Operation not implemented for this index type.");
        return 0.0;
    }

    default double crossDatasetConstraintPerNodeTextualSim(int internalNodeId, int externalNodeId, IDocumentIndex externalInvList, Map<Integer, Double> internalKeywordWeights, Map<Integer, Double> externalKeywordWeights, SimilarityType similarityType) {
        System.err.println("Operation not implemented for this index type.");
        return 0.0;
    }

    default Map<Integer, Double> crossDatasetConstraintTextualSim(int nodeId, List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType) {
        System.err.println("Operation not implemented for this index type.");
        return Collections.emptyMap();
    }

    default double crossDatasetDocumentSim(int internalNodeId, int externalNodeId, int internalDocId, int externalDocId,
                                           IDocumentIndex externalInvList, SimilarityType similarityType) {
        System.err.println("Operation not implemented for this index type.");
        return 0.0;
    }

    // EXPERIMENTAL

//    default Map<Integer, Double> calculateTextualRelevancy(int nodeId1, int nodeId2,
//                                                           List<Integer> keywords, List<Double> keywordWeights,
//                                                           SimilarityType similarityType, QueryStrategy queryStrategy) {
//        QueryStrategy effectiveStrategy = QueryStrategy.orDefault(queryStrategy);
//        if (effectiveStrategy == QueryStrategy.FULL_JOIN) {
//            return calculateTextualRelevancy(nodeId1, nodeId2, similarityType);
//        }
//        return calculateTextualRelevancy(nodeId1, nodeId2, keywords, keywordWeights, similarityType);
//    }
//
//    default Map<Integer, Double> calculateTextualRelevancyClusterEnhance(int nodeId1, int nodeId2,
//                                                                         List<Integer> keywords, List<Double> keywordWeights,
//                                                                         SimilarityType similarityType, QueryStrategy queryStrategy) {
//        QueryStrategy effectiveStrategy = QueryStrategy.orDefault(queryStrategy);
//        if (effectiveStrategy == QueryStrategy.FULL_JOIN) {
//            return calculateTextualRelevancyClusterEnhance(nodeId1, nodeId2, similarityType);
//        }
//        return calculateTextualRelevancyClusterEnhance(nodeId1, nodeId2, keywords, keywordWeights, similarityType);
//    }

//    default Map<Integer, Double> calculateTextualRelevancyClusterEnhance(int nodeId1, int nodeId2,
//                                                                         SimilarityType similarityType) {
//        return calculateTextualRelevancy(nodeId1, nodeId2, similarityType);
//    }



    default double nodesTextualSim(int nodeId1, int nodeId2, SimilarityType similarityType, double spatialThreshold) {
        return nodesTextualSim(nodeId1, nodeId2, similarityType);
    }



    default void printStatistics() {
        System.out.println("No statistics available for this index type.");
    }

    //double calculateTextualRelevancyMultiSet(int nodeIdX, int nodeIdY, IDocumentIndex secondaryInvertedList, SimilarityType similarityType, float spatialThreshold);





//    default Map<Integer, Double> crossDatasetConstraintTextualSim(int nodeIdX, int nodeIdY, IDocumentIndex secondaryInvertedList, List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType) {
//        System.err.println("Operation not implemented for this index type.");
//        return Collections.emptyMap();
//    }



//    Map<Integer, Double> calculateTextualRelevancy(int nodeId1, int nodeId2, SimilarityType similarityType);
//    Map<Integer, Double> calculateTextualRelevancyJaccard(int nodeId1, int nodeId2);

//    double calculateJaccardSimilarity(Set<Integer> terms1, Set<Integer> terms2);
}

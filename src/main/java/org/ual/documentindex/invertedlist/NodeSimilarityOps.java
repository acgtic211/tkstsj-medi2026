package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.SimilarityType;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.*;

import static org.ual.documentindex.invertedlist.PostingListUtils.*;

public class NodeSimilarityOps {
    private static final Logger logger = LogManager.getLogger(NodeSimilarityOps.class);

    protected NodeSimilarityOps() {}


    protected double rankingSum(InvertedListEntry nodeListA, InvertedListEntry nodeListB) {
        Map<Integer, Double> weightsA = new HashMap<>();
        for (WeightEntry entry : nodeListA.extractPseudoDocument()) {
            weightsA.put(entry.getWord(), entry.getWeight());
        }

        double score = 0.0;
        for (WeightEntry entry : nodeListB.extractPseudoDocument()) {
            Double weightA = weightsA.get(entry.getWord());
            if (weightA != null) {
                score += Math.min(weightA, entry.getWeight());
            }
        }

        return score;
    }

    protected double textSimilarity(InvertedListEntry listA, InvertedListEntry listB, SimilarityType  similarityType) {
        // For COSINE, we compute similarity across all terms in both nodes at once,
        // rather than aggregating term-by-term relevance scores like other similarity types.
        if (similarityType == SimilarityType.COSINE) {
            return cosineSimilarity(listA, listB);
        }

        InvertedListEntry smallList = (listA.getPostingListSize() <= listB.getPostingListSize()) ? listA : listB;
        InvertedListEntry largeList = (smallList == listA) ? listB : listA;

        Set<Integer> termsInSmallSet = smallList.getAllTerms();
        Set<Integer> termsInLargeSet = largeList.getAllTerms();

        double totalScore = 0.0;
        for (Integer termId : termsInSmallSet) {
            if (!termsInLargeSet.contains(termId)) continue;

            double relevancyScore = termRelevance(listA, listB, termId, similarityType);

            if (relevancyScore > 0) {
                totalScore += relevancyScore;
            }
        }

        return totalScore;
    }

    protected double textSimilarityForClusters(InvertedListEntry listA, InvertedListEntry listB, SimilarityType similarityType, int numClusters) {
        if (similarityType == SimilarityType.COSINE) {
            return cosineSimilarityForClusters(listA, listB, numClusters);
        }

        InvertedListEntry smallList = (listA.getPostingListSize() <= listB.getPostingListSize()) ? listA : listB;
        InvertedListEntry largeList = (smallList == listA) ? listB : listA;

        Set<Integer> termsInSmallSet = smallList.getAllTerms();
        Set<Integer> termsInLargeSet = largeList.getAllTerms();

        double totalScore = 0.0;
        for (Integer termId : termsInSmallSet) {
            if (!termsInLargeSet.contains(termId)) continue;

            double relevancyScore = termRelevanceForClusters(listA, listB, termId, similarityType, numClusters);

            if (relevancyScore > 0) {
                totalScore += relevancyScore;
            }
        }

        return totalScore;
    }

    protected double textualSimExact(InvertedListEntry listA, InvertedListEntry listB, int docIdA, int docIdB, SimilarityType similarityType) {

        Map<Integer, Double> docVecA = buildDocumentVector(listA, docIdA);
        Map<Integer, Double> docVecB = buildDocumentVector(listB, docIdB);

        if (docVecA.isEmpty() || docVecB.isEmpty()) {
            return 0.0;
        }

        switch (similarityType) {
            case WEIGHTED_SUM:
                return weightedSumDocSim(docVecA, docVecB);
            case WEIGHTED_JACCARD:
                return weightedJaccardDocSim(docVecA, docVecB);
            case COSINE:
                return cosineDocSim(docVecA, docVecB);
            default:
                throw new IllegalStateException("Unsupported SimilarityType: " + similarityType);
        }
    }


    private double termRelevance(InvertedListEntry listA, InvertedListEntry listB, int termId, SimilarityType type) {
        switch (type) {
            case WEIGHTED_SUM:
                return weightedSum(listA, listB, termId);
            case WEIGHTED_JACCARD:
                return weightedJaccard(listA, listB, termId);
            default:
                throw new IllegalStateException("Unsupported SimilarityType: " + type);
        }
    }

    private double termRelevanceForClusters(InvertedListEntry listA, InvertedListEntry listB, int termId, SimilarityType type, int numClusters) {
        switch (type) {
            case WEIGHTED_SUM:
                return weightedSumForClusters(listA, listB, termId, numClusters);
            case WEIGHTED_JACCARD:
                return weightedJaccardForClusters(listA, listB, termId, numClusters);
            default:
                throw new IllegalStateException("Unsupported SimilarityType: " + type);
        }
    }

    private double weightedSum(InvertedListEntry listA, InvertedListEntry listB, int termId) {
        List<PostingListEntry> postingsA = listA.getPostingList(termId);
        List<PostingListEntry> postingsB = listB.getPostingList(termId);

        if (postingsA == null || postingsA.isEmpty() || postingsB == null || postingsB.isEmpty()) {
            return 0.0;
        }

        // Build a lookup of docId -> weight for listB
        Map<Integer, Double> weightsB = new HashMap<>();
        for (PostingListEntry posting : postingsB) {
            weightsB.merge(posting.documentId, posting.weight, Double::max);
        }

        // Sum min(wA, wB) for documents appearing in both lists
        double score = 0.0;
        for (PostingListEntry posting : postingsA) {
            Double weightB = weightsB.get(posting.documentId);
            if (weightB != null) {
                score += Math.min(posting.weight, weightB);
            }
        }

        return score;
    }

    private double weightedSumForClusters(InvertedListEntry listA, InvertedListEntry listB, int termId, int numClusters) {
        Map<Integer, Double> clusterWeightsA = buildClusterMaxWeights(listA.getPostingList(termId), numClusters);
        Map<Integer, Double> clusterWeightsB = buildClusterMaxWeights(listB.getPostingList(termId),  numClusters);

        if (clusterWeightsA.isEmpty() || clusterWeightsB.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        for (int clusterId = 0; clusterId < numClusters; clusterId++) {
            score += Math.min(
                    clusterWeightsA.getOrDefault(clusterId, 0.0),
                    clusterWeightsB.getOrDefault(clusterId, 0.0)
            );
        }
        return score;
    }

    private double weightedJaccard(InvertedListEntry listA, InvertedListEntry listB, int termId) {
        List<PostingListEntry> postingsA = listA.getPostingList(termId);
        List<PostingListEntry> postingsB = listB.getPostingList(termId);

        if (postingsA == null || postingsA.isEmpty() || postingsB == null || postingsB.isEmpty()) {
            return 0.0;
        }

        Map<Integer, Double> weightsA = new HashMap<>();
        for (PostingListEntry posting : postingsA) {
            weightsA.merge(posting.documentId, posting.weight, Double::max);
        }

        Map<Integer, Double> weightsB = new HashMap<>();
        for (PostingListEntry posting : postingsB) {
            weightsB.merge(posting.documentId, posting.weight, Double::max);
        }

        double numerator = 0.0;
        double denominator = 0.0;

        Set<Integer> allDocs = new HashSet<>();
        allDocs.addAll(weightsA.keySet());
        allDocs.addAll(weightsB.keySet());

        for (Integer docId : allDocs) {
            double weightA = weightsA.getOrDefault(docId, 0.0);
            double weightB = weightsB.getOrDefault(docId, 0.0);
            numerator += Math.min(weightA, weightB);
            denominator += Math.max(weightA, weightB);
        }

        return denominator > 0 ? numerator / denominator : 0.0;
    }


    private double weightedJaccardForClusters(InvertedListEntry listA, InvertedListEntry listB, int termId, int numClusters) {
        Map<Integer, Double> clusterWeightsA = buildClusterMaxWeights(listA.getPostingList(termId),  numClusters);
        Map<Integer, Double> clusterWeightsB = buildClusterMaxWeights(listB.getPostingList(termId), numClusters);

        if (clusterWeightsA.isEmpty() || clusterWeightsB.isEmpty()) {
            return 0.0;
        }

        double numerator = 0.0;
        double denominator = 0.0;

        for (int clusterId = 0; clusterId < numClusters; clusterId++) {
            double weightA = clusterWeightsA.getOrDefault(clusterId, 0.0);
            double weightB = clusterWeightsB.getOrDefault(clusterId, 0.0);
            numerator += Math.min(weightA, weightB);
            denominator += Math.max(weightA, weightB);
        }

        return denominator > 0 ? numerator / denominator : 0.0;
    }

    private double cosineSimilarity(InvertedListEntry listA, InvertedListEntry listB) {
        Map<Integer, Double> vecA = buildPseudoDocVector(listA);
        Map<Integer, Double> vecB = buildPseudoDocVector(listB);

        double dotProduct = 0.0;
        for (Map.Entry<Integer, Double> entry : vecA.entrySet()) {
            Double wb = vecB.get(entry.getKey());
            if (wb != null) {
                dotProduct += entry.getValue() * wb;
            }
        }

        double normA = Math.sqrt(vecA.values().stream().mapToDouble(w -> w * w).sum());
        double normB = Math.sqrt(vecB.values().stream().mapToDouble(w -> w * w).sum());

        double normProduct = normA * normB;
        return normProduct > 0 ? dotProduct / normProduct : 0.0;
    }

    private double cosineSimilarityForClusters(InvertedListEntry listA, InvertedListEntry listB, int numClusters) {
        Map<Integer, Double> vecA = buildPseudoDocVectorForClusters(listA,  numClusters);
        Map<Integer, Double> vecB = buildPseudoDocVectorForClusters(listB,   numClusters);

        double dotProduct = 0.0;
        for (Map.Entry<Integer, Double> entry : vecA.entrySet()) {
            Double wb = vecB.get(entry.getKey());
            if (wb != null) {
                dotProduct += entry.getValue() * wb;
            }
        }

        double normA = Math.sqrt(vecA.values().stream().mapToDouble(w -> w * w).sum());
        double normB = Math.sqrt(vecB.values().stream().mapToDouble(w -> w * w).sum());

        double normProduct = normA * normB;
        return normProduct > 0 ? dotProduct / normProduct : 0.0;
    }


    private double weightedSumDocSim(Map<Integer, Double> vecA, Map<Integer, Double> vecB) {
        Map<Integer, Double> small = vecA.size() <= vecB.size() ? vecA : vecB;
        Map<Integer, Double> large = (small == vecA) ? vecB : vecA;

        double score = 0.0;
        for (Map.Entry<Integer, Double> entry : small.entrySet()) {
            Double other = large.get(entry.getKey());
            if (other != null) {
                score += Math.min(entry.getValue(), other);
            }
        }

        return score;
    }

    private double weightedJaccardDocSim(Map<Integer, Double> vecA, Map<Integer, Double> vecB) {
        Set<Integer> allTerms = new HashSet<>();
        allTerms.addAll(vecA.keySet());
        allTerms.addAll(vecB.keySet());

        double numerator = 0.0;
        double denominator = 0.0;
        for (Integer termId : allTerms) {
            double weightA = vecA.getOrDefault(termId, 0.0);
            double weightB = vecB.getOrDefault(termId, 0.0);
            numerator += Math.min(weightA, weightB);
            denominator += Math.max(weightA, weightB);
        }

        return denominator > 0.0 ? numerator / denominator : 0.0;
    }

    private double cosineDocSim(Map<Integer, Double> vecA, Map<Integer, Double> vecB) {
        Map<Integer, Double> small = vecA.size() <= vecB.size() ? vecA : vecB;
        Map<Integer, Double> large = (small == vecA) ? vecB : vecA;

        double dotProduct = 0.0;
        for (Map.Entry<Integer, Double> entry : small.entrySet()) {
            Double other = large.get(entry.getKey());
            if (other != null) {
                dotProduct += entry.getValue() * other;
            }
        }

        double normA = Math.sqrt(vecA.values().stream().mapToDouble(w -> w * w).sum());
        double normB = Math.sqrt(vecB.values().stream().mapToDouble(w -> w * w).sum());
        double normProduct = normA * normB;

        return normProduct > 0 ? dotProduct / normProduct : 0.0;
    }

}

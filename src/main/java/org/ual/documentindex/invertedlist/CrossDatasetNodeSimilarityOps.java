package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.SimilarityType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.ual.documentindex.invertedlist.PostingListUtils.buildDocumentVector;
import static org.ual.documentindex.invertedlist.PostingListUtils.buildPseudoDocVector;

public class CrossDatasetNodeSimilarityOps {
    private static final Logger logger = LogManager.getLogger(CrossDatasetNodeSimilarityOps.class);

    protected CrossDatasetNodeSimilarityOps() {}

    protected double textualSimUpperBound(InvertedListEntry internalList, InvertedListEntry externalList, SimilarityType similarityType) {
        // Cross-dataset comparison must operate in term space, not by document IDs.
        final Map<Integer, Double> vecInt = buildPseudoDocVector(internalList);
        final Map<Integer, Double> vecExt = buildPseudoDocVector(externalList);

        switch (similarityType) {
            case WEIGHTED_SUM:
                return weightedSumUpperBound(vecInt, vecExt);
            case WEIGHTED_JACCARD:
                return weightedJaccardUpperBound(internalList, externalList);
            case COSINE:
                return cosineSimilarityUpperBound(vecInt, vecExt);
            default:
                throw new IllegalStateException("Unsupported SimilarityType: " + similarityType);
        }
    }


    protected double textualSimLowerBound(InvertedListEntry internalList, InvertedListEntry externalList, SimilarityType similarityType) {
        // Cross-dataset comparison must operate in term space, not by document IDs.
        final Map<Integer, Double> vecInt = buildPseudoDocVector(internalList);
        final Map<Integer, Double> vecExt = buildPseudoDocVector(externalList);

        switch (similarityType) {
            case WEIGHTED_SUM:
                return weightedSumLowerBound(vecInt, vecExt);
            case WEIGHTED_JACCARD:
                return weightedJaccardLowerBound(vecInt, vecExt);
            case COSINE:
                return cosineSimilarityLowerBound(vecInt, vecExt);
            default:
                throw new IllegalStateException("Unsupported SimilarityType: " + similarityType);
        }
    }

    protected double textualSimExact(InvertedListEntry internalList, InvertedListEntry externalList,
                                     int internalDocId, int externalDocId, SimilarityType similarityType) {

        Map<Integer, Double> docVecA = buildDocumentVector(internalList, internalDocId);
        Map<Integer, Double> docVecB = buildDocumentVector(externalList, externalDocId);

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

    private double weightedSumUpperBound(Map<Integer, Double> vecA, Map<Integer, Double> vecB) {
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

    private double weightedSumLowerBound(Map<Integer, Double> vecA, Map<Integer, Double> vecB) {
        // TODO Quick fix, revise later
        logger.error("NOT IMPLEMENTED, USING UPPER BOUND AS LOWER BOUND");
        return weightedSumUpperBound(vecA, vecB);
    }



    /**
     * Overlap coefficient in term space (|A∩B| / min(|A|,|B|)).
     * It is intentionally optimistic and used as a pruning upper bound.
     */
    private double weightedJaccardUpperBound(InvertedListEntry listA, InvertedListEntry listB) {
        Set<Integer> termsA = listA.getAllTerms();
        Set<Integer> termsB = listB.getAllTerms();

        if (termsA == null || termsB == null || termsA.isEmpty() || termsB.isEmpty()) {
            return 0.0;
        }

        Set<Integer> small = termsA.size() <= termsB.size() ? termsA : termsB;
        Set<Integer> large = (small == termsA) ? termsB : termsA;

        int intersectionCount = 0;
        for (Integer termId : small) {
            if (large.contains(termId)) {
                intersectionCount++;
            }
        }

        return ((double) intersectionCount / Math.min(termsA.size(), termsB.size()));
    }

    private double weightedJaccardLowerBound(Map<Integer, Double> vecA, Map<Integer, Double> vecB) {
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

    private double cosineSimilarityLowerBound(Map<Integer, Double> vecA, Map<Integer, Double> vecB) {
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

    private double cosineSimilarityUpperBound(Map<Integer, Double> vecA, Map<Integer, Double> vecB) {
        // TODO Quick fix, revise later
        logger.error("NOT IMPLEMENTED, USING UPPER BOUND AS LOWER BOUND");
        return cosineSimilarityLowerBound(vecA, vecB);
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

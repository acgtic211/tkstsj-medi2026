package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.SimilarityType;

import java.util.*;

public class NodeConstraintSimilarityOps {
    private static final Logger logger = LogManager.getLogger(NodeConstraintSimilarityOps.class);

    protected NodeConstraintSimilarityOps() {}

    protected void textualSimilarity(InvertedListEntry invertedList, List<Integer> keywords, List<Double> keywordWeights,
                                                     Map<Integer, Double> similarityScores, SimilarityType similarityType) {
        switch (similarityType) {
            case WEIGHTED_SUM:
                weightedSumConstraint(invertedList, keywords, keywordWeights, similarityScores);
                break;
            case WEIGHTED_JACCARD:
                weightedJaccardConstraint(invertedList, keywords, keywordWeights, similarityScores);
                break;
            case COSINE:
                cosineSimilarityConstraint(invertedList, keywords, keywordWeights, similarityScores);
                break;
            default:
                throw new IllegalArgumentException("Unsupported SimilarityType: " + similarityType);
        }
    }

    protected void textualSimilarityForClusters(InvertedListEntry invertedList, List<Integer> keywords, List<Double> keywordWeights,
                                              Map<Integer, Double> relevancyScores, SimilarityType similarityType, int numClusters) {
        switch (similarityType) {
            case WEIGHTED_JACCARD:
                weightedJaccardForClusters(invertedList, keywords, keywordWeights, relevancyScores, numClusters);
                break;
            case COSINE:
                cosineSimilarityConstraintForClusters(invertedList, keywords, keywordWeights, relevancyScores, numClusters);
                break;
            case WEIGHTED_SUM:
                weightedSumConstraintForClusters(invertedList, keywords, keywordWeights, relevancyScores, numClusters);
                break;
            default:
                throw new IllegalArgumentException("Unsupported SimilarityType: " + similarityType);
        }
    }

    /**
     * Processes keywords for a given inverted list and updates relevancy scores.
     *
     * @param invertedList    The inverted list to process
     * @param keywords        List of term IDs to search for
     * @param keywordWeights  List of weights corresponding to each keyword
     * @param similarityScores Map to accumulate relevancy scores for documents
     */
    private void weightedSumConstraint(InvertedListEntry invertedList,
                                        List<Integer> keywords, List<Double> keywordWeights,
                                        Map<Integer, Double> similarityScores) {
        for (int i = 0; i < keywords.size(); i++) {
            int keyword = keywords.get(i);
            double weight = keywordWeights.get(i);

            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            if (postingList == null || postingList.isEmpty()) {
                continue;
            }

            double maxWeight = invertedList.getTermMaxWeight(keyword);
            if (maxWeight <= 0.0 || Double.isNaN(maxWeight) || Double.isInfinite(maxWeight)) {
                // Avoid division by zero and invalid score propagation.
                continue;
            }

            for (PostingListEntry posting : postingList) {
                double normalizedScore = (posting.weight / maxWeight) * weight;
                similarityScores.merge(posting.documentId, normalizedScore, Double::sum);
            }
        }
    }

    private void weightedSumConstraintForClusters(InvertedListEntry invertedList,
                                                      List<Integer> keywords, List<Double> keywordWeights,
                                                      Map<Integer, Double> relevancyScores, int numClusters) {
        Map<Integer, double[]> docClusterScores = new HashMap<>();

        for (int i = 0; i < keywords.size(); i++) {
            int keyword = keywords.get(i);
            double weight = keywordWeights.get(i);

            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            if (postingList == null || postingList.isEmpty()) {
                continue;
            }

            double maxWeight = postingList.stream()
                    .mapToDouble(posting -> posting.weight)
                    .max()
                    .orElse(1.0);

            if (maxWeight <= 0.0) {
                continue;
            }

            for (PostingListEntry posting : postingList) {
                if (posting.clusterId < 0 || posting.clusterId >= numClusters) {
                    logger.warn("Keyword-List: Invalid cluster ID {} for document {}, term {}. Skipping entry.",
                            posting.clusterId, posting.documentId, keyword);
                    continue;
                }

                double normalizedScore = (posting.weight / maxWeight) * weight;

                double[] scoresForDoc = docClusterScores.computeIfAbsent(
                        posting.documentId, k -> new double[numClusters]);
                scoresForDoc[posting.clusterId] += normalizedScore;
            }
        }

        for (Map.Entry<Integer, double[]> entry : docClusterScores.entrySet()) {
            int docId = entry.getKey();
            double maxClusterScore = Arrays.stream(entry.getValue()).max().orElse(0.0);

            if (maxClusterScore > 0) {
                relevancyScores.merge(docId, maxClusterScore, Double::sum);
            }
        }
    }

    private void weightedJaccardConstraint(InvertedListEntry invertedList, List<Integer> keywords,
                                           List<Double> keywordWeights, Map<Integer, Double> similarityScores) {
        Map<Integer, Double> numerator = new HashMap<>();
        Map<Integer, Double> denominator = new HashMap<>();
        Set<Integer> allDocuments = new HashSet<>();

        for (int keyword : keywords) {
            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            if (postingList != null && !postingList.isEmpty()) {
                for (PostingListEntry posting : postingList) {
                    allDocuments.add(posting.documentId);
                }
            }
        }

        for (int i = 0; i < keywords.size(); i++) {
            int keyword = keywords.get(i);
            double queryWeight = keywordWeights.get(i);

            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            Set<Integer> docsWithTerm = new HashSet<>();

            if (postingList != null && !postingList.isEmpty()) {
                for (PostingListEntry posting : postingList) {
                    docsWithTerm.add(posting.documentId);
                    double minWeight = Math.min(queryWeight, posting.weight);
                    double maxWeight = Math.max(queryWeight, posting.weight);
                    numerator.merge(posting.documentId, minWeight, Double::sum);
                    denominator.merge(posting.documentId, maxWeight, Double::sum);
                }
            }

            for (Integer docId : allDocuments) {
                if (!docsWithTerm.contains(docId)) {
                    denominator.merge(docId, queryWeight, Double::sum);
                }
            }
        }

        for (Map.Entry<Integer, Double> entry : denominator.entrySet()) {
            int docId = entry.getKey();
            double denom = entry.getValue();
            double num = numerator.getOrDefault(docId, 0.0);
            if (denom > 0) {
                similarityScores.put(docId, num / denom);
            }
        }
    }

    private void weightedJaccardForClusters(InvertedListEntry invertedList, List<Integer> keywords,
                                                                         List<Double> keywordWeights, Map<Integer, Double> relevancyScores, int numClusters) {

        Map<Integer, Map<Integer, double[]>> docClusterScores = new HashMap<>();
        final double queryWeightSum = keywordWeights.stream().mapToDouble(Double::doubleValue).sum();

        for (int i = 0; i < keywords.size(); i++) {
            int keyword = keywords.get(i);
            double queryWeight = keywordWeights.get(i);

            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            if (postingList == null || postingList.isEmpty()) continue;

            for (PostingListEntry posting : postingList) {
                int cluster = posting.clusterId;
                if (cluster < 0 || cluster >= numClusters) {
                    logger.warn("Weighted-Jaccard: Invalid cluster ID {} for document {}, term {}. Skipping entry.",
                            cluster, posting.documentId, keyword);
                    continue;
                }

                int docId = posting.documentId;
                Map<Integer, double[]> clusterMap = docClusterScores.computeIfAbsent(docId, k -> new HashMap<>());
                double[] scores = clusterMap.computeIfAbsent(cluster, k -> {
                    double[] s = new double[2];
                    s[1] = queryWeightSum;
                    return s;
                });

                double minWeight = Math.min(queryWeight, posting.weight);
                double maxWeight = Math.max(queryWeight, posting.weight);
                scores[0] += minWeight;
                scores[1] = scores[1] - queryWeight + maxWeight;
            }
        }

        for (Map.Entry<Integer, Map<Integer, double[]>> docEntry : docClusterScores.entrySet()) {
            int docId = docEntry.getKey();
            double maxScore = 0.0;

            for (double[] scoreData : docEntry.getValue().values()) {
                double numerator = scoreData[0];
                double denominator = scoreData[1];
                if (denominator > 0) {
                    double score = numerator / denominator;
                    maxScore = Math.max(maxScore, score);
                }
            }

            if (maxScore > 0) {
                relevancyScores.put(docId, maxScore);
            }
        }
    }


    private void cosineSimilarityConstraint(InvertedListEntry invertedList, List<Integer> keywords, List<Double> keywordWeights,
                                            Map<Integer, Double> similarityScores) {
        double queryNorm = Math.sqrt(keywordWeights.stream().mapToDouble(w -> w * w).sum());

        Map<Integer, Double> docDotProduct = new HashMap<>();
        Map<Integer, Double> docNormSquared = new HashMap<>();

        for (int i = 0; i < keywords.size(); i++) {
            int keyword = keywords.get(i);
            double queryWeight = keywordWeights.get(i);

            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            if (postingList == null || postingList.isEmpty()) {
                continue;
            }

            for (PostingListEntry posting : postingList) {
                docDotProduct.merge(posting.documentId, posting.weight * queryWeight, Double::sum);
                docNormSquared.merge(posting.documentId, posting.weight * posting.weight, Double::sum);
            }
        }

        for (Map.Entry<Integer, Double> entry : docDotProduct.entrySet()) {
            int docId = entry.getKey();
            double dotProduct = entry.getValue();
            double docNorm = Math.sqrt(docNormSquared.get(docId));
            if (docNorm > 0 && queryNorm > 0) {
                double cosineSim = dotProduct / (docNorm * queryNorm);
                similarityScores.put(docId, cosineSim);
                // maybe use merge with sum or max
            }
        }
    }

    private void cosineSimilarityConstraintForClusters(InvertedListEntry invertedList, List<Integer> keywords, List<Double> keywordWeights,
                                                       Map<Integer, Double> relevancyScores, int numClusters) {
        double queryNorm = Math.sqrt(keywordWeights.stream().mapToDouble(w -> w * w).sum());
        Map<Integer, Map<Integer, double[]>> docClusterData = new HashMap<>();

        for (int i = 0; i < keywords.size(); i++) {
            int keyword = keywords.get(i);
            double queryWeight = keywordWeights.get(i);

            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            if (postingList == null || postingList.isEmpty()) {
                continue;
            }

            for (PostingListEntry posting : postingList) {
                if (posting.clusterId < 0 || posting.clusterId >= numClusters) {
                    logger.warn("Cosine: Invalid cluster ID {} for document {}, term {}. Skipping entry.",
                            posting.clusterId, posting.documentId, keyword);
                    continue;
                }

                Map<Integer, double[]> clusterData = docClusterData.computeIfAbsent(posting.documentId,
                        k -> new HashMap<>());
                // double[] data = clusterData.computeIfAbsent(posting.clusterId, k -> new double[2]);
                double[] data = clusterData.computeIfAbsent(posting.clusterId, k -> {
                    double[] s = new double[2];
                    s[1] = 0.0;
                    return s;
                });

                data[0] += posting.weight * queryWeight;
                data[1] += posting.weight * posting.weight;
            }
        }

        for (Map.Entry<Integer, Map<Integer, double[]>> docEntry : docClusterData.entrySet()) {
            int docId = docEntry.getKey();
            double maxCosineSim = 0.0;

            for (double[] clusterData : docEntry.getValue().values()) {
                double dotProduct = clusterData[0];
                double docNorm = Math.sqrt(clusterData[1]);

                if (docNorm > 0 && queryNorm > 0) {
                    double cosineSim = dotProduct / (docNorm * queryNorm);
                    maxCosineSim = Math.max(maxCosineSim, cosineSim);
                }
            }

            if (maxCosineSim > 0) {
                relevancyScores.merge(docId, maxCosineSim, Double::sum);
            }
        }
    }
}

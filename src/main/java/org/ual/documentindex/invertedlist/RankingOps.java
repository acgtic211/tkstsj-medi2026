package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.RankingSumMode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankingOps {
    private static final Logger logger = LogManager.getLogger(RankingOps.class);

    protected RankingOps() {}

    @Deprecated
    protected Map<Integer, Double> rankingSum(InvertedListEntry invertedList, List<Integer> keywords) {
        Map<Integer, Double> docScores = new HashMap<>();

        for (Integer keyword : keywords) {
            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            if (postingList == null || postingList.isEmpty()) {
                continue;
            }

            for (PostingListEntry postingEntry : postingList) {
                docScores.merge(postingEntry.documentId, postingEntry.weight, Double::sum);
            }
        }
        return docScores;
    }


    protected Map<Integer, Double> rankingSum(InvertedListEntry invertedList, List<Integer> keywords, List<Double> keywordWeights, RankingSumMode scoringMode) {
        Map<Integer, Double> docScores = new HashMap<>();

        for (int i = 0; i < keywords.size(); i++) {
            int keyword = keywords.get(i);
            double weight = keywordWeights.get(i);

            List<PostingListEntry> postingList = invertedList.getPostingList(keyword);
            if (postingList == null || postingList.isEmpty()) {
                continue;
            }

            for (PostingListEntry ple : postingList) {
                if (scoringMode == RankingSumMode.PRECISE) {
                    docScores.merge(ple.documentId, ple.weight * weight, Double::sum);
                } else if (scoringMode == RankingSumMode.FAST_APPROXIMATE) {
                    docScores.merge(ple.documentId, weight, Double::sum);
                } else {
                    throw new IllegalArgumentException("Unsupported RankingSumMode: " + scoringMode);
                }

            }
        }
        return docScores;
    }


    protected Map<Integer, Double> rankingSumByCluster(InvertedListEntry invertedList, List<Integer> keywords, List<Double> keywordWeights,
                                                            RankingSumMode scoringMode, int numClusters) {
        Map<Integer, Double> docScore = new HashMap<>();
        Map<Integer, double[]> docClusterScores = new HashMap<>();

        for (int j = 0; j < keywords.size(); j++) {
            int word = keywords.get(j);
            double keywordQueryWeight = keywordWeights.get(j);

            List<PostingListEntry> postingList = invertedList.getPostingList(word);
            if (postingList == null || postingList.isEmpty()) {
                continue;
            }

            for (PostingListEntry ple : postingList) {
                if (ple.clusterId < 0 || ple.clusterId >= numClusters) {
                    logger.warn("Invalid cluster ID {} for document {}, term {}. Skipping entry.",
                            ple.clusterId, ple.documentId, word);
                    continue;
                }

                double[] scoresForDoc = docClusterScores.computeIfAbsent(ple.documentId, k -> new double[numClusters]);
                if (scoringMode  == RankingSumMode.PRECISE) {
                    scoresForDoc[ple.clusterId] += ple.weight * keywordQueryWeight;
                } else if (scoringMode == RankingSumMode.FAST_APPROXIMATE) {
                    scoresForDoc[ple.clusterId] += keywordQueryWeight;
                } else {
                    throw new IllegalArgumentException("Unsupported RankingSumMode: " + scoringMode);
                }
            }
        }

        for (Map.Entry<Integer, double[]> entry : docClusterScores.entrySet()) {
            int docId = entry.getKey();
            double maxScore = Arrays.stream(entry.getValue()).max().orElse(0.0);

            if (maxScore > 0) {
                docScore.put(docId, maxScore);
            }
        }

        return docScore;
    }

    protected Map<Integer, Integer> booleanFilter(InvertedListEntry invertedList, List<Integer> keywords) {
        Map<Integer, Integer> matchCounts = new HashMap<>();

        for (int termId : keywords) {
            List<PostingListEntry> postingList = invertedList.getPostingList(termId);
            if (postingList == null || postingList.isEmpty()) {
                continue;
            }

            for (PostingListEntry postingEntry : postingList) {
                matchCounts.merge(postingEntry.documentId, 1, Integer::sum);
            }
        }

        return matchCounts;
    }
}

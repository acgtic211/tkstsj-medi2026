package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostingListUtils {
    private static final Logger logger = LogManager.getLogger(PostingListUtils.class);

    protected static Map<Integer, Double> buildDocumentVector(InvertedListEntry list, int documentId) {
        Map<Integer, Double> vector = new HashMap<>();

        for (Map.Entry<Integer, List<PostingListEntry>> termEntry : list.postingLists.entrySet()) {
            Integer termId = termEntry.getKey();
            List<PostingListEntry> postingList = termEntry.getValue();
            for (PostingListEntry posting : postingList) {
                if (posting.documentId == documentId) {
                    vector.put(termId, posting.weight);
                    break;
                }
            }
        }

        return vector;
    }

    protected static Map<Integer, Double> buildPseudoDocVector(InvertedListEntry list) {
        Map<Integer, Double> vec = new HashMap<>();
        for (Integer termId : list.getAllTerms()) {
            List<PostingListEntry> postings = list.getPostingList(termId);
            if (postings != null) {
                double maxWeight = postings.stream()
                        .mapToDouble(p -> p.weight)
                        .max()
                        .orElse(0.0);
                vec.put(termId, maxWeight);
            }
        }
        return vec;
    }

    protected static Map<Integer, Double> buildPseudoDocVectorForClusters(InvertedListEntry list, int numClusters) {
        Map<Integer, Double> vec = new HashMap<>();
        for (Integer termId : list.getAllTerms()) {
            List<PostingListEntry> postings = list.getPostingList(termId);
            if (postings != null) {
                Map<Integer, Double> clusterWeights = buildClusterMaxWeights(postings, numClusters);
                double maxClusterWeight = clusterWeights.values().stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0);
                vec.put(termId, maxClusterWeight);
            }
        }
        return vec;
    }

    protected static Map<Integer, Double> buildClusterMaxWeights(List<PostingListEntry> postings, int numClusters) {
        Map<Integer, Double> clusterWeights = new HashMap<>();
        if (postings == null || postings.isEmpty()) {
            return clusterWeights;
        }

        for (PostingListEntry posting : postings) {
            if (posting.clusterId < 0 || posting.clusterId >= numClusters) {
                logger.warn("Invalid cluster ID {} for document {}, skipping clustered node-pair score.",
                        posting.clusterId, posting.documentId);
                continue;
            }
            clusterWeights.merge(posting.clusterId, posting.weight, Double::max);
        }

        return clusterWeights;
    }
}

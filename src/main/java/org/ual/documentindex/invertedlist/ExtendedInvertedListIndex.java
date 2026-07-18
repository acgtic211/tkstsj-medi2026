package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.RankingSumMode;
import org.ual.documentindex.SimilarityType;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Text-only signed inverted index.
 * Extends {@link InvertedListIndex} while keeping additional per-node metadata:
 * - 64-bit node signature for keyword pruning,
 * - per-term max weights,
 * - node norm (derived from term max weights).
 */
public class ExtendedInvertedListIndex extends InvertedListIndex {
    private static final Logger logger = LogManager.getLogger(ExtendedInvertedListIndex.class);

    private final Map<Integer, NodeStats> statsByNode = new ConcurrentHashMap<>();

    public ExtendedInvertedListIndex(int numberOfClusters) {
        super(numberOfClusters);
        //throw new UnsupportedOperationException("Old implementation of OLDSignedInvertedListIndex is not supported.");
    }

    @Override
    public void create(int nodeId) {
        super.create(nodeId);
        statsByNode.put(nodeId, new NodeStats());
    }

    @Override
    public void addDocument(int nodeId, int docId, List<WeightEntry> document) {
        super.addDocument(nodeId, docId, document);
        refreshStatsForDocument(nodeId, document);
    }

    @Override
    public void addDocument(int nodeId, int docId, List<WeightEntry> document, int cluster) {
        super.addDocument(nodeId, docId, document, cluster);
        refreshStatsForDocument(nodeId, document);
    }

    @Override
    public Map<Integer, Double> rankingSum(int nodeId,
                                           List<Integer> keywords,
                                           List<Double> keywordWeights,
                                           RankingSumMode scoringMode) {
        if (keywords == null || keywordWeights == null) {
            throw new IllegalArgumentException("Keywords and keywordWeights must not be null.");
        }
        if (keywords.size() != keywordWeights.size()) {
            throw new IllegalArgumentException("Keywords and keywordWeights must have the same size.");
        }

        NodeStats stats = statsByNode.get(nodeId);
        if (stats == null || !stats.hasAnyKeyword(keywords)) {
            return Collections.emptyMap();
        }

        return super.rankingSum(nodeId, filterKeywordsBySignature(nodeId, keywords),
                filterKeywordWeightsBySignature(nodeId, keywords, keywordWeights), scoringMode);
    }

    @Override
    public Map<Integer, Integer> booleanFilter(int nodeId, List<Integer> keywords) {
        if (keywords == null) {
            throw new IllegalArgumentException("keywords must not be null (nodeId=" + nodeId + ")");
        }

        NodeStats stats = statsByNode.get(nodeId);
        if (stats == null || !stats.hasAnyKeyword(keywords)) {
            return Collections.emptyMap();
        }

        return super.booleanFilter(nodeId, filterKeywordsBySignature(nodeId, keywords));
    }

    @Override
    public double rankingSum(int nodeIdA, int nodeIdB) {
        NodeStats a = statsByNode.get(nodeIdA);
        NodeStats b = statsByNode.get(nodeIdB);

        if (a == null || b == null) {
            return 0.0;
        }
        if ((a.nodeSignature & b.nodeSignature) == 0L) {
            return 0.0;
        }

        Map<Integer, Double> small = a.termMaxWeight.size() <= b.termMaxWeight.size() ? a.termMaxWeight : b.termMaxWeight;
        Map<Integer, Double> large = small == a.termMaxWeight ? b.termMaxWeight : a.termMaxWeight;

        double score = 0.0;
        for (Map.Entry<Integer, Double> e : small.entrySet()) {
            Double other = large.get(e.getKey());
            if (other != null) {
                score += Math.min(e.getValue(), other);
            }
        }
        return score;
    }

//    @Override
//    public Map<Integer, Double> calculateTextualRelevancy(int nodeIdA, int nodeIdB, SimilarityType similarityType) {
//        NodeStats a = statsByNode.get(nodeIdA);
//        NodeStats b = statsByNode.get(nodeIdB);
//        if (a == null || b == null || (a.nodeSignature & b.nodeSignature) == 0L) {
//            return Collections.emptyMap();
//        }
//        return super.calculateTextualRelevancy(nodeIdA, nodeIdB, similarityType);
//    }

    @Override
    public double nodesTextualSim(int nodeIdA, int nodeIdB, SimilarityType similarityType) {
        NodeStats a = statsByNode.get(nodeIdA);
        NodeStats b = statsByNode.get(nodeIdB);
        if (a == null || b == null || (a.nodeSignature & b.nodeSignature) == 0L) {
            return 0.0;
        }
        return super.nodesTextualSim(nodeIdA, nodeIdB, similarityType);
    }

    @Override
    public double nodesTextualSimForClusters(int nodeIdA, int nodeIdB, SimilarityType similarityType) {
        NodeStats a = statsByNode.get(nodeIdA);
        NodeStats b = statsByNode.get(nodeIdB);
        if (a == null || b == null || (a.nodeSignature & b.nodeSignature) == 0L) {
            return 0.0;
        }
        return super.nodesTextualSimForClusters(nodeIdA, nodeIdB, similarityType);
    }

    @Override
    public Map<Integer, Double> nodesConstraintTextualSim(int nodeId1, int nodeId2, List<Integer> keywords, List<Double> keywordWeights,
                                                SimilarityType similarityType) {
        if (keywords == null || keywordWeights == null) {
            throw new IllegalArgumentException("keywords and keywordWeights must not be null.");
        }
        if (keywords.size() != keywordWeights.size()) {
            throw new IllegalArgumentException("keywords and keywordWeights size mismatch.");
        }

        FilteredQuery filtered = filterKeywordsForEitherNode(nodeId1, nodeId2, keywords, keywordWeights);
        if (filtered.keywords.isEmpty()) {
            return Collections.emptyMap();
        }

        return super.nodesConstraintTextualSim(nodeId1, nodeId2, filtered.keywords, filtered.weights, similarityType);
    }

    @Override
    public Map<Integer, Double> nodesConstraintTextualSimForClusters(int nodeId1, int nodeId2, List<Integer> keywords, List<Double> keywordWeights,
                                                           SimilarityType similarityType) {
        if (keywords == null || keywordWeights == null) {
            throw new IllegalArgumentException("keywords and keywordWeights must not be null.");
        }
        if (keywords.size() != keywordWeights.size()) {
            throw new IllegalArgumentException("keywords and keywordWeights size mismatch.");
        }

        FilteredQuery filtered = filterKeywordsForEitherNode(nodeId1, nodeId2, keywords, keywordWeights);
        if (filtered.keywords.isEmpty()) {
            return Collections.emptyMap();
        }

        return super.nodesConstraintTextualSimForClusters(nodeId1, nodeId2,
                filtered.keywords, filtered.weights, similarityType);
    }

    public long getNodeSignature(int nodeId) {
        NodeStats stats = statsByNode.get(nodeId);
        return stats != null ? stats.nodeSignature : 0L;
    }

    public double getNodeNorm(int nodeId) {
        NodeStats stats = statsByNode.get(nodeId);
        return stats != null ? Math.sqrt(stats.nodeNormSquared) : 0.0;
    }

    public double getTermMaxWeight(int nodeId, int term) {
        NodeStats stats = statsByNode.get(nodeId);
        if (stats == null) {
            return 0.0;
        }
        return stats.termMaxWeight.getOrDefault(term, 0.0);
    }

    private void refreshStatsForDocument(int nodeId, List<WeightEntry> document) {
        if (document == null || document.isEmpty()) {
            return;
        }

        NodeStats stats = statsByNode.get(nodeId);
        if (stats == null) {
            logger.warn("Stats missing for node {} while updating signed metadata.", nodeId);
            return;
        }

        for (WeightEntry termWeight : document) {
            if (termWeight == null) {
                continue;
            }
            int term = termWeight.word;
            double weight = termWeight.weight;

            stats.nodeSignature |= signatureBit(term);
            double prevMax = stats.termMaxWeight.getOrDefault(term, 0.0);
            if (weight > prevMax) {
                stats.termMaxWeight.put(term, weight);
                stats.nodeNormSquared += (weight * weight) - (prevMax * prevMax);
            }
        }
    }

    private List<Integer> filterKeywordsBySignature(int nodeId, List<Integer> keywords) {
        NodeStats stats = statsByNode.get(nodeId);
        if (stats == null || keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> filtered = new ArrayList<>(keywords.size());
        for (Integer kw : keywords) {
            if (kw != null && (stats.nodeSignature & signatureBit(kw)) != 0L) {
                filtered.add(kw);
            }
        }
        return filtered;
    }

    private List<Double> filterKeywordWeightsBySignature(int nodeId,
                                                         List<Integer> keywords,
                                                         List<Double> keywordWeights) {
        NodeStats stats = statsByNode.get(nodeId);
        if (stats == null || keywords == null || keywordWeights == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<Double> filtered = new ArrayList<>(keywords.size());
        for (int i = 0; i < keywords.size(); i++) {
            Integer kw = keywords.get(i);
            if (kw != null && (stats.nodeSignature & signatureBit(kw)) != 0L) {
                filtered.add(keywordWeights.get(i));
            }
        }
        return filtered;
    }

    private FilteredQuery filterKeywordsForEitherNode(int nodeId1,
                                                      int nodeId2,
                                                      List<Integer> keywords,
                                                      List<Double> keywordWeights) {
        NodeStats stats1 = statsByNode.get(nodeId1);
        NodeStats stats2 = statsByNode.get(nodeId2);

        long combinedSignature = 0L;
        if (stats1 != null) {
            combinedSignature |= stats1.nodeSignature;
        }
        if (stats2 != null) {
            combinedSignature |= stats2.nodeSignature;
        }

        if (combinedSignature == 0L) {
            return new FilteredQuery(Collections.emptyList(), Collections.emptyList());
        }

        List<Integer> filteredKeywords = new ArrayList<>();
        List<Double> filteredWeights = new ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            Integer kw = keywords.get(i);
            if (kw != null && (combinedSignature & signatureBit(kw)) != 0L) {
                filteredKeywords.add(kw);
                filteredWeights.add(keywordWeights.get(i));
            }
        }

        return new FilteredQuery(filteredKeywords, filteredWeights);
    }

//    private static long signatureBit(int keyword) {
//        return 1L << (Math.floorMod(keyword, 64));
//    }

    private static long signatureBit(int keyword) {
        // Two independent hash positions to reduce false positives within 64 bits.
        int h1 = Math.floorMod(keyword, 64);
        int h2 = Math.floorMod(keyword * 0x9e3779b9, 64);
        return (1L << h1) | (1L << h2);
    }

    private static final class NodeStats {
        private long nodeSignature = 0L;
        private final Map<Integer, Double> termMaxWeight = new HashMap<>();
        private double nodeNormSquared = 0.0;

        private boolean hasAnyKeyword(List<Integer> keywords) {
            for (Integer kw : keywords) {
                if (kw != null && (nodeSignature & signatureBit(kw)) != 0L) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class FilteredQuery {
        private final List<Integer> keywords;
        private final List<Double> weights;

        private FilteredQuery(List<Integer> keywords, List<Double> weights) {
            this.keywords = keywords;
            this.weights = weights;
        }
    }
}



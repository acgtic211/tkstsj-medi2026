package org.ual.documentindex.invertedlist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.ual.documentindex.RankingSumMode;
import org.ual.documentindex.SimilarityType;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InvertedListIndexRegressionTest {

    private static final int NODE_1 = 1;
    private static final int NODE_2 = 2;
    private static final int CLUSTERS = 3;

    private InvertedListIndex index;

    private static List<WeightEntry> doc(WeightEntry... entries) {
        return Arrays.asList(entries);
    }

    private static List<Integer> ints(Integer... values) {
        return Arrays.asList(values);
    }

    private static List<Double> doubles(Double... values) {
        return Arrays.asList(values);
    }

    private static Set<Integer> setOf(Integer... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @BeforeEach
    void setUp() {
        index = new InvertedListIndex(CLUSTERS);
        index.create(NODE_1);
        index.create(NODE_2);
    }

    @Nested
    class CreateAndAddDocument {

        @Test
        void createShouldInitializeAnEmptyNode() {
            int nodeId = 10;
            index.create(nodeId);

            Map<Integer, Double> scores = index.rankingSum(nodeId, ints(100), doubles(1.0));
            assertTrue(scores.isEmpty());
        }

        @Test
        void addDocumentShouldThrowForUnknownNode() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> index.addDocument(999, 1, doc(new WeightEntry(100, 0.5))));

            assertTrue(ex.getMessage().contains("not found"));
        }

        @Test
        void addDocumentWithClusterShouldValidateClusterBounds() {
            List<WeightEntry> doc = doc(new WeightEntry(100, 0.5));

            assertThrows(IllegalArgumentException.class, () -> index.addDocument(NODE_1, 1, doc, -1));
            assertThrows(IllegalArgumentException.class, () -> index.addDocument(NODE_1, 1, doc, CLUSTERS));
        }
    }

    @Nested
    class StoreOperations {

        @Test
        void storeShouldKeepMaximumWeightPerTerm() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.4), new WeightEntry(200, 0.9)));
            index.addDocument(NODE_1, 2, doc(new WeightEntry(100, 0.8)));

            List<WeightEntry> pseudoDoc = index.store(NODE_1);

            assertEquals(2, pseudoDoc.size());
            assertTrue(pseudoDoc.stream().anyMatch(w -> w.word == 100 && Math.abs(w.weight - 0.8) < 1e-9));
            assertTrue(pseudoDoc.stream().anyMatch(w -> w.word == 200 && Math.abs(w.weight - 0.9) < 1e-9));
        }

        @Test
        void storeShouldReturnEmptyForMissingNode() {
            assertTrue(index.store(999).isEmpty());
        }

        @Test
        void storeClusterEnhanceShouldReturnPerClusterPseudoDocuments() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.5)), 0);
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.8)), 1);

            List<List<WeightEntry>> clusterDocs = index.storeClusterEnhance(NODE_1);

            assertEquals(CLUSTERS, clusterDocs.size());
            assertTrue(clusterDocs.get(0).stream().anyMatch(w -> w.word == 100 && Math.abs(w.weight - 0.5) < 1e-9));
            assertTrue(clusterDocs.get(1).stream().anyMatch(w -> w.word == 100 && Math.abs(w.weight - 0.8) < 1e-9));
            assertTrue(clusterDocs.get(2).isEmpty());
        }
    }

    @Nested
    class Ranking {

        @Test
        void rankingSumShouldAccumulateWeightedMatches() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.5), new WeightEntry(200, 0.8)));

            Map<Integer, Double> scores = index.rankingSum(NODE_1, ints(100, 200), doubles(2.0, 1.0), RankingSumMode.PRECISE);

            assertEquals(1, scores.size());
            assertEquals(1.8, scores.get(1), 1e-9);
        }

        @Test
        void rankingSumShouldRejectNullInputsAndMismatchedSizes() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 1.0)));

            assertThrows(IllegalArgumentException.class, () -> index.rankingSum(NODE_1, null, doubles(1.0)));
            assertThrows(IllegalArgumentException.class, () -> index.rankingSum(NODE_1, ints(100), null));
            assertThrows(IllegalArgumentException.class,
                    () -> index.rankingSum(NODE_1, ints(100, 200), doubles(1.0)));
        }

        @Test
        void rankingSumShouldReturnEmptyForMissingNode() {
            Map<Integer, Double> scores = index.rankingSum(999, ints(100), doubles(1.0));
            assertTrue(scores.isEmpty());
        }

        @Test
        void rankingSumByNodeShouldUseMinOfPseudoDocumentWeights() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.6), new WeightEntry(200, 0.4)));
            index.addDocument(NODE_2, 2, doc(new WeightEntry(100, 0.9), new WeightEntry(300, 0.2)));

            double score = index.rankingSum(NODE_1, NODE_2);

            assertEquals(0.6, score, 1e-9);
        }

        @Test
        void rankingSumClusterEnhanceShouldSelectBestClusterPerDocument() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.4)), 0);
            index.addDocument(NODE_1, 1, doc(new WeightEntry(200, 0.9)), 1);

            Map<Integer, Double> scores = index.rankingSumClusterEnhance(NODE_1,
                    ints(100, 200), doubles(1.0, 1.0), RankingSumMode.PRECISE);

            assertEquals(0.9, scores.get(1), 1e-9);
        }

        @Test
        void rankingSumClusterEnhanceShouldFallbackWhenClusterCountIsNonPositive() {
            InvertedListIndex noClusterIndex = new InvertedListIndex(0);
            noClusterIndex.create(NODE_1);
            noClusterIndex.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.7)));

            Map<Integer, Double> scores = noClusterIndex.rankingSumClusterEnhance(NODE_1,
                    ints(100), doubles(2.0), RankingSumMode.PRECISE);

            assertEquals(1.4, scores.get(1), 1e-9);
        }
    }

    @Nested
    class BooleanFilter {

        @Test
        void booleanFilterShouldCountMatchesAcrossKeywords() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.4), new WeightEntry(200, 0.8)));

            Map<Integer, Integer> counts = index.booleanFilter(NODE_1, ints(100, 200));

            assertEquals(2, counts.get(1));
        }

        @Test
        void booleanFilterShouldReturnEmptyForMissingNode() {
            assertTrue(index.booleanFilter(999, ints(100)).isEmpty());
        }

        @Test
        void booleanFilterShouldRejectNullKeywords() {
            assertThrows(IllegalArgumentException.class, () -> index.booleanFilter(NODE_1, null));
        }
    }

    @Nested
    class TextualRelevancyBetweenNodes {

        @Test
        void weightedSumSimilarityShouldBeComputedForSharedTerms() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.6)));
            index.addDocument(NODE_2, 1, doc(new WeightEntry(100, 0.8)));

            double termScores = index.nodesTextualSim(NODE_1, NODE_2, SimilarityType.WEIGHTED_SUM);

            assertEquals(0.6, termScores, 1e-9);
        }

        @Test
        void cosineSimilarityShouldBeOneForEquivalentVectors() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 3.0), new WeightEntry(200, 4.0)));
            index.addDocument(NODE_2, 1, doc(new WeightEntry(100, 3.0), new WeightEntry(200, 4.0)));

            double termScores = index.nodesTextualSim(NODE_1, NODE_2, SimilarityType.COSINE);

            assertEquals(1.0, termScores, 1e-9);
        }

        @Test
        void weightedJaccardShouldBeOneForPerfectTermMatch() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.5)));
            index.addDocument(NODE_2, 1, doc(new WeightEntry(100, 0.5)));

            double termScores = index.nodesTextualSim(NODE_1, NODE_2, SimilarityType.WEIGHTED_JACCARD);

            assertEquals(1.0, termScores, 1e-9);
        }

        @Test
        void shouldReturnEmptyWhenAnyNodeIsMissingForNodeSimilarity() {
            assertEquals(0.0, index.nodesTextualSim(999, NODE_1, SimilarityType.WEIGHTED_SUM));
        }
    }

    @Nested
    class TextualRelevancyWithKeywords {

        @Test
        void weightedSumKeywordScoringShouldNormalizeByTermMaxWeight() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 2.0), new WeightEntry(200, 1.0)));
            index.addDocument(NODE_1, 2, doc(new WeightEntry(100, 1.0)));

            Map<Integer, Double> scores = index.nodesConstraintTextualSim(NODE_1, NODE_1,
                    ints(100, 200), doubles(1.0, 1.0), SimilarityType.WEIGHTED_SUM);

            assertEquals(2.0, scores.get(1), 1e-9);
            assertEquals(0.5, scores.get(2), 1e-9);
        }

        @Test
        void cosineKeywordScoringShouldReachOneForMatchingDocumentAndQueryDirection() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 3.0), new WeightEntry(200, 4.0)));

            Map<Integer, Double> scores = index.nodesConstraintTextualSim(NODE_1, NODE_1,
                    ints(100, 200), doubles(3.0, 4.0), SimilarityType.COSINE);

            assertEquals(1.0, scores.get(1), 1e-9);
        }

        @Test
        void weightedJaccardKeywordScoringShouldBeOneForPerfectMatch() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.5), new WeightEntry(200, 0.5)));

            Map<Integer, Double> scores = index.nodesConstraintTextualSim(NODE_1, NODE_1,
                    ints(100, 200), doubles(0.5, 0.5), SimilarityType.WEIGHTED_JACCARD);

            assertEquals(1.0, scores.get(1), 1e-9);
        }

        @Test
        void shouldRejectNullOrMismatchedKeywordInputs() {
            assertThrows(IllegalArgumentException.class, () -> index.nodesConstraintTextualSim(
                    NODE_1, NODE_2, null, doubles(1.0), SimilarityType.WEIGHTED_SUM));
            assertThrows(IllegalArgumentException.class, () -> index.nodesConstraintTextualSim(
                    NODE_1, NODE_2, ints(100), null, SimilarityType.WEIGHTED_SUM));
            assertThrows(IllegalArgumentException.class, () -> index.nodesConstraintTextualSim(
                    NODE_1, NODE_2, ints(100, 200), doubles(1.0), SimilarityType.WEIGHTED_SUM));
        }

        @Test
        void emptyKeywordsShouldReturnEmptyResult() {
            Map<Integer, Double> scores = index.nodesConstraintTextualSim(NODE_1, NODE_2,
                    Collections.emptyList(), Collections.emptyList(), SimilarityType.WEIGHTED_SUM);

            assertTrue(scores.isEmpty());
        }

        @Test
        void shouldStillScoreWhenOnlyOneNodeExists() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 1.0)));

            Map<Integer, Double> scores = index.nodesConstraintTextualSim(NODE_1, 999,
                    ints(100), doubles(1.0), SimilarityType.WEIGHTED_SUM);

            assertEquals(1.0, scores.get(1), 1e-9);
        }

        @Test
        void weightedSumKeywordScoringShouldIgnoreTermsWithZeroMaxWeight() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.0)));

            Map<Integer, Double> scores = index.nodesConstraintTextualSim(
                    NODE_1,
                    NODE_1,
                    ints(100),
                    doubles(1.0),
                    SimilarityType.WEIGHTED_SUM);

            assertTrue(scores.isEmpty(), "Zero max-weight terms should not produce finite scores.");
        }
    }

    @Nested
    class ClusterEnhancedTextualRelevancy {

        @Test
        void weightedSumClusterEnhanceShouldPickBestClusterForEachDocument() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.5)), 0);
            index.addDocument(NODE_1, 1, doc(new WeightEntry(200, 1.0)), 1);

            Map<Integer, Double> scores = index.nodesConstraintTextualSimForClusters(
                    NODE_1, NODE_1, ints(100, 200), doubles(1.0, 1.0), SimilarityType.WEIGHTED_SUM);

            assertEquals(1.0, scores.get(1), 1e-9);
        }

        @Test
        void cosineClusterEnhanceShouldPickHighestClusterCosine() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 3.0), new WeightEntry(200, 4.0)), 1);

            Map<Integer, Double> scores = index.nodesConstraintTextualSimForClusters(
                    NODE_1, NODE_1, ints(100, 200), doubles(3.0, 4.0), SimilarityType.COSINE);

            assertEquals(1.0, scores.get(1), 1e-9);
        }

        @Test
        void weightedJaccardClusterEnhanceShouldPickHighestClusterJaccard() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.5), new WeightEntry(200, 0.5)), 0);

            Map<Integer, Double> scores = index.nodesConstraintTextualSimForClusters(
                    NODE_1, NODE_1, ints(100, 200), doubles(0.5, 0.5), SimilarityType.WEIGHTED_JACCARD);

            assertEquals(1.0, scores.get(1), 1e-9);
        }

        @Test
        void clusterEnhancedRelevancyShouldFallbackWhenClustersDisabled() {
            InvertedListIndex noClusterIndex = new InvertedListIndex(0);
            noClusterIndex.create(NODE_1);
            noClusterIndex.addDocument(NODE_1, 1, doc(new WeightEntry(100, 1.0)));

            Map<Integer, Double> scores = noClusterIndex.nodesConstraintTextualSimForClusters(
                    NODE_1, NODE_1, ints(100), doubles(1.0), SimilarityType.WEIGHTED_SUM);

            assertEquals(1.0, scores.get(1), 1e-9);
        }
    }

    @Nested
    class JaccardAndStats {

        @Test
        void calculateTextualRelevancyJaccardShouldMergeOverlappingDocumentIdsWithMaxPolicy() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 1.0), new WeightEntry(200, 1.0)));
            index.addDocument(NODE_2, 1, doc(new WeightEntry(100, 1.0)));

            Map<Integer, Double> scores = index.calculateTextualRelevancyJaccard(NODE_1, NODE_2);

            assertTrue(scores.containsKey(1));
            assertEquals(0.5, scores.get(1), 1e-9);
        }

        @Test
        void calculateJaccardSimilarityShouldHandleEmptyAndComputeCorrectValue() {
            assertEquals(0.0, index.calculateJaccardSimilarity(Collections.emptySet(), setOf(1, 2)), 1e-9);
            assertEquals(1.0 / 3.0, index.calculateJaccardSimilarity(setOf(1, 2), setOf(2, 3)), 1e-9);
        }

        @Test
        void getTotalDocumentsShouldCountPostingEntriesAcrossNodes() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.5), new WeightEntry(200, 0.5)));
            index.addDocument(NODE_2, 2, doc(new WeightEntry(100, 0.4)));

            assertEquals(3, index.getTotalDocuments());
        }

        @Test
        void getDocumentFrequencyShouldSumPostingListSizesAcrossNodes() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.5)));
            index.addDocument(NODE_1, 2, doc(new WeightEntry(100, 0.6)));
            index.addDocument(NODE_2, 3, doc(new WeightEntry(100, 0.7)));

            assertEquals(3, index.getDocumentFrequency(100));
            assertEquals(0, index.getDocumentFrequency(999));
        }
    }

    @Test
    void concurrentReadWriteSmokeTestShouldComplete() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                index.addDocument(NODE_1, i, doc(new WeightEntry(i, 1.0)));
            }
            latch.countDown();
        });

        Thread reader = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                index.rankingSum(NODE_1, ints(i), doubles(1.0));
            }
            latch.countDown();
        });

        writer.start();
        reader.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Nested
    class CrossDatasetConstraintPartialJoin {

        @Test
        void crossDatasetConstraintShouldFilterInvalidScoresFromZeroMaxWeight() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 0.0)));

            Map<Integer, Double> scores = index.crossDatasetConstraintTextualSim(
                    NODE_1,
                    ints(100),
                    doubles(1.0),
                    SimilarityType.WEIGHTED_SUM);

            assertTrue(scores.isEmpty(), "Cross-dataset partial scores should not contain NaN/Infinity entries.");
        }

        @Test
        void crossDatasetConstraintShouldReturnOnlyPositiveFiniteScores() {
            index.addDocument(NODE_1, 1, doc(new WeightEntry(100, 1.0)));
            index.addDocument(NODE_1, 2, doc(new WeightEntry(100, 0.5)));

            Map<Integer, Double> scores = index.crossDatasetConstraintTextualSim(
                    NODE_1,
                    ints(100),
                    doubles(1.0),
                    SimilarityType.WEIGHTED_SUM);

            assertEquals(2, scores.size());
            assertTrue(scores.values().stream().allMatch(v -> v > 0.0 && Double.isFinite(v)));
        }
    }
}

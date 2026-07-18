package org.ual.documentindex.invertedlist.legacy;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ual.documentindex.RankingSumMode;
import org.ual.documentindex.SimilarityType;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class LegacyOptimizedInvertedListTest {
    private InvertedListIndex index;
    private static final int TEST_NODE_ID = 1;
    private static final int NUM_CLUSTERS = 3;

    @BeforeEach
    void setUp() {
        index = new InvertedListIndex(NUM_CLUSTERS);
        index.create(TEST_NODE_ID);
    }

    @Test
    void testCreate() {
        int newNodeId = 2;
        index.create(newNodeId);

        Map<Integer, Double> result = index.rankingSum(newNodeId, Collections.singletonList(1), Collections.singletonList(1.0));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testAddDocument() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));
        document.add(new WeightEntry(200, 0.8));

        index.addDocument(TEST_NODE_ID, 1, document);

        Map<Integer, Double> scores = index.rankingSum(TEST_NODE_ID, Collections.singletonList(100), Collections.singletonList(1.0));
        assertTrue(scores.containsKey(1));
    }

    @Test
    void testAddDocumentWithCluster() {
        ArrayList<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));
        document.add(new WeightEntry(200, 0.8));

        index.addDocument(TEST_NODE_ID, 1, document, 0);

        Map<Integer, Double> scores = index.rankingSumClusterEnhance(TEST_NODE_ID,
                Collections.singletonList(100), Collections.singletonList(1.0));
        assertTrue(scores.containsKey(1));
    }

    @Test
    void testAddDocumentToNonExistentNode() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));

        assertThrows(IllegalArgumentException.class,
                () -> index.addDocument(999, 1, document));
    }

    @Test
    void testStore() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));
        document.add(new WeightEntry(200, 0.8));

        index.addDocument(TEST_NODE_ID, 1, document);
        List<WeightEntry> pseudoDoc = index.store(TEST_NODE_ID);

        assertEquals(2, pseudoDoc.size());
        assertTrue(pseudoDoc.stream().anyMatch(we -> we.word == 100 && we.weight == 0.5));
    }

    @Test
    void testStoreClusterEnhance() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));
        doc1.add(new WeightEntry(200, 0.8));

        index.addDocument(TEST_NODE_ID, 1, doc1, 0);
        List<List<WeightEntry>> clusterDocs = index.storeClusterEnhance(TEST_NODE_ID);

        assertEquals(NUM_CLUSTERS, clusterDocs.size());
        assertFalse(clusterDocs.get(0).isEmpty());
    }

    @Test
    void testRankingSum() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));
        document.add(new WeightEntry(200, 0.8));

        index.addDocument(TEST_NODE_ID, 1, document);

        Map<Integer, Double> scores = index.rankingSum(TEST_NODE_ID,
                Arrays.asList(100, 200), Arrays.asList(1.0, 1.0), RankingSumMode.PRECISE);

        assertEquals(1, scores.size());
        assertEquals(1.3, scores.get(1), 0.001);
    }

    @Test
    void testRankingSumClusterEnhance() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));

        index.addDocument(TEST_NODE_ID, 1, doc1, 0);

        Map<Integer, Double> scores = index.rankingSumClusterEnhance(TEST_NODE_ID,
                Collections.singletonList(100), Collections.singletonList(1.0), RankingSumMode.PRECISE);

        assertTrue(scores.containsKey(1));
        assertEquals(0.5, scores.get(1), 0.001);
    }

    @Test
    void testBooleanFilter() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));
        doc1.add(new WeightEntry(200, 0.8));

        index.addDocument(TEST_NODE_ID, 1, doc1);

        Map<Integer, Integer> matches = index.booleanFilter(TEST_NODE_ID,
                new ArrayList<>(Arrays.asList(100, 200)));

        assertEquals(1, matches.size());
        assertEquals(2, matches.get(1));
    }

    @Test
    void testCalculateTextualRelevancyWeightedSum() {
        int nodeId2 = 2;
        index.create(nodeId2);

        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));
        index.addDocument(TEST_NODE_ID, 1, doc1);

        Map<Integer, Double> scores = index.nodesConstraintTextualSim(TEST_NODE_ID, nodeId2,
                Collections.singletonList(100), Collections.singletonList(1.0), SimilarityType.WEIGHTED_SUM);

        assertTrue(scores.containsKey(1));
    }

    @Test
    void testCalculateTextualRelevancyCosine() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));
        doc1.add(new WeightEntry(200, 0.8));

        index.addDocument(TEST_NODE_ID, 1, doc1);

        Map<Integer, Double> scores = index.nodesConstraintTextualSim(TEST_NODE_ID, TEST_NODE_ID,
                Arrays.asList(100, 200), Arrays.asList(0.6, 0.8), SimilarityType.COSINE);

        assertTrue(scores.containsKey(1));
    }

    @Test
    void testCalculateTextualRelevancyWeightedJaccard() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));

        index.addDocument(TEST_NODE_ID, 1, doc1);

        Map<Integer, Double> scores = index.nodesConstraintTextualSim(TEST_NODE_ID, TEST_NODE_ID,
                Collections.singletonList(100), Collections.singletonList(0.7), SimilarityType.WEIGHTED_JACCARD);

        assertTrue(scores.containsKey(1));
    }

    @Test
    void testCalculateTextualRelevancyClusterEnhance() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));

        index.addDocument(TEST_NODE_ID, 1, doc1, 0);

        Map<Integer, Double> scores = index.nodesConstraintTextualSimForClusters(
                TEST_NODE_ID, TEST_NODE_ID, Collections.singletonList(100), Collections.singletonList(1.0),
                SimilarityType.WEIGHTED_SUM);

        assertTrue(scores.containsKey(1));
    }

    @Test
    void testGetTotalDocuments() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));

        index.addDocument(TEST_NODE_ID, 1, doc1);
        index.addDocument(TEST_NODE_ID, 2, doc1);

        int total = index.getTotalDocuments();
        assertEquals(2, total);
    }

    @Test
    void testGetDocumentFrequency() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));

        index.addDocument(TEST_NODE_ID, 1, doc1);
        index.addDocument(TEST_NODE_ID, 2, doc1);

        int frequency = index.getDocumentFrequency(100);
        assertEquals(2, frequency);
    }

    @Test
    void testEmptyDocumentHandling() {
        List<WeightEntry> emptyDoc = new ArrayList<>();
        index.addDocument(TEST_NODE_ID, 1, emptyDoc);

        Map<Integer, Double> scores = index.rankingSum(TEST_NODE_ID, Collections.singletonList(100), Collections.singletonList(1.0));
        assertTrue(scores.isEmpty());
    }

    @Test
    void testDuplicateTermsInDocument() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));
        document.add(new WeightEntry(100, 0.8)); // Same term, different weight

        index.addDocument(TEST_NODE_ID, 1, document);
        List<WeightEntry> stored = index.store(TEST_NODE_ID);

        // Verify how duplicates are handled
        assertEquals(1, stored.stream().filter(we -> we.word == 100).count());
    }

    // TODO: Reject negative weights in addDocument and test that behavior
    @Test
    void testZeroAndNegativeWeights() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.0));
        document.add(new WeightEntry(200, -0.5));

        //assertThrows(IllegalArgumentException.class, () -> index.addDocument(TEST_NODE_ID,1, document));

        index.addDocument(TEST_NODE_ID, 1, document);
        Map<Integer, Double> scores = index.rankingSum(TEST_NODE_ID, Arrays.asList(100, 200), Arrays.asList(1.0, 1.0), RankingSumMode.PRECISE);

        assertTrue(scores.containsKey(1), "Document 1 should be in scores");
        assertEquals(-0.5, scores.get(1), 0.001);
    }

    @Test
    void testInvalidClusterIds() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));

        // Test negative cluster
        assertThrows(IllegalArgumentException.class,
            () -> index.addDocument(TEST_NODE_ID, 1, document, -1));

        // Test cluster >= numberOfClusters
        assertThrows(IllegalArgumentException.class,
            () -> index.addDocument(TEST_NODE_ID, 1, document, NUM_CLUSTERS));
    }

    @Test
    void testMismatchedKeywordWeightLists() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));
        index.addDocument(TEST_NODE_ID, 1, document);

        // Mismatched sizes should be handled gracefully or throw exception
        assertThrows(IllegalArgumentException.class,
            () -> index.rankingSum(TEST_NODE_ID, Arrays.asList(100, 200), Arrays.asList(1.0)));
    }

    @Test
    void testMultipleDocumentsWithSameTerm() {
        List<WeightEntry> doc1 = new ArrayList<>();
        doc1.add(new WeightEntry(100, 0.5));

        List<WeightEntry> doc2 = new ArrayList<>();
        doc2.add(new WeightEntry(100, 0.8));

        index.addDocument(TEST_NODE_ID, 1, doc1);
        index.addDocument(TEST_NODE_ID, 2, doc2);

        Map<Integer, Double> scores = index.rankingSum(TEST_NODE_ID, Collections.singletonList(100), Collections.singletonList(1.0), RankingSumMode.PRECISE);
        assertEquals(2.0, scores.size());
        assertEquals(0.5, scores.get(1), 0.001);
        assertEquals(0.8, scores.get(2), 0.001);
    }

    @Test
    void testCosineSimilarityCorrectness() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 3.0));
        document.add(new WeightEntry(200, 4.0));
        index.addDocument(TEST_NODE_ID, 1, document);

        // Query vector [3, 4], document vector [3, 4]
        // Cosine similarity should be 1.0
        Map<Integer, Double> scores = index.nodesConstraintTextualSim(TEST_NODE_ID, TEST_NODE_ID,
            Arrays.asList(100, 200), Arrays.asList(3.0, 4.0), SimilarityType.COSINE);

        assertEquals(1.0, scores.get(1), 0.001);
    }

    @Test
    void testJaccardSimilarityCorrectness() {
        List<WeightEntry> document = new ArrayList<>();
        document.add(new WeightEntry(100, 0.5));
        document.add(new WeightEntry(200, 0.5));
        index.addDocument(TEST_NODE_ID, 1, document);

        // Perfect match should give Jaccard = 1.0
        Map<Integer, Double> scores = index.nodesConstraintTextualSim(TEST_NODE_ID, TEST_NODE_ID,
            Arrays.asList(100, 200), Arrays.asList(0.5, 0.5), SimilarityType.WEIGHTED_JACCARD);

        assertEquals(1.0, scores.get(1), 0.001);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                ArrayList<WeightEntry> doc = new ArrayList<>();
                doc.add(new WeightEntry(i, 1.0));
                index.addDocument(TEST_NODE_ID, i, doc);
            }
            latch.countDown();
        });

        Thread reader = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                index.rankingSum(TEST_NODE_ID, Collections.singletonList(i), Collections.singletonList(1.0));
            }
            latch.countDown();
        });

        writer.start();
        reader.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}

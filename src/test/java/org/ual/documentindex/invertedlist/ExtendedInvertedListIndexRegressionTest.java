package org.ual.documentindex.invertedlist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ual.documentindex.RankingSumMode;
import org.ual.documentindex.SimilarityType;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedInvertedListIndexRegressionTest {

    private static final int NODE_1 = 1;
    private static final int NODE_2 = 2;

    private InvertedListIndex baseline;
    private ExtendedInvertedListIndex signed;

    @BeforeEach
    void setUp() {
        baseline = new InvertedListIndex(3);
        signed = new ExtendedInvertedListIndex(3);

        baseline.create(NODE_1);
        baseline.create(NODE_2);
        signed.create(NODE_1);
        signed.create(NODE_2);

        addSameDoc(NODE_1, 10, doc(new WeightEntry(1, 0.5), new WeightEntry(2, 0.2)));
        addSameDoc(NODE_1, 11, doc(new WeightEntry(2, 0.7), new WeightEntry(3, 0.8)));
        addSameDoc(NODE_2, 20, doc(new WeightEntry(2, 0.9), new WeightEntry(4, 0.3)));
    }

    @Test
    void updatesSignatureAndCachedStatsOnInsert() {
        long signature = signed.getNodeSignature(NODE_1);
        assertTrue((signature & (1L << (1 % 64))) != 0L);
        assertTrue((signature & (1L << (2 % 64))) != 0L);
        assertTrue((signature & (1L << (3 % 64))) != 0L);

        assertEquals(0.5, signed.getTermMaxWeight(NODE_1, 1), 1e-9);
        assertEquals(0.7, signed.getTermMaxWeight(NODE_1, 2), 1e-9);
        assertEquals(0.8, signed.getTermMaxWeight(NODE_1, 3), 1e-9);

        double expectedNorm = Math.sqrt(0.5 * 0.5 + 0.7 * 0.7 + 0.8 * 0.8);
        assertEquals(expectedNorm, signed.getNodeNorm(NODE_1), 1e-9);
    }

    @Test
    void rankingSumMatchesBaseline() {
        List<Integer> keywords = Arrays.asList(1, 2, 5, 3);
        List<Double> weights = Arrays.asList(1.0, 2.0, 3.0, 0.5);

        Map<Integer, Double> expected = baseline.rankingSum(NODE_1, keywords, weights, RankingSumMode.PRECISE);
        Map<Integer, Double> actual = signed.rankingSum(NODE_1, keywords, weights, RankingSumMode.PRECISE);

        assertEquals(expected, actual);
    }

    @Test
    void booleanFilterMatchesBaseline() {
        List<Integer> keywords = Arrays.asList(2, 3, 99);

        Map<Integer, Integer> expected = baseline.booleanFilter(NODE_1, keywords);
        Map<Integer, Integer> actual = signed.booleanFilter(NODE_1, keywords);

        assertEquals(expected, actual);
    }

    @Test
    void nodePairAndSimilarityPathsMatchBaseline() {
        assertEquals(baseline.rankingSum(NODE_1, NODE_2), signed.rankingSum(NODE_1, NODE_2), 1e-9);

        double expected = baseline.nodesTextualSim(NODE_1, NODE_2, SimilarityType.WEIGHTED_SUM);
        double actual = signed.nodesTextualSim(NODE_1, NODE_2, SimilarityType.WEIGHTED_SUM);
        assertEquals(expected, actual);
    }

    private void addSameDoc(int nodeId, int docId, List<WeightEntry> entries) {
        baseline.addDocument(nodeId, docId, entries);
        signed.addDocument(nodeId, docId, entries);
    }

    private static List<WeightEntry> doc(WeightEntry... entries) {
        return Arrays.asList(entries);
    }
}


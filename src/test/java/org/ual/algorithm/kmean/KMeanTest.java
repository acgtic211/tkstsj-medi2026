package org.ual.algorithm.kmean;

import org.junit.jupiter.api.Test;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storage.IStore;
import org.ual.spatialindex.storage.Weight;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KMeanTest {

    @Test
    void legacyApiProducesAssignmentsForAllDocuments() {
        IStore store = createTwoGroupDataset();

        HashMap<Integer, Integer> assignments = KMean.calculateKMean(store, 2, 0);

        assertEquals(4, assignments.size());
        for (Integer cluster : assignments.values()) {
            assertTrue(cluster >= 0 && cluster < 2);
        }
    }

    @Test
    void sameSeedProducesDeterministicAssignments() {
        IStore store = createTwoGroupDataset();
        KMeanConfig config = KMeanConfig.builder(2)
                .maxMovesWithoutChange(0)
                .initializationStrategy(InitializationStrategy.KMEANS_PLUS_PLUS)
                .randomSeed(42L)
                .build();

        HashMap<Integer, Integer> first = KMean.calculateKMean(store, config);
        HashMap<Integer, Integer> second = KMean.calculateKMean(store, config);

        assertEquals(first, second);
    }

    @Test
    void configurableStrategiesBothReturnValidAssignments() {
        IStore store = createTwoGroupDataset();

        KMeanConfig randomConfig = KMeanConfig.builder(2)
                .maxMovesWithoutChange(0)
                .initializationStrategy(InitializationStrategy.RANDOM)
                .randomSeed(7L)
                .build();

        KMeanConfig plusPlusConfig = KMeanConfig.builder(2)
                .maxMovesWithoutChange(0)
                .initializationStrategy(InitializationStrategy.KMEANS_PLUS_PLUS)
                .randomSeed(7L)
                .build();

        HashMap<Integer, Integer> randomAssignments = KMean.calculateKMean(store, randomConfig);
        HashMap<Integer, Integer> plusPlusAssignments = KMean.calculateKMean(store, plusPlusConfig);

        assertEquals(4, randomAssignments.size());
        assertEquals(4, plusPlusAssignments.size());
    }

    @Test
    void throwsWhenNumClustersExceedsDocuments() {
        IStore store = createTwoGroupDataset();

        assertThrows(IllegalArgumentException.class, () -> KMean.calculateKMean(store, 5, 0));
    }

    @Test
    void throwsWhenStoreIsEmpty() {
        IStore emptyStore = new HashMapDocumentStore();

        assertThrows(IllegalArgumentException.class, () -> KMean.calculateKMean(emptyStore, 2, 0));
    }

    @Test
    void handlesEmptyClusterReinitializationWithoutFailure() {
        IStore store = createIdenticalDataset();
        KMeanConfig config = KMeanConfig.builder(3)
                .maxMovesWithoutChange(0)
                .initializationStrategy(InitializationStrategy.RANDOM)
                .randomSeed(9L)
                .build();

        HashMap<Integer, Integer> assignments = KMean.calculateKMean(store, config);

        assertNotNull(assignments);
        assertEquals(4, assignments.size());
    }

    @Test
    void maxIterationsSafeguardStillReturnsAssignments() {
        IStore store = createTwoGroupDataset();
        KMeanConfig config = KMeanConfig.builder(2)
                .maxMovesWithoutChange(0)
                .maxIterations(1)
                .initializationStrategy(InitializationStrategy.KMEANS_PLUS_PLUS)
                .randomSeed(123L)
                .build();

        HashMap<Integer, Integer> assignments = KMean.calculateKMean(store, config);

        assertEquals(4, assignments.size());
    }

    private static IStore createTwoGroupDataset() {
        HashMapDocumentStore store = new HashMapDocumentStore();

        store.write(new Weight(0, entries(new int[]{0, 1}, new double[]{1.0, 1.0})));
        store.write(new Weight(1, entries(new int[]{0, 1}, new double[]{1.0, 0.9})));
        store.write(new Weight(2, entries(new int[]{8, 9}, new double[]{1.0, 1.0})));
        store.write(new Weight(3, entries(new int[]{8, 9}, new double[]{0.8, 1.0})));

        return store;
    }

    private static IStore createIdenticalDataset() {
        HashMapDocumentStore store = new HashMapDocumentStore();

        store.write(new Weight(0, entries(new int[]{1, 2}, new double[]{1.0, 1.0})));
        store.write(new Weight(1, entries(new int[]{1, 2}, new double[]{1.0, 1.0})));
        store.write(new Weight(2, entries(new int[]{1, 2}, new double[]{1.0, 1.0})));
        store.write(new Weight(3, entries(new int[]{1, 2}, new double[]{1.0, 1.0})));

        return store;
    }

    private static ArrayList<WeightEntry> entries(int[] words, double[] weights) {
        ArrayList<WeightEntry> entries = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            entries.add(new WeightEntry(words[i], weights[i]));
        }
        return entries;
    }
}

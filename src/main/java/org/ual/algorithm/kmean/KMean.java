package org.ual.algorithm.kmean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.storage.IStore;
import org.ual.spatialindex.storage.Weight;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * KMeans clustering using cosine similarity over sparse document vectors.
 *
 * <p>The legacy static API remains available through {@link #calculateKMean(IStore, int, int)}
 * and delegates to a deterministic configurable engine.</p>
 */
public final class KMean {
    private static final Logger logger = LogManager.getLogger(KMean.class);

    private final KMeanConfig config;
    private final Random random;

    private List<DocEntry> docs;
    private MedoidEntry[] medoids;
    private int[] assignments;
    private int dimension;

    private KMean(KMeanConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.config = config;
        this.random = new Random(config.getRandomSeed());
        this.docs = Collections.emptyList();
        this.medoids = new MedoidEntry[0];
        this.assignments = new int[0];
        this.dimension = 0;
    }

    /**
     * Backward-compatible entry point used by CIRTree/CDIRTree code paths.
     */
    public static HashMap<Integer, Integer> calculateKMean(IStore weights, int numClusters, int numMoves) {
        KMeanConfig config = KMeanConfig.builder(numClusters)
                .maxMovesWithoutChange(numMoves)
                .build();
        return calculateKMean(weights, config);
    }

    /**
     * Configurable clustering entry point.
     */
    public static HashMap<Integer, Integer> calculateKMean(IStore weights, KMeanConfig config) {
        return new KMean(config).cluster(weights);
    }

    private HashMap<Integer, Integer> cluster(IStore weights) {
        if (weights == null) {
            throw new IllegalArgumentException("weights cannot be null");
        }

        long startTime = System.currentTimeMillis();
        loadDocuments(weights);

        if (docs.isEmpty()) {
            throw new IllegalArgumentException("No documents found in weights");
        }
        if (config.getNumClusters() > docs.size()) {
            throw new IllegalArgumentException("numClusters cannot exceed number of documents");
        }

        logger.info("KMean started with {} docs, {} clusters (strategy={}, seed={})",
                docs.size(), config.getNumClusters(), config.getInitializationStrategy(), config.getRandomSeed());

        medoids = new MedoidEntry[config.getNumClusters()];
        initializeMedoids();

        int iteration = 0;
        int moves;
        do {
            moves = assignDocuments();
            recomputeMedoids();
            iteration++;
            if (logger.isDebugEnabled()) {
                logger.debug("Iteration {} completed with {} moves", iteration, moves);
                logger.debug("Cluster distribution: {}", buildClusterDistribution());
            }

            if (iteration >= config.getMaxIterations()) {
                logger.warn("KMean reached maxIterations={} before full convergence", config.getMaxIterations());
                break;
            }
        } while (moves > config.getMaxMovesWithoutChange());

        long endTime = System.currentTimeMillis();
        logger.info("KMean finished in {} ms with {} iterations (strategy={}, seed={})",
                (endTime - startTime), iteration, config.getInitializationStrategy(), config.getRandomSeed());

        return buildResultMap();
    }

    private void loadDocuments(IStore weights) {
        List<DocEntry> loadedDocs = new ArrayList<>();

        int maxDocId = -1;
        int maxWordId = -1;

        Iterator<Weight> iterator = weights.iterator();
        while (iterator.hasNext()) {
            Weight weight = iterator.next();
            if (weight == null) {
                continue;
            }

            int docId = weight.wordId;
            maxDocId = Math.max(maxDocId, docId);

            List<WeightEntry> entries = weight.weights == null ? Collections.<WeightEntry>emptyList() : weight.weights;
            WordEntry[] words = new WordEntry[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                WeightEntry entry = entries.get(i);
                words[i] = new WordEntry(entry.word, entry.weight);
                maxWordId = Math.max(maxWordId, entry.word);
            }

            loadedDocs.add(new DocEntry(docId, words));
        }

        loadedDocs.sort(Comparator.comparingInt(DocEntry::getId));
        docs = loadedDocs;

        dimension = Math.max(maxWordId + 1, 0);
        assignments = new int[Math.max(maxDocId + 1, 0)];
        Arrays.fill(assignments, -1);
    }

    private void initializeMedoids() {
        if (config.getInitializationStrategy() == InitializationStrategy.RANDOM) {
            initializeRandom();
            return;
        }
        initializeKMeansPlusPlus();
    }

    private void initializeRandom() {
        List<Integer> indexes = new ArrayList<>(docs.size());
        for (int i = 0; i < docs.size(); i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes, random);

        for (int i = 0; i < medoids.length; i++) {
            DocEntry seed = docs.get(indexes.get(i));
            medoids[i] = new MedoidEntry(seed.getId(), dimension);
            medoids[i].copyFrom(seed);
        }
    }

    private void initializeKMeansPlusPlus() {
        boolean[] selected = new boolean[docs.size()];

        int firstIndex = random.nextInt(docs.size());
        selected[firstIndex] = true;
        medoids[0] = new MedoidEntry(docs.get(firstIndex).getId(), dimension);
        medoids[0].copyFrom(docs.get(firstIndex));

        for (int i = 1; i < medoids.length; i++) {
            double[] distances = new double[docs.size()];
            double totalDistance = 0.0;

            for (int j = 0; j < docs.size(); j++) {
                if (selected[j]) {
                    distances[j] = 0.0;
                    continue;
                }

                double minDistance = Double.MAX_VALUE;
                for (int k = 0; k < i; k++) {
                    double distance = 1.0 - cosineSimilarity(docs.get(j), medoids[k]);
                    distance = Math.max(0.0, distance);
                    minDistance = Math.min(minDistance, distance * distance);
                }

                if (minDistance == Double.MAX_VALUE) {
                    minDistance = 0.0;
                }

                distances[j] = minDistance;
                totalDistance += minDistance;
            }

            int selectedIndex = pickWeightedIndex(distances, totalDistance, selected);
            selected[selectedIndex] = true;
            medoids[i] = new MedoidEntry(docs.get(selectedIndex).getId(), dimension);
            medoids[i].copyFrom(docs.get(selectedIndex));
        }
    }

    private int pickWeightedIndex(double[] distances, double totalDistance, boolean[] selected) {
        if (totalDistance <= 0.0) {
            return pickRandomUnselectedIndex(selected);
        }

        double threshold = random.nextDouble() * totalDistance;
        double cumulative = 0.0;

        for (int i = 0; i < distances.length; i++) {
            if (selected[i]) {
                continue;
            }
            cumulative += distances[i];
            if (cumulative >= threshold) {
                return i;
            }
        }

        return pickRandomUnselectedIndex(selected);
    }

    private int pickRandomUnselectedIndex(boolean[] selected) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            if (!selected[i]) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No candidate left for centroid selection");
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    private int assignDocuments() {
        int moves = 0;
        for (DocEntry doc : docs) {
            int closest = findClosestMedoid(doc);
            int previous = assignments[doc.getId()];
            if (previous != closest) {
                moves++;
                assignments[doc.getId()] = closest;
            }
        }
        return moves;
    }

    private int findClosestMedoid(DocEntry doc) {
        double bestSimilarity = Double.NEGATIVE_INFINITY;
        int bestCluster = -1;

        for (int i = 0; i < medoids.length; i++) {
            double similarity = cosineSimilarity(doc, medoids[i]);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestCluster = i;
            }
        }

        if (bestCluster < 0) {
            throw new IllegalStateException("No medoid available for assignment");
        }
        return bestCluster;
    }

    private void recomputeMedoids() {
        for (MedoidEntry medoid : medoids) {
            medoid.resetAccumulator();
        }

        for (DocEntry doc : docs) {
            int cluster = assignments[doc.getId()];
            if (cluster < 0 || cluster >= medoids.length) {
                throw new IllegalStateException("Invalid cluster assignment for document " + doc.getId() + ": " + cluster);
            }
            medoids[cluster].add(doc);
        }

        for (int i = 0; i < medoids.length; i++) {
            if (medoids[i].getCardinality() == 0) {
                medoids[i].copyFrom(docs.get(random.nextInt(docs.size())));
                continue;
            }
            medoids[i].finalizeAverage();
        }
    }

    private HashMap<Integer, Integer> buildResultMap() {
        HashMap<Integer, Integer> result = new HashMap<>();
        for (DocEntry doc : docs) {
            int cluster = assignments[doc.getId()];
            if (cluster < 0 || cluster >= medoids.length) {
                throw new IllegalStateException("Invalid final cluster assignment for document " + doc.getId());
            }
            result.put(doc.getId(), cluster);
        }
        return result;
    }

    private Map<Integer, Integer> buildClusterDistribution() {
        Map<Integer, Integer> distribution = new HashMap<>();
        for (DocEntry doc : docs) {
            int cluster = assignments[doc.getId()];
            if (cluster >= 0) {
                distribution.merge(cluster, 1, Integer::sum);
            }
        }
        return distribution;
    }

    static double cosineSimilarity(DocEntry doc, MedoidEntry medoid) {
        if (doc.getNorm() == 0.0 || medoid.getNorm() == 0.0) {
            return 0.0;
        }

        double numerator = 0.0;
        double[] medoidWords = medoid.getWords();
        for (WordEntry word : doc.getWords()) {
            numerator += word.getWeight() * medoidWords[word.getId()];
        }

        return numerator / (doc.getNorm() * medoid.getNorm());
    }
}

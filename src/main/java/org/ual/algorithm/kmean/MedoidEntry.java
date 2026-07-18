package org.ual.algorithm.kmean;

import java.util.Arrays;

/**
 * Dense centroid representation used for cosine-similarity clustering updates.
 */
final class MedoidEntry {
    private int sourceDocId;
    private final double[] words;
    private int cardinality;
    private double norm;

    MedoidEntry(int sourceDocId, int size) {
        this.sourceDocId = sourceDocId;
        this.words = new double[size];
        this.cardinality = 0;
        this.norm = 0.0;
    }

    int getSourceDocId() {
        return sourceDocId;
    }

    double[] getWords() {
        return words;
    }

    int getCardinality() {
        return cardinality;
    }

    double getNorm() {
        return norm;
    }

    void copyFrom(DocEntry docEntry) {
        sourceDocId = docEntry.getId();
        Arrays.fill(words, 0.0);
        for (WordEntry word : docEntry.getWords()) {
            words[word.getId()] = word.getWeight();
        }
        cardinality = 1;
        norm = docEntry.getNorm();
    }

    void resetAccumulator() {
        cardinality = 0;
        norm = 0.0;
        Arrays.fill(words, 0.0);
    }

    void add(DocEntry docEntry) {
        cardinality++;
        for (WordEntry word : docEntry.getWords()) {
            words[word.getId()] += word.getWeight();
        }
    }

    void finalizeAverage() {
        if (cardinality == 0) {
            norm = 0.0;
            return;
        }

        double sumOfSquares = 0.0;
        for (int i = 0; i < words.length; i++) {
            words[i] /= cardinality;
            sumOfSquares += words[i] * words[i];
        }
        norm = Math.sqrt(sumOfSquares);
    }
}

package org.ual.algorithm.kmean;

/**
 * Sparse coordinate of a document vector.
 */
final class WordEntry {
    private final int id;
    private final double weight;

    WordEntry(int id, double weight) {
        this.id = id;
        this.weight = weight;
    }

    int getId() {
        return id;
    }

    double getWeight() {
        return weight;
    }
}

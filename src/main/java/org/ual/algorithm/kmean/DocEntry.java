package org.ual.algorithm.kmean;

/**
 * Immutable sparse document vector used during clustering.
 */
final class DocEntry {
    private final int id;
    private final WordEntry[] words;
    private final double norm;

    DocEntry(int id, WordEntry[] words) {
        this.id = id;
        this.words = words;
        this.norm = computeNorm(words);
    }

    int getId() {
        return id;
    }

    WordEntry[] getWords() {
        return words;
    }

    double getNorm() {
        return norm;
    }

    private static double computeNorm(WordEntry[] words) {
        double sumOfSquares = 0.0;
        for (WordEntry word : words) {
            sumOfSquares += word.getWeight() * word.getWeight();
        }
        return Math.sqrt(sumOfSquares);
    }
}

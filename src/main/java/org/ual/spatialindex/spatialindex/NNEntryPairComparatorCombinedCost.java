package org.ual.spatialindex.spatialindex;

import java.util.Comparator;

/**
 * Comparator for NNEntryPair objects that orders by combined cost (primary),
 * then spatial cost (secondary), and finally textual cost (tertiary).
 * Used in nearest neighbor search to prioritize entries by their aggregate cost metrics.
 */
public class NNEntryPairComparatorCombinedCost implements Comparator<NNEntryPair> {
    /**
     * Compares two NNEntryPair objects using a three-level sort strategy.
     *
     * @param pair1 the first entry pair to compare
     * @param pair2 the second entry pair to compare
     * @return a negative integer, zero, or a positive integer as pair1 is less than,
     *         equal to, or greater than pair2 based on combined cost, spatial cost,
     *         and textual cost in that priority order
     */
    @Override
    public int compare(NNEntryPair pair1, NNEntryPair pair2) {
        int result = Double.compare(pair1.combinedCost, pair2.combinedCost);
        if (result != 0) return result;
        result = Double.compare(pair1.spatialCost, pair2.spatialCost);
        if (result != 0) return result;

        return Double.compare(pair1.textualCost, pair2.textualCost);
    }
}

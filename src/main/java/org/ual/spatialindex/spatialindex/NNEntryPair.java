package org.ual.spatialindex.spatialindex;

import java.util.Objects;


/**
 * Represents a pair of Rtree entries with associated costs for nearest neighbor search.
 * This class is primarily used in spatio-keyword join (SKJOIN) queries to track and
 * compare pairs of entries based on their combined spatial and textual similarity costs.
 */
public class NNEntryPair implements Comparable<NNEntryPair> {
    public final RtreeEntry entry1;
    public final RtreeEntry entry2;
    public final double combinedCost;
    public final double spatialCost; // Debug: spatial cost for the pair
    public final double textualCost; // Debug: textual cost for the pair


    public NNEntryPair(RtreeEntry entry1, RtreeEntry entry2, double spatialCost, double textualCost, double combinedCost) {
        this.entry1 = entry1;
        this.entry2 = entry2;
        this.spatialCost = spatialCost;
        this.textualCost = textualCost;
        this.combinedCost = combinedCost;
    }

    @Override
    public int compareTo(NNEntryPair other) {
        // First compare by combinedCost (primary sorting criteria)
        int result = Double.compare(this.combinedCost, other.combinedCost);
        if (result != 0) return result;

        // If combined costs are equal, compare by spatial cost
        result = Double.compare(this.spatialCost, other.spatialCost);
        if (result != 0) return result;

        // If spatial costs are also equal, compare by textual cost
        return Double.compare(this.textualCost, other.textualCost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entry1, entry2);
    }
}

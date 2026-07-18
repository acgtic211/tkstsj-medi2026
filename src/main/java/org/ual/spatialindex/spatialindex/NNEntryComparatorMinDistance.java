package org.ual.spatialindex.spatialindex;

import java.util.Comparator;

/**
 * Original comparator that use minDistance
 */
public class NNEntryComparatorMinDistance implements Comparator<NNEntry> {
//    @Override
//    public int compare(NNEntry n1, NNEntry n2) {
//        if (n1.minDistance < n2.minDistance) return -1;
//        if (n1.minDistance > n2.maxDistance) return 1;
//        return 0;
//    }
    @Override
    public int compare(NNEntry n1, NNEntry n2) {
        return Double.compare(n1.getSpatialCost(), n2.getSpatialCost());
    }
}

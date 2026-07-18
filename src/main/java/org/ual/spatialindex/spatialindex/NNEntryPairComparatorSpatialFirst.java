package org.ual.spatialindex.spatialindex;

import java.util.Comparator;

public class NNEntryPairComparatorSpatialFirst implements Comparator<NNEntryPair> {
    @Override
    public int compare(NNEntryPair pair1, NNEntryPair pair2) {
        int result = Double.compare(pair1.spatialCost, pair2.spatialCost);
        if (result != 0) return result;
        result = Double.compare(pair1.textualCost, pair2.textualCost);
        if (result != 0) return result;

        return Double.compare(pair1.combinedCost, pair2.combinedCost);
    }
}

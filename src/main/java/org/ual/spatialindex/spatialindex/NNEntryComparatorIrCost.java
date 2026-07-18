package org.ual.spatialindex.spatialindex;

import java.util.Comparator;

public class NNEntryComparatorIrCost implements Comparator<NNEntry> {
    public int compare(NNEntry n1, NNEntry n2) {
        return Double.compare(n1.getIrCost(), n2.getIrCost());
    }
}

package org.ual.spatialindex.spatialindex;

public interface INearestNeighborComparator {
    double getMinimumDistance(IShape query, IEntry e);
}

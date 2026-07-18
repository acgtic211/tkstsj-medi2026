package org.ual.spatialindex.spatialindex;

import org.ual.spatialindex.storagemanager.PropertySet;

public interface ISpatialIndex {
    void insertData(int id, final IShape shape);
    boolean deleteData(int id, final IShape shape);
    void containmentQuery(final IShape query, final IVisitor v);
    void intersectionQuery(final IShape query, final IVisitor v);
    void pointLocationQuery(final IShape query, final IVisitor v);
    void nearestNeighborQuery(int k, final IShape query, final IVisitor v, INearestNeighborComparator nnc);
    void nearestNeighborQuery(int k, final IShape query, final IVisitor v);
    void queryStrategy(final IQueryStrategy qs);
    PropertySet getIndexProperties();
    boolean isIndexValid();
    IStatistics getStatistics();
}

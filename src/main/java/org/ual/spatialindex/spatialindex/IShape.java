package org.ual.spatialindex.spatialindex;

public interface IShape {
    boolean intersects(final IShape s);
    boolean contains(final IShape s);
    boolean touches(final IShape s);
    double[] getCenter();
    long getDimension();
    Region getMBR();
    double getArea();
    double getMinimumDistance(final IShape s);
}

package org.ual.spatialindex.spatialindex;

public class Point implements IShape, Cloneable {
    private double[] coordinates;

    public Point(double[] coordinates) {
        if (coordinates == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        this.coordinates = new double[coordinates.length];
        System.arraycopy(coordinates, 0, this.coordinates, 0, coordinates.length);
    }

    public Point(final Point point) {
        if (point == null) {
            throw new IllegalArgumentException("Point cannot be null");
        }
        this.coordinates = new double[point.coordinates.length];
        System.arraycopy(point.coordinates, 0, this.coordinates, 0, point.coordinates.length);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Point) {
            Point otherPoint = (Point) o;

            if (otherPoint.coordinates.length != coordinates.length)
                return false;

            for (int dimension = 0; dimension < coordinates.length; dimension++) {
                if (coordinates[dimension] < otherPoint.coordinates[dimension] - SpatialIndex.EPSILON
                        || coordinates[dimension] > otherPoint.coordinates[dimension] + SpatialIndex.EPSILON)
                    return false;
            }

            return true;
        }

        return false;
    }

    //
    // Cloneable interface
    //

    @Override
    public Object clone() {
        return new Point(coordinates);
    }

    //
    // IShape interface
    //

    @Override
    public boolean intersects(final IShape shape) {
        if (shape instanceof Region) {
            return ((Region) shape).contains(this);
        }
        return false;
    }

    @Override
    public boolean contains(final IShape shape) {
        return false;
    }

    @Override
    public boolean touches(final IShape shape) {
        if (shape instanceof Point && this.equals(shape)) {
            return true;
        }
        if (shape instanceof Region) {
            return ((Region) shape).touches(this);
        }
        return false;
    }

    @Override
    public double[] getCenter() {
        double[] centerCoordinates = new double[coordinates.length];
        System.arraycopy(coordinates, 0, centerCoordinates, 0, coordinates.length);
        return centerCoordinates;
    }

    @Override
    public long getDimension() {
        return coordinates.length;
    }

    @Override
    public Region getMBR() {
        return new Region(coordinates, coordinates);
    }

    @Override
    public double getArea() {
        return 0.0;
    }

    @Override
    public double getMinimumDistance(final IShape shape) {
        if (shape instanceof Region) {
            return ((Region) shape).getMinimumDistance(this);
        }
        if (shape instanceof Point) {
            return getMinimumDistance((Point) shape);
        }
        throw new IllegalStateException("getMinimumDistance: Not implemented yet!");
    }

    double getMinimumDistance(final Point point) {
        if (coordinates.length != point.coordinates.length) {
            throw new IllegalArgumentException("getMinimumDistance: Shape has the wrong number of dimensions.");
        }

        double sumOfSquaredDifferences = 0.0;
        for (int i = 0; i < coordinates.length; i++) {
            sumOfSquaredDifferences += Math.pow(coordinates[i] - point.coordinates[i], 2.0);
        }
        return Math.sqrt(sumOfSquaredDifferences);
    }

    public double getCoord(int index) throws IndexOutOfBoundsException {
        if (index >= coordinates.length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for dimension " + coordinates.length);
        }
        return coordinates[index];
    }

    public int getCoordLength() {
        return coordinates.length;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < coordinates.length; i++) {
            sb.append((int) coordinates[i]);
            if (i < coordinates.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }
}

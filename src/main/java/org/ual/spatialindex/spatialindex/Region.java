package org.ual.spatialindex.spatialindex;

import java.util.Arrays;
import java.util.Collection;

public class Region implements IShape {
    private double[] low;
    private double[] high;

    public Region() {
    }

    public Region(final double[] lowerBounds, final double[] upperBounds) {
        if (lowerBounds.length != upperBounds.length) {
            throw new IllegalArgumentException("Region: arguments have different number of dimensions.");
        }

        this.low = new double[lowerBounds.length];
        System.arraycopy(lowerBounds, 0, this.low, 0, lowerBounds.length);

        this.high = new double[upperBounds.length];
        System.arraycopy(upperBounds, 0, this.high, 0, upperBounds.length);
    }

    public Region(final Point lowerPoint, final Point upperPoint) {
        if (lowerPoint.getDimension() != upperPoint.getDimension()) {
            throw new IllegalArgumentException("Region: arguments have different number of dimensions.");
        }

        int dimensions = (int)lowerPoint.getDimension();
        this.low = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            this.low[i] = lowerPoint.getCoord(i);
        }

        this.high = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            this.high[i] = upperPoint.getCoord(i);
        }
    }

    public Region(final Region region) {
        this.low = new double[region.low.length];
        System.arraycopy(region.low, 0, this.low, 0, region.low.length);

        this.high = new double[region.high.length];
        System.arraycopy(region.high, 0, this.high, 0, region.high.length);
    }

    public Region(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Region: dimension must be positive.");
        }

        this.low = new double[dimension];
        this.high = new double[dimension];
    }

    @Deprecated
    public static Region recalculateMBR(Region[] mbr, int children) {
        if (mbr == null || mbr.length == 0 || children <= 0) {
            throw new IllegalArgumentException("Invalid input: mbr array is empty or children is non-positive.");
        }

        int dimensions = mbr[0].low.length;
        double[] lowerBounds = new double[dimensions];
        double[] upperBounds = new double[dimensions];

        // Initialize bounds arrays
        Arrays.fill(lowerBounds, Double.POSITIVE_INFINITY);
        Arrays.fill(upperBounds, Double.NEGATIVE_INFINITY);

        // Calculate the MBR
        for (int i = 0; i < children; i++) {
            double[] currentLow = mbr[i].low;
            double[] currentHigh = mbr[i].high;

            for (int dim = 0; dim < dimensions; dim++) {
                lowerBounds[dim] = Math.min(lowerBounds[dim], currentLow[dim]);
                upperBounds[dim] = Math.max(upperBounds[dim], currentHigh[dim]);
            }
        }

        return new Region(lowerBounds, upperBounds);
    }

    public static Region recalculateMBR(Collection<Region> nodeEntriesMBRs) {
        if (nodeEntriesMBRs == null || nodeEntriesMBRs.isEmpty()) {
            throw new IllegalArgumentException("Invalid input: mbr collection is empty or null.");
        }

        Region firstRegion = nodeEntriesMBRs.iterator().next();
        int dimensions = firstRegion.low.length;
        double[] lowerBounds = new double[dimensions];
        double[] upperBounds = new double[dimensions];

        // Initialize bounds arrays
        Arrays.fill(lowerBounds, Double.POSITIVE_INFINITY);
        Arrays.fill(upperBounds, Double.NEGATIVE_INFINITY);

        // Calculate the MBR
        for (Region region : nodeEntriesMBRs) {
            double[] currentLow = region.low;
            double[] currentHigh = region.high;

            for (int dim = 0; dim < dimensions; dim++) {
                lowerBounds[dim] = Math.min(lowerBounds[dim], currentLow[dim]);
                upperBounds[dim] = Math.max(upperBounds[dim], currentHigh[dim]);
            }
        }

        return new Region(lowerBounds, upperBounds);
    }


    /**
     * Expands the MBR by a given epsilon value in all dimensions.
     * @param epsilon The amount to expand the MBR in each dimension.
     * @return A new Region that represents the expanded MBR.
     */
    public Region expandMBR(double epsilon) {
        if (epsilon < 0) {
            throw new IllegalArgumentException("Epsilon must be non-negative.");
        }

        int dimensions = low.length;
        double[] newLow = new double[dimensions];
        double[] newHigh = new double[dimensions];

        for (int dim = 0; dim < dimensions; dim++) {
            newLow[dim] = low[dim] - epsilon;
            newHigh[dim] = high[dim] + epsilon;
        }

        return new Region(newLow, newHigh);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Region)) {
            return false;
        }

        Region other = (Region) obj;
        if (other.low.length != low.length) {
            return false;
        }

        for (int dim = 0; dim < low.length; dim++) {
            if (low[dim] < other.low[dim] - SpatialIndex.EPSILON ||
                    low[dim] > other.low[dim] + SpatialIndex.EPSILON ||
                    high[dim] < other.high[dim] - SpatialIndex.EPSILON ||
                    high[dim] > other.high[dim] + SpatialIndex.EPSILON) {
                return false;
            }
        }
        return true;
    }

    //
    // Cloneable interface
    //

    @Override
    public Region clone() {
        return new Region(low, high);
    }

    //
    // IShape interface implementation
    //

    @Override
    public boolean intersects(final IShape shape) {
        if (shape instanceof Region) return intersects((Region) shape);
        if (shape instanceof Point) return contains((Point) shape);
        throw new IllegalStateException("intersects: Not implemented for this shape type!");
    }

    @Override
    public boolean contains(final IShape shape) {
        if (shape instanceof Region) return contains((Region) shape);
        if (shape instanceof Point) return contains((Point) shape);
        throw new IllegalStateException("contains: Not implemented for this shape type!");
    }

    @Override
    public boolean touches(final IShape shape) {
        if (shape instanceof Region) return touches((Region) shape);
        if (shape instanceof Point) return touches((Point) shape);
        throw new IllegalStateException("touches: Not implemented for this shape type!");
    }

    @Override
    public double[] getCenter() {
        double[] centerCoordinates = new double[low.length];
        for (int dim = 0; dim < low.length; dim++) {
            centerCoordinates[dim] = (low[dim] + high[dim]) / 2.0;
        }
        return centerCoordinates;
    }

    @Override
    public long getDimension() {
        return low.length;
    }

    @Override
    public Region getMBR() {
        return new Region(low, high);
    }

    @Override
    public double getArea() {
        double area = 1.0;
        for (int dim = 0; dim < low.length; dim++) {
            area *= high[dim] - low[dim];
        }
        return area;
    }

    @Override
    public double getMinimumDistance(final IShape shape) {
        if (shape instanceof Region) return getMinimumDistance((Region) shape);
        if (shape instanceof Point) return getMinimumDistance((Point) shape);
        throw new IllegalStateException("getMinimumDistance: Not implemented for this shape type!");
    }

    public double getMaximumDistance(final IShape shape) {
        if (shape instanceof Region) {
            Region region = (Region) shape;
            if (low.length != region.low.length) {
                throw new IllegalArgumentException("getMaximumDistance: Shape has the wrong number of dimensions.");
            }

            double distance = 0.0;
            for (int dim = 0; dim < low.length; dim++) {
                double dimDistance = Math.max(Math.abs(high[dim] - region.low[dim]), Math.abs(region.high[dim] - low[dim]));
                distance += dimDistance * dimDistance;
            }

            return Math.sqrt(distance);
        } else if (shape instanceof Point) {
            Point point = (Point) shape;
            if (low.length != point.getDimension()) {
                throw new IllegalArgumentException("getMaximumDistance: Shape has the wrong number of dimensions.");
            }

            double distanceSquared = 0.0;
            for (int dim = 0; dim < low.length; dim++) {
                double coord = point.getCoord(dim);
                double dimDistance = Math.max(Math.abs(high[dim] - coord), Math.abs(coord - low[dim]));
                distanceSquared += dimDistance * dimDistance;
            }

            return Math.sqrt(distanceSquared);
        } else {
            throw new IllegalStateException("getMaximumDistance: Not implemented for this shape type!");
        }
    }

    public double getDiagonalLength() {
        double sum = 0.0;
        for (int dim = 0; dim < low.length; dim++) {
            double length = high[dim] - low[dim];
            sum += length * length;
        }
        return Math.sqrt(sum);
    }


    public double getMaxExtent() {
        double maxExtent = 0.0;
        for (int dim = 0; dim < low.length; dim++) {
            double extent = high[dim] - low[dim];
            if (extent > maxExtent) {
                maxExtent = extent;
            }
        }
        return maxExtent;
    }

    public boolean intersects(final Region region) {
        if (region == null) {
            throw new IllegalArgumentException("intersects: Region must not be null.");
        }
        if (low.length != region.low.length) {
            throw new IllegalArgumentException("intersects: Shape has the wrong number of dimensions.");
        }

        for (int dim = 0; dim < low.length; dim++) {
            if (low[dim] > region.high[dim] + SpatialIndex.EPSILON
                    || high[dim] < region.low[dim] - SpatialIndex.EPSILON) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(final Region region) {
        if (region == null) {
            throw new IllegalArgumentException("contains: Region must not be null.");
        }
        if (low.length != region.low.length) {
            throw new IllegalArgumentException("contains: Shape has the wrong number of dimensions.");
        }

        for (int dim = 0; dim < low.length; dim++) {
            if (low[dim] > region.low[dim] + SpatialIndex.EPSILON
                    || high[dim] < region.high[dim] - SpatialIndex.EPSILON) {
                return false;
            }
        }
        return true;
    }

    public boolean touches(final Region region) {
        if (low.length != region.low.length) {
            throw new IllegalArgumentException("touches: Shape has the wrong number of dimensions.");
        }

        for (int dim = 0; dim < low.length; dim++) {
            if ((low[dim] > region.low[dim] - SpatialIndex.EPSILON && low[dim] < region.low[dim] + SpatialIndex.EPSILON) ||
                    (high[dim] > region.high[dim] - SpatialIndex.EPSILON && high[dim] < region.high[dim] + SpatialIndex.EPSILON)) {
                return true;
            }
        }
        return false;
    }


    public double getMinimumDistance(final Region region) {
        if (low.length != region.low.length) {
            throw new IllegalArgumentException("getMinimumDistance: Shape has the wrong number of dimensions.");
        }

        double distance = 0.0;
        for (int dim = 0; dim < low.length; dim++) {
            double dimDistance = 0.0;
            if (region.high[dim] < low[dim]) {
                dimDistance = Math.abs(region.high[dim] - low[dim]);
            } else if (high[dim] < region.low[dim]) {
                dimDistance = Math.abs(region.low[dim] - high[dim]);
            }
            distance += dimDistance * dimDistance;
        }

        return Math.sqrt(distance);
    }

    public boolean contains(final Point point) {
        if (low.length != point.getDimension()) {
            throw new IllegalArgumentException("contains: Shape has the wrong number of dimensions.");
        }

        for (int dim = 0; dim < low.length; dim++) {
            if (low[dim] > point.getCoord(dim) || high[dim] < point.getCoord(dim)) {
                return false;
            }
        }
        return true;
    }

    public boolean touches(final Point point) {
        if (low.length != point.getDimension()) {
            throw new IllegalArgumentException("touches: Shape has the wrong number of dimensions.");
        }

        for (int dim = 0; dim < low.length; dim++) {
            double coord = point.getCoord(dim);
            if ((low[dim] > coord - SpatialIndex.EPSILON && low[dim] < coord + SpatialIndex.EPSILON) ||
                    (high[dim] > coord - SpatialIndex.EPSILON && high[dim] < coord + SpatialIndex.EPSILON)) {
                return true;
            }
        }
        return false;
    }

    public double getMinimumDistance(final Point point) {
        if (low.length != point.getDimension()) {
            throw new IllegalArgumentException("getMinimumDistance: Shape has the wrong number of dimensions.");
        }

        double distanceSquared = 0.0;
        for (int dim = 0; dim < low.length; dim++) {
            double coord = point.getCoord(dim);
            if (coord < low[dim]) {
                distanceSquared += Math.pow(low[dim] - coord, 2);
            } else if (coord > high[dim]) {
                distanceSquared += Math.pow(coord - high[dim], 2);
            }
        }

        return Math.sqrt(distanceSquared);
    }

    public double getIntersectingArea(final Region region) {
        if (low.length != region.low.length) {
            throw new IllegalArgumentException("getIntersectingArea: Shape has the wrong number of dimensions.");
        }

        // Check for intersection
        for (int dim = 0; dim < low.length; dim++) {
            if (low[dim] > region.high[dim] || high[dim] < region.low[dim]) {
                return 0.0;
            }
        }

        double area = 1.0;
        for (int dim = 0; dim < low.length; dim++) {
            double minHigh = Math.min(high[dim], region.high[dim]);
            double maxLow = Math.max(low[dim], region.low[dim]);
            area *= (minHigh - maxLow);
        }

        return area;
    }

    public Region getIntersectingRegion(final Region region) {
        if (low.length != region.low.length) {
            throw new IllegalArgumentException("getIntersectingRegion: Regions have different number of dimensions.");
        }

        // Check for intersection
        for (int dim = 0; dim < low.length; dim++) {
            if (low[dim] > region.high[dim] || high[dim] < region.low[dim]) {
                // No intersection, return empty region
                double[] zeroCoords = new double[low.length];
                return new Region(zeroCoords, zeroCoords);
            }
        }

        // Calculate intersection
        double[] newLow = new double[low.length];
        double[] newHigh = new double[low.length];

        for (int dim = 0; dim < low.length; dim++) {
            newLow[dim] = Math.max(low[dim], region.low[dim]);
            newHigh[dim] = Math.min(high[dim], region.high[dim]);
        }

        return new Region(newLow, newHigh);
    }

    public Region combinedRegion(final Region region) {
        if (low.length != region.low.length) {
            throw new IllegalArgumentException("combinedRegion: Shape has the wrong number of dimensions.");
        }

        double[] newLow = new double[low.length];
        double[] newHigh = new double[low.length];

        for (int dim = 0; dim < low.length; dim++) {
            newLow[dim] = Math.min(low[dim], region.low[dim]);
            newHigh[dim] = Math.max(high[dim], region.high[dim]);
        }

        return new Region(newLow, newHigh);
    }

    public static Region combinedRegion(Region[] regions) {
        if (regions == null || regions.length == 0) {
            throw new IllegalArgumentException("combinedRegion: Input array is empty or null.");
        }

        int dimensions = regions[0].low.length;
        double[] newLow = new double[dimensions];
        double[] newHigh = new double[dimensions];

        for (int dim = 0; dim < dimensions; dim++) {
            newLow[dim] = Double.POSITIVE_INFINITY;
            newHigh[dim] = Double.NEGATIVE_INFINITY;

            for (Region region : regions) {
                newLow[dim] = Math.min(newLow[dim], region.low[dim]);
                newHigh[dim] = Math.max(newHigh[dim], region.high[dim]);
            }
        }

        return new Region(newLow, newHigh);
    }

    // Modifies the first argument to include the second.
    public static void combinedRegion(Region regionToModify, final Region constantRegion) {
        if (regionToModify.low.length != constantRegion.low.length) {
            throw new IllegalArgumentException("combinedRegion: Shape has the wrong number of dimensions.");
        }

        for (int dim = 0; dim < regionToModify.low.length; dim++) {
            regionToModify.low[dim] = Math.min(regionToModify.low[dim], constantRegion.low[dim]);
            regionToModify.high[dim] = Math.max(regionToModify.high[dim], constantRegion.high[dim]);
        }
    }

    // Returns the margin of a region. It is calculated as the sum of 2^(d-1) * width in each dimension.
    public double getMargin() {
        double multiplier = Math.pow(2.0, (low.length) - 1.0);
        double margin = 0.0;

        for (int dim = 0; dim < low.length; dim++) {
            margin += (high[dim] - low[dim]) * multiplier;
        }

        return margin;
    }

    public double getLow(int index) throws IndexOutOfBoundsException {
        if (index >= low.length) {
            throw new IndexOutOfBoundsException("Index " + index + " is out of bounds for dimension " + low.length);
        }
        return low[index];
    }

    public double getHigh(int index) throws IndexOutOfBoundsException {
        if (index >= low.length) {
            throw new IndexOutOfBoundsException("Index " + index + " is out of bounds for dimension " + high.length);
        }
        return high[index];
    }

    public void setLow(double[] lowValues) {
        if (lowValues.length != low.length) {
            throw new IllegalArgumentException("setLow: Input array has different number of dimensions.");
        }
        System.arraycopy(lowValues, 0, this.low, 0, lowValues.length);
    }

    public void setLow(int dim, double minLow) {
        if (dim >= low.length) {
            throw new IndexOutOfBoundsException("Index " + dim + " is out of bounds for dimension " + low.length);
        }
        this.low[dim] = minLow;
    }

    public void setHigh(int dim, double maxHigh) {
        if (dim >= high.length) {
            throw new IndexOutOfBoundsException("Index " + dim + " is out of bounds for dimension " + high.length);
        }
        this.high[dim] = maxHigh;
    }

    public void setHigh(double[] highValues) {
        if (highValues.length != high.length) {
            throw new IllegalArgumentException("setHigh: Input array has different number of dimensions.");
        }
        System.arraycopy(highValues, 0, this.high, 0, highValues.length);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Append lower bounds
        for (int dim = 0; dim < low.length; dim++) {
            sb.append(low[dim]);
            if (dim < low.length - 1) {
                sb.append(" ");
            }
        }

        sb.append(" : ");

        // Append upper bounds
        for (int dim = 0; dim < high.length; dim++) {
            sb.append(high[dim]);
            if (dim < high.length - 1) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }

    public double getMinX() {
        if (low.length == 0) {
            throw new IllegalStateException("Region has no dimensions.");
        }
        return low[0];
    }

    public double getMinY() {
        if (low.length < 2) {
            throw new IllegalStateException("Region has no Y dimension.");
        }
        return low[1];
    }

    public double getMaxX() {
        if (low.length == 0) {
            throw new IllegalStateException("Region has no dimensions.");
        }
        return high[0];
    }

    public double getMaxY() {
        if (low.length < 2) {
            throw new IllegalStateException("Region has no Y dimension.");
        }
        return high[1];
    }

    public double getCenterX() {
        if (low.length == 0) {
            throw new IllegalStateException("Region has no dimensions.");
        }
        return (low[0] + high[0]) / 2.0;
    }

    public double getCenterY() {
        if (low.length < 2) {
            throw new IllegalStateException("Region has no Y dimension.");
        }
        return (low[1] + high[1]) / 2.0;
    }


}

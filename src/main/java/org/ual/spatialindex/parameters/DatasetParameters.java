package org.ual.spatialindex.parameters;

import org.ual.spatialindex.spatialindex.Point;

public class DatasetParameters {
    public final String keywordFile;
    public final String locationFile;
    public final double latitudeStart;
    public final double latitudeEnd;
    public final double longitudeStart;
    public final double longitudeEnd;
    public final int uniqueKeywords;
    public final double maxEuclideanDistance; // Maximum Euclidean distance (d_max) between any two points in the bounded region
    public final int[] topkWords; // Top-k words for each dataset. Calculated in KeywordsAnalyzer.java
    public final Point[] topkPoints; // Top-k points for each dataset. Calculated in LocationsAnalyzer.java

    public DatasetParameters(String keywordFile, String locationFile, double latitudeStart, double latitudeEnd, double longitudeStart, double longitudeEnd, int uniqueKeywords, int[] topkWords, Point[] topkPoints) {
        this.keywordFile = keywordFile;
        this.locationFile = locationFile;
        this.latitudeStart = latitudeStart;
        this.latitudeEnd = latitudeEnd;
        this.longitudeStart = longitudeStart;
        this.longitudeEnd = longitudeEnd;
        this.uniqueKeywords = uniqueKeywords;
        this.topkWords = topkWords;
        this.topkPoints = topkPoints;
        this.maxEuclideanDistance = calculateMaxEuclideanDistance();
    }

    /**
     * Calculates the maximum Euclidean distance (d_max) between any two points in the bounded region.
     * This value represents the diagonal distance of the rectangular region defined by the latitude
     * and longitude bounds.
     *<p>
     * Formula:
     * <pre>
     * d_max = √[(latitudeEnd - latitudeStart)² + (longitudeEnd - longitudeStart)²]
     * </pre>
     *
     * @return The maximum possible Euclidean distance between any two points in the bounded region
     * @see #maxEuclideanDistance field which stores this pre-calculated value
     */
    private double calculateMaxEuclideanDistance() {
        double latitudeDifference = latitudeEnd - latitudeStart;
        double longitudeDifference = longitudeEnd - longitudeStart;
        return Math.sqrt(latitudeDifference * latitudeDifference +
                        longitudeDifference * longitudeDifference);
    }
}

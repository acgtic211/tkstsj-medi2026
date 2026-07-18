package org.ual.querygeneration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.query.Query;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.spatialindex.Point;
import org.ual.spatialindex.spatialindex.Region;

import java.util.ArrayList;
import java.util.List;

public class SKNNQueryGenerator extends QueryGenerator {

    private static final Logger log = LogManager.getLogger(SKNNQueryGenerator.class);

    public SKNNQueryGenerator(int seed, DatasetParameters parameters) {
        super(seed, parameters);
    }

    public List<SKNNQuery> generateBooleanKNNQueries(int numQueries, int numKeywords, double querySpaceAreaPercentage, double keywordSpaceSizePercentage) {
        resetRandomSeed();
        List<SKNNQuery> bkQueries = new ArrayList<>();
        double areaRatio = normalizeAreaRatio(querySpaceAreaPercentage);

        for (int queryId = 0; queryId < numQueries; queryId++) {
            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart) * Math.sqrt(areaRatio);
            double longtitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart) * Math.sqrt(areaRatio);

            double centroidLatitude = randomCenterInSpan(parameters.latitudeStart, parameters.latitudeEnd, latitudeSpan);
            double centroidLongtitude = randomCenterInSpan(parameters.longitudeStart, parameters.longitudeEnd, longtitudeSpan);

            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100.0);
            int keywordSpaceMiddle = RANDOM.nextInt(parameters.uniqueKeywords - keywordSpaceSpan + 1);

            // TODO: Change creation method
            Query query = createTopKQuery(queryId, numKeywords, keywordSpaceMiddle, keywordSpaceSpan, centroidLatitude,
                    centroidLongtitude, latitudeSpan, longtitudeSpan, parameters.topkWords);
            // TESTING
//            if(queryId < 5) {
//                query.keywords.clear();
//                query.keywords.add(1);
//                //query.location = new Point(new double[]{106, -6});
//            }
//            query.keywords.clear();
//            query.keywords.add(1);
            //System.out.println("Query: " + query.toString());

//            SKNNQuery bkQuery = new SKNNQuery(queryId);
//            bkQuery.setKNNKQuery(query);
            SKNNQuery bkQuery = new SKNNQuery(query);
            bkQueries.add(bkQuery);
        }

        return bkQueries;
    }

    public List<SKNNQuery> generateTopKNNQueries(int numQueries, int numberOfKeywords, double querySpaceAreaPercentage, double keywordSpaceSizePercentage) {
        resetRandomSeed();
        List<SKNNQuery> tkQueries = new ArrayList<>();
        double areaRatio = normalizeAreaRatio(querySpaceAreaPercentage);

        // Generate QueryID, Point(X, Y) and List<Int> Keywords
        for (int queryId = 0; queryId < numQueries; queryId++) {
            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart) * Math.sqrt(areaRatio);
            double longitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart) * Math.sqrt(areaRatio);

            double centroidLatitude = randomCenterInSpan(parameters.latitudeStart, parameters.latitudeEnd, latitudeSpan);
            double centroidLongitude = randomCenterInSpan(parameters.longitudeStart, parameters.longitudeEnd, longitudeSpan);

            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100.0);   // Added Math.ceil to fix rounding issue to 0
            int keywordSpaceMiddle = RANDOM.nextInt(parameters.uniqueKeywords - keywordSpaceSpan + 1);

            //double queryWeight = 1.0;
            // TODO: Check creation method
            //Query query = createKWQuery(queryId, queryWeight, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan, centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan);
            Query query = createTopKQuery(queryId, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan, centroidLatitude,
                    centroidLongitude, latitudeSpan, longitudeSpan, parameters.topkWords);

            // Test
//            query.keywords.clear();
//            query.keywords.add(1);

//            SKNNQuery tkQuery = new SKNNQuery(queryId);
//            tkQuery.setKNNKQuery(query);
            SKNNQuery tkQuery = new SKNNQuery(query);
            tkQueries.add(tkQuery);
        }

        return tkQueries;
    }

    public List<SKNNQuery> generateBooleanRangeQueries(int numQueries, int numberOfKeywords, double querySpaceAreaPercentage, double keywordSpaceSizePercentage) {
        resetRandomSeed();
        List<SKNNQuery> brQueries = new ArrayList<>();
        double areaRatio = normalizeAreaRatio(querySpaceAreaPercentage);

        // Generate QueryID, Point(X, Y) and List<Int> Keywords
        for (int queryId = 0; queryId < numQueries; queryId++) {
            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart) * Math.sqrt(areaRatio);
            double longitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart) * Math.sqrt(areaRatio);

            double centroidLatitude = randomCenterInSpan(parameters.latitudeStart, parameters.latitudeEnd, latitudeSpan);
            double centroidLongitude = randomCenterInSpan(parameters.longitudeStart, parameters.longitudeEnd, longitudeSpan);

            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100.0);
            int keywordSpaceMiddle = RANDOM.nextInt(parameters.uniqueKeywords - keywordSpaceSpan + 1);

            //double queryWeight = 1.0;
            // TODO: Check creation method
            //Query query = createKWQuery(queryId, queryWeight, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan, centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan);
            Query query = createTopKQuery(queryId, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan, centroidLatitude,
                    centroidLongitude, latitudeSpan, longitudeSpan, parameters.topkWords);

//            SKNNQuery brQuery = new SKNNQuery(queryId);
//            brQuery.setKNNKQuery(query);
            SKNNQuery brQuery = new SKNNQuery(query);
            brQueries.add(brQuery);
        }

        return brQueries;
    }

    // TODO: Check if this is correct
//    public List<SKJoinQuery> generateJoinSKQueries(int numQueries, int numberOfKeywords, double querySpaceAreaPercentage, double keywordSpaceSizePercentage) {
//        resetRandomSeed();
//        List<SKJoinQuery> joinQueries = new ArrayList<>();
//
//        for (int queryId = 0; queryId < numQueries; queryId++) {
//            // Use varied region sizes to capture different spatial densities
//            double regionVariation = 0.5 + RANDOM.nextDouble(); // 0.5 to 1.5 multiplier
//            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart) * Math.sqrt(querySpaceAreaPercentage / 100) * regionVariation;
//            double longitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart) * Math.sqrt(querySpaceAreaPercentage / 100) * regionVariation;
//
//            // Focus on different areas of the dataset
//            double areaFocus = queryId / (double) numQueries;
//            double centroidLatitude = parameters.latitudeStart + areaFocus * (parameters.latitudeEnd - parameters.latitudeStart);
//            double centroidLongitude = parameters.longitudeStart + areaFocus * (parameters.longitudeEnd - parameters.longitudeStart);
//
//            // Add some randomness within the focused area
//            double focusRadius = Math.min(latitudeSpan, longitudeSpan);
//            centroidLatitude += (RANDOM.nextDouble() - 0.5) * focusRadius;
//            centroidLongitude += (RANDOM.nextDouble() - 0.5) * focusRadius;
//
//            // Ensure bounds
//            centroidLatitude = Math.max(parameters.latitudeStart + latitudeSpan/2,
//                              Math.min(parameters.latitudeEnd - latitudeSpan/2, centroidLatitude));
//            centroidLongitude = Math.max(parameters.longitudeStart + longitudeSpan/2,
//                               Math.min(parameters.longitudeEnd - longitudeSpan/2, centroidLongitude));
//
//            // Vary keyword space distribution
//            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100.0);
//            // Use different keyword ranges for different queries
//            int keywordOffset = (queryId * keywordSpaceSpan / 3) % (parameters.uniqueKeywords - keywordSpaceSpan + 1);
//            int keywordSpaceMiddle = keywordOffset;
//
//            Query query = createSelfJoinQuery(queryId, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan,
//                                        centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan, parameters.topkWords);
//
//            SKJoinQuery joinQuery = new SKJoinQuery(query);
//            joinQueries.add(joinQuery);
//        }
//
//        return joinQueries;
//    }

    public List<SKJoinQuery> generateJoinSKQueries(int numQueries, int numberOfKeywords, double querySpaceAreaPercentage, double keywordSpaceSizePercentage, boolean useDensePoints) {
        //TODO Clean up
        log.info("Generating SKNN Join Query with dense points: {}", useDensePoints);
        resetRandomSeed();
        List<SKJoinQuery> joinQueries = new ArrayList<>();
        double areaRatio = normalizeAreaRatio(querySpaceAreaPercentage);
        Point[] densePoints = parameters.topkPoints;

        // Guard rail: Ensure we actually have dense points to work with
        if (useDensePoints && (densePoints == null || densePoints.length == 0)) {
            log.error("Dense points (topkPoints) are not precalculated or are empty. Falling back to random points.");
            useDensePoints = false;
            //throw new IllegalStateException("Dense points (topkPoints) are not precalculated or are empty.");
        }

        for (int queryId = 0; queryId < numQueries; queryId++) {
            double centroidLongitude;
            double centroidLatitude;

            // 1. Calculate spans based on the required area percentage
            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart) * Math.sqrt(areaRatio);
            double longitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart) * Math.sqrt(areaRatio);


            if (useDensePoints) {
                // 2. Select a dense point as the centroid (uses modulo to safely wrap around if numQueries > densePoints.length)
                Point centroid = densePoints[queryId % densePoints.length];

                centroidLongitude = centroid.getCoord(0);
                centroidLatitude = centroid.getCoord(1);
            } else {
                // 2. Generate a random centroid within the bounds of the dataset, ensuring it can accommodate the span
                centroidLatitude = randomCenterInSpan(parameters.latitudeStart, parameters.latitudeEnd, latitudeSpan);
                centroidLongitude = randomCenterInSpan(parameters.longitudeStart, parameters.longitudeEnd, longitudeSpan);
            }

            // Vary keyword space distribution
            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100.0);
            int keywordSpaceMiddle = RANDOM.nextInt(parameters.uniqueKeywords - keywordSpaceSpan + 1);

            //int[] keywords = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }; // Use a fixed set of keywords for joins

            // Create the spatial query using the calculated centroid
            Query query = createTopKQuery(queryId, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan,
                    centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan, parameters.topkWords);

            SKJoinQuery joinQuery = new SKJoinQuery(query);

//            double minX = Math.max(parameters.longitudeStart, centroidLongitude - longitudeSpan / 2.0);
//            double maxX = Math.min(parameters.longitudeEnd, centroidLongitude + longitudeSpan / 2.0);
//            double minY = Math.max(parameters.latitudeStart, centroidLatitude - latitudeSpan / 2.0);
//            double maxY = Math.min(parameters.latitudeEnd, centroidLatitude + latitudeSpan / 2.0);

            // Calculate raw boundaries centered on the point
            double minX = centroidLongitude - longitudeSpan / 2.0;
            double maxX = centroidLongitude + longitudeSpan / 2.0;
            double minY = centroidLatitude - latitudeSpan / 2.0;
            double maxY = centroidLatitude + latitudeSpan / 2.0;

            // Shift Horizontally if out of bounds
            if (minX < parameters.longitudeStart) {
                maxX += (parameters.longitudeStart - minX);
                minX = parameters.longitudeStart;
            } else if (maxX > parameters.longitudeEnd) {
                minX -= (maxX - parameters.longitudeEnd);
                maxX = parameters.longitudeEnd;
            }

            // Shift Vertically if out of bounds
            if (minY < parameters.latitudeStart) {
                maxY += (parameters.latitudeStart - minY);
                minY = parameters.latitudeStart;
            } else if (maxY > parameters.latitudeEnd) {
                minY -= (maxY - parameters.latitudeEnd);
                maxY = parameters.latitudeEnd;
            }

            joinQuery.setSpatialWindow(new Region(new double[]{minX, minY}, new double[]{maxX, maxY}));
            joinQueries.add(joinQuery);
        }

        return joinQueries;
    }


    public List<SKJoinQuery> generateJoinSKQueries(int numQueries, int numberOfKeywords, double querySpaceAreaPercentage, double keywordSpaceSizePercentage) {
        resetRandomSeed();
        List<SKJoinQuery> joinQueries = new ArrayList<>();
        double areaRatio = normalizeAreaRatio(querySpaceAreaPercentage);
        //Point[] densePoints = parameters.topkPoints;

        for (int queryId = 0; queryId < numQueries; queryId++) {
            // Select a dense point as the centroid
            //Point centroid = densePoints[queryId % densePoints.length];
            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart) * Math.sqrt(areaRatio);
            double longitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart) * Math.sqrt(areaRatio);

            double centroidLatitude = randomCenterInSpan(parameters.latitudeStart, parameters.latitudeEnd, latitudeSpan);
            double centroidLongitude = randomCenterInSpan(parameters.longitudeStart, parameters.longitudeEnd, longitudeSpan);


            // Vary keyword space distribution
            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100.0);
            int keywordSpaceMiddle = RANDOM.nextInt(parameters.uniqueKeywords - keywordSpaceSpan + 1);

            //int[] keywords = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }; // Use a fixed set of keywords for joins

            Query query = createTopKQuery(queryId, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan,
                                          centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan, parameters.topkWords);

            SKJoinQuery joinQuery = new SKJoinQuery(query);
            double minX = Math.max(parameters.longitudeStart, centroidLongitude - longitudeSpan / 2.0);
            double maxX = Math.min(parameters.longitudeEnd, centroidLongitude + longitudeSpan / 2.0);
            double minY = Math.max(parameters.latitudeStart, centroidLatitude - latitudeSpan / 2.0);
            double maxY = Math.min(parameters.latitudeEnd, centroidLatitude + latitudeSpan / 2.0);
            joinQuery.setSpatialWindow(new Region(new double[]{minX, minY}, new double[]{maxX, maxY}));
            joinQueries.add(joinQuery);
        }

        return joinQueries;
    }

    private double normalizeAreaRatio(double querySpaceAreaPercentage) {
        if (querySpaceAreaPercentage <= 0.0) {
            return 0.0;
        }

        // Backward compatible: support both [0..100] percentages and [0..1] fractions.
        double ratio = querySpaceAreaPercentage <= 1.0
                ? querySpaceAreaPercentage
                : querySpaceAreaPercentage / 100.0;

        return Math.max(0.0, Math.min(1.0, ratio));
    }

    private double randomCenterInSpan(double lowerBound, double upperBound, double span) {
        double extent = upperBound - lowerBound;
        if (span >= extent) {
            return (lowerBound + upperBound) / 2.0;
        }

        double margin = span / 2.0;
        return (lowerBound + margin) + RANDOM.nextDouble() * (extent - span);
    }
}

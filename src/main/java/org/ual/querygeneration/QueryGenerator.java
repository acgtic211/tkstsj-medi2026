package org.ual.querygeneration;

import org.ual.query.Query;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.spatialindex.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QueryGenerator {
    protected Random RANDOM = new Random(System.currentTimeMillis());
    private final int seed;
    //static int NUMBER_OF_QUERIES = 20;
    protected DatasetParameters parameters;

    public QueryGenerator(int seed, DatasetParameters parameters) {
        this.seed = seed;
        RANDOM.setSeed(seed);
        this.parameters = parameters;
    }

    public void resetRandomSeed() {
        RANDOM.setSeed(seed);
    }

    public int getRandomSeed() {
        return RANDOM.nextInt();
    }

    // Query Generation


    /**
     * Generate a query with keywords and weights
     */
//    protected Query createKWQuery(int queryId, double queryWeight, int numberOfKeywords, int keywordSpaceMiddle, int keywordSpaceSpan,
//                                         double centroidLatitude, double centroidLongitude, double latitudeSpan, double longitudeSpan, int[] topkWords) {
//
//        double x = (centroidLatitude - latitudeSpan / 2) + RANDOM.nextDouble() * latitudeSpan;
//        double y = (centroidLongitude - longitudeSpan / 2) + RANDOM.nextDouble() * longitudeSpan;
//
//        List<Integer> keywords = new ArrayList<>();
//        List<Double> keywordWeights = new ArrayList<>();
//
//        double weightTotal = 0.0;
//
//        for (int k = 0; k < numberOfKeywords; k++) {
//            // Add top-k words if available
//            if(k < topkWords.length) {
//                keywords.add(topkWords[k]);
//            } else {
//                int keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));// Avoid negative values
//                keywords.add(keyword);
//            }
//            //int keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));
//            double keywordWeight = 0.5 + RANDOM.nextDouble() / 2;
//            weightTotal += keywordWeight;
//
//            //keywords.add(keyword);
//            keywordWeights.add(keywordWeight);
//        }
//
//        for (int k = 0; k < keywordWeights.size(); k++) {
//            double weight = (keywordWeights.get(k) / weightTotal);
//            keywordWeights.set(k, weight);
//        }
//
//        Query query = new Query(queryId, queryWeight, new Point(new double[]{x, y}), keywords, keywordWeights);
//
//        return query;
//    }
    public Query createKWQuery(int queryId, double queryWeight, int numberOfKeywords, int keywordSpaceMiddle, int keywordSpaceSpan,
                               double centroidLatitude, double centroidLongitude, double latitudeSpan, double longitudeSpan, int[] topkWords) {

        double x = (centroidLongitude - longitudeSpan / 2) + RANDOM.nextDouble() * longitudeSpan;
        double y = (centroidLatitude - latitudeSpan / 2) + RANDOM.nextDouble() * latitudeSpan;

        List<Integer> keywords = new ArrayList<>();
        List<Double> keywordWeights = new ArrayList<>();

        double weightTotal = 0.0;

        for (int k = 0; k < numberOfKeywords; k++) {
            // Add top-k words if available
            if(k < topkWords.length) {
                keywords.add(topkWords[k]);
            } else {
                int keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));// Avoid negative values
                keywords.add(keyword);
            }

            double keywordWeight = 0.5 + RANDOM.nextDouble() / 2;
            weightTotal += keywordWeight;
            keywordWeights.add(keywordWeight);
        }

        for (int k = 0; k < keywordWeights.size(); k++) {
            double weight = (keywordWeights.get(k) / weightTotal);
            keywordWeights.set(k, weight);
        }

        Query query = new Query(queryId, queryWeight, new Point(new double[]{x, y}), keywords, keywordWeights);

        return query;
    }

    /**
     * Generate a keyword only query, without weights
     */
//    protected Query createTopKQuery(int queryId, int numberOfKeywords, int keywordSpaceMiddle, int keywordSpaceSpan,
//                                           double centroidLatitude, double centroidLongitude, double latitudeSpan, double longitudeSpan, int[] topkWords) {
//
//        double x = (centroidLatitude - latitudeSpan / 2) + RANDOM.nextDouble() * latitudeSpan;
//        double y = (centroidLongitude - longitudeSpan / 2) + RANDOM.nextDouble() * longitudeSpan;
//
//        List<Integer> keywords = new ArrayList<>();
//
//        for (int k = 0; k < numberOfKeywords; k++) {
//            // Add top-k words if available
//            if(k < topkWords.length) {
//                keywords.add(topkWords[k]);
//            } else {
//                int keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));// Avoid negative values
//                keywords.add(keyword);
//            }
//            //int keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));
//            //System.out.println("Keyword GENERATED: " + keyword);
//            //keywords.add(keyword);
//        }
//
//        Query query = new Query(queryId, new Point(new double[]{x, y}), keywords);
//
//        return query;
//    }
    public Query createTopKQuery(int queryId, int numberOfKeywords, int keywordSpaceMiddle, int keywordSpaceSpan,
                                 double centroidLatitude, double centroidLongitude, double latitudeSpan, double longitudeSpan, int[] topkWords) {

        double x = (centroidLongitude - longitudeSpan / 2) + RANDOM.nextDouble() * longitudeSpan;
        double y = (centroidLatitude - latitudeSpan / 2) + RANDOM.nextDouble() * latitudeSpan;

        List<Integer> keywords = new ArrayList<>();
        List<Double> keywordWeights = new ArrayList<>();

        double weightTotal = 0.0;

        for (int k = 0; k < numberOfKeywords; k++) {
            // Add top-k words if available
            if(k < topkWords.length) {
                keywords.add(topkWords[k]);
            } else {
                int keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));// Avoid negative values
                keywords.add(keyword);
            }

            double keywordWeight = 0.5 + RANDOM.nextDouble() / 2;
            weightTotal += keywordWeight;
            keywordWeights.add(keywordWeight);
        }

        for (int k = 0; k < keywordWeights.size(); k++) {
            double weight = (keywordWeights.get(k) / weightTotal);
            keywordWeights.set(k, weight);
        }

        Query query = new Query(queryId, new Point(new double[]{x, y}), keywords, keywordWeights);

        return query;
    }


    public Query createRangeQuery(int queryId, double queryWeight, int numberOfKeywords, Point point, double radius,
                                  int keywordSpaceMiddle, int keywordSpaceSpan, int[] topkWords) {

        // Create a point centered around the given point and within the given radius
        double[] coordinates = new double[point.getCoordLength()];
        for (int i = 0; i < point.getCoordLength(); i++) {
            coordinates[i] = point.getCoord(i) + (RANDOM.nextDouble() * radius);
        }

        List<Integer> keywords = new ArrayList<>();
        List<Double> keywordWeights = new ArrayList<>();

        double weightTotal = 0.0;

        for (int k = 0; k < numberOfKeywords; k++) {
            // Add top-k words if available
            if(k < topkWords.length) {
                keywords.add(topkWords[k]);
            } else {
                int keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));// Avoid negative values
                keywords.add(keyword);
            }
            //int keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));
            double keywordWeight = 0.5 + RANDOM.nextDouble() / 2;
            weightTotal += keywordWeight;

            //keywords.add(keyword);
            keywordWeights.add(keywordWeight);
        }

        for (int k = 0; k < keywordWeights.size(); k++) {
            double weight = (keywordWeights.get(k) / weightTotal);
            keywordWeights.set(k, weight);
        }

        Query query = new Query(queryId, queryWeight, new Point(coordinates), keywords, keywordWeights);

        return query;
    }


    public Query createSelfJoinQuery(int queryId, int numberOfKeywords, int keywordSpaceMiddle, int keywordSpaceSpan,
                                     double centroidLatitude, double centroidLongitude, double latitudeSpan, double longitudeSpan, int[] topkWords) {

        // Create overlapping spatial regions to increase join probability
        double overlapFactor = 0.3; // 30% overlap with neighboring regions
        double adjustedLatSpan = latitudeSpan * (1 + overlapFactor);
        double adjustedLngSpan = longitudeSpan * (1 + overlapFactor);

        double x = (centroidLongitude - adjustedLngSpan / 2) + RANDOM.nextDouble() * adjustedLngSpan;
        double y = (centroidLatitude - adjustedLatSpan / 2) + RANDOM.nextDouble() * adjustedLatSpan;

        List<Integer> keywords = new ArrayList<>();
        List<Double> keywordWeights = new ArrayList<>();

        double weightTotal = 0.0;

        // Use more common keywords for better join results
        int commonKeywordRatio = Math.max(1, numberOfKeywords / 2); // At least half should be common

        for (int k = 0; k < numberOfKeywords; k++) {
            int keyword;

            if(k < topkWords.length) {
                // Prioritize top-k words as they're most common
                keywords.add(topkWords[k]);
            } else if (k < commonKeywordRatio) {
                // Use keywords from the center of the keyword space (more common)
                int centerOffset = keywordSpaceSpan / 4; // Bias toward center
                keyword = keywordSpaceMiddle + centerOffset + RANDOM.nextInt(Math.max(1, keywordSpaceSpan / 2));
                keywords.add(keyword);
            } else {
                // Use broader keyword range for diversity
                keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));
                keywords.add(keyword);
            }

            // Weight common keywords higher
            double keywordWeight;
            if (k < topkWords.length || k < commonKeywordRatio) {
                keywordWeight = 0.7 + RANDOM.nextDouble() * 0.3; // Higher weights for common keywords
            } else {
                keywordWeight = 0.3 + RANDOM.nextDouble() * 0.4; // Lower weights for diverse keywords
            }

            weightTotal += keywordWeight;
            keywordWeights.add(keywordWeight);
        }

        // Normalize weights
        for (int k = 0; k < keywordWeights.size(); k++) {
            double weight = (keywordWeights.get(k) / weightTotal);
            keywordWeights.set(k, weight);
        }

        Query query = new Query(queryId, new Point(new double[]{x, y}), keywords, keywordWeights);
        return query;
    }

    public Query createMultiSetJoinQuery(int queryId, int numberOfKeywords, int keywordSpaceMiddle, int keywordSpaceSpan,
                                     double centroidLatitude, double centroidLongitude, double latitudeSpan, double longitudeSpan, int[] topkWords) {

        // Create overlapping spatial regions to increase join probability
        double overlapFactor = 0.3; // 30% overlap with neighboring regions
        double adjustedLatSpan = latitudeSpan * (1 + overlapFactor);
        double adjustedLngSpan = longitudeSpan * (1 + overlapFactor);

        double x = (centroidLongitude - adjustedLngSpan / 2) + RANDOM.nextDouble() * adjustedLngSpan;
        double y = (centroidLatitude - adjustedLatSpan / 2) + RANDOM.nextDouble() * adjustedLatSpan;

        List<Integer> keywords = new ArrayList<>();
        List<Double> keywordWeights = new ArrayList<>();

        double weightTotal = 0.0;

        // Use more common keywords for better join results
        int commonKeywordRatio = Math.max(1, numberOfKeywords / 2); // At least half should be common

        for (int k = 0; k < numberOfKeywords; k++) {
            int keyword;

            if(k < topkWords.length) {
                // Prioritize top-k words as they're most common
                keywords.add(topkWords[k]);
            } else if (k < commonKeywordRatio) {
                // Use keywords from the center of the keyword space (more common)
                int centerOffset = keywordSpaceSpan / 4; // Bias toward center
                keyword = keywordSpaceMiddle + centerOffset + RANDOM.nextInt(Math.max(1, keywordSpaceSpan / 2));
                keywords.add(keyword);
            } else {
                // Use broader keyword range for diversity
                keyword = keywordSpaceMiddle + RANDOM.nextInt(Math.abs(keywordSpaceSpan));
                keywords.add(keyword);
            }

            // Weight common keywords higher
            double keywordWeight;
            if (k < topkWords.length || k < commonKeywordRatio) {
                keywordWeight = 0.7 + RANDOM.nextDouble() * 0.3; // Higher weights for common keywords
            } else {
                keywordWeight = 0.3 + RANDOM.nextDouble() * 0.4; // Lower weights for diverse keywords
            }

            weightTotal += keywordWeight;
            keywordWeights.add(keywordWeight);
        }

        // Normalize weights
        for (int k = 0; k < keywordWeights.size(); k++) {
            double weight = (keywordWeights.get(k) / weightTotal);
            keywordWeights.set(k, weight);
        }

        Query query = new Query(queryId, new Point(new double[]{x, y}), keywords, keywordWeights);
        return query;
    }

}

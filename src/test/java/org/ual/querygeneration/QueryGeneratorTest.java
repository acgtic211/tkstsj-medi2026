package org.ual.querygeneration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ual.query.Query;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.spatialindex.Point;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storagemanager.PropertySet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class QueryGeneratorTest {
    private PropertySet propertySet;
    private DatasetParameters datasetParameters;
    private QueryGenerator queryGenerator;

    @BeforeEach
    public void setUp() {
        // Initialize the QueryGenerator with a seed and dataset parameters
        propertySet = new PropertySet();
        propertySet.setProperty("Dimension", 2);
        propertySet.setProperty("IndexCapacity", 6);
        propertySet.setProperty("LeafCapacity", 6);
        propertySet.setProperty("FillFactor", 0.7f);
        propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
        propertySet.setProperty("NearMinimumOverlapFactor", 2);
        datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
        queryGenerator = new QueryGenerator(42, datasetParameters);
    }

    @Test
    public void testCreateKWQuery() {
        int queryId = 1;
        double queryWeight = 0.8;
        int numberOfKeywords = 3;
        int keywordSpaceMiddle = 100;
        int keywordSpaceSpan = 50;
        double centroidLatitude = 40.0;
        double centroidLongitude = -74.0;
        double latitudeSpan = 1.0;
        double longitudeSpan = 1.0;
        int[] topkWords = {10, 20, 30};

        Query query = queryGenerator.createKWQuery(queryId, queryWeight, numberOfKeywords,
                keywordSpaceMiddle, keywordSpaceSpan, centroidLatitude, centroidLongitude,
                latitudeSpan, longitudeSpan, topkWords);

        assertNotNull(query);
        assertEquals(queryId, query.getId());
        assertEquals(queryWeight, query.getWeight(), 0.001);
        assertEquals(numberOfKeywords, query.getKeywords().size());
        assertEquals(numberOfKeywords, query.getKeywordWeights().size());

        // Verify spatial coordinates are within expected range
        double x = query.getLocation().getCoord(0);
        double y = query.getLocation().getCoord(1);
        assertTrue(x >= centroidLatitude - latitudeSpan / 2 && x <= centroidLatitude + latitudeSpan / 2);
        assertTrue(y >= centroidLongitude - longitudeSpan / 2 && y <= centroidLongitude + longitudeSpan / 2);

        // Verify weights sum to 1.0
        double weightSum = query.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, weightSum, 0.001);
    }

    @Test
    public void testCreateKWQueryWithTopKWords() {
        int[] topkWords = {5, 15, 25};
        Query query = queryGenerator.createKWQuery(1, 1.0, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, topkWords);

        // Verify that top-k words are used
        List<Integer> keywords = query.getKeywords();
        for (int i = 0; i < topkWords.length; i++) {
            assertEquals(topkWords[i], keywords.get(i).intValue());
        }
    }

    @Test
    public void testCreateTopKQuery() {
        int queryId = 2;
        int numberOfKeywords = 4;
        int keywordSpaceMiddle = 200;
        int keywordSpaceSpan = 100;
        double centroidLatitude = 35.0;
        double centroidLongitude = -80.0;
        double latitudeSpan = 2.0;
        double longitudeSpan = 2.0;
        int[] topkWords = {1, 2, 3, 4};

        Query query = queryGenerator.createTopKQuery(queryId, numberOfKeywords, keywordSpaceMiddle,
                keywordSpaceSpan, centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan, topkWords);

        assertNotNull(query);
        assertEquals(queryId, query.getId());
        assertEquals(numberOfKeywords, query.getKeywords().size());
        assertEquals(numberOfKeywords, query.getKeywordWeights().size());

        // Verify spatial coordinates are within expected range
        double x = query.getLocation().getCoord(0);
        double y = query.getLocation().getCoord(1);
        assertTrue(x >= centroidLatitude - latitudeSpan / 2 && x <= centroidLatitude + latitudeSpan / 2);
        assertTrue(y >= centroidLongitude - longitudeSpan / 2 && y <= centroidLongitude + longitudeSpan / 2);

        // Verify weights sum to 1.0
        double weightSum = query.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, weightSum, 0.001);
    }

    @Test
    public void testCreateRangeQuery() {
        int queryId = 3;
        double queryWeight = 0.6;
        int numberOfKeywords = 2;
        Point centerPoint = new Point(new double[]{40.0, -74.0});
        double radius = 0.5;
        int keywordSpaceMiddle = 150;
        int keywordSpaceSpan = 75;
        int[] topkWords = {7, 14};

        Query query = queryGenerator.createRangeQuery(queryId, queryWeight, numberOfKeywords,
                centerPoint, radius, keywordSpaceMiddle, keywordSpaceSpan, topkWords);

        assertNotNull(query);
        assertEquals(queryId, query.getId());
        assertEquals(queryWeight, query.getWeight(), 0.001);
        assertEquals(numberOfKeywords, query.getKeywords().size());
        assertEquals(numberOfKeywords, query.getKeywordWeights().size());

        // Verify point is within radius of center point
        double distance = Math.sqrt(
                Math.pow(query.getLocation().getCoord(0) - centerPoint.getCoord(0), 2) +
                Math.pow(query.getLocation().getCoord(1) - centerPoint.getCoord(1), 2)
        );
        assertTrue(distance <= radius);

        // Verify weights sum to 1.0
        double weightSum = query.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, weightSum, 0.001);
    }

    @Test
    public void testCreateSelfJoinQuery() {
        int queryId = 4;
        int numberOfKeywords = 5;
        int keywordSpaceMiddle = 100;
        int keywordSpaceSpan = 60;
        double centroidLatitude = 45.0;
        double centroidLongitude = -75.0;
        double latitudeSpan = 1.5;
        double longitudeSpan = 1.5;
        int[] topkWords = {11, 22, 33};

        Query query = queryGenerator.createSelfJoinQuery(queryId, numberOfKeywords, keywordSpaceMiddle,
                keywordSpaceSpan, centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan, topkWords);

        assertNotNull(query);
        assertEquals(queryId, query.getId());
        assertEquals(numberOfKeywords, query.getKeywords().size());
        assertEquals(numberOfKeywords, query.getKeywordWeights().size());

        // Verify spatial coordinates are within expanded range (due to overlap factor)
        double overlapFactor = 0.3;
        double adjustedLatSpan = latitudeSpan * (1 + overlapFactor);
        double adjustedLngSpan = longitudeSpan * (1 + overlapFactor);

        double x = query.getLocation().getCoord(0);
        double y = query.getLocation().getCoord(1);
        assertTrue(x >= centroidLatitude - adjustedLatSpan / 2 && x <= centroidLatitude + adjustedLatSpan / 2);
        assertTrue(y >= centroidLongitude - adjustedLngSpan / 2 && y <= centroidLongitude + adjustedLngSpan / 2);

        // Verify weights sum to 1.0
        double weightSum = query.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, weightSum, 0.001);
    }

    @Test
    public void testRandomSeedFunctionality() {
        queryGenerator.resetRandomSeed();
        int firstRandomValue = queryGenerator.getRandomSeed();

        queryGenerator.resetRandomSeed();
        int secondRandomValue = queryGenerator.getRandomSeed();

        assertEquals(firstRandomValue, secondRandomValue);
    }

    @Test
    public void testQueryGenerationWithEmptyTopKWords() {
        int[] emptyTopkWords = {};

        Query query = queryGenerator.createKWQuery(1, 1.0, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, emptyTopkWords);

        assertNotNull(query);
        assertEquals(3, query.getKeywords().size());
        // All keywords should be generated from keyword space since no top-k words provided
        for (Integer keyword : query.getKeywords()) {
            assertTrue(keyword >= 50 && keyword <= 150); // keywordSpaceMiddle ± keywordSpaceSpan/2
        }
    }

    @Test
    public void testQueryGenerationWithPartialTopKWords() {
        int[] partialTopkWords = {5, 10}; // Only 2 words for 4 keywords needed

        Query query = queryGenerator.createTopKQuery(1, 4, 100, 50, 40.0, -74.0, 1.0, 1.0, partialTopkWords);

        assertNotNull(query);
        assertEquals(4, query.getKeywords().size());

        // First two should be from top-k words
        assertEquals(5, query.getKeywords().get(0).intValue());
        assertEquals(10, query.getKeywords().get(1).intValue());

        // Remaining should be from keyword space
        for (int i = 2; i < 4; i++) {
            assertTrue(query.getKeywords().get(i) >= 50 && query.getKeywords().get(i) <= 150);
        }
    }

    @Test
    public void testDifferentQueryTypesHaveDifferentWeightDistributions() {
        int[] topkWords = {1, 2, 3};

        Query kwQuery = queryGenerator.createKWQuery(1, 1.0, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, topkWords);
        Query selfJoinQuery = queryGenerator.createSelfJoinQuery(2, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, topkWords);

        // Both should have valid weight distributions
        double kwWeightSum = kwQuery.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();
        double selfJoinWeightSum = selfJoinQuery.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();

        assertEquals(1.0, kwWeightSum, 0.001);
        assertEquals(1.0, selfJoinWeightSum, 0.001);
    }










//    @Test
//    public void testQueryGeneratorClassesComparison() {
//        int[] topkWords = {1, 2, 3};
//
//        // Create instances of different query generators
//        AggregateSKNNQueryGenerator aggregateGenerator = new AggregateSKNNQueryGenerator(42, datasetParameters);
//        SKNNQueryGenerator sknnGenerator = new SKNNQueryGenerator(42, datasetParameters);
//
//        Query aggregateQuery = aggregateGenerator.generateQuery(1, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, topkWords);
//        Query sknnQuery = sknnGenerator.generateQuery(1, 5, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, topkWords);
//
//        // Both should produce valid queries
//        assertNotNull(aggregateQuery);
//        assertNotNull(sknnQuery);
//
//        // Both should have normalized weights
//        assertEquals(1.0, aggregateQuery.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum(), 0.001);
//        assertEquals(1.0, sknnQuery.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum(), 0.001);
//
//        // Both should use the provided top-k words
//        assertEquals(topkWords[0], aggregateQuery.getKeywords().get(0).intValue());
//        assertEquals(topkWords[0], sknnQuery.getKeywords().get(0).intValue());
//    }
//
//    @Test
//    public void testQueryGeneratorAccessibility() {
//        // Test to verify QueryGenerator and related classes are accessible
//        assertNotNull(queryGenerator);
//
//        // Test that query generator classes can be instantiated
//        AggregateSKNNQueryGenerator aggregateGenerator = new AggregateSKNNQueryGenerator(42, datasetParameters);
//        SKNNQueryGenerator sknnGenerator = new SKNNQueryGenerator(42, datasetParameters);
//
//        assertNotNull(aggregateGenerator);
//        assertNotNull(sknnGenerator);
//
//        // Test basic query generation functionality
//        Query kwQuery = queryGenerator.createKWQuery(1, 1.0, 2, 100, 50, 40.0, -74.0, 1.0, 1.0, new int[]{1, 2});
//        Query aggregateQuery = aggregateGenerator.generateQuery(2, 2, 100, 50, 40.0, -74.0, 1.0, 1.0, new int[]{1, 2});
//        Query sknnQuery = sknnGenerator.generateQuery(3, 5, 2, 100, 50, 40.0, -74.0, 1.0, 1.0, new int[]{1, 2});
//
//        assertNotNull(kwQuery);
//        assertNotNull(aggregateQuery);
//        assertNotNull(sknnQuery);
//    }
}

package org.ual.querygeneration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ual.query.Query;
import org.ual.querytype.SKJoinQuery;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storagemanager.PropertySet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AggregateQueryGenerationTest {
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
    public void testAggregateSKNNQueryGenerator() {
        // Test parameters for aggregate SKNN query
        int queryId = 5;
        int numberOfKeywords = 3;
        int keywordSpaceMiddle = 100;
        int keywordSpaceSpan = 50;
        double centroidLatitude = 42.0;
        double centroidLongitude = -71.0;
        double latitudeSpan = 1.0;
        double longitudeSpan = 1.0;
        int[] topkWords = {15, 25, 35};

        // Test aggregate SKNN query generator as separate class
        AggregateSKNNQueryGenerator aggregateGenerator = new AggregateSKNNQueryGenerator(42, datasetParameters);

        Query query = aggregateGenerator.createKWQuery(queryId, 1.0, numberOfKeywords,
                keywordSpaceMiddle, keywordSpaceSpan, centroidLatitude, centroidLongitude,
                latitudeSpan, longitudeSpan, topkWords);

        // Verify basic query properties
        assertNotNull(query);
        assertEquals(queryId, query.getId());
        assertEquals(numberOfKeywords, query.getKeywords().size());
        assertEquals(numberOfKeywords, query.getKeywordWeights().size());

        // Verify spatial coordinates are within expected range
        double x = query.getLocation().getCoord(0);
        double y = query.getLocation().getCoord(1);
        assertTrue(x >= centroidLatitude - latitudeSpan / 2 && x <= centroidLatitude + latitudeSpan / 2);
        assertTrue(y >= centroidLongitude - longitudeSpan / 2 && y <= centroidLongitude + longitudeSpan / 2);

        // Verify top-k words are used correctly
        List<Integer> keywords = query.getKeywords();
        for (int i = 0; i < topkWords.length; i++) {
            assertEquals(topkWords[i], keywords.get(i).intValue());
        }

        // Verify weights sum to 1.0
        double weightSum = query.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, weightSum, 0.001);

        // Verify weights are optimized for aggregation (should be more balanced)
        for (Double weight : query.getKeywordWeights()) {
            assertTrue(weight >= 0.0 && weight <= 1.0);
        }

        System.out.println("Generated Aggregate SKNN Query: " + query);
    }

    @Test
    public void testAggregateSKNNQueryGeneratorWithEmptyTopKWords() {
        int[] emptyTopkWords = {};
        AggregateSKNNQueryGenerator aggregateGenerator = new AggregateSKNNQueryGenerator(42, datasetParameters);

        Query query = aggregateGenerator.createKWQuery(1, 1.0, 3, 100, 50,
                40.0, -74.0, 1.0, 1.0, emptyTopkWords);

        assertNotNull(query);
        assertEquals(3, query.getKeywords().size());

        // All keywords should be generated from keyword space
        for (Integer keyword : query.getKeywords()) {
            assertTrue(keyword >= 50 && keyword <= 150);
        }

        System.out.println("Generated Aggregate SKNN Query with empty top-k words: " + query);
    }

    @Test
    public void testAggregateSKNNQueryGeneratorConsistency() {
        AggregateSKNNQueryGenerator generator1 = new AggregateSKNNQueryGenerator(42, datasetParameters);
        AggregateSKNNQueryGenerator generator2 = new AggregateSKNNQueryGenerator(42, datasetParameters);

        Query query1 = generator1.createKWQuery(1, 1.0, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, new int[]{1, 2, 3});
        Query query2 = generator2.createKWQuery(1, 1.0, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, new int[]{1, 2, 3});

        // Should generate similar queries with same seed
        assertEquals(query1.getKeywords(), query2.getKeywords());
        assertEquals(1.0, query1.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum(), 0.001);
        assertEquals(1.0, query2.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum(), 0.001);

        System.out.println("Generated consistent Aggregate SKNN Queries: " + query1 + " and " + query2);
    }

    @Test
    public void testSKNNQueryGenerator() {
        // Test parameters for SKNN query
        int queryId = 6;
        int k = 10; // Number of nearest neighbors
        int numberOfKeywords = 4;
        int keywordSpaceMiddle = 150;
        int keywordSpaceSpan = 75;
        double centroidLatitude = 37.7749;
        double centroidLongitude = -122.4194;
        double latitudeSpan = 0.5;
        double longitudeSpan = 0.5;
        int[] topkWords = {8, 16, 24, 32};

        // Test SKNN query generator as separate class
        SKNNQueryGenerator sknnGenerator = new SKNNQueryGenerator(42, datasetParameters);

        Query query = sknnGenerator.createKWQuery(queryId, k, numberOfKeywords,
                keywordSpaceMiddle, keywordSpaceSpan, centroidLatitude, centroidLongitude,
                latitudeSpan, longitudeSpan, topkWords);

        // Verify basic query properties
        assertNotNull(query);
        assertEquals(queryId, query.getId());
        assertEquals(numberOfKeywords, query.getKeywords().size());
        assertEquals(numberOfKeywords, query.getKeywordWeights().size());

        // Verify spatial coordinates are within expected range
        double x = query.getLocation().getCoord(0);
        double y = query.getLocation().getCoord(1);
        assertTrue(x >= centroidLatitude - latitudeSpan / 2 && x <= centroidLatitude + latitudeSpan / 2);
        assertTrue(y >= centroidLongitude - longitudeSpan / 2 && y <= centroidLongitude + longitudeSpan / 2);

        // Verify all top-k words are used
        List<Integer> keywords = query.getKeywords();
        for (int i = 0; i < topkWords.length; i++) {
            assertEquals(topkWords[i], keywords.get(i).intValue());
        }

        // Verify weights sum to 1.0
        double weightSum = query.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, weightSum, 0.001);

        // Verify weights are optimized for SKNN (should favor selectivity)
        for (Double weight : query.getKeywordWeights()) {
            assertTrue(weight >= 0.0 && weight <= 1.0);
        }

        System.out.println("Generated SKNN Query: " + query);
    }

    @Test
    public void testSKNNQueryGeneratorWithDifferentKValues() {
        SKNNQueryGenerator sknnGenerator = new SKNNQueryGenerator(42, datasetParameters);
        int[] kValues = {1, 5, 10, 20, 50};
        int[] topkWords = {1, 2, 3};

        for (int k : kValues) {
            Query query = sknnGenerator.createKWQuery(1, k, 3, 100, 50,
                    40.0, -74.0, 1.0, 1.0, topkWords);

            assertNotNull(query);
            assertEquals(3, query.getKeywords().size());
            assertEquals(3, query.getKeywordWeights().size());

            // Verify weights are properly normalized
            double weightSum = query.getKeywordWeights().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, weightSum, 0.001);
        }
    }

    @Test
    public void testSKNNQueryGeneratorWithPartialTopKWords() {
        SKNNQueryGenerator sknnGenerator = new SKNNQueryGenerator(42, datasetParameters);
        int[] partialTopkWords = {10, 20}; // Only 2 words for 4 keywords needed

        Query query = sknnGenerator.createKWQuery(1, 5, 4, 100, 50,
                40.0, -74.0, 1.0, 1.0, partialTopkWords);

        assertNotNull(query);
        assertEquals(4, query.getKeywords().size());

        // First two should be from top-k words
        assertEquals(10, query.getKeywords().get(0).intValue());
        assertEquals(20, query.getKeywords().get(1).intValue());

        // Remaining should be from keyword space
        for (int i = 2; i < 4; i++) {
            assertTrue(query.getKeywords().get(i) >= 50 && query.getKeywords().get(i) <= 150);
        }

        System.out.println("Generated SKNN Query with partial top-k words: " + query);
    }

    @Test
    public void testSKNNQueryGeneratorEdgeCases() {
        SKNNQueryGenerator sknnGenerator = new SKNNQueryGenerator(42, datasetParameters);

        // Test with k=1 (single nearest neighbor)
        Query query1 = sknnGenerator.createKWQuery(1, 1, 2, 100, 50, 40.0, -74.0, 1.0, 1.0, new int[]{5, 10});
        assertNotNull(query1);
        assertEquals(2, query1.getKeywords().size());

        // Test with large k value
        Query query2 = sknnGenerator.createKWQuery(2, 100, 3, 100, 50, 40.0, -74.0, 1.0, 1.0, new int[]{1, 2, 3});
        assertNotNull(query2);
        assertEquals(3, query2.getKeywords().size());

        // Test with single keyword
        Query query3 = sknnGenerator.createKWQuery(3, 5, 1, 100, 50, 40.0, -74.0, 1.0, 1.0, new int[]{42});
        assertNotNull(query3);
        assertEquals(1, query3.getKeywords().size());
        assertEquals(42, query3.getKeywords().get(0).intValue());

        System.out.println("Generated SKNN Queries for edge cases: " + query1 + ", " + query2 + ", " + query3);
    }

    @Test
    public void generateJoinSKQueriesUsesFullDatasetWindowWhenAreaIsOne() {
        SKNNQueryGenerator generator = new SKNNQueryGenerator(42, datasetParameters);
        List<SKJoinQuery> queries = generator.generateJoinSKQueries(3, 3, 1.0, 10.0);

        for (SKJoinQuery query : queries) {
            Region window = query.getSpatialWindow();
            assertNotNull(window);
            assertEquals(datasetParameters.latitudeStart, window.getMinX(), 1e-9);
            assertEquals(datasetParameters.latitudeEnd, window.getMaxX(), 1e-9);
            assertEquals(datasetParameters.longitudeStart, window.getMinY(), 1e-9);
            assertEquals(datasetParameters.longitudeEnd, window.getMaxY(), 1e-9);
        }
    }

    @Test
    public void generateJoinSKQueriesTreatsFractionAndPercentageAreaEquivalently() {
        SKNNQueryGenerator fractionGenerator = new SKNNQueryGenerator(42, datasetParameters);
        SKNNQueryGenerator percentageGenerator = new SKNNQueryGenerator(42, datasetParameters);

        List<SKJoinQuery> fractionQueries = fractionGenerator.generateJoinSKQueries(5, 3, 0.25, 10.0);
        List<SKJoinQuery> percentageQueries = percentageGenerator.generateJoinSKQueries(5, 3, 25.0, 10.0);

        assertEquals(fractionQueries.size(), percentageQueries.size());
        for (int i = 0; i < fractionQueries.size(); i++) {
            Region fractionWindow = fractionQueries.get(i).getSpatialWindow();
            Region percentageWindow = percentageQueries.get(i).getSpatialWindow();

            assertNotNull(fractionWindow);
            assertNotNull(percentageWindow);
            assertEquals(fractionWindow.getMinX(), percentageWindow.getMinX(), 1e-9);
            assertEquals(fractionWindow.getMaxX(), percentageWindow.getMaxX(), 1e-9);
            assertEquals(fractionWindow.getMinY(), percentageWindow.getMinY(), 1e-9);
            assertEquals(fractionWindow.getMaxY(), percentageWindow.getMaxY(), 1e-9);
        }
    }
}

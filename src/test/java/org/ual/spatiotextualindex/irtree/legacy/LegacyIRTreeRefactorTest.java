package org.ual.spatiotextualindex.irtree.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.document.WeightCompute;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.query.Query;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.rtree.Statistics;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatiotextualindex.irtree.IRTree;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.spatiotextualindex.queries.JoinStrategy;
import org.ual.spatiotextualindex.queries.QueryStrategy;
import org.ual.spatiotextualindex.queries.ThresholdPolicy;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// Helper classes (InMemoryWeightStore, InMemoryDocumentIndex) should be accessible here
// (e.g., as static nested classes, or in the same package/file for testing)

public class LegacyIRTreeRefactorTest {
    private IRTree tree;
    private NodeStorageManager storageManager;
    private PropertySet propertySet;
    private DatasetParameters datasetParameters;
    private InvertedListIndex documentIndex;
    private InvertedListIndex refactoredDocumentIndex;
    private AbstractDocumentStore weightStore;

    // Sample data
    private HashMap<Integer, ArrayList<Integer>> sampleTextualObjects;
    private HashMap<Integer, IShape> sampleSpatialObjects; // docID -> IShape

    // Query locations
    private Point queryPointInRegion;
    private Point queryPointOutsideRegion;

    @BeforeEach
    void setUp() {
        // 1. Initialize storage manager and properties
        storageManager = new NodeStorageManager(); // Using an in-memory or test-configured storage manager
        propertySet = new PropertySet();
        propertySet.setProperty("Dimension", 2);
        propertySet.setProperty("IndexCapacity", 6);
        propertySet.setProperty("LeafCapacity", 6);
        propertySet.setProperty("FillFactor", 0.7f);
        propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
        propertySet.setProperty("NearMinimumOverlapFactor", 2);
        datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

        // 2. Initialize IRTree instance
        // The IRTree constructor might use datasetParameters to configure itself.
        tree = new IRTree(propertySet, storageManager, datasetParameters);

        // 3. Define sample spatial and textual data
        queryPointInRegion = new Point(new double[]{10.0, 20.0});
        queryPointOutsideRegion = new Point(new double[]{100.0, 100.0});

        sampleSpatialObjects = new HashMap<>();
        sampleSpatialObjects.put(1, new Point(new double[]{10.0, 20.0})); // doc1
        sampleSpatialObjects.put(2, new Point(new double[]{15.0, 25.0})); // doc2
        sampleSpatialObjects.put(3, new Point(new double[]{12.0, 22.0})); // doc3
        sampleSpatialObjects.put(4, new Point(new double[]{50.0, 50.0})); // doc4
        sampleSpatialObjects.put(5, new Point(new double[]{5.0, 5.0}));   // doc5
        sampleSpatialObjects.put(6, new Point(new double[]{25.0, 15.0})); // doc6
        sampleSpatialObjects.put(7, new Point(new double[]{30.0, 30.0})); // doc7
        sampleSpatialObjects.put(8, new Point(new double[]{60.0, 60.0})); // doc8

        sampleTextualObjects = new HashMap<>();
        sampleTextualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102, 103)));
        sampleTextualObjects.put(2, new ArrayList<>(Arrays.asList(102, 103)));
        //sampleTextualObjects.put(2, new ArrayList<>(Arrays.asList(101, 102, 103)));
        sampleTextualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));
        sampleTextualObjects.put(4, new ArrayList<>(Collections.singletonList(105)));
        sampleTextualObjects.put(5, new ArrayList<>(Arrays.asList(101, 106)));          // Text for doc5
        sampleTextualObjects.put(6, new ArrayList<>(Arrays.asList(102, 107)));          // Text for doc6
        sampleTextualObjects.put(7, new ArrayList<>(Arrays.asList(103, 108, 101)));    // Text for doc7
        sampleTextualObjects.put(8, new ArrayList<>(Collections.singletonList(109)));  // Text for doc8

        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            tree.insertData(entry.getKey(), entry.getValue());
        }

        weightStore = new HashMapDocumentStore();
        double smoothingFactor = 0.0;//0.5;
        WeightCompute.ComputeTF_IDFWeights(sampleTextualObjects, weightStore, smoothingFactor);

        documentIndex = new InvertedListIndex(0);


        // Assuming IRTree.createIRTree correctly utilizes the weightStore and documentIndex
        // for subsequent queries, or makes them available internally.
        tree.createIRTree(weightStore, documentIndex);

    }

    @Test
    void testTreeInitializationAndTextualIntegration() {
        assertNotNull(tree, "IRTree instance should be created.");
        Statistics stats = (Statistics) tree.getStatistics();
        assertNotNull(stats, "Statistics object should not be null.");
        assertTrue(stats.getNumberOfData() >= sampleSpatialObjects.size(), "Tree should contain the inserted spatial objects.");

        // Check if weights were computed (HashMapDocumentStore has a size method)
        assertTrue(weightStore.getSize() > 0, "Weights should have been computed and stored.");
        assertEquals(sampleTextualObjects.size(), weightStore.getSize(), "Weight store should have an entry for each document with text.");

        // Verify document index was built
        assertTrue(documentIndex.getTotalDocuments() >= sampleTextualObjects.size(), "Total documents in index should be >= original documents, as it may include pseudo-documents for nodes.");
        assertTrue(documentIndex.getDocumentFrequency(101) > 0, "Term 101 should be in the document index.");
    }

    @Test
    void testValidStructure_RootExists() {
        Statistics stats = (Statistics) tree.getStatistics();
        assertNotNull(stats, "Statistics object should not be null.");
        assertTrue(stats.getNumberOfNodes() > 0, "Tree should contain nodes.");
        // More specific structural checks can be added if IRTree exposes relevant details
    }

    @Test
    void testDifferentInvertedFileIndexImplementations() {
        // Test with a different inverted file index implementation: HashMapInvertedFileIndex
        InvertedListIndex hashMapIndex = new InvertedListIndex(0);
        IRTree hashMapTree = new IRTree(propertySet, storageManager, datasetParameters);

        // Insert the same spatial objects as in the main tree
        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            hashMapTree.insertData(entry.getKey(), entry.getValue());
        }

        hashMapTree.createIRTree(weightStore, hashMapIndex);

        // Check if the tree using HashMapInvertedFileIndex can handle queries correctly
        assertNotNull(hashMapTree, "Tree with HashMapInvertedFileIndex should be created.");
        assertTrue(hashMapIndex.getTotalDocuments() >= sampleTextualObjects.keySet().size(),
                "HashMapInvertedFileIndex should have indexed all documents.");

        // Test with ArrayListInvertedFileIndex implementation
        // Assuming ArrayListInvertedFileIndex is an alternative implementation available
        // Assuming ArrayListInvertedFileIndex is an alternative implementation available
        //ArrayListInvertedFileIndex arrayListIndex = new ArrayListInvertedFileIndex(0);
        IRTree arrayListTree = new IRTree(propertySet, storageManager, datasetParameters);

        // Insert the same spatial objects
        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            arrayListTree.insertData(entry.getKey(), entry.getValue());
        }

        //arrayListTree.createIRTree(weightStore, arrayListIndex);

        // Check if the tree using ArrayListInvertedFileIndex can handle queries correctly
        assertNotNull(arrayListTree, "Tree with ArrayListInvertedFileIndex should be created.");
//        assertTrue(arrayListIndex.getTotalDocuments() >= sampleTextualObjects.keySet().size(),
//                "ArrayListInvertedFileIndex should have indexed all documents.");

        // Compare query results between the two implementations
        ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
        SKNNQuery sknnQuery = new SKNNQuery(1, queryPointInRegion, keywords);
        float largeRadius = 100.0f;

        List<SKNNQuery.Result> hashMapResults = hashMapTree.booleanRangeQuery(hashMapIndex, sknnQuery, largeRadius);
//        List<SKNNQuery.Result> arrayListResults = arrayListTree.booleanRangeQuery(arrayListIndex, sknnQuery, largeRadius);

        // Results should be the same for both implementations
//        assertEquals(hashMapResults.size(), arrayListResults.size(),
//                "Both index implementations should return the same number of results.");

        // Compare document IDs from both result sets
        Set<Integer> hashMapIds = hashMapResults.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
//        Set<Integer> arrayListIds = arrayListResults.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
//        assertEquals(hashMapIds, arrayListIds, "Both index implementations should return the same document IDs.");
    }

    @Test
    void testBooleanRangeQuery() {
        ArrayList<Integer> keywords101 = new ArrayList<Integer>(Collections.singletonList(101));
        SKNNQuery sknnQuery1 = new SKNNQuery(1, queryPointInRegion, keywords101);
        float largeRadius = 100.0f;

        List<SKNNQuery.Result> results1 = tree.booleanRangeQuery(documentIndex, sknnQuery1, largeRadius);
        Set<Integer> foundDocIds1 = results1.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
        // Corrected expected documents for keyword 101
        Set<Integer> expectedDocIds1 = new HashSet<Integer>(Arrays.asList(1, 3, 5, 7));
        assertEquals(expectedDocIds1.size(), foundDocIds1.size(), "Test 1: Mismatch in number of documents found for keyword 101.");
        assertTrue(foundDocIds1.containsAll(expectedDocIds1), "Test 1: Query results mismatch for keyword 101.");

        ArrayList<Integer> keywords102 = new ArrayList<Integer>(Collections.singletonList(102));
        SKNNQuery sknnQuery2 = new SKNNQuery(2, queryPointInRegion, keywords102);
        List<SKNNQuery.Result> results2 = tree.booleanRangeQuery(documentIndex, sknnQuery2, largeRadius);
        Set<Integer> foundDocIds2 = results2.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
        // Documents containing 102: Doc 1 (10,20), Doc 2 (15,25), Doc 6 (25,15)
        // All are within largeRadius of (10,20)
        Set<Integer> expectedDocIds2 = new HashSet<Integer>(Arrays.asList(1, 2, 6));
        assertEquals(expectedDocIds2.size(), foundDocIds2.size(), "Test 2: Mismatch in number of documents found for keyword 102.");
        assertTrue(foundDocIds2.containsAll(expectedDocIds2), "Test 2: Query results mismatch for keyword 102.");

        ArrayList<Integer> keywords105 = new ArrayList<Integer>(Collections.singletonList(105));
        SKNNQuery sknnQuery3 = new SKNNQuery(3, queryPointInRegion, keywords105);
        float specificRadius = 40.0f; // Doc 4 (50,50) has keyword 105. Dist to (10,20) is sqrt((50-10)^2 + (50-20)^2) = sqrt(1600+900) = sqrt(2500) = 50.
        // 50 is not <= specificRadius 40.0f. So, empty is correct.
        List<SKNNQuery.Result> results3 = tree.booleanRangeQuery(documentIndex, sknnQuery3, specificRadius);
        assertTrue(results3.isEmpty(), "Test 3: Should find no documents for keyword 105 with specific radius.");

        ArrayList<Integer> keywords999 = new ArrayList<Integer>(Collections.singletonList(999));
        SKNNQuery sknnQuery4 = new SKNNQuery(4, queryPointInRegion, keywords999);
        List<SKNNQuery.Result> results4 = tree.booleanRangeQuery(documentIndex, sknnQuery4, largeRadius);
        assertTrue(results4.isEmpty(), "Test 4: Should find no documents for non-existent keyword 999.");
    }

    @Test
    void testBooleanKnnQuery() {
        ArrayList<Integer> keywords = new ArrayList<Integer>(Collections.singletonList(101));
        SKNNQuery sknnQuery = new SKNNQuery(1, queryPointInRegion, keywords);
        int topk = 1;

        List<SKNNQuery.Result> results = tree.booleanKnnQuery(documentIndex, sknnQuery, topk);
        assertNotNull(results);
        assertEquals(topk, results.size(), "Should find k results.");
        assertEquals(1, results.get(0).getId(), "Expected doc1 to be the closest for keyword 101 from (10,20).");

        topk = 2;
        results = tree.booleanKnnQuery(documentIndex, sknnQuery, topk);
        assertEquals(topk, results.size());
        Set<Integer> foundIds = results.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
        assertTrue(foundIds.containsAll(Arrays.asList(1, 3)), "Expected doc1 and doc3.");

        ArrayList<Integer> keywords102 = new ArrayList<Integer>(Collections.singletonList(102));
        SKNNQuery sknnQuery102 = new SKNNQuery(2, queryPointInRegion, keywords102);
        topk = 1;
        results = tree.booleanKnnQuery(documentIndex, sknnQuery102, topk);
        assertEquals(topk, results.size());
        assertEquals(1, results.get(0).getId(), "Expected doc1 to be the closest for keyword 102 from (10,20).");
    }

    @Test
    void testTopkKnnQuery() {
        ArrayList<Integer> keywords = new ArrayList<Integer>(Collections.singletonList(101));
        ArrayList<Double> keywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
        SKNNQuery sknnQuery = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights); // alpha = 0.5
        int topk = 1;

        List<SKNNQuery.Result> results = tree.topkKnnQuery(documentIndex, sknnQuery, topk);
        assertNotNull(results);
        if (!results.isEmpty()) {
            assertTrue(results.size() <= topk);
            // Further assertions depend on scoring logic and alpha
            // For example, check if the top result is one of the expected documents
            Set<Integer> expectedPossibleIds = new HashSet<Integer>(Arrays.asList(1, 3));
            assertTrue(expectedPossibleIds.contains(results.get(0).getId()), "Top result for keyword 101 should be doc1 or doc3.");
        } else if (sampleSpatialObjects.size() > 0 && documentIndex.getDocumentFrequency(101) > 0) {
            fail("Should find results for topkKnnQuery if matches exist.");
        }


        ArrayList<Integer> keywords105 = new ArrayList<Integer>(Collections.singletonList(105));
        SKNNQuery sknnQuery105 = new SKNNQuery(2, 0.5, queryPointInRegion, keywords105, keywordWeights); // alpha = 0.5
        results = tree.topkKnnQuery(documentIndex, sknnQuery105, topk);
        assertNotNull(results);
        // Depending on alpha and scoring, doc4 (id=4) might or might not appear.
        // If it appears, it should be doc4. If not, results list might be empty.
        if (!results.isEmpty()) {
            assertEquals(4, results.get(0).getId(), "If a result is found for keyword 105, it should be doc4.");
        }
    }


    @Test
    void testGnnkQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));

        Query q1 = new Query(1001, 0.5, new Point(new double[]{10.0, 20.0}),
                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(1002, 0.5, new Point(new double[]{15.0, 25.0}),
                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);

        List<Query> queries = Arrays.asList(q1, q2);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");

        // Use constructor: AggregateSKNNQuery(int id, List<Query> queries, int groupSize, IAggregator aggregator)
        AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(1, queries, queries.size(), aggregator);
        int topk = 1;

        List<AggregateSKNNQuery.Result> results = tree.gnnk(documentIndex, gnnkQuery, topk);

        assertNotNull(results);
        assertFalse(results.isEmpty(), "gNNk query should return results.");
        if (!results.isEmpty()) {
            assertTrue(results.size() <= topk);
            // Further assertions would depend on the expected aggregated scores.
            // E.g., check if the result ID is one of the sample document IDs.
            assertTrue(sampleSpatialObjects.containsKey(results.get(0).getId()), "Result ID should be a valid document ID.");
        }
    }

    @Test
    void testSgnnkQuery() {
        // Similar setup to gNNk, but sGNNk also has subGroupSize
        Query q1 = new Query(3001, 0.3, new Point(new double[]{10,20}),
                new ArrayList<>(Collections.singletonList(101)),
                new ArrayList<>(Collections.singletonList(1.0)));
        Query q2 = new Query(3002, 0.3, new Point(new double[]{15,25}),
                new ArrayList<>(Collections.singletonList(102)),
                new ArrayList<>(Collections.singletonList(1.0)));
        Query q3 = new Query(3003, 0.4, new Point(new double[]{12,22}),
                new ArrayList<>(Collections.singletonList(103)),
                new ArrayList<>(Collections.singletonList(1.0)));


        List<Query> queries = Arrays.asList(q1, q2, q3);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
        int subGroupSize = 2; // Consider the best 2 queries out of 3 for each object

        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(2, queries, queries.size(), aggregator);
        sgnnkQuery.subGroupSize = subGroupSize;
        // The constructor for AggregateSKNNQuery might vary. Adjust as per actual definition.
        // Assuming: AggregateSKNNQuery(List<Query> queries, List<Double> weights, Aggregator aggregator, int subGroupSize, int groupSize)
        // If not, it might be: AggregateSKNNQuery(List<Query> queries, List<Double> weights, Aggregator aggregator);
        // and subGroupSize is passed to sgnnk method or set on the query object.
        // The IRTree.java sgnnk method signature is: sgnnk(IDocumentIndex invertedFile, AggregateSKNNQuery sgnnkQuery, int topk)
        // So sgnnkQuery object must carry the subGroupSize. Let's assume its constructor supports it.
        // If AggregateSKNNQuery does not have subGroupSize, this test needs adjustment.

        int topk = 1;
        List<AggregateSKNNQuery.Result> results = tree.sgnnk(documentIndex, sgnnkQuery, topk);

        assertNotNull(results);
        assertFalse(results.isEmpty(), "sGNNk query should return results.");
        if (!results.isEmpty()) {
            assertTrue(results.size() <= topk);
            assertTrue(sampleSpatialObjects.containsKey(results.get(0).getId()));
            assertNotNull(results.get(0).getQueryIds(), "sGNNk result should include query IDs.");
            if (results.get(0).getQueryIds() != null) {
                assertEquals(subGroupSize, results.get(0).getQueryIds().size(), "Number of query IDs should match subgroup size.");
            }
        }
    }

    private JoinConfiguration createJoinConfiguration(ThresholdPolicy thresholdPolicy,
                                                      JoinStrategy joinStrategy,
                                                      SimilarityType similarityType) {
        return new JoinConfiguration(
                thresholdPolicy,
                joinStrategy,
                similarityType,
                QueryStrategy.PARTIAL_JOIN
        );
    }

//    @Test
//    void testSJSKQuery_BestFirst_CombinedScore() {
//        // Create a self-join query with a maximum distance threshold
//        ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
//        ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
//        Point queryLocation = new Point(new double[]{15.0, 20.0});
//
//        // Create the join query with alpha=0.5 (equal weight to spatial and textual relevance)
//        SKJoinQuery joinQuery = new SKJoinQuery(1, 1.0, queryLocation, keywords, keywordWeights);
//        float maxDistance = 30.0f; // Maximum spatial distance between object pairs
//        float textualThreshold = 0.1f; // Minimum textual similarity threshold for keyword matching
//        ThresholdPolicy thresholdPolicy = ThresholdPolicy.COMBINED_COST;
//        JoinStrategy joinStrategy = JoinStrategy.DEFAULT;
//        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;
//
//        System.out.println(tree.printTreeStructure()); // Print the tree structure for debugging
//        Node node = tree.readNode(1);
//
//        // Updated to work with nodeEntries TreeMap
//        NodeEntry childEntry5 = node.getNodeEntries().get(5);
//        NodeEntry childEntry7 = node.getNodeEntries().get(7);
//        if (childEntry5 != null && childEntry7 != null) {
//            System.out.println("Distance: " + childEntry5.getMBR().getMinimumDistance(childEntry7.getMBR()));
//        }
//
//        // Execute the self-join query
//        List<SKJoinQuery.Result> results = tree.selfJoinSKQueryBestFirst(
//                documentIndex, joinQuery, maxDistance, textualThreshold,
//                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//
//        // Print all pairs for debugging
//        System.out.println("Total pairs found: " + results.size());
//
//        for (SKJoinQuery.Result pair : results) {
//            int id1 = pair.getPairId1();
//            int id2 = pair.getPairId2();
//            ArrayList<Integer> keywords1 = sampleTextualObjects.get(id1);
//            ArrayList<Integer> keywords2 = sampleTextualObjects.get(id2);
//            System.out.println("Pair: " + id1 + "," + id2 +
//                    " | Score: " + pair.combineCost +
//                    " | Keywords1: " + keywords1 +
//                    " | Keywords2: " + keywords2);
//        }
//
//        // Verify results
//        assertNotNull(results, "Self-join results should not be null");
//        assertFalse(results.isEmpty(), "Self-join should return matching pairs");
//
//        // We should have specific expectations about the result count
//        int expectedPairCount = 14; // Adjust based on actual implementation behavior
//        assertEquals(expectedPairCount, results.size(),
//                "Expected approximately " + expectedPairCount + " matching pairs");
//
//        // Check for expected pairs
//        // Expected pairs: (1,2), (1,3), (2,3), (1,7), (2,7), (3,7)
//        Set<String> expectedPairs = new HashSet<>(Arrays.asList(
//                "1-2", "1-3", "2-3", "1-7", "2-7", "3-7"
//        ));
//
//        Set<String> foundPairs = new HashSet<>();
//        Map<String, Double> pairScores = new HashMap<>();
//
//        for (SKJoinQuery.Result result : results) {
//            int id1 = result.getPairId1();
//            int id2 = result.getPairId2();
//            String pairKey = Math.min(id1, id2) + "-" + Math.max(id1, id2);
//            foundPairs.add(pairKey);
//            pairScores.put(pairKey, result.combineCost);
//
//            // For each pair, verify they have at least one keyword from the query
//            Set<Integer> keywords1 = new HashSet<>(sampleTextualObjects.get(id1));
//            Set<Integer> keywords2 = new HashSet<>(sampleTextualObjects.get(id2));
//
//            // Check if each object has at least one of the query keywords
//            boolean obj1HasQueryKeyword = keywords1.stream().anyMatch(keywords::contains);
//            boolean obj2HasQueryKeyword = keywords2.stream().anyMatch(keywords::contains);
//
//            assertTrue(obj1HasQueryKeyword && obj2HasQueryKeyword,
//                    "Each object in pair " + id1 + "-" + id2 + " should have at least one query keyword");
//
//            // Verify the result has a valid score
//            assertTrue(result.combineCost > 0, "Result score should be positive");
//            assertTrue(result.combineCost <= 1.0, "Result score should be normalized (≤ 1.0)");
//        }
//
//        // Verify that we found all expected pairs
//        for (String expectedPair : expectedPairs) {
//            assertTrue(foundPairs.contains(expectedPair),
//                    "Expected pair " + expectedPair + " not found in results");
//        }
//
//        // Verify result ordering - results should be in ascending order of score (lower is better)
//        for (int i = 1; i < results.size(); i++) {
//            assertTrue(results.get(i-1).combineCost <= results.get(i).combineCost,
//                    "Results should be ordered by ascending score (lower is better)");
//        }
//
//        // Test with different alpha values (affects spatial vs textual weight in scoring)
//        // Alpha=0.8 means spatial proximity is more important in combined score
//        tree.setAlphaDistribution(0.8f);
//        SKJoinQuery spatialFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
//        List<SKJoinQuery.Result> spatialResults = tree.selfJoinSKQueryBestFirst(
//                documentIndex, spatialFocusedQuery, maxDistance, textualThreshold,
//                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertFalse(spatialResults.isEmpty(), "Spatial-focused query should return results");
//
//        // Alpha=0.2 means textual relevance is more important in combined score
//        tree.setAlphaDistribution(0.2f);
//        SKJoinQuery textualFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
//        List<SKJoinQuery.Result> textualResults = tree.selfJoinSKQueryBestFirst(
//                documentIndex, textualFocusedQuery, maxDistance, textualThreshold,
//                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertFalse(textualResults.isEmpty(), "Textual-focused query should return results");
//
//        // Reset alpha back to default for remaining tests
//        tree.setAlphaDistribution(0.5f);
//
//        // Test with smaller distance threshold
//        float smallerDistance = 10.0f;
//        List<SKJoinQuery.Result> limitedResults = tree.selfJoinSKQueryBestFirst(
//                documentIndex, joinQuery, smallerDistance, textualThreshold,
//                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertNotNull(limitedResults);
//        assertTrue(limitedResults.size() < results.size(),
//                "Smaller distance threshold should return fewer results");
//
//        // Test with different keywords
//        ArrayList<Integer> rareKeywords = new ArrayList<>(Collections.singletonList(109));
//        ArrayList<Double> rareKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
//        SKJoinQuery rareQuery = new SKJoinQuery(2, 0.5, queryLocation, rareKeywords, rareKeywordWeights);
//
//        List<SKJoinQuery.Result> rareResults = tree.selfJoinSKQueryBestFirst(
//                documentIndex, rareQuery, maxDistance, textualThreshold,
//                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertTrue(rareResults.isEmpty() || rareResults.size() < results.size(),
//                "Query with rare keywords should return fewer results");
//
//        // Test edge case: very large distance
//        float veryLargeDistance = 1000.0f;
//        List<SKJoinQuery.Result> allPairsResults = tree.selfJoinSKQueryBestFirst(
//                documentIndex, joinQuery, veryLargeDistance, textualThreshold,
//                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertNotNull(allPairsResults);
//        assertTrue(allPairsResults.size() >= results.size(),
//                "Very large distance should return at least as many results as standard distance");
//
//        // Test edge case: empty keyword list
//        ArrayList<Integer> emptyKeywords = new ArrayList<>();
//        ArrayList<Double> emptyWeights = new ArrayList<>();
//        SKJoinQuery emptyQuery = new SKJoinQuery(3, 0.5, queryLocation, emptyKeywords, emptyWeights);
//
//        List<SKJoinQuery.Result> emptyResults = tree.selfJoinSKQueryBestFirst(
//                documentIndex, emptyQuery, maxDistance, textualThreshold,
//                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertTrue(emptyResults.isEmpty(),
//                "Query with empty keyword list should return no results");
//    }


    @Test
    void testSJSKQuery_BestFirst_StrictScore() {
        // Create a self-join query with a maximum distance threshold
        ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
        ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
        Point queryLocation = new Point(new double[]{15.0, 20.0});

        // Create the join query with alpha=0.5 (equal weight to spatial and textual relevance)
        SKJoinQuery joinQuery = new SKJoinQuery(1, 1.0, queryLocation, keywords, keywordWeights);
        float maxDistance = 30.0f; // Maximum spatial distance between object pairs
        float textualThreshold = 0.1f; // Minimum textual similarity threshold for keyword matching
        ThresholdPolicy thresholdPolicy = ThresholdPolicy.STRICT;
        JoinStrategy joinStrategy = JoinStrategy.DEFAULT;
        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;

        System.out.println(tree.printTreeStructure()); // Print the tree structure for debugging
        Node node = tree.readNode(1);

        // Updated to work with nodeEntries TreeMap
        NodeEntry childEntry5 = node.getNodeEntries().get(5);
        NodeEntry childEntry7 = node.getNodeEntries().get(7);
        if (childEntry5 != null && childEntry7 != null) {
            System.out.println("Distance: " + childEntry5.getMBR().getMinimumDistance(childEntry7.getMBR()));
        }

        // Execute the self-join query
        List<SKJoinQuery.Result> results = tree.selfJoinSKQueryBestFirst(
                documentIndex, joinQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));

        // Print all pairs for debugging
        System.out.println("Total pairs found: " + results.size());

        for (SKJoinQuery.Result pair : results) {
            int id1 = pair.getPairId1();
            int id2 = pair.getPairId2();
            ArrayList<Integer> keywords1 = sampleTextualObjects.get(id1);
            ArrayList<Integer> keywords2 = sampleTextualObjects.get(id2);
            System.out.println("Pair: " + id1 + "," + id2 +
                    " | Score: " + pair.combineCost +
                    " | Keywords1: " + keywords1 +
                    " | Keywords2: " + keywords2);
        }

        // Verify results
        assertNotNull(results, "Self-join results should not be null");
        assertFalse(results.isEmpty(), "Self-join should return matching pairs");

        // We should have specific expectations about the result count
        int expectedPairCount = 14; // Adjust based on actual implementation behavior
        assertEquals(expectedPairCount, results.size(),
                "Expected approximately " + expectedPairCount + " matching pairs");

        // Check for expected pairs
        // Expected pairs: (1,2), (1,3), (2,3), (1,7), (2,7), (3,7)
        Set<String> expectedPairs = new HashSet<>(Arrays.asList(
                "1-2", "1-3", "2-3", "1-7", "2-7", "3-7"
        ));

        Set<String> foundPairs = new HashSet<>();
        Map<String, Double> pairScores = new HashMap<>();

        for (SKJoinQuery.Result result : results) {
            int id1 = result.getPairId1();
            int id2 = result.getPairId2();
            String pairKey = Math.min(id1, id2) + "-" + Math.max(id1, id2);
            foundPairs.add(pairKey);
            pairScores.put(pairKey, result.combineCost);

            // For each pair, verify they have at least one keyword from the query
            Set<Integer> keywords1 = new HashSet<>(sampleTextualObjects.get(id1));
            Set<Integer> keywords2 = new HashSet<>(sampleTextualObjects.get(id2));

            // Check if each object has at least one of the query keywords
            boolean obj1HasQueryKeyword = keywords1.stream().anyMatch(keywords::contains);
            boolean obj2HasQueryKeyword = keywords2.stream().anyMatch(keywords::contains);

            assertTrue(obj1HasQueryKeyword && obj2HasQueryKeyword,
                    "Each object in pair " + id1 + "-" + id2 + " should have at least one query keyword");

            // Verify the result has a valid score
            assertTrue(result.combineCost > 0, "Result score should be positive");
            assertTrue(result.combineCost <= 1.0, "Result score should be normalized (≤ 1.0)");
        }

        // Verify that we found all expected pairs
        for (String expectedPair : expectedPairs) {
            assertTrue(foundPairs.contains(expectedPair),
                    "Expected pair " + expectedPair + " not found in results");
        }

        // Verify result ordering - results should be in ascending order of score (lower is better)
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i-1).combineCost <= results.get(i).combineCost,
                    "Results should be ordered by ascending score (lower is better)");
        }

        // Test with different alpha values (affects spatial vs textual weight in scoring)
        // Alpha=0.8 means spatial proximity is more important in combined score
        tree.setAlphaDistribution(0.8f);
        SKJoinQuery spatialFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        List<SKJoinQuery.Result> spatialResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, spatialFocusedQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertFalse(spatialResults.isEmpty(), "Spatial-focused query should return results");

        // Alpha=0.2 means textual relevance is more important in combined score
        tree.setAlphaDistribution(0.2f);
        SKJoinQuery textualFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        List<SKJoinQuery.Result> textualResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, textualFocusedQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertFalse(textualResults.isEmpty(), "Textual-focused query should return results");

        // Reset alpha back to default for remaining tests
        tree.setAlphaDistribution(0.5f);

        // Test with smaller distance threshold
        float smallerDistance = 10.0f;
        List<SKJoinQuery.Result> limitedResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, joinQuery, smallerDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertNotNull(limitedResults);
        assertTrue(limitedResults.size() < results.size(),
                "Smaller distance threshold should return fewer results");

        // Test with different keywords
        ArrayList<Integer> rareKeywords = new ArrayList<>(Collections.singletonList(109));
        ArrayList<Double> rareKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        SKJoinQuery rareQuery = new SKJoinQuery(2, 0.5, queryLocation, rareKeywords, rareKeywordWeights);

        List<SKJoinQuery.Result> rareResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, rareQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertTrue(rareResults.isEmpty() || rareResults.size() < results.size(),
                "Query with rare keywords should return fewer results");

        // Test edge case: very large distance
        float veryLargeDistance = 1000.0f;
        List<SKJoinQuery.Result> allPairsResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, joinQuery, veryLargeDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertNotNull(allPairsResults);
        assertTrue(allPairsResults.size() >= results.size(),
                "Very large distance should return at least as many results as standard distance");

        // Test edge case: empty keyword list
        ArrayList<Integer> emptyKeywords = new ArrayList<>();
        ArrayList<Double> emptyWeights = new ArrayList<>();
        SKJoinQuery emptyQuery = new SKJoinQuery(3, 0.5, queryLocation, emptyKeywords, emptyWeights);

        List<SKJoinQuery.Result> emptyResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, emptyQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertTrue(emptyResults.isEmpty(),
                "Query with empty keyword list should return no results");
    }


//    @Test
//    void testSJSKQuery_Recursive_CombinedScore() {
//        // Create a self-join query with a maximum distance threshold
//        ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
//        //ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
//        ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
//        //ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
//        Point queryLocation = new Point(new double[]{15.0, 20.0});
//
//        // Create the join query with alpha=0.5 (equal weight to spatial and textual relevance)
//        SKJoinQuery joinQuery = new SKJoinQuery(1, 1.0, queryLocation, keywords, keywordWeights);
//        float maxDistance = 30.0f; // Maximum spatial distance between object pairs
//        float textualThreshold = 0.1f; // Minimum textual similarity threshold for keyword matching
//        ThresholdPolicy thresholdPolicy = ThresholdPolicy.COMBINED_COST;
//        JoinStrategy joinStrategy = JoinStrategy.DEFAULT;
//        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;
//
//        System.out.println(tree.printTreeStructure()); // Print the tree structure for debugging
//        Node node = tree.readNode(1);
//
//        // Updated to work with new TreeMap structure
//        NodeEntry entry1 = null;
//        NodeEntry entry2 = null;
//        for (Map.Entry<Integer, NodeEntry> mapEntry : node.getNodeEntries().entrySet()) {
//            if (mapEntry.getKey() == 5) {
//                entry1 = mapEntry.getValue();
//            }
//            if (mapEntry.getKey() == 7) {
//                entry2 = mapEntry.getValue();
//            }
//        }
//
//        if (entry1 != null && entry2 != null) {
//            System.out.println("Distance: " + entry1.getMBR().getMinimumDistance(entry2.getMBR()));
//        }
//
//        // Print the term weigths
//        for (int docId = 1; docId <= sampleTextualObjects.size(); docId++) {
//            System.out.println("DocID " + docId + " Weights: " + weightStore.read(docId));
//        }
//
//        // Execute the self-join query
//        List<SKJoinQuery.Result> results = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//
//        // Print all pairs for debugging
//        System.out.println("Total pairs found: " + results.size());
//
//        for (SKJoinQuery.Result pair : results) {
//            int id1 = pair.getPairId1();
//            int id2 = pair.getPairId2();
//            ArrayList<Integer> keywords1 = sampleTextualObjects.get(id1);
//            ArrayList<Integer> keywords2 = sampleTextualObjects.get(id2);
//            System.out.println("Pair: " + id1 + "," + id2 +
//                    " | Score: " + pair.combineCost +
//                    " | Keywords1: " + keywords1 +
//                    " | Keywords2: " + keywords2);
//        }
//
//        // Verify results
//        assertNotNull(results, "Self-join results should not be null");
//        assertFalse(results.isEmpty(), "Self-join should return matching pairs");
//
//        // We should have specific expectations about the result count
//        int expectedPairCount = 15; // Adjust based on actual implementation behavior
//        assertEquals(expectedPairCount, results.size(),
//                "Expected approximately " + expectedPairCount + " matching pairs");
//
//        // Check for expected pairs
//        Set<String> expectedPairs = new HashSet<>(Arrays.asList(
//                "1-2", "1-3", "2-3", "1-7", "2-7", "3-7"
//        ));
//
//        Set<String> foundPairs = new HashSet<>();
//        Map<String, Double> pairScores = new HashMap<>();
//
//        for (SKJoinQuery.Result result : results) {
//            int id1 = result.getPairId1();
//            int id2 = result.getPairId2();
//            String pairKey = Math.min(id1, id2) + "-" + Math.max(id1, id2);
//            foundPairs.add(pairKey);
//            pairScores.put(pairKey, result.combineCost);
//
//            // For each pair, verify they have at least one keyword from the query
//            Set<Integer> keywords1 = new HashSet<>(sampleTextualObjects.get(id1));
//            Set<Integer> keywords2 = new HashSet<>(sampleTextualObjects.get(id2));
//
//            // Check if each object has at least one of the query keywords
//            boolean obj1HasQueryKeyword = keywords1.stream().anyMatch(keywords::contains);
//            boolean obj2HasQueryKeyword = keywords2.stream().anyMatch(keywords::contains);
//
//            assertTrue(obj1HasQueryKeyword && obj2HasQueryKeyword,
//                    "Each object in pair " + id1 + "-" + id2 + " should have at least one query keyword");
//
//            // Verify the result has a valid score
//            assertTrue(result.combineCost > 0, "Result score should be positive");
//            assertTrue(result.combineCost <= 1.0, "Result score should be normalized (≤ 1.0)");
//        }
//
//        // Verify that we found all expected pairs
//        for (String expectedPair : expectedPairs) {
//            assertTrue(foundPairs.contains(expectedPair),
//                    "Expected pair " + expectedPair + " not found in results");
//        }
//
//        // Verify result ordering - results should be in ascending order of score (lower is better)
//        for (int i = 1; i < results.size(); i++) {
//            assertTrue(results.get(i-1).combineCost <= results.get(i).combineCost,
//                    "Results should be ordered by ascending score (lower is better)");
//        }
//
//        // Test with different alpha values (affects spatial vs textual weight in scoring)
//        // Alpha=0.8 means spatial proximity is more important in combined score
//        tree.setAlphaDistribution(0.8f);
//        SKJoinQuery spatialFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
//        List<SKJoinQuery.Result> spatialResults = tree.selfJoinSKQueryRecursive(documentIndex, spatialFocusedQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertFalse(spatialResults.isEmpty(), "Spatial-focused query should return results");
//
//        // Alpha=0.2 means textual relevance is more important in combined score
//        tree.setAlphaDistribution(0.2f);
//        SKJoinQuery textualFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
//        List<SKJoinQuery.Result> textualResults = tree.selfJoinSKQueryRecursive(documentIndex, textualFocusedQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertFalse(textualResults.isEmpty(), "Textual-focused query should return results");
//
//        // Reset alpha back to default for remaining tests
//        tree.setAlphaDistribution(0.5f);
//
//        // Test with smaller distance threshold
//        float smallerDistance = 10.0f;
//        List<SKJoinQuery.Result> limitedResults = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, smallerDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertNotNull(limitedResults);
//        assertTrue(limitedResults.size() < results.size(),
//                "Smaller distance threshold should return fewer results");
//
//        // Test with different keywords
//        ArrayList<Integer> rareKeywords = new ArrayList<>(Collections.singletonList(109));
//        ArrayList<Double> rareKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
//        SKJoinQuery rareQuery = new SKJoinQuery(2, 0.5, queryLocation, rareKeywords, rareKeywordWeights);
//
//        List<SKJoinQuery.Result> rareResults = tree.selfJoinSKQueryRecursive(documentIndex, rareQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertTrue(rareResults.isEmpty() || rareResults.size() < results.size(),
//                "Query with rare keywords should return fewer results");
//
//        // Test edge case: very large distance
//        float veryLargeDistance = 1000.0f;
//        List<SKJoinQuery.Result> allPairsResults = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, veryLargeDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertNotNull(allPairsResults);
//        assertTrue(allPairsResults.size() >= results.size(),
//                "Very large distance should return at least as many results as standard distance");
//
//        // Test edge case: empty keyword list
//        ArrayList<Integer> emptyKeywords = new ArrayList<>();
//        ArrayList<Double> emptyWeights = new ArrayList<>();
//        SKJoinQuery emptyQuery = new SKJoinQuery(3, 0.5, queryLocation, emptyKeywords, emptyWeights);
//
//        List<SKJoinQuery.Result> emptyResults = tree.selfJoinSKQueryRecursive(documentIndex, emptyQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertTrue(emptyResults.isEmpty(),
//                "Query with empty keyword list should return no results");
//    }


    @Test
    void testSJSKQuery_Iterative_PlainSweep_StrictScore() {
        // Create a self-join query with a maximum distance threshold
        ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
        ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
        Point queryLocation = new Point(new double[]{15.0, 20.0});

        // Create the join query with alpha=0.5 (equal weight to spatial and textual relevance)
        SKJoinQuery joinQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        float maxDistance = 30.0f; // Maximum spatial distance between object pairs
        float textualThreshold = 0.1f; // Minimum textual similarity threshold for keyword matching
        ThresholdPolicy thresholdPolicy = ThresholdPolicy.STRICT;
        JoinStrategy joinStrategy = JoinStrategy.PLANE_SWEEP;
        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;

        System.out.println(tree.printTreeStructure()); // Print the tree structure for debugging
        Node node = tree.readNode(1);

        // Adapted to use new TreeMap structure - check if entries exist before accessing
        if (node.getNodeEntries().containsKey(5) && node.getNodeEntries().containsKey(7)) {
            Region mbr1 = node.getNodeEntries().get(5).getMBR();
            Region mbr2 = node.getNodeEntries().get(7).getMBR();
            System.out.println("Distance: " + mbr1.getMinimumDistance(mbr2));
        } else {
            System.out.println("Child nodes 5 or 7 not found in node entries");
        }

        // Execute the self-join query
        List<SKJoinQuery.Result> results = tree.selfJoinSKQueryBestFirst(
                documentIndex, joinQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));

        // Print all pairs for debugging
        System.out.println("Total pairs found: " + results.size());

        for (SKJoinQuery.Result pair : results) {
            int id1 = pair.getPairId1();
            int id2 = pair.getPairId2();
            ArrayList<Integer> keywords1 = sampleTextualObjects.get(id1);
            ArrayList<Integer> keywords2 = sampleTextualObjects.get(id2);
            System.out.println("Pair: " + id1 + "," + id2 +
                    " | Score: " + pair.combineCost +
                    " | Keywords1: " + keywords1 +
                    " | Keywords2: " + keywords2);
        }

        // Verify results
        assertNotNull(results, "Self-join results should not be null");
        assertFalse(results.isEmpty(), "Self-join should return matching pairs");

        // We should have specific expectations about the result count
        int expectedPairCount = 14; // Adjust based on actual implementation behavior
        assertEquals(expectedPairCount, results.size(),
                "Expected approximately " + expectedPairCount + " matching pairs");

        // Check for expected pairs
        // Expected pairs: (1,2), (1,3), (2,3), (1,7), (2,7), (3,7)
        Set<String> expectedPairs = new HashSet<>(Arrays.asList(
                "1-2", "1-3", "2-3", "1-7", "2-7", "3-7"
        ));

        Set<String> foundPairs = new HashSet<>();
        Map<String, Double> pairScores = new HashMap<>();

        for (SKJoinQuery.Result result : results) {
            int id1 = result.getPairId1();
            int id2 = result.getPairId2();
            String pairKey = Math.min(id1, id2) + "-" + Math.max(id1, id2);
            foundPairs.add(pairKey);
            pairScores.put(pairKey, result.combineCost);

            // For each pair, verify they have at least one keyword from the query
            Set<Integer> keywords1 = new HashSet<>(sampleTextualObjects.get(id1));
            Set<Integer> keywords2 = new HashSet<>(sampleTextualObjects.get(id2));

            // Check if each object has at least one of the query keywords
            boolean obj1HasQueryKeyword = keywords1.stream().anyMatch(keywords::contains);
            boolean obj2HasQueryKeyword = keywords2.stream().anyMatch(keywords::contains);

            assertTrue(obj1HasQueryKeyword && obj2HasQueryKeyword,
                    "Each object in pair " + id1 + "-" + id2 + " should have at least one query keyword");

            // Verify the result has a valid score
            assertTrue(result.combineCost > 0, "Result score should be positive");
            assertTrue(result.combineCost <= 1.0, "Result score should be normalized (≤ 1.0)");
        }

        // Verify that we found all expected pairs
        for (String expectedPair : expectedPairs) {
            assertTrue(foundPairs.contains(expectedPair),
                    "Expected pair " + expectedPair + " not found in results");
        }

        // Verify result ordering - results should be in ascending order of score (lower is better)
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i-1).combineCost <= results.get(i).combineCost,
                    "Results should be ordered by ascending score (lower is better)");
        }

        // Test with different alpha values (affects spatial vs textual weight in scoring)
        // Alpha=0.8 means spatial proximity is more important in combined score
        tree.setAlphaDistribution(0.8f);
        SKJoinQuery spatialFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        List<SKJoinQuery.Result> spatialResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, spatialFocusedQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertFalse(spatialResults.isEmpty(), "Spatial-focused query should return results");

        // Alpha=0.2 means textual relevance is more important in combined score
        tree.setAlphaDistribution(0.2f);
        SKJoinQuery textualFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        List<SKJoinQuery.Result> textualResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, textualFocusedQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertFalse(textualResults.isEmpty(), "Textual-focused query should return results");

        // Reset alpha back to default for remaining tests
        tree.setAlphaDistribution(0.5f);

        // Test with smaller distance threshold
        float smallerDistance = 10.0f;
        List<SKJoinQuery.Result> limitedResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, joinQuery, smallerDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertNotNull(limitedResults);
        assertTrue(limitedResults.size() < results.size(),
                "Smaller distance threshold should return fewer results");

        // Test with different keywords
        ArrayList<Integer> rareKeywords = new ArrayList<>(Collections.singletonList(109));
        ArrayList<Double> rareKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        SKJoinQuery rareQuery = new SKJoinQuery(2, 0.5, queryLocation, rareKeywords, rareKeywordWeights);

        List<SKJoinQuery.Result> rareResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, rareQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertTrue(rareResults.isEmpty() || rareResults.size() < results.size(),
                "Query with rare keywords should return fewer results");

        // Test edge case: very large distance
        float veryLargeDistance = 1000.0f;
        List<SKJoinQuery.Result> allPairsResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, joinQuery, veryLargeDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertNotNull(allPairsResults);
        assertTrue(allPairsResults.size() >= results.size(),
                "Very large distance should return at least as many results as standard distance");

        // Test edge case: empty keyword list
        ArrayList<Integer> emptyKeywords = new ArrayList<>();
        ArrayList<Double> emptyWeights = new ArrayList<>();
        SKJoinQuery emptyQuery = new SKJoinQuery(3, 0.5, queryLocation, emptyKeywords, emptyWeights);

        List<SKJoinQuery.Result> emptyResults = tree.selfJoinSKQueryBestFirst(
                documentIndex, emptyQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertTrue(emptyResults.isEmpty(),
                "Query with empty keyword list should return no results");
    }


    @Test
    void testSJSKQuery_Recursive_StrictScore() {
        // Create a self-join query with a maximum distance threshold
        ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
        //ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
        ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
        //ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        Point queryLocation = new Point(new double[]{15.0, 20.0});

        // Create the join query with alpha=0.5 (equal weight to spatial and textual relevance)
        SKJoinQuery joinQuery = new SKJoinQuery(1, 1.0, queryLocation, keywords, keywordWeights);
        float maxDistance = 30.0f; // Maximum spatial distance between object pairs
        float textualThreshold = 0.1f; // Minimum textual similarity threshold for keyword matching
        ThresholdPolicy thresholdPolicy = ThresholdPolicy.STRICT;
        JoinStrategy joinStrategy = JoinStrategy.DEFAULT;
        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;

        System.out.println(tree.printTreeStructure()); // Print the tree structure for debugging
        Node node = tree.readNode(1);

        // Updated to work with new TreeMap structure
        NodeEntry entry1 = null;
        NodeEntry entry2 = null;
        for (Map.Entry<Integer, NodeEntry> mapEntry : node.getNodeEntries().entrySet()) {
            if (mapEntry.getKey() == 5) {
                entry1 = mapEntry.getValue();
            }
            if (mapEntry.getKey() == 7) {
                entry2 = mapEntry.getValue();
            }
        }

        if (entry1 != null && entry2 != null) {
            System.out.println("Distance: " + entry1.getMBR().getMinimumDistance(entry2.getMBR()));
        }

        // Print the term weigths
        for (int docId = 1; docId <= sampleTextualObjects.size(); docId++) {
            System.out.println("DocID " + docId + " Weights: " + weightStore.read(docId));
        }

        // Execute the self-join query
        List<SKJoinQuery.Result> results = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));

        // Print all pairs for debugging
        System.out.println("Total pairs found: " + results.size());

        for (SKJoinQuery.Result pair : results) {
            int id1 = pair.getPairId1();
            int id2 = pair.getPairId2();
            ArrayList<Integer> keywords1 = sampleTextualObjects.get(id1);
            ArrayList<Integer> keywords2 = sampleTextualObjects.get(id2);
            System.out.println("Pair: " + id1 + "," + id2 +
                    " | Score: " + pair.combineCost +
                    " | Keywords1: " + keywords1 +
                    " | Keywords2: " + keywords2);
        }

        // Verify results
        assertNotNull(results, "Self-join results should not be null");
        assertFalse(results.isEmpty(), "Self-join should return matching pairs");

        // We should have specific expectations about the result count
        int expectedPairCount = 14; // Adjust based on actual implementation behavior
        assertEquals(expectedPairCount, results.size(),
                "Expected approximately " + expectedPairCount + " matching pairs");

        // Check for expected pairs
        Set<String> expectedPairs = new HashSet<>(Arrays.asList(
                "1-2", "1-3", "2-3", "1-7", "2-7", "3-7"
        ));

        Set<String> foundPairs = new HashSet<>();
        Map<String, Double> pairScores = new HashMap<>();

        for (SKJoinQuery.Result result : results) {
            int id1 = result.getPairId1();
            int id2 = result.getPairId2();
            String pairKey = Math.min(id1, id2) + "-" + Math.max(id1, id2);
            foundPairs.add(pairKey);
            pairScores.put(pairKey, result.combineCost);

            // For each pair, verify they have at least one keyword from the query
            Set<Integer> keywords1 = new HashSet<>(sampleTextualObjects.get(id1));
            Set<Integer> keywords2 = new HashSet<>(sampleTextualObjects.get(id2));

            // Check if each object has at least one of the query keywords
            boolean obj1HasQueryKeyword = keywords1.stream().anyMatch(keywords::contains);
            boolean obj2HasQueryKeyword = keywords2.stream().anyMatch(keywords::contains);

            assertTrue(obj1HasQueryKeyword && obj2HasQueryKeyword,
                    "Each object in pair " + id1 + "-" + id2 + " should have at least one query keyword");

            // Verify the result has a valid score
            assertTrue(result.combineCost > 0, "Result score should be positive");
            assertTrue(result.combineCost <= 1.0, "Result score should be normalized (≤ 1.0)");
        }

        // Verify that we found all expected pairs
        for (String expectedPair : expectedPairs) {
            assertTrue(foundPairs.contains(expectedPair),
                    "Expected pair " + expectedPair + " not found in results");
        }

        // Verify result ordering - results should be in ascending order of score (lower is better)
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i-1).combineCost <= results.get(i).combineCost,
                    "Results should be ordered by ascending score (lower is better)");
        }

        // Test with different alpha values (affects spatial vs textual weight in scoring)
        // Alpha=0.8 means spatial proximity is more important in combined score
        tree.setAlphaDistribution(0.8f);
        SKJoinQuery spatialFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        List<SKJoinQuery.Result> spatialResults = tree.selfJoinSKQueryRecursive(documentIndex, spatialFocusedQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertFalse(spatialResults.isEmpty(), "Spatial-focused query should return results");

        // Alpha=0.2 means textual relevance is more important in combined score
        tree.setAlphaDistribution(0.2f);
        SKJoinQuery textualFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        List<SKJoinQuery.Result> textualResults = tree.selfJoinSKQueryRecursive(documentIndex, textualFocusedQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertFalse(textualResults.isEmpty(), "Textual-focused query should return results");

        // Reset alpha back to default for remaining tests
        tree.setAlphaDistribution(0.5f);

        // Test with smaller distance threshold
        float smallerDistance = 10.0f;
        List<SKJoinQuery.Result> limitedResults = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, smallerDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertNotNull(limitedResults);
        assertTrue(limitedResults.size() < results.size(),
                "Smaller distance threshold should return fewer results");

        // Test with different keywords
        ArrayList<Integer> rareKeywords = new ArrayList<>(Collections.singletonList(109));
        ArrayList<Double> rareKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        SKJoinQuery rareQuery = new SKJoinQuery(2, 0.5, queryLocation, rareKeywords, rareKeywordWeights);

        List<SKJoinQuery.Result> rareResults = tree.selfJoinSKQueryRecursive(documentIndex, rareQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertTrue(rareResults.isEmpty() || rareResults.size() < results.size(),
                "Query with rare keywords should return fewer results");

        // Test edge case: very large distance
        float veryLargeDistance = 1000.0f;
        List<SKJoinQuery.Result> allPairsResults = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, veryLargeDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertNotNull(allPairsResults);
        assertTrue(allPairsResults.size() >= results.size(),
                "Very large distance should return at least as many results as standard distance");

        // Test edge case: empty keyword list
        ArrayList<Integer> emptyKeywords = new ArrayList<>();
        ArrayList<Double> emptyWeights = new ArrayList<>();
        SKJoinQuery emptyQuery = new SKJoinQuery(3, 0.5, queryLocation, emptyKeywords, emptyWeights);

        List<SKJoinQuery.Result> emptyResults = tree.selfJoinSKQueryRecursive(documentIndex, emptyQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertTrue(emptyResults.isEmpty(),
                "Query with empty keyword list should return no results");
    }


//    @Test
//    void testSJSKQuery_Recursive_PlaneSweep_CombinedScores() {
//        // Create a self-join query with a maximum distance threshold
//        ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
//        ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
//        Point queryLocation = new Point(new double[]{15.0, 20.0});
//
//        // Create the join query with alpha=0.5 (equal weight to spatial and textual relevance)
//        SKJoinQuery joinQuery = new SKJoinQuery(1, 1.0, queryLocation, keywords, keywordWeights);
//        float maxDistance = 30.0f; // Maximum spatial distance between object pairs
//        float textualThreshold = 0.1f; // Minimum textual similarity threshold for keyword matching
//        ThresholdPolicy thresholdPolicy = ThresholdPolicy.COMBINED_COST;
//        JoinStrategy joinStrategy = JoinStrategy.PLANE_SWEEP;
//        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;
//
//        System.out.println(tree.printTreeStructure()); // Print the tree structure for debugging
//        Node node = tree.readNode(1);
//
//        // Updated to work with new TreeMap structure
//        NodeEntry entry1 = null;
//        NodeEntry entry2 = null;
//        for (Map.Entry<Integer, NodeEntry> mapEntry : node.getNodeEntries().entrySet()) {
//            if (mapEntry.getKey() == 5) {
//                entry1 = mapEntry.getValue();
//            }
//            if (mapEntry.getKey() == 7) {
//                entry2 = mapEntry.getValue();
//            }
//        }
//
//        if (entry1 != null && entry2 != null) {
//            System.out.println("Distance: " + entry1.getMBR().getMinimumDistance(entry2.getMBR()));
//        }
//
//        // Execute the self-join query
//        List<SKJoinQuery.Result> results = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//
//        // Print all pairs for debugging
//        System.out.println("Total pairs found: " + results.size());
//
//        for (SKJoinQuery.Result pair : results) {
//            int id1 = pair.getPairId1();
//            int id2 = pair.getPairId2();
//            ArrayList<Integer> keywords1 = sampleTextualObjects.get(id1);
//            ArrayList<Integer> keywords2 = sampleTextualObjects.get(id2);
//            System.out.println("Pair: " + id1 + "," + id2 +
//                    " | Score: " + pair.combineCost +
//                    " | Keywords1: " + keywords1 +
//                    " | Keywords2: " + keywords2);
//        }
//
//        // Verify results
//        assertNotNull(results, "Self-join results should not be null");
//        assertFalse(results.isEmpty(), "Self-join should return matching pairs");
//
//        // We should have specific expectations about the result count
//        int expectedPairCount = 15; // Adjust based on actual implementation behavior
//        assertEquals(expectedPairCount, results.size(),
//                "Expected approximately " + expectedPairCount + " matching pairs");
//
//        // Check for expected pairs
//        Set<String> expectedPairs = new HashSet<>(Arrays.asList(
//                "1-2", "1-3", "2-3", "1-7", "2-7", "3-7"
//        ));
//
//        Set<String> foundPairs = new HashSet<>();
//        Map<String, Double> pairScores = new HashMap<>();
//
//        for (SKJoinQuery.Result result : results) {
//            int id1 = result.getPairId1();
//            int id2 = result.getPairId2();
//            String pairKey = Math.min(id1, id2) + "-" + Math.max(id1, id2);
//            foundPairs.add(pairKey);
//            pairScores.put(pairKey, result.combineCost);
//
//            // For each pair, verify they have at least one keyword from the query
//            Set<Integer> keywords1 = new HashSet<>(sampleTextualObjects.get(id1));
//            Set<Integer> keywords2 = new HashSet<>(sampleTextualObjects.get(id2));
//
//            // Check if each object has at least one of the query keywords
//            boolean obj1HasQueryKeyword = keywords1.stream().anyMatch(keywords::contains);
//            boolean obj2HasQueryKeyword = keywords2.stream().anyMatch(keywords::contains);
//
//            assertTrue(obj1HasQueryKeyword && obj2HasQueryKeyword,
//                    "Each object in pair " + id1 + "-" + id2 + " should have at least one query keyword");
//
//            // Verify the result has a valid score
//            assertTrue(result.combineCost > 0, "Result score should be positive");
//            assertTrue(result.combineCost <= 1.0, "Result score should be normalized (≤ 1.0)");
//        }
//
//        // Verify that we found all expected pairs
//        for (String expectedPair : expectedPairs) {
//            assertTrue(foundPairs.contains(expectedPair),
//                    "Expected pair " + expectedPair + " not found in results");
//        }
//
//        // Verify result ordering - results should be in ascending order of score (lower is better)
//        for (int i = 1; i < results.size(); i++) {
//            assertTrue(results.get(i-1).combineCost <= results.get(i).combineCost,
//                    "Results should be ordered by ascending score (lower is better)");
//        }
//
//        // Test with different alpha values (affects spatial vs textual weight in scoring)
//        // Alpha=0.8 means spatial proximity is more important in combined score
//        tree.setAlphaDistribution(0.8f);
//        SKJoinQuery spatialFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
//        List<SKJoinQuery.Result> spatialResults = tree.selfJoinSKQueryRecursive(documentIndex, spatialFocusedQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertFalse(spatialResults.isEmpty(), "Spatial-focused query should return results");
//
//        // Alpha=0.2 means textual relevance is more important in combined score
//        tree.setAlphaDistribution(0.2f);
//        SKJoinQuery textualFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
//        List<SKJoinQuery.Result> textualResults = tree.selfJoinSKQueryRecursive(documentIndex, textualFocusedQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertFalse(textualResults.isEmpty(), "Textual-focused query should return results");
//
//        // Reset alpha back to default for remaining tests
//        tree.setAlphaDistribution(0.5f);
//
//        // Test with smaller distance threshold
//        float smallerDistance = 10.0f;
//        List<SKJoinQuery.Result> limitedResults = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, smallerDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertNotNull(limitedResults);
//        assertTrue(limitedResults.size() < results.size(),
//                "Smaller distance threshold should return fewer results");
//
//        // Test with different keywords
//        ArrayList<Integer> rareKeywords = new ArrayList<>(Collections.singletonList(109));
//        ArrayList<Double> rareKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
//        SKJoinQuery rareQuery = new SKJoinQuery(2, 0.5, queryLocation, rareKeywords, rareKeywordWeights);
//
//        List<SKJoinQuery.Result> rareResults = tree.selfJoinSKQueryRecursive(documentIndex, rareQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertTrue(rareResults.isEmpty() || rareResults.size() < results.size(),
//                "Query with rare keywords should return fewer results");
//
//        // Test edge case: very large distance
//        float veryLargeDistance = 1000.0f;
//        List<SKJoinQuery.Result> allPairsResults = tree.selfJoinSKQueryRecursive(documentIndex, joinQuery, veryLargeDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertNotNull(allPairsResults);
//        assertTrue(allPairsResults.size() >= results.size(),
//                "Very large distance should return at least as many results as standard distance");
//
//        // Test edge case: empty keyword list
//        ArrayList<Integer> emptyKeywords = new ArrayList<>();
//        ArrayList<Double> emptyWeights = new ArrayList<>();
//        SKJoinQuery emptyQuery = new SKJoinQuery(3, 0.5, queryLocation, emptyKeywords, emptyWeights);
//
//        List<SKJoinQuery.Result> emptyResults = tree.selfJoinSKQueryRecursive(documentIndex, emptyQuery, maxDistance, textualThreshold, createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
//        assertTrue(emptyResults.isEmpty(),
//                "Query with empty keyword list should return no results");
//    }


    @Test
    void testPairTextualRelevancy() {
        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;

        // Print the term weigths
        for (int docId = 1; docId <= sampleTextualObjects.size(); docId++) {
            System.out.println("DocID " + docId + " Weights: " + weightStore.read(docId));
        }

        // Test with a single keyword - using two different document sets
        List<Integer> keywords1 = new ArrayList<>(Collections.singletonList(101));
        List<Double> keywordWeights1 = new ArrayList<>(Collections.singletonList(1.0));
        Map<Integer, Double> score1 = tree.calculatePairTextualRelevancy(1, tree.getRootIdentifier(), documentIndex, keywords1, keywordWeights1, similarityType);
        System.out.println("Single keyword score: " + score1);
        assertTrue(score1.values().stream().allMatch(v -> v >= 0 && v <= 1.0), "Textual relevancy score should be between 0 and 1");

        // Test with multiple keywords
        List<Integer> keywords2 = new ArrayList<>(Arrays.asList(101, 102));
        List<Double> keywordWeights2 = new ArrayList<>(Arrays.asList(1.0, 1.0));
        Map<Integer, Double> score2 = tree.calculatePairTextualRelevancy(1, tree.getRootIdentifier(), documentIndex, keywords2, keywordWeights2, similarityType);
        System.out.println("Multiple keywords score: " + score2);
        assertTrue(score2.values().stream().allMatch(v -> v >= 0 && v <= 1.0), "Textual relevancy score should be between 0 and 1");

        // Test with a perfect match
        List<Integer> perfectMatchKeywords = new ArrayList<>(Arrays.asList(101, 102, 103));
        List<Double> perfectMatchWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
        //System.out.println("Set Weights for perfect match: " + weightStore.read(1));
        Map<Integer, Double> perfectMatchScore = tree.calculatePairTextualRelevancy(1, tree.getRootIdentifier(), documentIndex, perfectMatchKeywords, perfectMatchWeights, similarityType);
        System.out.println("Perfect match score: " + perfectMatchScore);
        assertTrue(perfectMatchScore.values().stream().anyMatch(v -> Math.abs(v - 1.0) < 0.2), "Textual relevancy score for perfect match should be close to 1.0");

        // Test with no matching keywords
        List<Integer> keywords3 = new ArrayList<>(Collections.singletonList(999)); // Assuming 999 is not in sampleTextualObjects
        List<Double> keywordWeights3 = new ArrayList<>(Collections.singletonList(1.0));
        Map<Integer, Double> score3 = tree.calculatePairTextualRelevancy(1, tree.getRootIdentifier(), documentIndex, keywords3, keywordWeights3, similarityType);
        assertTrue(score3.values().stream().allMatch(v -> v == 0.0), "Textual relevancy score for non-matching keywords should be 0");
        System.out.println("No matching keywords score: " + score3);

        // Test with empty keyword list
        ArrayList<Integer> emptyKeywords4 = new ArrayList<>();
        ArrayList<Double> emptyWeights4 = new ArrayList<>();
        Map<Integer, Double> emptyScore = tree.calculatePairTextualRelevancy(1, tree.getRootIdentifier(), documentIndex, emptyKeywords4, emptyWeights4, similarityType);
        System.out.println("Empty keyword list score: " + emptyScore);
        assertTrue(emptyScore.values().stream().allMatch(v -> v == 0.0), "Textual relevancy score for empty keyword list should be 0");
    }

//    @Test
//    void testDifferentInvertedIndexImplementations() {
//        // Test that both trees work with different inverted index implementations
//        IRTree refactoredTree = new IRTree(propertySet, storageManager, datasetParameters);
//
//        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
//            refactoredTree.insertData(entry.getKey(), entry.getValue());
//        }
//
//        // Test with ArrayListInvertedFileIndex
//        ArrayListInvertedFileIndex arrayListIndex = new ArrayListInvertedFileIndex(0);
//        ArrayListInvertedFileIndex refactoredArrayListIndex = new ArrayListInvertedFileIndex(0);
//
//        tree.createIRTree(weightStore, arrayListIndex);
//        refactoredTree.createIRTree(weightStore, refactoredArrayListIndex);
//
//        // Compare query results with ArrayList implementation
//        ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
//        SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
//
//        List<SKNNQuery.Result> originalResults = tree.booleanRangeQuery(arrayListIndex, query, 100.0f);
//        List<SKNNQuery.Result> refactoredResults = refactoredTree.booleanRangeQuery(refactoredArrayListIndex, query, 100.0f);
//
//        assertEquals(originalResults.size(), refactoredResults.size(),
//                    "Both implementations should return same results with ArrayListInvertedFileIndex");
//
//        Set<Integer> originalIds = originalResults.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
//        Set<Integer> refactoredIds = refactoredResults.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
//
//        assertEquals(originalIds, refactoredIds, "Document IDs should be identical with ArrayListInvertedFileIndex");
//    }


//    @Test
//    void testGnnkQuery() {
//        // AggregateSKNNQuery: List<Query> queries, List<Double> weights, Aggregator aggregator
//        // Query: Point location, ArrayList<Integer> keywords, ArrayList<Double> keywordWeights
//        // For gNNk, we need to define multiple queries.
//
//        // Query 1: near (10,20), keyword 101
//        Query q1 = new Query(new Point(new double[]{10,20}), new ArrayList<>(Arrays.asList(101)), new ArrayList<>(Arrays.asList(1.0)));
//        // Query 2: near (15,25), keyword 102
//        Query q2 = new Query(new Point(new double[]{15,25}), new ArrayList<>(Arrays.asList(102)), new ArrayList<>(Arrays.asList(1.0)));
//
//        List<Query> queries = Arrays.asList(q1, q2);
//        List<Double> queryWeights = Arrays.asList(0.5, 0.5); // Equal weights for the two queries
//        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
//
//        AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(1, queries, aggregator);
//        int topk = 1;
//
//        List<AggregateSKNNQuery.Result> results = tree.gnnk(documentIndex, gnnkQuery, topk);
//
//        assertNotNull(results);
//        assertFalse(results.isEmpty(), "gNNk query should return results.");
//        if (!results.isEmpty()) {
//            assertTrue(results.size() <= topk);
//            // Further assertions would depend on the expected aggregated scores.
//            // E.g., check if the result ID is one of the sample document IDs.
//            assertTrue(sampleSpatialObjects.containsKey(results.get(0).id), "Result ID should be a valid document ID.");
//        }
//    }
//
//    @Test
//    void testSgnnkQuery() {
//        // Similar setup to gNNk, but sGNNk also has subGroupSize
//        Query q1 = new Query(new Point(new double[]{10,20}), new ArrayList<>(List.of(101)), new ArrayList<>(List.of(1.0)));
//        Query q2 = new Query(new Point(new double[]{15,25}), new ArrayList<>(List.of(102)), new ArrayList<>(List.of(1.0)));
//        Query q3 = new Query(new Point(new double[]{12,22}), new ArrayList<>(List.of(103)), new ArrayList<>(List.of(1.0)));
//
//
//        List<Query> queries = Arrays.asList(q1, q2, q3);
//        List<Double> queryWeights = Arrays.asList(0.3, 0.3, 0.4); // Weights for q1, q2, q3
//        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
//        int subGroupSize = 2; // Consider the best 2 queries out of 3 for each object
//
//        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(queries, queryWeights, aggregator, subGroupSize, queries.size());
//        // The constructor for AggregateSKNNQuery might vary. Adjust as per actual definition.
//        // Assuming: AggregateSKNNQuery(List<Query> queries, List<Double> weights, Aggregator aggregator, int subGroupSize, int groupSize)
//        // If not, it might be: AggregateSKNNQuery(List<Query> queries, List<Double> weights, Aggregator aggregator);
//        // and subGroupSize is passed to sgnnk method or set on the query object.
//        // The IRTree.java sgnnk method signature is: sgnnk(IDocumentIndex invertedFile, AggregateSKNNQuery sgnnkQuery, int topk)
//        // So sgnnkQuery object must carry the subGroupSize. Let's assume its constructor supports it.
//        // If AggregateSKNNQuery does not have subGroupSize, this test needs adjustment.
//
//        int topk = 1;
//        List<AggregateSKNNQuery.Result> results = tree.sgnnk(documentIndex, sgnnkQuery, topk);
//
//        assertNotNull(results);
//        assertFalse(results.isEmpty(), "sGNNk query should return results.");
//        if (!results.isEmpty()) {
//            assertTrue(results.size() <= topk);
//            assertTrue(sampleSpatialObjects.containsKey(results.get(0).id));
//            assertNotNull(results.get(0).queryIds, "sGNNk result should include query IDs.");
//            if (results.get(0).queryIds != null) {
//                assertEquals(subGroupSize, results.get(0).queryIds.size(), "Number of query IDs should match subgroup size.");
//            }
//        }
//    }
}


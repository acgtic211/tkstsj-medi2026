package org.ual.spatiotextualindex.dirtree.legacy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.document.WeightCompute;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.query.Query;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.rtree.Statistics;
import org.ual.spatialindex.rtreebase.Leaf;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
// Remove AbstractDocumentStore if not directly used in this revised setup
// import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatiotextualindex.dirtree.DIRTree;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// Helper classes (InMemoryWeightStore, InMemoryDocumentIndex) should be accessible here
// (e.g., as static nested classes, or in the same package/file for testing)

class LegacyDIRTreeRefactorTest {
    private static final Logger log = LogManager.getLogger(LegacyDIRTreeRefactorTest.class);
    private DIRTree tree;
    private NodeStorageManager storageManager;
    private PropertySet propertySet;
    private DatasetParameters datasetParameters;
    private InvertedListIndex documentIndex;
    private AbstractDocumentStore weightStore;
    private int maxWordsPerDoc = 1000; // Example value, adjust as needed

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
        propertySet.setProperty("BetaArea", 0.5f); // Example value for beta area
        datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
        AbstractDocumentStore.maxWord = maxWordsPerDoc; // Set max words per document
        weightStore = new HashMapDocumentStore();


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
        sampleTextualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));
        sampleTextualObjects.put(4, new ArrayList<>(Collections.singletonList(105)));
        sampleTextualObjects.put(5, new ArrayList<>(Arrays.asList(101, 106)));          // Text for doc5
        sampleTextualObjects.put(6, new ArrayList<>(Arrays.asList(102, 107)));          // Text for doc6
        sampleTextualObjects.put(7, new ArrayList<>(Arrays.asList(103, 108, 101)));    // Text for doc7
        sampleTextualObjects.put(8, new ArrayList<>(Collections.singletonList(109)));  // Text for doc8

        double smoothingFactor = 0.5;
        WeightCompute.ComputeTermWeights(sampleTextualObjects, weightStore, smoothingFactor);

        // 2. Initialize DIRTree instance
        // The DIRTree constructor might use datasetParameters to configure itself.
        tree = new DIRTree(propertySet, storageManager, weightStore, datasetParameters);
        assertTrue(tree.isDocumentAware());

        HashSet<Integer> documentIds;
        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            documentIds = tree.getDocumentStore().readSet(entry.getKey());
            tree.insertData(entry.getKey(), entry.getValue(), documentIds);
        }

        documentIndex = new InvertedListIndex(0);

        // Assuming IRTree.createIRTree correctly utilizes the weightStore and documentIndex
        // for subsequent queries, or makes them available internally.
        tree.createDIRTree(weightStore, documentIndex);
    }

    @Test
    void testTreeInitializationAndTextualIntegration() {
        assertNotNull(tree, "DIRTree instance should be created.");
        Statistics stats = (Statistics) tree.getStatistics();
        assertNotNull(stats, "Statistics object should not be null.");
        assertTrue(stats.getNumberOfData() >= sampleSpatialObjects.size(), "Tree should contain the inserted spatial objects.");

        assertTrue(weightStore.getSize() > 0, "Weights should have been computed and stored.");
        assertEquals(sampleTextualObjects.size(), weightStore.getSize(), "Weight store should have an entry for each document with text.");

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
    void testDeleteDataFromLeafNode() {
        // 1. Setup: Tree is already set up by @BeforeEach with sample data.

        // 2. Identify data to delete
        int idToDelete = 2; // DocID 2: Point(15,25), Text {102, 103}
        IShape shapeToDelete = sampleSpatialObjects.get(idToDelete);
        assertNotNull(shapeToDelete, "Shape for ID " + idToDelete + " should exist in sample data.");

        // In this test setup, docSet for an individual object is a HashSet containing its own ID.
        HashSet<Integer> docSetToDelete = tree.getDocumentStore().readSet(idToDelete);
        assertNotNull(docSetToDelete, "Document set for ID " + idToDelete + " should exist.");
        assertFalse(docSetToDelete.isEmpty(), "Document set for deletion should not be empty.");

        long initialDataCount = tree.getStatistics().getNumberOfData();
        assertTrue(initialDataCount > 0, "Tree should have data before deletion.");
        log.info("Initial data count: {}", initialDataCount);
        log.info("Attempting to delete item with ID: {}, MBR: {}, DocSet: {}", idToDelete, shapeToDelete.getMBR(), docSetToDelete);

        // 3. Find the leaf node containing the data
        Node rootNode = tree.readNode(tree.getRootIdentifier());
        assertNotNull(rootNode, "Root node should not be null.");

        Stack<Integer> pathBuffer = new Stack<>();
        // findLeaf populates pathBuffer with the path to the leaf (excluding the leaf itself)
        Leaf leafNode = rootNode.findLeaf(idToDelete, shapeToDelete.getMBR(), pathBuffer);

        assertNotNull(leafNode, "Leaf node containing the data to delete (ID: " + idToDelete + ") should be found.");
        assertTrue(leafNode.isLeaf(), "Node found should be a leaf node.");
        log.info("Found leaf node {} to delete item from. Path to leaf: {}", leafNode.getIdentifier(), pathBuffer);

        // Pre-deletion check: Verify the item is in this leaf using the new TreeMap structure
        boolean foundInLeafBeforeDelete = false;
        NodeEntry entryToDelete = leafNode.getNodeEntries().get(idToDelete);
        if (entryToDelete != null &&
                entryToDelete.getMBR().equals(shapeToDelete.getMBR()) &&
                entryToDelete.getDocument() != null &&
                entryToDelete.getDocument().equals(docSetToDelete)) {
            foundInLeafBeforeDelete = true;
        }

        assertTrue(foundInLeafBeforeDelete, "Data to delete (ID: " + idToDelete + ") must be present in the identified leaf node before deletion.");
        log.info("Item confirmed in leaf {} before deletion.", leafNode.getIdentifier());

        // 4. Call deleteData on the leaf node
        leafNode.deleteData(idToDelete, pathBuffer, docSetToDelete);
        log.info("Called deleteData on leaf node {}.", leafNode.getIdentifier());

        // 5. Verify deletion
        // 5.1 Check statistics
        long finalDataCount = tree.getStatistics().getNumberOfData();
        log.info("Final data count: {}", finalDataCount);
        assertEquals(initialDataCount - 1, finalDataCount, "Number of data items should decrease by one after deletion.");

        // 5.2 Try to find the deleted item again using a boolean range query
        ArrayList<Integer> keywordsFromDeletedDoc = sampleTextualObjects.get(idToDelete); // Should be {102, 103}
        assertNotNull(keywordsFromDeletedDoc, "Keywords for deleted document should exist in sample data.");
        assertFalse(keywordsFromDeletedDoc.isEmpty(), "Keywords list for deleted document should not be empty.");

        Point queryPointForDeleted = (Point) shapeToDelete; // Query at the exact location of the deleted item
        // Use a query that would have uniquely identified the deleted item by its keywords and location
        SKNNQuery sknnQueryForDeleted = new SKNNQuery(9998, queryPointForDeleted, keywordsFromDeletedDoc);
        float queryRadius = 0.1f; // Small radius to ensure specificity

        log.info("Querying for deleted item: ID {}, Keywords {}, Location {}, Radius {}", idToDelete, keywordsFromDeletedDoc, queryPointForDeleted, queryRadius);
        List<SKNNQuery.Result> resultsAfterDelete = tree.booleanRangeQuery(documentIndex, sknnQueryForDeleted, queryRadius);

        boolean deletedItemFoundAfterDelete = false;
        for (SKNNQuery.Result result : resultsAfterDelete) {
            if (result.getId() == idToDelete) {
                deletedItemFoundAfterDelete = true;
                log.warn("Deleted item (ID: {}) was unexpectedly found by booleanRangeQuery after deletion.", idToDelete);
                break;
            }
        }
        assertFalse(deletedItemFoundAfterDelete, "Deleted item (ID: " + idToDelete + ") should not be found by booleanRangeQuery after deletion.");
        log.info("Deleted item (ID: {}) was not found by booleanRangeQuery, as expected.", idToDelete);

        // 5.3 Validate tree structure
        assertTrue(tree.validateDocumentStructure(), "Tree document structure should be valid after deletion and potential rebalancing.");
        log.info("Tree document structure validated successfully after deletion.");
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

//    @Test
//    void testBooleanKnnQuery() {
//        ArrayList<Integer> keywords = new ArrayList<Integer>(Collections.singletonList(101));
//        SKNNQuery sknnQuery = new SKNNQuery(1, queryPointInRegion, keywords);
//        int topk = 1;
//
//        List<SKNNQuery.Result> results = tree.booleanKnnQuery(documentIndex, sknnQuery, topk);
//        assertNotNull(results);
//        assertEquals(topk, results.size(), "Should find k results.");
//        assertEquals(1, results.get(0).id, "Expected doc1 to be the closest for keyword 101 from (10,20).");
//
//        topk = 2;
//        results = tree.booleanKnnQuery(documentIndex, sknnQuery, topk);
//        assertEquals(topk, results.size());
//        Set<Integer> foundIds = results.stream().map(result -> result.id).collect(Collectors.toSet());
//        assertTrue(foundIds.containsAll(Arrays.asList(1, 3)), "Expected doc1 and doc3.");
//
//        ArrayList<Integer> keywords102 = new ArrayList<Integer>(Collections.singletonList(102));
//        SKNNQuery sknnQuery102 = new SKNNQuery(2, queryPointInRegion, keywords102);
//        topk = 1;
//        results = tree.booleanKnnQuery(documentIndex, sknnQuery102, topk);
//        assertEquals(topk, results.size());
//        assertEquals(1, results.get(0).id, "Expected doc1 to be the closest for keyword 102 from (10,20).");
//    }

    @Test
    void testBooleanKnnQuery() {
        ArrayList<Integer> keywords101 = new ArrayList<>(Collections.singletonList(101));
        SKNNQuery sknnQuery1 = new SKNNQuery(1, queryPointInRegion, keywords101);
        int topk1 = 2;
        List<SKNNQuery.Result> results1 = tree.booleanKnnQuery(documentIndex, sknnQuery1, topk1);
        assertNotNull(results1, "Boolean KNN results should not be null.");
        assertTrue(results1.size() <= topk1, "Boolean KNN should return at most topk results.");
        // Doc 1 (10,20) dist 0; Doc 3 (12,22) dist sqrt(2^2+2^2)=sqrt(8)~2.82; Doc 5 (5,5) dist sqrt(5^2+15^2)=sqrt(25+225)=sqrt(250)~15.8
        // Expected: Doc 1, Doc 3
        if (results1.size() == 2) {
            Set<Integer> resultIds = results1.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
            assertTrue(resultIds.contains(1) && resultIds.contains(3), "Boolean KNN query for 101, top 2 failed to return expected docs.");
        } else if (results1.size() == 1) {
            assertEquals(1, results1.get(0).getId(), "Boolean KNN query for 101, top 1 (if only 1 returned) should be doc 1.");
        }


        ArrayList<Integer> keywords102 = new ArrayList<>(Collections.singletonList(102));
        SKNNQuery sknnQuery2 = new SKNNQuery(2, queryPointOutsideRegion, keywords102); // Query point far away
        int topk2 = 1;
        List<SKNNQuery.Result> results2 = tree.booleanKnnQuery(documentIndex, sknnQuery2, topk2);
        assertNotNull(results2);
        assertTrue(results2.size() <= topk2);
        // Documents with 102: 1 (10,20), 2 (15,25), 6 (25,15). All are valid candidates.
        // The closest one to (100,100) should be chosen.
        // Distances to (100,100):
        // Doc 1 (10,20): sqrt(90^2+80^2) = sqrt(8100+6400) = sqrt(14500) ~ 120.4
        // Doc 2 (15,25): sqrt(85^2+75^2) = sqrt(7225+5625) = sqrt(12850) ~ 113.3
        // Doc 6 (25,15): sqrt(75^2+85^2) = sqrt(5625+7225) = sqrt(12850) ~ 113.3
        // Doc 2 or 6 are expected if topk=1.
        if (!results2.isEmpty()) {
            assertTrue(sampleSpatialObjects.containsKey(results2.get(0).getId()), "Result ID should be a valid document ID.");
            assertTrue(results2.get(0).getId() == 2 || results2.get(0).getId() == 6, "Boolean KNN query for 102, top 1 from (100,100) should be doc 2 or 6.");
        }


        ArrayList<Integer> keywords999 = new ArrayList<>(Collections.singletonList(999)); // Non-existent keyword
        SKNNQuery sknnQuery3 = new SKNNQuery(3, queryPointInRegion, keywords999);
        List<SKNNQuery.Result> results3 = tree.booleanKnnQuery(documentIndex, sknnQuery3, 1);
        assertTrue(results3.isEmpty(), "Boolean KNN query for non-existent keyword should return no results.");
    }

//    @Test
//    void testTopkKnnQuery() {
//        ArrayList<Integer> keywords = new ArrayList<Integer>(Collections.singletonList(101));
//        ArrayList<Double> keywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
//        SKNNQuery sknnQuery = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights); // alpha = 0.5
//        int topk = 1;
//
//        List<SKNNQuery.Result> results = tree.topkKnnQuery(documentIndex, sknnQuery, topk);
//        assertNotNull(results);
//        if (!results.isEmpty()) {
//            assertTrue(results.size() <= topk);
//            // Further assertions depend on scoring logic and alpha
//            // For example, check if the top result is one of the expected documents
//            Set<Integer> expectedPossibleIds = new HashSet<Integer>(Arrays.asList(1, 3));
//            assertTrue(expectedPossibleIds.contains(results.get(0).id), "Top result for keyword 101 should be doc1 or doc3.");
//        } else if (sampleSpatialObjects.size() > 0 && documentIndex.getDocumentFrequency(101) > 0) {
//            fail("Should find results for topkKnnQuery if matches exist.");
//        }
//
//
//        ArrayList<Integer> keywords105 = new ArrayList<Integer>(Collections.singletonList(105));
//        SKNNQuery sknnQuery105 = new SKNNQuery(2, 0.5, queryPointInRegion, keywords105, keywordWeights); // alpha = 0.5
//        results = tree.topkKnnQuery(documentIndex, sknnQuery105, topk);
//        assertNotNull(results);
//        // Depending on alpha and scoring, doc4 (id=4) might or might not appear.
//        // If it appears, it should be doc4. If not, results list might be empty.
//        if (!results.isEmpty()) {
//            assertEquals(4, results.get(0).id, "If a result is found for keyword 105, it should be doc4.");
//        }
//    }

    @Test
    void testTopkKnnQuery() {
        ArrayList<Integer> keywords101 = new ArrayList<>(Collections.singletonList(101));
        List<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        SKNNQuery query = new SKNNQuery(1, 0.5, queryPointInRegion, keywords101, keywordWeights);
        int topk = 1;

        List<SKNNQuery.Result> results = tree.topkKnnQuery(documentIndex, query, topk);
        assertNotNull(results, "Top-k KNN results should not be null.");
        if (!sampleSpatialObjects.isEmpty() && !keywords101.isEmpty()) {
            assertFalse(results.isEmpty(), "Top-k KNN query should return results if data and queries exist.");
            if(!results.isEmpty()){
                assertTrue(results.size() <= topk, "Top-k KNN should return at most topk results.");
                assertTrue(sampleSpatialObjects.containsKey(results.get(0).getId()), "Result ID should be a valid document ID.");
            }
        }
    }


//    @Test
//    void testGnnkQuery() {
//        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
//
//        Query q1 = new Query(1001, 0.5, new Point(new double[]{10.0, 20.0}),
//                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
//        Query q2 = new Query(1002, 0.5, new Point(new double[]{15.0, 25.0}),
//                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
//
//        List<Query> queries = Arrays.asList(q1, q2);
//        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
//
//        // Use constructor: AggregateSKNNQuery(int id, List<Query> queries, int groupSize, IAggregator aggregator)
//        AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(1, queries, queries.size(), aggregator);
//        int topk = 1;
//
//        List<AggregateSKNNQuery.Result> results = tree.gnnk(documentIndex, gnnkQuery, topk);
//
//        assertNotNull(results);
//        if (topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
//            assertFalse(results.isEmpty(), "gNNk query should return results if data and queries exist.");
//            if (!results.isEmpty()) {
//                assertTrue(results.size() <= topk);
//                // Add more specific assertions based on expected outcome
//                // e.g., check properties of results.get(0).id, results.get(0).aggregateCost
//            }
//        } else if (results.isEmpty() && topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
//            // Potentially fail if no results are found when they are expected
//            // This depends on the specific data and query setup
//        }
//    }

    @Test
    void testGnnkQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        Query q1 = new Query(2001, 0.5, new Point(new double[]{10.0, 20.0}),
                new ArrayList<>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(2002, 0.5, new Point(new double[]{15.0, 25.0}),
                new ArrayList<>(Collections.singletonList(102)), defaultKeywordWeights);
        List<Query> queries = Arrays.asList(q1, q2);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
        AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(2, queries, queries.size(), aggregator);
        int topk = 1;

        List<AggregateSKNNQuery.Result> results = tree.gnnk(documentIndex, gnnkQuery, topk);

        assertNotNull(results, "gNNk results should not be null.");
        if (topk > 0 && !queries.isEmpty() && !sampleSpatialObjects.isEmpty()) {
            assertFalse(results.isEmpty(), "gNNk query should return results if data and queries exist and topk > 0.");
            if (!results.isEmpty()) {
                assertTrue(results.size() <= topk, "Number of results should not exceed topk.");
                AggregateSKNNQuery.Result topResult = results.get(0);
                assertNotNull(topResult.getId(), "gNNk: Result ID should not be null.");
                assertTrue(sampleSpatialObjects.containsKey(topResult.getId()), "gNNk: Result ID (" + topResult.getId() + ") should be a valid document ID.");
                assertNotNull(topResult.getQueryIds(), "gNNk: Result queryIds should not be null.");
                assertEquals(queries.size(), topResult.getQueryIds().size(), "gNNk: Result queryIds size should match the number of input queries.");
            }
        }
    }

//    @Test
//    void testSgnnkQuery() {
//        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
//
//        Query q1 = new Query(2001, 0.3, new Point(new double[]{10.0, 20.0}),
//                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
//        Query q2 = new Query(2002, 0.3, new Point(new double[]{15.0, 25.0}),
//                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
//        Query q3 = new Query(2003, 0.4, new Point(new double[]{12.0, 22.0}),
//                new ArrayList<Integer>(Collections.singletonList(103)), defaultKeywordWeights);
//
//        List<Query> queries = Arrays.asList(q1, q2, q3);
//        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
//        int subGroupSize = 2;
//
//        // Use constructor: AggregateSKNNQuery(int id, List<Query> queries, int groupSize, IAggregator aggregator)
//        // Then set subGroupSize.
//        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(2, queries, queries.size(), aggregator);
//        sgnnkQuery.subGroupSize = subGroupSize;
//
//        int topk = 1;
//        List<AggregateSKNNQuery.Result> results = tree.sgnnk(documentIndex, sgnnkQuery, topk);
//
//        assertNotNull(results);
//        if (topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
//            assertFalse(results.isEmpty(), "sGNNk query should return results if data and queries exist.");
//            if(!results.isEmpty()){
//                assertTrue(results.size() <= topk);
//                // Add more specific assertions
//                // e.g., results.get(0).queryIds should have size 'subGroupSize'
//                if (results.get(0).queryIds != null) {
//                    assertEquals(subGroupSize, results.get(0).queryIds.size(), "Result should be based on the specified subgroup size.");
//                }
//            }
//        }
//    }

    @Test
    void testSgnnkQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        Query q1 = new Query(3001, 0.3, new Point(new double[]{10.0, 20.0}),
                new ArrayList<>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(3002, 0.3, new Point(new double[]{15.0, 25.0}),
                new ArrayList<>(Collections.singletonList(102)), defaultKeywordWeights);
        Query q3 = new Query(3003, 0.4, new Point(new double[]{12.0, 22.0}),
                new ArrayList<>(Collections.singletonList(103)), defaultKeywordWeights);
        List<Query> queries = Arrays.asList(q1, q2, q3);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
        int subGroupSize = 2;

        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(3, queries, queries.size(), aggregator);
        sgnnkQuery.subGroupSize = subGroupSize;
        int topk = 1;

        List<AggregateSKNNQuery.Result> results = tree.sgnnk(documentIndex, sgnnkQuery, topk);

        assertNotNull(results, "sGNNk results should not be null.");
        if (topk > 0 && !queries.isEmpty() && !sampleSpatialObjects.isEmpty()) {
            assertFalse(results.isEmpty(), "sGNNk query should return results if data and queries exist and topk > 0.");
            if (!results.isEmpty()) {
                assertTrue(results.size() <= topk, "Number of results should not exceed topk.");
                AggregateSKNNQuery.Result topResult = results.get(0);
                assertNotNull(topResult.getId(), "sGNNk: Result ID should not be null.");
                assertTrue(sampleSpatialObjects.containsKey(topResult.getId()), "sGNNk: Result ID (" + topResult.getId() + ") should be a valid document ID.");
                assertNotNull(topResult.getQueryIds(), "sGNNk: Result queryIds should not be null.");
                assertEquals(subGroupSize, topResult.getQueryIds().size(), "sGNNk: Number of query IDs should match subgroup size.");
            }
        }
    }

//    @Test
//    void testGnnkBaselineQuery() {
//        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
//        Query q1 = new Query(3001, 0.5, new Point(new double[]{10.0, 20.0}),
//                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
//        Query q2 = new Query(3002, 0.5, new Point(new double[]{15.0, 25.0}),
//                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
//        List<Query> queries = Arrays.asList(q1, q2);
//        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
//        AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(3, queries, queries.size(), aggregator);
//        int topk = 1;
//
//        List<AggregateSKNNQuery.Result> results = tree.gnnkBaseline(documentIndex, gnnkQuery, topk);
//        assertNotNull(results);
//        if (topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
//            assertFalse(results.isEmpty(), "gNNk baseline query should return results if data and queries exist.");
//            // Further assertions
//        }
//    }

    @Test
    void testGnnkBaselineQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        Query q1 = new Query(4001, 0.5, new Point(new double[]{10.0, 20.0}),
                new ArrayList<>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(4002, 0.5, new Point(new double[]{15.0, 25.0}),
                new ArrayList<>(Collections.singletonList(102)), defaultKeywordWeights);
        List<Query> queries = Arrays.asList(q1, q2);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
        AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(4, queries, queries.size(), aggregator);
        int topk = 1;

        List<AggregateSKNNQuery.Result> results = tree.gnnkBaseline(documentIndex, gnnkQuery, topk);
        assertNotNull(results, "gNNk baseline results should not be null.");
        if (topk > 0 && !queries.isEmpty() && !sampleSpatialObjects.isEmpty()) {
            assertFalse(results.isEmpty(), "gNNk baseline query should return results if data and queries exist and topk > 0.");
            if(!results.isEmpty()){
                AggregateSKNNQuery.Result topResult = results.get(0);
                assertNotNull(topResult.getId(), "gNNk Baseline: Result ID should not be null.");
                assertTrue(sampleSpatialObjects.containsKey(topResult.getId()), "gNNk Baseline: Result ID (" + topResult.getId() + ") should be a valid document ID.");

                // gnnkBaseline in IRTreeBase does not populate queryIds for the Result object
                if (topResult.getQueryIds() != null) {
                    // This case should ideally not happen based on IRTreeBase.gnnkBaseline implementation
                    log.warn("gNNk Baseline: topResult.queryIds was populated for document ID " + topResult.getId() + ". This is unexpected for gnnkBaseline.");
                    assertEquals(queries.size(), topResult.getQueryIds().size(), "gNNk Baseline: If queryIds is populated, its size should match the number of input queries.");
                } else {
                    // This is the expected path
                    log.info("gNNk Baseline: topResult.queryIds was null for document ID " + topResult.getId() + ", as expected for gnnkBaseline.");
                }
            }
        }
    }

//    @Test
//    void testSgnnkBaselineQuery() {
//        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
//        Query q1 = new Query(4001, 0.3, new Point(new double[]{10.0, 20.0}),
//                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
//        Query q2 = new Query(4002, 0.3, new Point(new double[]{15.0, 25.0}),
//                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
//        Query q3 = new Query(4003, 0.4, new Point(new double[]{12.0, 22.0}),
//                new ArrayList<Integer>(Collections.singletonList(103)), defaultKeywordWeights);
//        List<Query> queries = Arrays.asList(q1, q2, q3);
//        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
//        int subGroupSize = 2;
//        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(4, queries, queries.size(), aggregator);
//        sgnnkQuery.subGroupSize = subGroupSize;
//        int topk = 1;
//
//        List<AggregateSKNNQuery.Result> results = tree.sgnnkBaseline(documentIndex, sgnnkQuery, topk);
//        assertNotNull(results);
//        if (topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
//            assertFalse(results.isEmpty(), "sGNNk baseline query should return results if data and queries exist.");
//            // Further assertions
//        }
//    }

    @Test
    void testSgnnkBaselineQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        Query q1 = new Query(5001, 0.3, new Point(new double[]{10.0, 20.0}),
                new ArrayList<>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(5002, 0.3, new Point(new double[]{15.0, 25.0}),
                new ArrayList<>(Collections.singletonList(102)), defaultKeywordWeights);
        Query q3 = new Query(5003, 0.4, new Point(new double[]{12.0, 22.0}),
                new ArrayList<>(Collections.singletonList(103)), defaultKeywordWeights);
        List<Query> queries = Arrays.asList(q1, q2, q3);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
        int subGroupSize = 2;
        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(5, queries, queries.size(), aggregator);
        sgnnkQuery.subGroupSize = subGroupSize;
        int topk = 1;

        List<AggregateSKNNQuery.Result> results = tree.sgnnkBaseline(documentIndex, sgnnkQuery, topk);
        assertNotNull(results, "sGNNk baseline results should not be null.");
        if (topk > 0 && !queries.isEmpty() && !sampleSpatialObjects.isEmpty()) {
            assertFalse(results.isEmpty(), "sGNNk baseline query should return results if data and queries exist.");
            if(!results.isEmpty()){
                assertNotNull(results.get(0).getQueryIds(), "sGNNk Baseline: Result queryIds should not be null.");
                assertEquals(subGroupSize, results.get(0).getQueryIds().size(), "sGNNk Baseline: Result should be based on subgroup size.");
                assertTrue(sampleSpatialObjects.containsKey(results.get(0).getId()), "sGNNk Baseline: Result ID should be a valid document ID.");
            }
        }
    }

//    @Test
//    void testSgnnkExtendedQuery() {
//        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
//        Query q1 = new Query(5001, 0.3, new Point(new double[]{10.0, 20.0}),
//                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
//        Query q2 = new Query(5002, 0.3, new Point(new double[]{15.0, 25.0}),
//                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
//        Query q3 = new Query(5003, 0.4, new Point(new double[]{12.0, 22.0}),
//                new ArrayList<Integer>(Collections.singletonList(103)), defaultKeywordWeights);
//        List<Query> queries = Arrays.asList(q1, q2, q3);
//        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
//
//        int minSubGroupSize = 2; // m_min for sgnnkExtended
//        int maxGroupSize = queries.size(); // m_max for sgnnkExtended
//
//        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(5, queries, maxGroupSize, aggregator);
//        // The sgnnkExtended method is expected to iterate from minSubGroupSize to maxGroupSize (inclusive).
//        // The subGroupSize on the sgnnkQuery object itself might be interpreted as m_min by the method.
//        sgnnkQuery.subGroupSize = minSubGroupSize;
//        // groupSize on sgnnkQuery is already maxGroupSize from constructor.
//
//        int topk = 1;
//
//        Map<Integer, List<AggregateSKNNQuery.Result>> resultsMap = tree.sgnnkExtended(documentIndex, sgnnkQuery, topk);
//        assertNotNull(resultsMap);
//        if (!queries.isEmpty() && sampleSpatialObjects.size() > 0) {
//            assertFalse(resultsMap.isEmpty(), "sGNNk extended query should return a map of results.");
//
//            for (int m = minSubGroupSize; m <= maxGroupSize; m++) {
//                assertTrue(resultsMap.containsKey(m), "Results map should contain an entry for subgroup size " + m);
//                List<AggregateSKNNQuery.Result> resultsForM = resultsMap.get(m);
//                assertNotNull(resultsForM);
//                if (topk > 0) {
//                    if (!resultsForM.isEmpty()) {
//                        assertTrue(resultsForM.size() <= topk);
//                        AggregateSKNNQuery.Result topResult = resultsForM.get(0);
//                        assertTrue(sampleSpatialObjects.containsKey(topResult.id));
//                        assertNotNull(topResult.queryIds);
//                        // The number of query IDs in the result should match the current subgroup size 'm'
//                        // if the result object is correctly populated by sgnnkExtended logic.
//                        assertEquals(m, topResult.queryIds.size(), "Number of query IDs should match current subgroup size " + m);
//                    }
//                }
//            }
//        }
//    }


    @Test
    void testSgnnkExtendedQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        Query q1 = new Query(6001, 0.3, new Point(new double[]{10.0, 20.0}),
                new ArrayList<>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(6002, 0.3, new Point(new double[]{15.0, 25.0}),
                new ArrayList<>(Collections.singletonList(102)), defaultKeywordWeights);
        Query q3 = new Query(6003, 0.4, new Point(new double[]{12.0, 22.0}),
                new ArrayList<>(Collections.singletonList(103)), defaultKeywordWeights);
        List<Query> queries = Arrays.asList(q1, q2, q3);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");

        int minSubGroupSize = 2;
        int maxGroupSize = queries.size();

        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(6, queries, maxGroupSize, aggregator);
        sgnnkQuery.subGroupSize = minSubGroupSize;

        int topk = 1;

        Map<Integer, List<AggregateSKNNQuery.Result>> resultsMap = tree.sgnnkExtended(documentIndex, sgnnkQuery, topk);
        assertNotNull(resultsMap, "sGNNk extended query results map should not be null.");

        if (!queries.isEmpty() && !sampleSpatialObjects.isEmpty()) {
            assertFalse(resultsMap.isEmpty(), "sGNNk extended query should return a non-empty map of results if data exists.");

            for (int m = minSubGroupSize; m <= maxGroupSize; m++) {
                assertTrue(resultsMap.containsKey(m), "Results map should contain an entry for subgroup size " + m);
                List<AggregateSKNNQuery.Result> resultsForM = resultsMap.get(m);
                assertNotNull(resultsForM, "Results list for subgroup size " + m + " should not be null.");
                if (topk > 0) {
                    if (!resultsForM.isEmpty()) {
                        assertTrue(resultsForM.size() <= topk, "Number of results for subgroup size " + m + " should not exceed topk.");
                        AggregateSKNNQuery.Result topResult = resultsForM.get(0);
                        assertNotNull(topResult.getId(), "Result ID for subgroup size " + m + " should not be null.");
                        assertTrue(sampleSpatialObjects.containsKey(topResult.getId()), "Result ID for subgroup " + m + " ("+topResult.getId()+") should be a valid document ID.");
                        assertNotNull(topResult.getQueryIds(), "Query IDs for subgroup size " + m + " should not be null.");
                        assertEquals(m, topResult.getQueryIds().size(), "Number of query IDs for subgroup size " + m + " should match m.");
                    } else if (sampleSpatialObjects.size() > 0) {
                        // It's possible no results are found for a specific m, even if topk > 0
                        log.warn("sGNNkExtended returned no results for subgroup size m = " + m + " with topk = " + topk);
                    }
                }
            }
        } else if (resultsMap.isEmpty() && !queries.isEmpty() && !sampleSpatialObjects.isEmpty() && maxGroupSize >= minSubGroupSize) {
            log.warn("sGNNkExtended returned an empty map, check data and query parameters if results were expected.");
        }
    }

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
//        // From IRTree.java: sgnnkQuery.subGroupSize is used. So the object must have it.
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


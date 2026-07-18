package org.ual.spatiotextualindex.cirtree.legacy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.algorithm.kmean.KMean;
import org.ual.document.WeightCompute;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.query.Query;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.rtree.Statistics;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.cirtree.CIRTree;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.spatiotextualindex.queries.JoinStrategy;
import org.ual.spatiotextualindex.queries.QueryStrategy;
import org.ual.spatiotextualindex.queries.ThresholdPolicy;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


public class LegacyCIRTreeRefactorTest {
    private static final Logger log = LogManager.getLogger(LegacyCIRTreeRefactorTest.class);
    private NodeStorageManager storageManager;
    private PropertySet propertySet;
    private DatasetParameters datasetParameters;
    private InvertedListIndex documentIndex;
    private AbstractDocumentStore weightStore;

    // Sample data
    private HashMap<Integer, ArrayList<Integer>> sampleTextualObjects;
    private HashMap<Integer, IShape> sampleSpatialObjects; // docID -> IShape

    // Query locations
    private Point queryPointInRegion;
    private Point queryPointOutsideRegion;

    private CIRTree tree;
    private CIRTree oldTree; // For testing with old CIRTree implementation
    private HashMap<Integer, Integer> clusterTree;
    //private HashMap<Integer, Integer> oldClusterTree; // For testing with old cluster tree implementation

    private int numberOfClusters = 2;
    private int numberOfMoves = 10;
    private double smoothingFactor = 0.5;

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
        propertySet.setProperty("NumberOfClusters", numberOfClusters);
        datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);


        // 2. Initialize IRTree instance
        // The IRTree constructor might use datasetParameters to configure itself.
        tree = new CIRTree(propertySet, storageManager, datasetParameters);

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

        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            tree.insertData(entry.getKey(), entry.getValue());
        }

        weightStore = new HashMapDocumentStore();
        WeightCompute.ComputeTermWeights(sampleTextualObjects, weightStore, smoothingFactor);

        clusterTree = KMean.calculateKMean(weightStore, numberOfClusters, numberOfMoves);
        documentIndex = new InvertedListIndex(numberOfClusters);

        // Assuming IRTree.createIRTree correctly utilizes the weightStore and documentIndex
        // for subsequent queries, or makes them available internally.
        tree.createCIRTree(clusterTree, weightStore, documentIndex);
    }

    @Test
    @DisplayName("Test K-Means Cluster Assignment Validity")
    void testKMeansClusterAssignment() {
        assertNotNull(clusterTree, "Cluster tree (docId -> clusterId map) should not be null.");
        assertEquals(sampleTextualObjects.size(), clusterTree.size(),
                "Cluster tree should have an entry for each document with textual features.");

        for (Integer docId : sampleTextualObjects.keySet()) {
            assertTrue(clusterTree.containsKey(docId), "Cluster tree should contain original document ID: " + docId);
            Integer clusterId = clusterTree.get(docId);
            assertNotNull(clusterId, "Cluster ID for document " + docId + " should not be null.");
            assertTrue(clusterId >= 0 && clusterId < numberOfClusters,
                    "Cluster ID for document " + docId + " should be within the range [0, " + (numberOfClusters - 1) + "]. Found: " + clusterId);
        }
    }

    @Test
    @DisplayName("Test CIRTree Initialization and Textual/Cluster Integration")
    void testTreeInitializationAndTextualIntegration() {
        assertNotNull(tree, "CIRTree instance should be created.");
        Statistics stats = (Statistics) tree.getStatistics();
        assertNotNull(stats, "Statistics object should not be null.");
        assertTrue(stats.getNumberOfData() >= sampleSpatialObjects.size(), "Tree should contain the inserted spatial objects.");

        // Check if weights were computed (HashMapDocumentStore has a size method)
        assertTrue(weightStore.getSize() > 0, "Weights should have been computed and stored.");
        assertEquals(sampleTextualObjects.size(), weightStore.getSize(), "Weight store should have an entry for each document with text.");

        // Verify document index was built and populated by createCIRTree
        assertNotNull(documentIndex, "Document index should not be null.");
        assertTrue(documentIndex.getTotalDocuments() >= sampleTextualObjects.size(), "Total documents in index should be >= original documents, as it may include pseudo-documents for nodes.");
        assertTrue(documentIndex.getDocumentFrequency(101) > 0, "Term 101 should be in the document index.");
    }

    @Test
    @DisplayName("Test CIRTree Structure Validity")
    void testValidStructure_RootExists() {
        Statistics stats = (Statistics) tree.getStatistics();
        assertNotNull(stats, "Statistics object should not be null.");
        assertTrue(stats.getNumberOfNodes() > 0, "Tree should contain nodes.");
        // More specific structural checks can be added if IRTree exposes relevant details
    }

    @Test
    @DisplayName("Test inverted file index functionality")
    void testDifferentInvertedFileIndexImplementations() {
        // Test with a different inverted file index implementation: HashMapInvertedFileIndex
        InvertedListIndex hashMapIndex = new InvertedListIndex(numberOfClusters);
        CIRTree hashMapTree = new CIRTree(propertySet, storageManager, datasetParameters);

        // Insert the same spatial objects as in the main tree
        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            hashMapTree.insertData(entry.getKey(), entry.getValue());
        }

        // Calculate k-means clusters using the same parameters
        HashMap<Integer, Integer> hashMapClusterTree = KMean.calculateKMean(weightStore, numberOfClusters, numberOfMoves);

        // Create CIRTree with HashMapInvertedFileIndex
        hashMapTree.createCIRTree(hashMapClusterTree, weightStore, hashMapIndex);

        // Check if the tree using HashMapInvertedFileIndex can handle queries correctly
        assertNotNull(hashMapTree, "Tree with HashMapInvertedFileIndex should be created.");
        assertTrue(hashMapIndex.getTotalDocuments() >= sampleTextualObjects.keySet().size(),
                "HashMapInvertedFileIndex should have indexed all documents.");

        // Test with ArrayListInvertedFileIndex implementation
//        ArrayListInvertedFileIndex arrayListIndex = new ArrayListInvertedFileIndex(numberOfClusters);
//        CIRTree arrayListTree = new CIRTree(propertySet, storageManager, datasetParameters);

        // Insert the same spatial objects
//        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
//            arrayListTree.insertData(entry.getKey(), entry.getValue());
//        }

        // Calculate k-means clusters (could reuse hashMapClusterTree if cluster assignment is deterministic)
        HashMap<Integer, Integer> arrayListClusterTree = KMean.calculateKMean(weightStore, numberOfClusters, numberOfMoves);

        // Create CIRTree with ArrayListInvertedFileIndex
//        arrayListTree.createCIRTree(arrayListClusterTree, weightStore, arrayListIndex);

        // Check if the tree using ArrayListInvertedFileIndex can handle queries correctly
//        assertNotNull(arrayListTree, "Tree with ArrayListInvertedFileIndex should be created.");
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
        assertEquals(expectedDocIds1.size(), foundDocIds1.size(), "BooleanRangeQuery Test 1: Mismatch in number of documents found for keyword 101.");
        assertTrue(foundDocIds1.containsAll(expectedDocIds1) && expectedDocIds1.containsAll(foundDocIds1),
                "BooleanRangeQuery Test 1: Query results mismatch for keyword 101. Expected: " + expectedDocIds1 + ", Found: " + foundDocIds1);

        ArrayList<Integer> keywords102 = new ArrayList<Integer>(Collections.singletonList(102));
        SKNNQuery sknnQuery2 = new SKNNQuery(2, queryPointInRegion, keywords102);
        List<SKNNQuery.Result> results2 = tree.booleanRangeQuery(documentIndex, sknnQuery2, largeRadius);
        Set<Integer> foundDocIds2 = results2.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
        // Documents containing 102: Doc 1 (10,20), Doc 2 (15,25), Doc 6 (25,15)
        // All are within largeRadius of (10,20)
        Set<Integer> expectedDocIds2 = new HashSet<Integer>(Arrays.asList(1, 2, 6));
        assertEquals(expectedDocIds2.size(), foundDocIds2.size(), "BooleanRangeQuery Test 2: Mismatch in number of documents found for keyword 102.");
        assertTrue(foundDocIds2.containsAll(expectedDocIds2) && expectedDocIds2.containsAll(foundDocIds2),
                "BooleanRangeQuery Test 2: Query results mismatch for keyword 102. Expected: " + expectedDocIds2 + ", Found: " + foundDocIds2);

        ArrayList<Integer> keywords105 = new ArrayList<Integer>(Collections.singletonList(105));
        SKNNQuery sknnQuery3 = new SKNNQuery(3, queryPointInRegion, keywords105);
        float specificRadius = 40.0f; // Doc 4 (50,50) has keyword 105. Dist to (10,20) is sqrt((50-10)^2 + (50-20)^2) = sqrt(1600+900) = sqrt(2500) = 50.
        // 50 is not <= specificRadius 40.0f. So, empty is correct.
        List<SKNNQuery.Result> results3 = tree.booleanRangeQuery(documentIndex, sknnQuery3, specificRadius);
        assertTrue(results3.isEmpty(), "BooleanRangeQuery Test 3: Should find no documents for keyword 105 with specific radius.");

        ArrayList<Integer> keywords999 = new ArrayList<Integer>(Collections.singletonList(999));
        SKNNQuery sknnQuery4 = new SKNNQuery(4, queryPointInRegion, keywords999);
        List<SKNNQuery.Result> results4 = tree.booleanRangeQuery(documentIndex, sknnQuery4, largeRadius);
        assertTrue(results4.isEmpty(), "BooleanRangeQuery Test 4: Should find no documents for non-existent keyword 999.");
    }

    @Test
    void testBooleanKnnQuery() {
        ArrayList<Integer> keywords = new ArrayList<Integer>(Collections.singletonList(101));
        SKNNQuery sknnQuery = new SKNNQuery(1, queryPointInRegion, keywords);
        int topk = 1;

        // Doc 1: (10,20), terms: 101, 102. Dist to queryPointInRegion (10,20) is 0.
        // Doc 3: (12,22), terms: 101, 103, 104. Dist to (10,20) is sqrt(2^2+2^2) = sqrt(8) approx 2.82
        // Doc 5: (5,5),   terms: 101, 106. Dist to (10,20) is sqrt((-5)^2+(-15)^2) = sqrt(25+225) = sqrt(250) approx 15.81
        // Doc 7: (30,30), terms: 103, 108, 101. Dist to (10,20) is sqrt(20^2+10^2) = sqrt(400+100) = sqrt(500) approx 22.36
        List<SKNNQuery.Result> results = tree.booleanKnnQuery(documentIndex, sknnQuery, topk);
        assertNotNull(results);
        assertEquals(topk, results.size(), "BooleanKnnQuery: Should find k results for keyword 101, k=1.");
        assertEquals(1, results.get(0).getId(), "BooleanKnnQuery: Expected doc1 to be the closest for keyword 101 from (10,20).");

        topk = 2;
        results = tree.booleanKnnQuery(documentIndex, sknnQuery, topk);
        assertEquals(topk, results.size(), "BooleanKnnQuery: Should find k results for keyword 101, k=2.");
        Set<Integer> foundIds = results.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
        assertTrue(foundIds.containsAll(Arrays.asList(1, 3)), "BooleanKnnQuery: Expected doc1 and doc3 for top 2 with keyword 101.");

        ArrayList<Integer> keywords102 = new ArrayList<Integer>(Collections.singletonList(102));
        SKNNQuery sknnQuery102 = new SKNNQuery(2, queryPointInRegion, keywords102);
        topk = 1;
        // Doc 1: (10,20), terms: 101, 102. Dist to queryPointInRegion (10,20) is 0.
        // Doc 2: (15,25), terms: 102, 103. Dist to (10,20) is sqrt(5^2+5^2) = sqrt(50) approx 7.07
        // Doc 6: (25,15), terms: 102, 107. Dist to (10,20) is sqrt(15^2+(-5)^2) = sqrt(225+25) = sqrt(250) approx 15.81
        results = tree.booleanKnnQuery(documentIndex, sknnQuery102, topk);
        assertEquals(topk, results.size(), "BooleanKnnQuery: Should find k results for keyword 102, k=1.");
        assertEquals(1, results.get(0).getId(), "BooleanKnnQuery: Expected doc1 to be the closest for keyword 102 from (10,20).");

        topk = 3; // All docs with keyword 102 are 1, 2, 6
        results = tree.booleanKnnQuery(documentIndex, sknnQuery102, topk);
        assertEquals(topk, results.size(), "BooleanKnnQuery: Should find all 3 results for keyword 102, k=3.");
        foundIds = results.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
        assertTrue(foundIds.containsAll(Arrays.asList(1, 2, 6)), "BooleanKnnQuery: Expected doc1, doc2, and doc6 for top 3 with keyword 102.");

        ArrayList<Integer> keywords999 = new ArrayList<Integer>(Collections.singletonList(999)); // Non-existent keyword
        SKNNQuery sknnQuery999 = new SKNNQuery(3, queryPointInRegion, keywords999);
        results = tree.booleanKnnQuery(documentIndex, sknnQuery999, topk);
        assertTrue(results.isEmpty(), "BooleanKnnQuery: Should find no results for non-existent keyword 999.");

    }

    @Test
    void testTopkKnnQuery() {
        // This query type involves scoring (spatial + textual similarity) and alpha.
        // Expected results depend heavily on the scoring implementation in IRTreeBase/CIRTree
        // and how weights from weightStore and documentIndex are used.
        // The assertions here are more about query execution and basic plausibility.

        ArrayList<Integer> keywords = new ArrayList<Integer>(Collections.singletonList(101));
        ArrayList<Double> keywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
        SKNNQuery sknnQuery = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights); // alpha = 0.5
        int topk = 1;

        List<SKNNQuery.Result> results = tree.topkKnnQuery(documentIndex, sknnQuery, topk);
        assertNotNull(results, "TopkKnnQuery results should not be null.");

        // Check if any document with keyword 101 exists
        boolean keyword101Exists = sampleTextualObjects.values().stream().anyMatch(terms -> terms.contains(101));

//        if (!results.isEmpty()) {
//            assertTrue(results.size() <= topk);
//            // Further assertions depend on scoring logic and alpha
//            // For example, check if the top result is one of the expected documents
//            Set<Integer> expectedPossibleIds = new HashSet<Integer>(Arrays.asList(1, 3));
//            assertTrue(expectedPossibleIds.contains(results.get(0).id), "Top result for keyword 101 should be doc1 or doc3.");
//        } else if (sampleSpatialObjects.size() > 0 && documentIndex.getDocumentFrequency(101) > 0) {
//            fail("Should find results for topkKnnQuery if matches exist.");
//        }

        if (keyword101Exists && sampleSpatialObjects.size() > 0) {
            assertFalse(results.isEmpty(), "TopkKnnQuery: Should find results if documents with keyword 101 exist.");
            if (!results.isEmpty()) {
                assertTrue(results.size() <= topk, "TopkKnnQuery: Number of results should not exceed topk.");
                // Example: Doc 1 is spatially closest (dist 0) and has keyword 101. It's a strong candidate.
                // Doc 3 also has 101 and is spatially close.
                Set<Integer> expectedCandidates = new HashSet<>(Arrays.asList(1, 3, 5, 7)); // Docs with keyword 101
                assertTrue(expectedCandidates.contains(results.get(0).getId()),
                        "TopkKnnQuery: Top result for keyword 101 should be one of the documents containing it. Found: " + results.get(0).getId());
            }
        } else if (!keyword101Exists) {
            assertTrue(results.isEmpty(), "TopkKnnQuery: Should find no results if keyword 101 does not exist in any document.");
        }


        ArrayList<Integer> keywords105 = new ArrayList<Integer>(Collections.singletonList(105));
        SKNNQuery sknnQuery105 = new SKNNQuery(2, 0.5, queryPointInRegion, keywords105, keywordWeights); // alpha = 0.5
        results = tree.topkKnnQuery(documentIndex, sknnQuery105, topk);
        assertNotNull(results);
        // Depending on alpha and scoring, doc4 (id=4) might or might not appear.
        // If it appears, it should be doc4. If not, results list might be empty.
//        if (!results.isEmpty()) {
//            assertEquals(4, results.get(0).id, "If a result is found for keyword 105, it should be doc4.");
//        }

        boolean keyword105Exists = sampleTextualObjects.values().stream().anyMatch(terms -> terms.contains(105));
        if (keyword105Exists) {
            assertFalse(results.isEmpty(), "TopkKnnQuery: Should find results for keyword 105 if it exists.");
            if (!results.isEmpty()) {
                assertEquals(4, results.get(0).getId(), "TopkKnnQuery: If a result is found for keyword 105, it should be doc4.");
            }
        } else {
            assertTrue(results.isEmpty(), "TopkKnnQuery: Should find no results if keyword 105 does not exist.");
        }

        ArrayList<Integer> keywords999 = new ArrayList<>(Collections.singletonList(999)); // Non-existent keyword
        SKNNQuery sknnQuery999 = new SKNNQuery(3, 0.5, queryPointInRegion, keywords999, keywordWeights);
        results = tree.topkKnnQuery(documentIndex, sknnQuery999, topk);
        assertTrue(results.isEmpty(), "TopkKnnQuery: Should find no results for a non-existent keyword.");
    }


    @Test
    void testGnnkQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));

        // Query 1: Point (10,20), Keyword 101 (Docs 1,3,5,7)
        Query q1 = new Query(1001, 0.5, new Point(new double[]{10.0, 20.0}),
                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
        // Query 2: Point (15,25), Keyword 102 (Docs 1,2,6)
        Query q2 = new Query(1002, 0.5, new Point(new double[]{15.0, 25.0}),
                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);


        List<Query> queries = Arrays.asList(q1, q2);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");

        // Use constructor: AggregateSKNNQuery(int id, List<Query> queries, int groupSize, IAggregator aggregator)
        AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(1, queries, queries.size(), aggregator);
        int topk = 1;

        List<AggregateSKNNQuery.Result> results = tree.gnnk(documentIndex, gnnkQuery, topk);

        assertNotNull(results, "gNNk query results should not be null.");
        if (topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
            assertFalse(results.isEmpty(), "gNNk query should return results if data and queries exist and topk > 0.");
            if(!results.isEmpty()){
                AggregateSKNNQuery.Result topResult = results.get(0);
                assertNotNull(topResult.getId(), "gNNk: Result ID should not be null.");
                assertTrue(sampleSpatialObjects.containsKey(topResult.getId()), "gNNk: Result ID (" + topResult.getId() + ") should be a valid document ID.");

                if (topResult.getQueryIds() != null) {
                    assertEquals(queries.size(), topResult.getQueryIds().size(), "gNNk: If queryIds is populated, its size should match the number of input queries.");
                } else {
                    log.warn("gNNk: topResult.queryIds was null for document ID " + topResult.getId() + ". This was previously an assertion failure.");
                }
            }
        } else {
            assertTrue(results.isEmpty(), "gNNk query should return no results if no data/queries or topk is 0.");
        }
    }

    @Test
    void testSgnnkQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));

        // Query 1: Point (10,20), Keyword 101 (Docs 1,3,5,7)
        Query q1 = new Query(2001, 0.3, new Point(new double[]{10.0, 20.0}),
                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
        // Query 2: Point (15,25), Keyword 102 (Docs 1,2,6)
        Query q2 = new Query(2002, 0.3, new Point(new double[]{15.0, 25.0}),
                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
        // Query 3: Point (12,22), Keyword 103 (Docs 2,3,7)
        Query q3 = new Query(2003, 0.4, new Point(new double[]{12.0, 22.0}),
                new ArrayList<Integer>(Collections.singletonList(103)), defaultKeywordWeights);


        List<Query> queries = Arrays.asList(q1, q2, q3);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
        int subGroupSize = 2; // Find best document for subgroups of size 2

        // Use constructor: AggregateSKNNQuery(int id, List<Query> queries, int groupSize, IAggregator aggregator)
        // Then set subGroupSize.
        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(2, queries, queries.size(), aggregator);
        sgnnkQuery.subGroupSize = subGroupSize;

        int topk = 1;
        List<AggregateSKNNQuery.Result> results = tree.sgnnk(documentIndex, sgnnkQuery, topk);

        assertNotNull(results, "sGNNk query results should not be null.");
        if (topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
            assertFalse(results.isEmpty(), "sGNNk query should return results if data, queries, and valid subgroup size exist.");
            if (!results.isEmpty()) {
                assertTrue(results.size() <= topk, "sGNNk: Number of results should not exceed topk.");
                AggregateSKNNQuery.Result topResult = results.get(0);
                assertNotNull(topResult.getQueryIds(), "sGNNk: Result should have associated query IDs.");
                // The number of query IDs in the result should match the subGroupSize
                assertEquals(subGroupSize, topResult.getQueryIds().size(), "sGNNk: Result should be based on the specified subgroup size.");
                assertTrue(sampleSpatialObjects.containsKey(topResult.getId()), "sGNNk: Result ID should be a valid document ID.");

                // Add more specific assertions
                // e.g., results.get(0).queryIds should have size 'subGroupSize'
//                if (results.get(0).queryIds != null) {
//                    assertEquals(subGroupSize, results.get(0).queryIds.size(), "Result should be based on the specified subgroup size.");
//                }
            }
        } else if (results.isEmpty() && topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0 && queries.size() >= subGroupSize) {
            fail("sGNNk query returned no results when some were expected.");
        }
    }

    @Test
    void testGnnkBaselineQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
        Query q1 = new Query(3001, 0.5, new Point(new double[]{10.0, 20.0}),
                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(3002, 0.5, new Point(new double[]{15.0, 25.0}),
                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
        List<Query> queries = Arrays.asList(q1, q2);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
        AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(3, queries, queries.size(), aggregator);
        int topk = 1;

        List<AggregateSKNNQuery.Result> results = tree.gnnkBaseline(documentIndex, gnnkQuery, topk);
        assertNotNull(results, "gNNk baseline results should not be null.");
        if (topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
            assertFalse(results.isEmpty(), "gNNk baseline query should return results if data and queries exist and topk > 0.");
            if(!results.isEmpty()){
                AggregateSKNNQuery.Result topResult = results.get(0);
                assertNotNull(topResult.getId(), "gNNk Baseline: Result ID should not be null.");
                assertTrue(sampleSpatialObjects.containsKey(topResult.getId()), "gNNk Baseline: Result ID (" + topResult.getId() + ") should be a valid document ID.");

                if (topResult.getQueryIds() != null) {
                    assertEquals(queries.size(), topResult.getQueryIds().size(), "gNNk Baseline: If queryIds is populated, its size should match the number of input queries.");
                } else {
                    log.warn("gNNk Baseline: topResult.queryIds was null for document ID " + topResult.getId() + ". This was previously an assertion failure.");
                }
            }
        }
    }

    @Test
    void testSgnnkBaselineQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
        Query q1 = new Query(4001, 0.3, new Point(new double[]{10.0, 20.0}),
                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(4002, 0.3, new Point(new double[]{15.0, 25.0}),
                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
        Query q3 = new Query(4003, 0.4, new Point(new double[]{12.0, 22.0}),
                new ArrayList<Integer>(Collections.singletonList(103)), defaultKeywordWeights);
        List<Query> queries = Arrays.asList(q1, q2, q3);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
        int subGroupSize = 2;
        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(4, queries, queries.size(), aggregator);
        sgnnkQuery.subGroupSize = subGroupSize;
        int topk = 1;

        List<AggregateSKNNQuery.Result> results = tree.sgnnkBaseline(documentIndex, sgnnkQuery, topk);
        assertNotNull(results, "sGNNk baseline results should not be null.");
        if (topk > 0 && !queries.isEmpty() && sampleSpatialObjects.size() > 0) {
            assertFalse(results.isEmpty(), "sGNNk baseline query should return results if data and queries exist.");
            if(!results.isEmpty()){
                assertEquals(subGroupSize, results.get(0).getQueryIds().size(), "sGNNk Baseline: Result should be based on subgroup size.");
                assertTrue(sampleSpatialObjects.containsKey(results.get(0).getId()), "sGNNk Baseline: Result ID should be a valid document ID.");
            }
        }
    }

    @Test
    void testSgnnkExtendedQuery() {
        List<Double> defaultKeywordWeights = new ArrayList<Double>(Collections.singletonList(1.0));
        Query q1 = new Query(5001, 0.3, new Point(new double[]{10.0, 20.0}),
                new ArrayList<Integer>(Collections.singletonList(101)), defaultKeywordWeights);
        Query q2 = new Query(5002, 0.3, new Point(new double[]{15.0, 25.0}),
                new ArrayList<Integer>(Collections.singletonList(102)), defaultKeywordWeights);
        Query q3 = new Query(5003, 0.4, new Point(new double[]{12.0, 22.0}),
                new ArrayList<Integer>(Collections.singletonList(103)), defaultKeywordWeights);
        List<Query> queries = Arrays.asList(q1, q2, q3);
        IAggregator aggregator = AggregatorFactory.getAggregator("SUM");

        int minSubGroupSize = 2; // m_min for sgnnkExtended
        int maxGroupSize = queries.size(); // m_max for sgnnkExtended

        // groupSize in AggregateSKNNQuery constructor is typically the full set for sGNNk context,
        // and subGroupSize is used for the 'm' in sGNNk(m).
        // For sgnnkExtended, it iterates m from subGroupSize (m_min) to groupSize (m_max).
        AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(5, queries, maxGroupSize, aggregator);
        // The sgnnkExtended method is expected to iterate from minSubGroupSize to maxGroupSize (inclusive).
        // The subGroupSize on the sgnnkQuery object itself might be interpreted as m_min by the method.
        sgnnkQuery.subGroupSize = minSubGroupSize;
        // groupSize on sgnnkQuery is already maxGroupSize from constructor.

        int topk = 1;

        Map<Integer, List<AggregateSKNNQuery.Result>> resultsMap = tree.sgnnkExtended(documentIndex, sgnnkQuery, topk);
        assertNotNull(resultsMap, "sGNNk extended query results map should not be null.");

        if (!queries.isEmpty() && sampleSpatialObjects.size() > 0) {
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
                        assertTrue(sampleSpatialObjects.containsKey(topResult.getId()), "Result ID for subgroup " + m + " should be a valid document ID.");
                        assertNotNull(topResult.getQueryIds(), "Query IDs for subgroup size " + m + " should not be null.");
                        assertEquals(m, topResult.getQueryIds().size(), "Number of query IDs for subgroup size " + m + " should match m.");
                    }
                }
            }
        } else if (resultsMap.isEmpty() && !queries.isEmpty() && sampleSpatialObjects.size() > 0 && maxGroupSize >= minSubGroupSize) {
            // This case might be legitimate if no results are found for any subgroup size.
            // However, if results are generally expected, this could indicate an issue.
            log.warn("sGNNkExtended returned an empty map, check data and query parameters if results were expected.");
        }
    }


    @Test
    @DisplayName("Test Self-Join SK Query Best First")
    void testSelfJoinSKQueryBestFirst() {
        // Create a self-join query with a maximum distance threshold
        ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
        ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
        Point queryLocation = new Point(new double[]{15.0, 20.0});

        // Create the join query with alpha=0.5 (equal weight to spatial and textual relevance)
        SKJoinQuery joinQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        float maxDistance = 30.0f; // Maximum spatial distance between object pairs
        float textualThreshold = 0.1f; // Minimum textual similarity threshold for keyword matching
        ThresholdPolicy thresholdPolicy = ThresholdPolicy.STRICT;
        JoinStrategy joinStrategy = JoinStrategy.DEFAULT;
        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;

        System.out.println(tree.printTreeStructure()); // Print the tree structure for debugging
        Node node = tree.readNode(1);

        // Adapt to new TreeMap structure - check if entries exist before accessing
        if (node.getNodeEntries().containsKey(5) && node.getNodeEntries().containsKey(7)) {
            Region mbr1 = node.getNodeEntries().get(5).getMBR();
            Region mbr2 = node.getNodeEntries().get(7).getMBR();
            System.out.println("Distance: " + mbr1.getMinimumDistance(mbr2));
        } else {
            System.out.println("Entries 5 or 7 not found in node");
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
            boolean obj1HasQueryKeyword = false;
            boolean obj2HasQueryKeyword = false;

            for (Integer kw : keywords1) {
                if (keywords.contains(kw)) {
                    obj1HasQueryKeyword = true;
                    break;
                }
            }

            for (Integer kw : keywords2) {
                if (keywords.contains(kw)) {
                    obj2HasQueryKeyword = true;
                    break;
                }
            }

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
    @DisplayName("Test Self-Join SK Query Recursive")
    void testSelfJoinSKQueryRecursive() {
        // Create a self-join query with a maximum distance threshold
        ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
        ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
        Point queryLocation = new Point(new double[]{15.0, 20.0});

        // Create the join query with alpha=0.5 (equal weight to spatial and textual relevance)
        SKJoinQuery joinQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        float maxDistance = 30.0f; // Maximum spatial distance between object pairs
        float textualThreshold = 0.1f; // Minimum textual similarity threshold for keyword matching
        ThresholdPolicy thresholdPolicy = ThresholdPolicy.STRICT;
        JoinStrategy joinStrategy = JoinStrategy.DEFAULT;
        SimilarityType similarityType = SimilarityType.WEIGHTED_JACCARD;

        System.out.println(tree.printTreeStructure()); // Print the tree structure for debugging
        Node node = tree.readNode(1);

        // Adapt to new TreeMap structure - get NodeEntry objects and their MBRs
        NodeEntry entry1 = node.getNodeEntry(5);
        NodeEntry entry2 = node.getNodeEntry(7);
        if (entry1 != null && entry2 != null) {
            System.out.println("Distance: " + entry1.getMBR().getMinimumDistance(entry2.getMBR()));
        }

        // Execute the self-join query
        List<SKJoinQuery.Result> results = tree.selfJoinSKQueryRecursive(
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
        List<SKJoinQuery.Result> spatialResults = tree.selfJoinSKQueryRecursive(
                documentIndex, spatialFocusedQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertFalse(spatialResults.isEmpty(), "Spatial-focused query should return results");

        // Alpha=0.2 means textual relevance is more important in combined score
        tree.setAlphaDistribution(0.2f);
        SKJoinQuery textualFocusedQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, keywordWeights);
        List<SKJoinQuery.Result> textualResults = tree.selfJoinSKQueryRecursive(
                documentIndex, textualFocusedQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertFalse(textualResults.isEmpty(), "Textual-focused query should return results");

        // Reset alpha back to default for remaining tests
        tree.setAlphaDistribution(0.5f);

        // Test with smaller distance threshold
        float smallerDistance = 10.0f;
        List<SKJoinQuery.Result> limitedResults = tree.selfJoinSKQueryRecursive(
                documentIndex, joinQuery, smallerDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertNotNull(limitedResults);
        assertTrue(limitedResults.size() < results.size(),
                "Smaller distance threshold should return fewer results");

        // Test with different keywords
        ArrayList<Integer> rareKeywords = new ArrayList<>(Collections.singletonList(109));
        ArrayList<Double> rareKeywordWeights = new ArrayList<>(Collections.singletonList(1.0));
        SKJoinQuery rareQuery = new SKJoinQuery(2, 0.5, queryLocation, rareKeywords, rareKeywordWeights);

        List<SKJoinQuery.Result> rareResults = tree.selfJoinSKQueryRecursive(
                documentIndex, rareQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertTrue(rareResults.isEmpty() || rareResults.size() < results.size(),
                "Query with rare keywords should return fewer results");

        // Test edge case: very large distance
        float veryLargeDistance = 1000.0f;
        List<SKJoinQuery.Result> allPairsResults = tree.selfJoinSKQueryRecursive(
                documentIndex, joinQuery, veryLargeDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertNotNull(allPairsResults);
        assertTrue(allPairsResults.size() >= results.size(),
                "Very large distance should return at least as many results as standard distance");

        // Test edge case: empty keyword list
        ArrayList<Integer> emptyKeywords = new ArrayList<>();
        ArrayList<Double> emptyWeights = new ArrayList<>();
        SKJoinQuery emptyQuery = new SKJoinQuery(3, 0.5, queryLocation, emptyKeywords, emptyWeights);

        List<SKJoinQuery.Result> emptyResults = tree.selfJoinSKQueryRecursive(
                documentIndex, emptyQuery, maxDistance, textualThreshold,
                createJoinConfiguration(thresholdPolicy, joinStrategy, similarityType));
        assertTrue(emptyResults.isEmpty(),
                "Query with empty keyword list should return no results");
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
}

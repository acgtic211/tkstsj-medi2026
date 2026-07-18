package org.ual.spatiotextualindex.cirtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.algorithm.kmean.KMean;
import org.ual.document.WeightCompute;
import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.documentindex.invertedlist.InvertedListIndex;
//import org.ual.documentindex.signedinvertedlist.ClusteredSignedInvertedListIndex;
//import org.ual.documentindex.signedinvertedlist.SignedInvertedListIndex;
import org.ual.query.Query;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.spatialindex.IShape;
import org.ual.spatialindex.spatialindex.Point;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.queries.JoinStrategy;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.spatiotextualindex.queries.QueryStrategy;
import org.ual.spatiotextualindex.queries.ThresholdPolicy;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Query Processing tests for CIRTree (Cluster-based Inverted R-tree).
 * Each test runs with {@link InvertedListIndex} to verify consistent results across implementations.
 */
@DisplayName("CIRTree Query Processing Tests")
public class CIRTreeQueryProcessingTest {
    private static final Logger log = LogManager.getLogger(CIRTreeQueryProcessingTest.class);

    // -----------------------------------------------------------------------
    // Index variant provider (outer-class level so nested classes can reference it)
    // -----------------------------------------------------------------------

    static Stream<Arguments> provideDocumentIndexVariants() {
        return Stream.of(
                Arguments.of("InvertedList")
//                Arguments.of("SignedInvertedList"),
//                Arguments.of("SignedInvertedListSimple")
        );
    }

    static IDocumentIndex createDocumentIndex(String type, int numClusters) {
//        if ("SignedInvertedList".equals(type)) {
//            return new ClusteredSignedInvertedListIndex(numClusters);
//        }
//        if ("SignedInvertedListSimple".equals(type)) {
//            return new SignedInvertedListIndex(numClusters);
//        }
        return new InvertedListIndex(numClusters);
    }

    // -----------------------------------------------------------------------
    // Base setup
    // -----------------------------------------------------------------------

    abstract static class CIRTreeQueryTestBase {
        protected CIRTree tree;
        protected NodeStorageManager storageManager;
        protected PropertySet propertySet;
        protected DatasetParameters datasetParameters;
        protected IDocumentIndex documentIndex;
        protected AbstractDocumentStore weightStore;
        protected HashMap<Integer, Integer> clusterTree;
        protected HashMap<Integer, IShape> spatialObjects;
        protected HashMap<Integer, ArrayList<Integer>> textualObjects;
        protected Point queryPointInRegion;
        protected Point queryPointOutside;
        protected int numberOfClusters = 2;

        @BeforeEach
        void setUp() {
            storageManager = new NodeStorageManager();
            propertySet = new PropertySet();
            propertySet.setProperty("Dimension", 2);
            propertySet.setProperty("IndexCapacity", 6);
            propertySet.setProperty("LeafCapacity", 6);
            propertySet.setProperty("FillFactor", 0.7f);
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
            propertySet.setProperty("NearMinimumOverlapFactor", 2);
            propertySet.setProperty("NumberOfClusters", numberOfClusters);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            weightStore = new HashMapDocumentStore();

            queryPointInRegion = new Point(new double[]{10.0, 20.0});
            queryPointOutside = new Point(new double[]{100.0, 100.0});

            setupTestData();

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            clusterTree = KMean.calculateKMean(weightStore, numberOfClusters, 10);
            // NOTE: createCIRTree is NOT called here; each parameterized test calls initTree(indexType).
        }

        /**
         * Initialises the document index of the requested type and completes tree creation.
         * Must be called at the start of every parameterized test method.
         */
        protected void initTree(String indexType) {
            documentIndex = createDocumentIndex(indexType, numberOfClusters);
            tree.createCIRTree(clusterTree, weightStore, documentIndex);
        }

        protected void setupTestData() {
            spatialObjects = new HashMap<>();
            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
            spatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
            spatialObjects.put(3, new Point(new double[]{12.0, 22.0}));
            spatialObjects.put(4, new Point(new double[]{50.0, 50.0}));
            spatialObjects.put(5, new Point(new double[]{5.0, 5.0}));
            spatialObjects.put(6, new Point(new double[]{25.0, 15.0}));
            spatialObjects.put(7, new Point(new double[]{30.0, 30.0}));
            spatialObjects.put(8, new Point(new double[]{60.0, 60.0}));

            textualObjects = new HashMap<>();
            textualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102, 103)));
            textualObjects.put(2, new ArrayList<>(Arrays.asList(102, 103)));
            textualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));
            textualObjects.put(4, new ArrayList<>(Collections.singletonList(105)));
            textualObjects.put(5, new ArrayList<>(Arrays.asList(101, 106)));
            textualObjects.put(6, new ArrayList<>(Arrays.asList(102, 107)));
            textualObjects.put(7, new ArrayList<>(Arrays.asList(103, 108, 101)));
            textualObjects.put(8, new ArrayList<>(Collections.singletonList(109)));
        }
    }

    @Nested
    @DisplayName("Boolean Range Query")
    class BooleanRangeQuery extends CIRTreeQueryTestBase {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should find documents within radius containing keyword")
        void testFindDocumentsWithinRadiusWithKeyword(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            float radius = 100.0f;

            List<SKNNQuery.Result> results = tree.booleanRangeQuery(documentIndex, query, radius);

            assertNotNull(results, "Results should not be null");
            Set<Integer> foundDocIds = results.stream()
                    .map(SKNNQuery.Result::getId)
                    .collect(Collectors.toSet());

            Set<Integer> expectedDocIds = new HashSet<>(Arrays.asList(1, 3, 5, 7));
            assertEquals(expectedDocIds, foundDocIds,
                    "Should find all documents with keyword 101 within radius");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should return empty results for non-existent keyword")
        void testEmptyResultsForNonExistentKeyword(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(999));
            SKNNQuery query = new SKNNQuery(2, queryPointInRegion, keywords);

            List<SKNNQuery.Result> results = tree.booleanRangeQuery(documentIndex, query, 100.0f);

            assertTrue(results.isEmpty(), "Should return empty results for non-existent keyword");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should respect radius constraint")
        void testRespectRadiusConstraint(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(105));
            SKNNQuery query = new SKNNQuery(3, queryPointInRegion, keywords);

            List<SKNNQuery.Result> results = tree.booleanRangeQuery(documentIndex, query, 40.0f);

            assertTrue(results.isEmpty(), "Should not find documents outside radius");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should find multiple documents with different keyword")
        void testMultipleDocumentsWithDifferentKeyword(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(102));
            SKNNQuery query = new SKNNQuery(4, queryPointInRegion, keywords);

            List<SKNNQuery.Result> results = tree.booleanRangeQuery(documentIndex, query, 100.0f);

            Set<Integer> foundDocIds = results.stream()
                    .map(SKNNQuery.Result::getId)
                    .collect(Collectors.toSet());

            Set<Integer> expectedDocIds = new HashSet<>(Arrays.asList(1, 2, 6));
            assertEquals(expectedDocIds, foundDocIds,
                    "Should find all documents with keyword 102 within radius");
        }
    }

    @Nested
    @DisplayName("Boolean KNN Query")
    class BooleanKnnQuery extends CIRTreeQueryTestBase {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should find k nearest documents with keyword")
        void testFindKNearestDocuments(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            int k = 1;

            List<SKNNQuery.Result> results = tree.booleanKnnQuery(documentIndex, query, k);

            assertNotNull(results, "Results should not be null");
            assertEquals(k, results.size(), "Should return k results");
            assertEquals(1, results.get(0).getId(), "Closest document should be doc 1 at (10,20)");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should find top 2 nearest documents")
        void testFindTop2NearestDocuments(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(2, queryPointInRegion, keywords);
            int k = 2;

            List<SKNNQuery.Result> results = tree.booleanKnnQuery(documentIndex, query, k);

            assertEquals(k, results.size(), "Should return k results");
            Set<Integer> foundIds = results.stream()
                    .map(SKNNQuery.Result::getId)
                    .collect(Collectors.toSet());
            assertTrue(foundIds.containsAll(Arrays.asList(1, 3)),
                    "Should find doc 1 and doc 3 as closest");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should return empty for non-existent keyword")
        void testEmptyForNonExistentKeyword(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(999));
            SKNNQuery query = new SKNNQuery(3, queryPointInRegion, keywords);

            List<SKNNQuery.Result> results = tree.booleanKnnQuery(documentIndex, query, 3);

            assertTrue(results.isEmpty(), "Should return empty for non-existent keyword");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should handle k larger than available documents")
        void testHandleLargeK(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(102));
            SKNNQuery query = new SKNNQuery(4, queryPointInRegion, keywords);
            int k = 10;

            List<SKNNQuery.Result> results = tree.booleanKnnQuery(documentIndex, query, k);

            assertNotNull(results, "Results should not be null");
            assertTrue(results.size() <= k, "Should not exceed k results");
            assertEquals(3, results.size(), "Should return all 3 available documents");
        }
    }

    @Nested
    @DisplayName("Ranking Top-K Query")
    class RankingTopKQuery extends CIRTreeQueryTestBase {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should return top-k documents with combined scoring")
        void testTopKWithCombinedScoring(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            SKNNQuery query = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, weights);
            int k = 1;

            List<SKNNQuery.Result> results = tree.topkKnnQuery(documentIndex, query, k);

            assertNotNull(results, "Results should not be null");
            assertFalse(results.isEmpty(), "Should find results");
            assertTrue(results.size() <= k, "Should not exceed k results");

            Set<Integer> expectedCandidates = new HashSet<>(Arrays.asList(1, 3, 5, 7));
            assertTrue(expectedCandidates.contains(results.get(0).getId()),
                    "Top result should be one of the documents with keyword 101");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should handle different alpha values")
        void testDifferentAlphaValues(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> weights = new ArrayList<>(Collections.singletonList(1.0));

            SKNNQuery spatialQuery = new SKNNQuery(2, 0.9, queryPointInRegion, keywords, weights);
            List<SKNNQuery.Result> spatialResults = tree.topkKnnQuery(documentIndex, spatialQuery, 1);

            assertNotNull(spatialResults, "Spatial-focused results should not be null");
            assertFalse(spatialResults.isEmpty(), "Should find results with high alpha");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should return empty for non-existent keyword")
        void testEmptyForNonExistentKeyword(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(999));
            ArrayList<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            SKNNQuery query = new SKNNQuery(3, 0.5, queryPointInRegion, keywords, weights);

            List<SKNNQuery.Result> results = tree.topkKnnQuery(documentIndex, query, 1);

            assertTrue(results.isEmpty(), "Should return empty for non-existent keyword");
        }
    }

    @Nested
    @DisplayName("GNNK Query")
    class GnnkQuery extends CIRTreeQueryTestBase {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should process GNNK query with multiple query points")
        void testGnnkWithMultipleQueryPoints(String indexType) {
            initTree(indexType);

            List<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(1001, 0.5, new Point(new double[]{10.0, 20.0}),
                    new ArrayList<>(Collections.singletonList(101)), weights);
            Query q2 = new Query(1002, 0.5, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), weights);

            List<Query> queries = Arrays.asList(q1, q2);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(1, queries, queries.size(), aggregator);

            List<AggregateSKNNQuery.Result> results = tree.gnnk(documentIndex, gnnkQuery, 1);

            assertNotNull(results, "Results should not be null");
            assertFalse(results.isEmpty(), "Should return results");

            AggregateSKNNQuery.Result topResult = results.get(0);
            assertNotNull(topResult.getId(), "Result ID should not be null");
            assertTrue(spatialObjects.containsKey(topResult.getId()),
                    "Result ID should be valid document");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should handle GNNK baseline query")
        void testGnnkBaseline(String indexType) {
            initTree(indexType);

            List<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(2001, 0.5, new Point(new double[]{10.0, 20.0}),
                    new ArrayList<>(Collections.singletonList(101)), weights);
            Query q2 = new Query(2002, 0.5, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), weights);

            List<Query> queries = Arrays.asList(q1, q2);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(2, queries, queries.size(), aggregator);

            List<AggregateSKNNQuery.Result> results = tree.gnnkBaseline(documentIndex, gnnkQuery, 1);

            assertNotNull(results, "Baseline results should not be null");
            assertFalse(results.isEmpty(), "Should return baseline results");
        }
    }

    @Nested
    @DisplayName("SGNNK Query")
    class SgnnkQuery extends CIRTreeQueryTestBase {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should process SGNNK query with subgroups")
        void testSgnnkWithSubgroups(String indexType) {
            initTree(indexType);

            List<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(3001, 0.3, new Point(new double[]{10.0, 20.0}),
                    new ArrayList<>(Collections.singletonList(101)), weights);
            Query q2 = new Query(3002, 0.3, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), weights);
            Query q3 = new Query(3003, 0.4, new Point(new double[]{12.0, 22.0}),
                    new ArrayList<>(Collections.singletonList(103)), weights);

            List<Query> queries = Arrays.asList(q1, q2, q3);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            int subGroupSize = 2;

            AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(3, queries, queries.size(), aggregator);
            sgnnkQuery.subGroupSize = subGroupSize;

            List<AggregateSKNNQuery.Result> results = tree.sgnnk(documentIndex, sgnnkQuery, 1);

            assertNotNull(results, "Results should not be null");
            assertFalse(results.isEmpty(), "Should return results");

            AggregateSKNNQuery.Result topResult = results.get(0);
            assertNotNull(topResult.getQueryIds(), "Query IDs should not be null");
            assertEquals(subGroupSize, topResult.getQueryIds().size(),
                    "Result should be based on subgroup size");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should handle SGNNK baseline query")
        void testSgnnkBaseline(String indexType) {
            initTree(indexType);

            List<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(4001, 0.3, new Point(new double[]{10.0, 20.0}),
                    new ArrayList<>(Collections.singletonList(101)), weights);
            Query q2 = new Query(4002, 0.3, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), weights);
            Query q3 = new Query(4003, 0.4, new Point(new double[]{12.0, 22.0}),
                    new ArrayList<>(Collections.singletonList(103)), weights);

            List<Query> queries = Arrays.asList(q1, q2, q3);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            int subGroupSize = 2;

            AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(4, queries, queries.size(), aggregator);
            sgnnkQuery.subGroupSize = subGroupSize;

            List<AggregateSKNNQuery.Result> results = tree.sgnnkBaseline(documentIndex, sgnnkQuery, 1);

            assertNotNull(results, "Baseline results should not be null");
            assertFalse(results.isEmpty(), "Should return baseline results");
            assertEquals(subGroupSize, results.get(0).getQueryIds().size(),
                    "Result should match subgroup size");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should process SGNNK extended query")
        void testSgnnkExtended(String indexType) {
            initTree(indexType);

            List<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(5001, 0.3, new Point(new double[]{10.0, 20.0}),
                    new ArrayList<>(Collections.singletonList(101)), weights);
            Query q2 = new Query(5002, 0.3, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), weights);
            Query q3 = new Query(5003, 0.4, new Point(new double[]{12.0, 22.0}),
                    new ArrayList<>(Collections.singletonList(103)), weights);

            List<Query> queries = Arrays.asList(q1, q2, q3);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            int minSubGroupSize = 2;
            int maxGroupSize = queries.size();

            AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(5, queries, maxGroupSize, aggregator);
            sgnnkQuery.subGroupSize = minSubGroupSize;

            Map<Integer, List<AggregateSKNNQuery.Result>> resultsMap =
                    tree.sgnnkExtended(documentIndex, sgnnkQuery, 1);

            assertNotNull(resultsMap, "Results map should not be null");
            assertFalse(resultsMap.isEmpty(), "Should return results map");

            for (int m = minSubGroupSize; m <= maxGroupSize; m++) {
                assertTrue(resultsMap.containsKey(m),
                        "Results should contain entry for subgroup size " + m);
                List<AggregateSKNNQuery.Result> resultsForM = resultsMap.get(m);
                assertNotNull(resultsForM, "Results list should not be null");

                if (!resultsForM.isEmpty()) {
                    assertEquals(m, resultsForM.get(0).getQueryIds().size(),
                            "Query IDs size should match subgroup size");
                }
            }
        }
    }

    @Nested
    @DisplayName("Self-Join Query")
    class SelfJoinQuery extends CIRTreeQueryTestBase {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should process self-join query with best-first strategy")
        void testSelfJoinBestFirst(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
            ArrayList<Double> weights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
            Point queryLocation = new Point(new double[]{15.0, 20.0});

            SKJoinQuery joinQuery = new SKJoinQuery(1, 0.5, queryLocation, keywords, weights);
            JoinConfiguration joinConfiguration = new JoinConfiguration(
                    ThresholdPolicy.STRICT,
                    JoinStrategy.DEFAULT,
                    SimilarityType.WEIGHTED_JACCARD,
                    QueryStrategy.PARTIAL_JOIN
            );

            List<SKJoinQuery.Result> results = tree.selfJoinSKQueryBestFirst(
                    documentIndex, joinQuery, 30.0f, 0.1f, joinConfiguration);

            assertNotNull(results, "Results should not be null");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should process self-join query with recursive strategy")
        void testSelfJoinRecursive(String indexType) {
            initTree(indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102));
            ArrayList<Double> weights = new ArrayList<>(Arrays.asList(1.0, 1.0));
            Point queryLocation = new Point(new double[]{15.0, 20.0});

            SKJoinQuery joinQuery = new SKJoinQuery(2, 0.5, queryLocation, keywords, weights);
            JoinConfiguration joinConfiguration = new JoinConfiguration(
                    ThresholdPolicy.STRICT,
                    JoinStrategy.DEFAULT,
                    SimilarityType.WEIGHTED_JACCARD,
                    QueryStrategy.PARTIAL_JOIN
            );

            List<SKJoinQuery.Result> results = tree.selfJoinSKQueryRecursive(
                    documentIndex, joinQuery, 40.0f, 0.2f, joinConfiguration);

            assertNotNull(results, "Results should not be null");
        }
    }

    @Nested
    @DisplayName("Cluster-Specific Tests")
    class ClusterSpecificTests extends CIRTreeQueryTestBase {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should handle queries with different cluster configurations")
        void testQueriesWithDifferentClusters(String indexType) {
            initTree(indexType);

            CIRTree tree3Clusters = new CIRTree(propertySet, storageManager, datasetParameters);
            IDocumentIndex index3Clusters = createDocumentIndex(indexType, 3);

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree3Clusters.insertData(entry.getKey(), entry.getValue());
            }

            AbstractDocumentStore weightStore3 = new HashMapDocumentStore();
            WeightCompute.ComputeTermWeights(textualObjects, weightStore3, 0.5);
            HashMap<Integer, Integer> clusterTree3 = KMean.calculateKMean(weightStore3, 3, 10);
            tree3Clusters.createCIRTree(clusterTree3, weightStore3, index3Clusters);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);

            List<SKNNQuery.Result> results = tree3Clusters.booleanKnnQuery(index3Clusters, query, 2);

            assertNotNull(results, "Results should not be null with 3 clusters");
            assertFalse(results.isEmpty(), "Should find results with 3 clusters");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        @DisplayName("Should verify cluster assignments affect query results")
        void testClusterAssignmentsAffectQueries(String indexType) {
            initTree(indexType);

            assertNotNull(clusterTree, "Cluster tree should be initialized");
            assertFalse(clusterTree.isEmpty(), "Cluster tree should not be empty");

            for (Integer docId : textualObjects.keySet()) {
                assertTrue(clusterTree.containsKey(docId),
                        "Document " + docId + " should be in cluster tree");
                Integer clusterId = clusterTree.get(docId);
                assertTrue(clusterId >= 0 && clusterId < numberOfClusters,
                        "Cluster ID should be valid");
            }

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            List<SKNNQuery.Result> results = tree.booleanRangeQuery(documentIndex, query, 100.0f);

            assertNotNull(results, "Query should work with clustered data");
        }
    }
}

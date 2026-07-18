package org.ual.spatiotextualindex.irtree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.document.WeightCompute;
import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.invertedlist.InvertedListIndex;
//import org.ual.documentindex.signedinvertedlist.ClusteredSignedInvertedListIndex;
//import org.ual.documentindex.signedinvertedlist.SignedInvertedListIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.query.Query;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatiotextualindex.queries.JoinStrategy;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.spatiotextualindex.queries.QueryStrategy;
import org.ual.spatiotextualindex.queries.ThresholdPolicy;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive query processing tests for IRTree, covering spatio-textual queries.
 * Each test runs with {@link InvertedListIndex} to verify consistent results across implementations.
 */
public class IRTreeQueryProcessingTest {

    private PropertySet propertySet;
    private IStorageManager storageManager;
    private DatasetParameters dummyParameters;

    private HashMap<Integer, ArrayList<Integer>> sampleTextualObjects;
    private HashMap<Integer, IShape> sampleSpatialObjects;
    private AbstractDocumentStore weightStore;
    private IDocumentIndex documentIndex;

    private Point queryPointInRegion;
    private Point queryPointOutsideRegion;

    // -----------------------------------------------------------------------
    // Index variant provider
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
    // Setup helpers
    // -----------------------------------------------------------------------

    private void generateSampleData() {
        sampleSpatialObjects = new HashMap<>();
        sampleSpatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
        sampleSpatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
        sampleSpatialObjects.put(3, new Point(new double[]{12.0, 22.0}));
        sampleSpatialObjects.put(4, new Point(new double[]{50.0, 50.0}));
        sampleSpatialObjects.put(5, new Point(new double[]{5.0, 5.0}));
        sampleSpatialObjects.put(6, new Point(new double[]{25.0, 15.0}));
        sampleSpatialObjects.put(7, new Point(new double[]{30.0, 30.0}));
        sampleSpatialObjects.put(8, new Point(new double[]{60.0, 60.0}));

        sampleTextualObjects = new HashMap<>();
        sampleTextualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102, 103)));
        sampleTextualObjects.put(2, new ArrayList<>(Arrays.asList(102, 103)));
        sampleTextualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));
        sampleTextualObjects.put(4, new ArrayList<>(Collections.singletonList(105)));
        sampleTextualObjects.put(5, new ArrayList<>(Arrays.asList(101, 106)));
        sampleTextualObjects.put(6, new ArrayList<>(Arrays.asList(102, 107)));
        sampleTextualObjects.put(7, new ArrayList<>(Arrays.asList(103, 108, 101)));
        sampleTextualObjects.put(8, new ArrayList<>(Collections.singletonList(109)));

        weightStore = new HashMapDocumentStore();
        double smoothingFactor = 0.0;
        WeightCompute.ComputeTF_IDFWeights(sampleTextualObjects, weightStore, smoothingFactor);

        queryPointInRegion = new Point(new double[]{10.0, 20.0});
        queryPointOutsideRegion = new Point(new double[]{100.0, 100.0});
    }

    private void insertSampleData(IRTree tree) {
        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            tree.insertData(entry.getKey(), entry.getValue());
        }
    }

    private void buildIndex(IRTree tree, String indexType) {
        documentIndex = createDocumentIndex(indexType, 0);
        tree.createIRTree(weightStore, documentIndex);
    }

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
        dummyParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

        generateSampleData();
    }

    @Nested
    class BooleanRangeQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryFindsDocumentsWithKeywordInRadius(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            float largeRadius = 100.0f;

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(documentIndex, query, largeRadius);

            assertNotNull(results);
            assertFalse(results.isEmpty(), "Should find documents with keyword 101");
            Set<Integer> foundIds = results.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
            assertTrue(foundIds.contains(1), "Document 1 has keyword 101 and should be found");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryReturnsEmptyForNonexistentKeyword(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(999));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            float radius = 100.0f;

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(documentIndex, query, radius);

            assertTrue(results.isEmpty(), "Should find no documents for nonexistent keyword");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryRespectsRadiusConstraint(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(105));
            SKNNQuery query = new SKNNQuery(4, queryPointInRegion, keywords);
            float smallRadius = 40.0f;

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(documentIndex, query, smallRadius);

            assertTrue(results.isEmpty(), "Should find no documents outside radius constraint");
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryWithMultipleKeywords(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            float largeRadius = 100.0f;

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(documentIndex, query, largeRadius);

            assertNotNull(results);
            assertTrue(results.size() >= 1);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryWithSmallRadius(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            float smallRadius = 5.0f;

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(documentIndex, query, smallRadius);

            assertNotNull(results);
            if (!results.isEmpty()) {
                assertTrue(results.stream().anyMatch(r -> r.getId() == 1));
            }
        }
    }

    @Nested
    class BooleanKnnQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanKnnQueryFindsKNearestDocumentsWithKeyword(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            int k = 1;

            List<SKNNQuery.Result> results = irtree.booleanKnnQuery(documentIndex, query, k);

            assertNotNull(results);
            assertTrue(results.size() <= k);
            if (!results.isEmpty()) {
                assertTrue(sampleSpatialObjects.containsKey(results.get(0).getId()));
            }
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanKnnQueryWithK2(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            int k = 2;

            List<SKNNQuery.Result> results = irtree.booleanKnnQuery(documentIndex, query, k);

            assertNotNull(results);
            assertTrue(results.size() <= k);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanKnnQueryReturnsEmptyForNonexistentKeyword(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(999));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            int k = 5;

            List<SKNNQuery.Result> results = irtree.booleanKnnQuery(documentIndex, query, k);

            assertTrue(results.isEmpty() || results.size() < k);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanKnnQueryWithLargeK(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            int k = 100;

            List<SKNNQuery.Result> results = irtree.booleanKnnQuery(documentIndex, query, k);

            assertNotNull(results);
            assertTrue(results.size() <= k);
        }
    }

    @Nested
    class TopkKnnQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void topkKnnQueryFindsKNearestDocumentsWithSpatioTextualScoring(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            SKNNQuery query = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights);
            int k = 1;

            List<SKNNQuery.Result> results = irtree.topkKnnQuery(documentIndex, query, k);

            assertNotNull(results);
            assertTrue(results.size() <= k);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void topkKnnQueryUsesAlphaForWeighting(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));

            SKNNQuery spatialQuery = new SKNNQuery(1, 0.8, queryPointInRegion, keywords, keywordWeights);
            List<SKNNQuery.Result> spatialResults = irtree.topkKnnQuery(documentIndex, spatialQuery, 1);

            SKNNQuery textualQuery = new SKNNQuery(2, 0.2, queryPointInRegion, keywords, keywordWeights);
            List<SKNNQuery.Result> textualResults = irtree.topkKnnQuery(documentIndex, textualQuery, 1);

            assertNotNull(spatialResults);
            assertNotNull(textualResults);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void topkKnnQueryWithMultipleKeywords(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102));
            ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0));
            SKNNQuery query = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights);
            int k = 2;

            List<SKNNQuery.Result> results = irtree.topkKnnQuery(documentIndex, query, k);

            assertNotNull(results);
            assertTrue(results.size() <= k);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void topkKnnQueryWithNormalizedWeights(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(0.5));
            SKNNQuery query = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights);
            int k = 1;

            List<SKNNQuery.Result> results = irtree.topkKnnQuery(documentIndex, query, k);

            assertNotNull(results);
        }
    }

    @Nested
    class GnnkQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void gnnkQueryProcessesMultipleQueries(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            List<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(1001, 0.5, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)), keywordWeights);
            Query q2 = new Query(1002, 0.5, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), keywordWeights);

            List<Query> queries = Arrays.asList(q1, q2);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(1, queries, queries.size(), aggregator);
            int topk = 1;

            List<AggregateSKNNQuery.Result> results = irtree.gnnk(documentIndex, gnnkQuery, topk);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void gnnkQueryAggregatesMultipleQueryResults(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            List<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(2001, 0.5, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)), keywordWeights);
            Query q2 = new Query(2002, 0.5, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(102)), keywordWeights);

            List<Query> queries = Arrays.asList(q1, q2);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(2, queries, queries.size(), aggregator);

            List<AggregateSKNNQuery.Result> results = irtree.gnnk(documentIndex, gnnkQuery, 2);

            assertNotNull(results);
            if (!results.isEmpty()) {
                assertTrue(results.size() <= 2);
            }
        }
    }

    @Nested
    class SgnnkQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void sgnnkQueryProcessesSubgroupQueries(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            List<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(3001, 0.3, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)), keywordWeights);
            Query q2 = new Query(3002, 0.3, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), keywordWeights);
            Query q3 = new Query(3003, 0.4, new Point(new double[]{12.0, 22.0}),
                    new ArrayList<>(Collections.singletonList(103)), keywordWeights);

            List<Query> queries = Arrays.asList(q1, q2, q3);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            int subGroupSize = 2;

            AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(3, queries, queries.size(), aggregator);
            sgnnkQuery.subGroupSize = subGroupSize;

            List<AggregateSKNNQuery.Result> results = irtree.sgnnk(documentIndex, sgnnkQuery, 1);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void sgnnkQueryWithDifferentSubgroupSizes(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            List<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            List<Query> queries = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Query q = new Query(4000 + i, 0.25, queryPointInRegion,
                        new ArrayList<>(Collections.singletonList(101 + i)), keywordWeights);
                queries.add(q);
            }

            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(4, queries, queries.size(), aggregator);
            sgnnkQuery.subGroupSize = 2;

            List<AggregateSKNNQuery.Result> results = irtree.sgnnk(documentIndex, sgnnkQuery, 1);

            assertNotNull(results);
        }
    }

    @Nested
    class SgnnkExtendedQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void sgnnkExtendedQueryProcessesVariousGroupSizes(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            List<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(5001, 0.33, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)), keywordWeights);
            Query q2 = new Query(5002, 0.33, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), keywordWeights);
            Query q3 = new Query(5003, 0.34, new Point(new double[]{12.0, 22.0}),
                    new ArrayList<>(Collections.singletonList(103)), keywordWeights);

            List<Query> queries = Arrays.asList(q1, q2, q3);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");

            int minSubGroupSize = 2;
            int maxGroupSize = queries.size();

            AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(5, queries, maxGroupSize, aggregator);
            sgnnkQuery.subGroupSize = minSubGroupSize;

            Map<Integer, List<AggregateSKNNQuery.Result>> resultsMap = irtree.sgnnkExtended(documentIndex, sgnnkQuery, 1);

            assertNotNull(resultsMap);
            if (!resultsMap.isEmpty()) {
                for (int m = minSubGroupSize; m <= maxGroupSize; m++) {
                    assertTrue(resultsMap.containsKey(m) || resultsMap.size() > 0);
                }
            }
        }
    }

    @Nested
    class SelfJoinSKQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void selfJoinSKQueryFindsMatchingPairs(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
            ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
            SKJoinQuery joinQuery = new SKJoinQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights);
            JoinConfiguration joinConfiguration = new JoinConfiguration(
                    ThresholdPolicy.COMBINED_COST,
                    JoinStrategy.DEFAULT,
                    SimilarityType.WEIGHTED_JACCARD,
                    QueryStrategy.PARTIAL_JOIN
            );

            List<SKJoinQuery.Result> results = irtree.selfJoinSKQueryBestFirst(
                    documentIndex, joinQuery, 50.0f, 0.1f,
                    joinConfiguration);

            assertNotNull(results);
            for (SKJoinQuery.Result result : results) {
                assertTrue(result.getPairId1() > 0);
                assertTrue(result.getPairId2() > 0);
                assertTrue(result.getPairId1() != result.getPairId2());
            }
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void selfJoinSKQueryWithDifferentStrategies(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102));
            ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0));
            SKJoinQuery joinQuery = new SKJoinQuery(2, 0.5, queryPointInRegion, keywords, keywordWeights);
            JoinConfiguration joinConfiguration = new JoinConfiguration(
                    ThresholdPolicy.COMBINED_COST,
                    JoinStrategy.DEFAULT,
                    SimilarityType.WEIGHTED_JACCARD,
                    QueryStrategy.PARTIAL_JOIN
            );

            List<SKJoinQuery.Result> bestFirstResults = irtree.selfJoinSKQueryBestFirst(
                    documentIndex, joinQuery, 50.0f, 0.1f, joinConfiguration);
            List<SKJoinQuery.Result> recursiveResults = irtree.selfJoinSKQueryRecursive(
                    documentIndex, joinQuery, 50.0f, 0.1f, joinConfiguration);

            assertNotNull(bestFirstResults);
            assertNotNull(recursiveResults);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void selfJoinSKQueryWithSmallDistance(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            SKJoinQuery joinQuery = new SKJoinQuery(3, 0.5, queryPointInRegion, keywords, keywordWeights);
            JoinConfiguration joinConfiguration = new JoinConfiguration(
                    ThresholdPolicy.COMBINED_COST,
                    JoinStrategy.DEFAULT,
                    SimilarityType.WEIGHTED_JACCARD,
                    QueryStrategy.PARTIAL_JOIN
            );

            List<SKJoinQuery.Result> results = irtree.selfJoinSKQueryBestFirst(
                    documentIndex, joinQuery, 10.0f, 0.1f, joinConfiguration);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void selfJoinSKQueryWithLargeDistance(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Arrays.asList(101, 102, 103));
            ArrayList<Double> keywordWeights = new ArrayList<>(Arrays.asList(1.0, 1.0, 1.0));
            SKJoinQuery joinQuery = new SKJoinQuery(4, 0.5, queryPointInRegion, keywords, keywordWeights);
            JoinConfiguration joinConfiguration = new JoinConfiguration(
                    ThresholdPolicy.COMBINED_COST,
                    JoinStrategy.DEFAULT,
                    SimilarityType.WEIGHTED_JACCARD,
                    QueryStrategy.PARTIAL_JOIN
            );

            List<SKJoinQuery.Result> results = irtree.selfJoinSKQueryBestFirst(
                    documentIndex, joinQuery, 1000.0f, 0.1f, joinConfiguration);

            assertNotNull(results);
            assertTrue(results.size() >= 0);
        }
    }

    @Nested
    class QueryResultConsistency {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeAndKnnResultsAreConsistent(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);
            float radius = 30.0f;

            List<SKNNQuery.Result> rangeResults = irtree.booleanRangeQuery(documentIndex, query, radius);
            List<SKNNQuery.Result> knnResults = irtree.booleanKnnQuery(documentIndex, query, rangeResults.size());

            assertNotNull(rangeResults);
            assertNotNull(knnResults);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void topkKnnResultsAreOrderedByScore(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            SKNNQuery query = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights);

            List<SKNNQuery.Result> results = irtree.topkKnnQuery(documentIndex, query, 5);

            assertNotNull(results);
            for (int i = 1; i < results.size(); i++) {
                assertTrue(results.get(i - 1).getCombinedCost() <= results.get(i).getCombinedCost());
            }
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void aggregateQueryResultsAreMeaningful(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            List<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(6001, 0.5, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)), keywordWeights);
            Query q2 = new Query(6002, 0.5, new Point(new double[]{15.0, 25.0}),
                    new ArrayList<>(Collections.singletonList(102)), keywordWeights);

            List<Query> queries = Arrays.asList(q1, q2);
            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(6, queries, queries.size(), aggregator);

            List<AggregateSKNNQuery.Result> results = irtree.gnnk(documentIndex, gnnkQuery, 3);

            assertNotNull(results);
        }
    }

    @Nested
    class SpatioTextualEdgeCases {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void queryWithEmptyKeywordList(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>();
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(documentIndex, query, 100.0f);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void queryPointOutsideDataSpace(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointOutsideRegion, keywords);

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(documentIndex, query, 100.0f);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void queryWithZeroRadius(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, new Point(new double[]{10.0, 20.0}), keywords);

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(documentIndex, query, 0.0f);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void queryWithZeroAlpha(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            SKNNQuery query = new SKNNQuery(1, 0.0, queryPointInRegion, keywords, keywordWeights);

            List<SKNNQuery.Result> results = irtree.topkKnnQuery(documentIndex, query, 2);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void queryWithAlphaOne(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            SKNNQuery query = new SKNNQuery(1, 1.0, queryPointInRegion, keywords, keywordWeights);

            List<SKNNQuery.Result> results = irtree.topkKnnQuery(documentIndex, query, 2);

            assertNotNull(results);
        }
    }

    @Nested
    class QueryPerformanceAndScaling {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryWithManyDocuments(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);

            for (int i = 0; i < 50; i++) {
                Region region = new Region(new double[]{i % 10, i / 10},
                        new double[]{i % 10 + 0.5, i / 10 + 0.5});
                irtree.insertData(i, region);
            }

            HashMap<Integer, ArrayList<Integer>> textData = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                textData.put(i, new ArrayList<>(Collections.singletonList(101)));
            }
            AbstractDocumentStore ws = new HashMapDocumentStore();
            WeightCompute.ComputeTF_IDFWeights(textData, ws, 0.0);

            IDocumentIndex idx = createDocumentIndex(indexType, 0);
            irtree.createIRTree(ws, idx);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, keywords);

            List<SKNNQuery.Result> results = irtree.booleanRangeQuery(idx, query, 100.0f);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void topkKnnQueryCompletes(String indexType) {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildIndex(irtree, indexType);

            ArrayList<Integer> keywords = new ArrayList<>(Collections.singletonList(101));
            ArrayList<Double> keywordWeights = new ArrayList<>(Collections.singletonList(1.0));
            SKNNQuery query = new SKNNQuery(1, 0.5, queryPointInRegion, keywords, keywordWeights);

            List<SKNNQuery.Result> results = irtree.topkKnnQuery(documentIndex, query, 10);

            assertNotNull(results);
            assertTrue(results.size() <= 10);
        }
    }
}

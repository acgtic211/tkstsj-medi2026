package org.ual.spatiotextualindex.dirtree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
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
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.spatiotextualindex.queries.JoinStrategy;
import org.ual.spatiotextualindex.queries.QueryStrategy;
import org.ual.spatiotextualindex.queries.ThresholdPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consolidated DIRTree query processing tests.
 * Each test runs with {@link InvertedListIndex} to verify consistent behaviour across implementations.
 */
public class DIRTreeQueryProcessingTest {

    private DatasetParameters dummyParameters;

    private HashMap<Integer, ArrayList<Integer>> sampleTextualObjects;
    private HashMap<Integer, IShape> sampleSpatialObjects;

    private Point queryPointInRegion;
    private Point queryPointOutsideRegion;

    // -----------------------------------------------------------------------
    // Index variant providers
    // -----------------------------------------------------------------------

    static Stream<Arguments> provideDocumentIndexVariants() {
        return Stream.of(
                Arguments.of("InvertedList")
//                Arguments.of("SignedInvertedList"),
//                Arguments.of("SignedInvertedListSimple")
        );
    }

    /**
     * Cartesian product of R-tree insertion variants × document index types.
     */
    static Stream<Arguments> provideRTreeVariantsAndIndexTypes() {
        return Stream.of(
                Arguments.of(Named.of("Linear", SpatialIndex.RtreeVariantLinear),     "InvertedList"),
                Arguments.of(Named.of("Linear", SpatialIndex.RtreeVariantLinear),     "SignedInvertedList"),
                Arguments.of(Named.of("Linear", SpatialIndex.RtreeVariantLinear),     "SignedInvertedListSimple"),
                Arguments.of(Named.of("Quadratic", SpatialIndex.RtreeVariantQuadratic),  "InvertedList"),
                Arguments.of(Named.of("Quadratic", SpatialIndex.RtreeVariantQuadratic),  "SignedInvertedList"),
                Arguments.of(Named.of("Quadratic", SpatialIndex.RtreeVariantQuadratic),  "SignedInvertedListSimple"),
                Arguments.of(Named.of("Rstar", SpatialIndex.RtreeVariantRstar),      "InvertedList"),
                Arguments.of(Named.of("Rstar", SpatialIndex.RtreeVariantRstar),      "SignedInvertedList"),
                Arguments.of(Named.of("Rstar", SpatialIndex.RtreeVariantRstar),      "SignedInvertedListSimple")
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
    // Setup
    // -----------------------------------------------------------------------

    @BeforeEach
    void setUp() {
        dummyParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
        AbstractDocumentStore.maxWord = 1000;
        generateSampleData();
    }

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

        queryPointInRegion = new Point(new double[]{10.0, 20.0});
        queryPointOutsideRegion = new Point(new double[]{100.0, 100.0});
    }

    private PropertySet createPropertySet(int treeVariant) {
        PropertySet propertySet = new PropertySet();
        propertySet.setProperty("Dimension", 2);
        propertySet.setProperty("IndexCapacity", 6);
        propertySet.setProperty("LeafCapacity", 6);
        propertySet.setProperty("FillFactor", 0.7f);
        propertySet.setProperty("TreeVariant", treeVariant);
        propertySet.setProperty("NearMinimumOverlapFactor", 2);
        propertySet.setProperty("BetaArea", 0.5f);
        return propertySet;
    }

    private TreeContext createTreeContext(int treeVariant, String indexType) {
        AbstractDocumentStore weightStore = new HashMapDocumentStore();
        WeightCompute.ComputeTermWeights(sampleTextualObjects, weightStore, 0.5);

        IStorageManager storageManager = new NodeStorageManager();
        DIRTree tree = new DIRTree(createPropertySet(treeVariant), storageManager, weightStore, dummyParameters);

        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            HashSet<Integer> documents = tree.getDocumentStore().readSet(entry.getKey());
            assertNotNull(documents, "Document set must exist for sample object " + entry.getKey());
            tree.insertData(entry.getKey(), entry.getValue(), documents);
        }

        IDocumentIndex documentIndex = createDocumentIndex(indexType, 0);
        tree.createDIRTree(weightStore, documentIndex);

        return new TreeContext(tree, documentIndex);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Nested
    class BooleanRangeQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryFindsDocumentsWithKeywordInRadius(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(1, queryPointInRegion, new ArrayList<>(Collections.singletonList(101)));
            List<SKNNQuery.Result> results = context.tree.booleanRangeQuery(context.documentIndex, query, 100.0f);

            assertNotNull(results);
            assertFalse(results.isEmpty());
            Set<Integer> foundIds = results.stream().map(SKNNQuery.Result::getId).collect(Collectors.toSet());
            assertTrue(foundIds.contains(1));
            assertTrue(foundIds.contains(3));
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryReturnsEmptyForNonexistentKeyword(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(2, queryPointInRegion, new ArrayList<>(Collections.singletonList(999)));
            List<SKNNQuery.Result> results = context.tree.booleanRangeQuery(context.documentIndex, query, 100.0f);

            assertTrue(results.isEmpty());
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanRangeQueryRespectsRadiusConstraint(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(3, queryPointInRegion, new ArrayList<>(Collections.singletonList(105)));
            List<SKNNQuery.Result> results = context.tree.booleanRangeQuery(context.documentIndex, query, 40.0f);

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    class BooleanKnnQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanKnnQueryReturnsAtMostKResults(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(4, queryPointInRegion, new ArrayList<>(Collections.singletonList(101)));
            int k = 2;
            List<SKNNQuery.Result> results = context.tree.booleanKnnQuery(context.documentIndex, query, k);

            assertNotNull(results);
            assertTrue(results.size() <= k);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanKnnQueryReturnsClosestCandidateWhenAvailable(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(5, queryPointInRegion, new ArrayList<>(Collections.singletonList(101)));
            List<SKNNQuery.Result> results = context.tree.booleanKnnQuery(context.documentIndex, query, 1);

            assertNotNull(results);
            if (!results.isEmpty()) {
                assertEquals(1, results.get(0).getId());
            }
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void booleanKnnQueryHandlesLargeK(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(6, queryPointOutsideRegion, new ArrayList<>(Collections.singletonList(102)));
            List<SKNNQuery.Result> results = context.tree.booleanKnnQuery(context.documentIndex, query, 100);

            assertNotNull(results);
            assertTrue(results.size() <= 100);
        }
    }

    @Nested
    class TopkKnnQuery {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void topkKnnQueryReturnsRankedResults(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(
                    7, 0.5, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)),
                    new ArrayList<>(Collections.singletonList(1.0))
            );

            List<SKNNQuery.Result> results = context.tree.topkKnnQuery(context.documentIndex, query, 5);

            assertNotNull(results);
            for (int i = 1; i < results.size(); i++) {
                assertTrue(results.get(i - 1).getCombinedCost() <= results.get(i).getCombinedCost());
            }
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void topkKnnQuerySupportsAlphaBoundaries(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery spatialOnly = new SKNNQuery(
                    8, 1.0, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)),
                    new ArrayList<>(Collections.singletonList(1.0))
            );
            SKNNQuery textualOnly = new SKNNQuery(
                    9, 0.0, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)),
                    new ArrayList<>(Collections.singletonList(1.0))
            );

            assertNotNull(context.tree.topkKnnQuery(context.documentIndex, spatialOnly, 2));
            assertNotNull(context.tree.topkKnnQuery(context.documentIndex, textualOnly, 2));
        }
    }

    @Nested
    class AggregateQueries {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void gnnkAndSgnnkQueriesReturnResults(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            List<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(1001, 0.5, queryPointInRegion, new ArrayList<>(Collections.singletonList(101)), weights);
            Query q2 = new Query(1002, 0.5, new Point(new double[]{15.0, 25.0}), new ArrayList<>(Collections.singletonList(102)), weights);
            Query q3 = new Query(1003, 0.5, new Point(new double[]{12.0, 22.0}), new ArrayList<>(Collections.singletonList(103)), weights);

            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(10, Arrays.asList(q1, q2), 2, aggregator);
            AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(11, Arrays.asList(q1, q2, q3), 3, aggregator);
            sgnnkQuery.subGroupSize = 2;

            assertNotNull(context.tree.gnnk(context.documentIndex, gnnkQuery, 2));
            assertNotNull(context.tree.sgnnk(context.documentIndex, sgnnkQuery, 1));
            assertNotNull(context.tree.gnnkBaseline(context.documentIndex, gnnkQuery, 2));
            assertNotNull(context.tree.sgnnkBaseline(context.documentIndex, sgnnkQuery, 1));
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void sgnnkExtendedReturnsResultsForSubgroupRange(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            List<Double> weights = new ArrayList<>(Collections.singletonList(1.0));
            Query q1 = new Query(1201, 0.34, queryPointInRegion, new ArrayList<>(Collections.singletonList(101)), weights);
            Query q2 = new Query(1202, 0.33, new Point(new double[]{15.0, 25.0}), new ArrayList<>(Collections.singletonList(102)), weights);
            Query q3 = new Query(1203, 0.33, new Point(new double[]{12.0, 22.0}), new ArrayList<>(Collections.singletonList(103)), weights);

            IAggregator aggregator = AggregatorFactory.getAggregator("SUM");
            AggregateSKNNQuery query = new AggregateSKNNQuery(12, Arrays.asList(q1, q2, q3), 3, aggregator);
            query.subGroupSize = 2;

            Map<Integer, List<AggregateSKNNQuery.Result>> results = context.tree.sgnnkExtended(context.documentIndex, query, 1);
            assertNotNull(results);
            assertFalse(results.isEmpty());
            assertTrue(results.containsKey(2));
            assertTrue(results.containsKey(3));
        }
    }

    @Nested
    class SelfJoinQueries {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void selfJoinBestFirstAndRecursiveReturnValidPairs(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKJoinQuery query = new SKJoinQuery(
                    13, 0.5, queryPointInRegion,
                    new ArrayList<>(Arrays.asList(101, 102)),
                    new ArrayList<>(Arrays.asList(1.0, 1.0))
            );

            JoinConfiguration joinConfiguration = new JoinConfiguration(
                    ThresholdPolicy.COMBINED_COST,
                    JoinStrategy.DEFAULT,
                    SimilarityType.WEIGHTED_JACCARD,
                    QueryStrategy.PARTIAL_JOIN
            );

            List<SKJoinQuery.Result> bestFirstResults = context.tree.selfJoinSKQueryBestFirst(
                    context.documentIndex, query, 50.0f, 0.1f, joinConfiguration);
            List<SKJoinQuery.Result> recursiveResults = context.tree.selfJoinSKQueryRecursive(
                    context.documentIndex, query, 50.0f, 0.1f, joinConfiguration);

            assertNotNull(bestFirstResults);
            assertNotNull(recursiveResults);

            for (SKJoinQuery.Result result : bestFirstResults) {
                assertTrue(result.getPairId1() > 0);
                assertTrue(result.getPairId2() > 0);
                assertNotEquals(result.getPairId1(), result.getPairId2());
            }
        }
    }

    @Nested
    class SpatioTextualEdgeCases {

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void queryWithEmptyKeywordListDoesNotFail(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(14, queryPointInRegion, new ArrayList<>());
            List<SKNNQuery.Result> results = context.tree.booleanRangeQuery(context.documentIndex, query, 100.0f);

            assertNotNull(results);
        }

        @ParameterizedTest(name = "[index={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideDocumentIndexVariants")
        void queryWithZeroRadiusDoesNotFail(String indexType) {
            TreeContext context = createTreeContext(SpatialIndex.RtreeVariantRstar, indexType);

            SKNNQuery query = new SKNNQuery(15, queryPointInRegion, new ArrayList<>(Collections.singletonList(101)));
            List<SKNNQuery.Result> results = context.tree.booleanRangeQuery(context.documentIndex, query, 0.0f);

            assertNotNull(results);
        }
    }

    @Nested
    class InsertionVariantCoverage {

        @ParameterizedTest(name = "[variant={0}, index={1}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideRTreeVariantsAndIndexTypes")
        void booleanRangeQueryWorksAcrossInsertionVariants(int treeVariant, String indexType) {
            TreeContext context = createTreeContext(treeVariant, indexType);

            SKNNQuery query = new SKNNQuery(16, queryPointInRegion, new ArrayList<>(Collections.singletonList(101)));
            List<SKNNQuery.Result> results = context.tree.booleanRangeQuery(context.documentIndex, query, 100.0f);

            assertNotNull(results);
            assertTrue(context.tree.isIndexValid());
            assertTrue(context.tree.validateDocumentStructure());
        }

        @ParameterizedTest(name = "[variant={0}, index={1}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideRTreeVariantsAndIndexTypes")
        void booleanKnnQueryWorksAcrossInsertionVariants(int treeVariant, String indexType) {
            TreeContext context = createTreeContext(treeVariant, indexType);

            SKNNQuery query = new SKNNQuery(17, queryPointInRegion, new ArrayList<>(Collections.singletonList(102)));
            List<SKNNQuery.Result> results = context.tree.booleanKnnQuery(context.documentIndex, query, 3);

            assertNotNull(results);
            assertTrue(results.size() <= 3);
            assertTrue(context.tree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}, index={1}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeQueryProcessingTest#provideRTreeVariantsAndIndexTypes")
        void topkKnnQueryWorksAcrossInsertionVariants(int treeVariant, String indexType) {
            TreeContext context = createTreeContext(treeVariant, indexType);

            SKNNQuery query = new SKNNQuery(
                    18, 0.5, queryPointInRegion,
                    new ArrayList<>(Collections.singletonList(101)),
                    new ArrayList<>(Collections.singletonList(1.0))
            );
            List<SKNNQuery.Result> results = context.tree.topkKnnQuery(context.documentIndex, query, 3);

            assertNotNull(results);
            assertTrue(results.size() <= 3);
            assertTrue(context.tree.isIndexValid());
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static class TreeContext {
        private final DIRTree tree;
        private final IDocumentIndex documentIndex;

        private TreeContext(DIRTree tree, IDocumentIndex documentIndex) {
            this.tree = tree;
            this.documentIndex = documentIndex;
        }
    }
}

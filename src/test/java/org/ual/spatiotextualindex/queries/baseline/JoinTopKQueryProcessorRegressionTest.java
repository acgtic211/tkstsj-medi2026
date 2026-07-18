package org.ual.spatiotextualindex.queries.baseline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ual.document.WeightCompute;
import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.querytype.SKJoinQuery;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.spatialindex.IShape;
import org.ual.spatialindex.spatialindex.Point;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.irtree.IRTree;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.spatiotextualindex.queries.JoinStrategy;
import org.ual.spatiotextualindex.queries.QueryStrategy;
import org.ual.spatiotextualindex.queries.ThresholdPolicy;
import org.ual.spatiotextualindex.queries.baseline.join.JoinTopKQueryProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinTopKQueryProcessorRegressionTest {

    private PropertySet propertySet;
    private DatasetParameters datasetParameters;

    @BeforeEach
    void setUp() {
        propertySet = new PropertySet();
        propertySet.setProperty("Dimension", 2);
        propertySet.setProperty("IndexCapacity", 6);
        propertySet.setProperty("LeafCapacity", 6);
        propertySet.setProperty("FillFactor", 0.7f);
        propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
        propertySet.setProperty("NearMinimumOverlapFactor", 2);

        datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
    }

    @Test
    void bestFirstHandlesLeafObjectIdsWithoutNodeIdLookupRegression() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(100, new Point(new double[]{0.0, 0.0}));
        spatial.put(101, new Point(new double[]{0.0, 0.2}));
        spatial.put(102, new Point(new double[]{0.0, 1.0}));
        spatial.put(103, new Point(new double[]{5.0, 5.0}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(100, new ArrayList<>(Arrays.asList(7001, 7100)));
        textual.put(101, new ArrayList<>(Arrays.asList(7001, 7101)));
        textual.put(102, new ArrayList<>(Arrays.asList(7001, 7102)));
        textual.put(103, new ArrayList<>(Collections.singletonList(7999)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        List<SKJoinQuery.Result> results = processor.selfJoinSKQueryBestFirst(
                invertedList,
                queryWithTerm(7001),
                2,
                fullJoinConfig()
        );

        assertEquals(2, results.size(), "Best-first should return top-k even when object IDs differ from node IDs.");
        assertTrue(results.stream().allMatch(r -> r.getPairId1() >= 100 && r.getPairId2() >= 100));
    }

    @Test
    void bestFirstAndRecursiveReturnConsistentTopKPairs() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(1, new Point(new double[]{0.0, 0.0}));
        spatial.put(2, new Point(new double[]{0.0, 0.3}));
        spatial.put(3, new Point(new double[]{0.0, 1.2}));
        spatial.put(4, new Point(new double[]{4.0, 4.0}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(1, new ArrayList<>(Arrays.asList(8001, 8100)));
        textual.put(2, new ArrayList<>(Arrays.asList(8001, 8101)));
        textual.put(3, new ArrayList<>(Arrays.asList(8001, 8102)));
        textual.put(4, new ArrayList<>(Collections.singletonList(8999)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        SKJoinQuery query = queryWithTerm(8001);
        JoinConfiguration config = fullJoinConfig();

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(invertedList, query, 3, config);
        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(invertedList, query, 3, config);

        assertEquals(3, bestFirst.size());
        assertEquals(3, recursive.size());

        Set<String> bestPairs = toPairSet(bestFirst);
        Set<String> recursivePairs = toPairSet(recursive);
        assertEquals(bestPairs, recursivePairs,
                "Best-first and recursive should agree on top-k pair identities under same configuration.");

        assertFalse(bestFirst.isEmpty());
        assertTrue(bestFirst.get(0).getCombineCost() <= bestFirst.get(bestFirst.size() - 1).getCombineCost());
        assertTrue(recursive.get(0).getCombineCost() <= recursive.get(recursive.size() - 1).getCombineCost());
    }

    @Test
    void planeSweepKeepsSameChildPairsInSelfJoin() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(10, new Point(new double[]{0.0, 0.0}));
        spatial.put(11, new Point(new double[]{0.0, 0.05}));
        spatial.put(20, new Point(new double[]{100.0, 100.0}));
        spatial.put(21, new Point(new double[]{100.0, 100.05}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(10, new ArrayList<>(Collections.singletonList(9001)));
        textual.put(11, new ArrayList<>(Collections.singletonList(9001)));
        textual.put(20, new ArrayList<>(Collections.singletonList(9002)));
        textual.put(21, new ArrayList<>(Collections.singletonList(9002)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTreeWithCapacities(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        JoinConfiguration config = new JoinConfiguration(
                ThresholdPolicy.COMBINED_COST,
                JoinStrategy.PLANE_SWEEP,
                SimilarityType.WEIGHTED_SUM,
                QueryStrategy.CONSTRAINT_TEXTUAL_JOIN
        );

        SKJoinQuery query = queryWithTerm(9001);

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(invertedList, query, 1, config);
        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(invertedList, query, 1, config);

        assertFalse(bestFirst.isEmpty(), "Best-first plane-sweep should return at least one valid pair.");
        assertFalse(recursive.isEmpty(), "Recursive plane-sweep should return at least one valid pair.");

        String expectedPair = "10-11";
        assertTrue(toPairSet(bestFirst).contains(expectedPair), "Best-first should keep same-child pair traversal.");
        assertTrue(toPairSet(recursive).contains(expectedPair), "Recursive should keep same-child pair traversal.");
    }

    @Test
    void constraintAllJoinAppliesBothTextualAndSpatialConstraints() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(1, new Point(new double[]{0.1, 0.1}));
        spatial.put(2, new Point(new double[]{0.2, 0.2}));
        spatial.put(3, new Point(new double[]{3.0, 3.0}));
        spatial.put(4, new Point(new double[]{3.1, 3.1}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(1, new ArrayList<>(Collections.singletonList(9201)));
        textual.put(2, new ArrayList<>(Collections.singletonList(9201)));
        textual.put(3, new ArrayList<>(Collections.singletonList(9201)));
        textual.put(4, new ArrayList<>(Collections.singletonList(9201)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        SKJoinQuery windowedQuery = queryWithTerm(9201);
        windowedQuery.setSpatialWindow(new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}));

        assertTrue(QueryStrategy.CONSTRAINT_ALL_JOIN.usesConstraintTextualFilter());
        assertTrue(QueryStrategy.CONSTRAINT_ALL_JOIN.usesSpatialWindowConstraint());
        assertFalse(QueryStrategy.CONSTRAINT_ALL_JOIN.usesExactTextualSimilarity());

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                invertedList,
                windowedQuery,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_ALL_JOIN)
        );
        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                invertedList,
                windowedQuery,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_ALL_JOIN)
        );

        Set<String> expectedPairs = new HashSet<>(Collections.singletonList("1-2"));
        assertEquals(expectedPairs, toPairSet(bestFirst),
                "Best-first should honor both the spatial window and the textual constraint.");
        assertEquals(expectedPairs, toPairSet(recursive),
                "Recursive should honor both the spatial window and the textual constraint.");
    }

    private IRTree buildTree(Map<Integer, IShape> spatial,
                             Map<Integer, ArrayList<Integer>> textual,
                             IDocumentIndex documentIndex) {
        IStorageManager storageManager = new NodeStorageManager();
        IRTree tree = new IRTree(propertySet, storageManager, datasetParameters);

        for (Map.Entry<Integer, IShape> entry : spatial.entrySet()) {
            tree.insertData(entry.getKey(), entry.getValue());
        }

        AbstractDocumentStore weightStore = new HashMapDocumentStore();
        WeightCompute.ComputeTF_IDFWeights(new HashMap<>(textual), weightStore, 0.0);
        tree.createIRTree(weightStore, documentIndex);

        return tree;
    }

    private IRTree buildTreeWithCapacities(Map<Integer, IShape> spatial,
                                           Map<Integer, ArrayList<Integer>> textual,
                                           IDocumentIndex documentIndex) {
        PropertySet custom = new PropertySet();
        custom.setProperty("Dimension", 2);
        custom.setProperty("IndexCapacity", 3);
        custom.setProperty("LeafCapacity", 3);
        custom.setProperty("FillFactor", 0.7f);
        custom.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
        custom.setProperty("NearMinimumOverlapFactor", 2);

        IStorageManager storageManager = new NodeStorageManager();
        IRTree tree = new IRTree(custom, storageManager, datasetParameters);

        for (Map.Entry<Integer, IShape> entry : spatial.entrySet()) {
            tree.insertData(entry.getKey(), entry.getValue());
        }

        AbstractDocumentStore weightStore = new HashMapDocumentStore();
        WeightCompute.ComputeTF_IDFWeights(new HashMap<>(textual), weightStore, 0.0);
        tree.createIRTree(weightStore, documentIndex);

        return tree;
    }

    private static SKJoinQuery queryWithTerm(int termId) {
        return new SKJoinQuery(
                1,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(termId)),
                new ArrayList<>(Collections.singletonList(1.0))
        );
    }

    private static Set<String> toPairSet(List<SKJoinQuery.Result> results) {
        return results.stream()
                .map(r -> r.getPairId1() + "-" + r.getPairId2())
                .collect(Collectors.toSet());
    }

    private static JoinConfiguration fullJoinConfig() {
        return new JoinConfiguration(
                ThresholdPolicy.COMBINED_COST,
                JoinStrategy.DEFAULT,
                SimilarityType.WEIGHTED_SUM,
                QueryStrategy.FULL_JOIN
        );
    }

    //==========================================================================================
    //============================ CONSTRAINT_SPATIAL_JOIN Tests =============================
    //==========================================================================================

    @Test
    void constraintSpatialJoinBestFirstFiltersPairsOutsideQueryWindow() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(1, new Point(new double[]{0.1, 0.1}));
        spatial.put(2, new Point(new double[]{0.2, 0.2}));
        spatial.put(3, new Point(new double[]{3.0, 3.0}));
        spatial.put(4, new Point(new double[]{3.1, 3.1}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(1, new ArrayList<>(Collections.singletonList(9101)));
        textual.put(2, new ArrayList<>(Collections.singletonList(9101)));
        textual.put(3, new ArrayList<>(Collections.singletonList(9101)));
        textual.put(4, new ArrayList<>(Collections.singletonList(9101)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        SKJoinQuery windowedQuery = queryWithTerm(9101);
        windowedQuery.setSpatialWindow(new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}));

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                invertedList,
                windowedQuery,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        Set<String> expectedPairs = new HashSet<>(Collections.singletonList("1-2"));
        assertEquals(expectedPairs, toPairSet(bestFirst),
                "Best-first should only return pairs where both objects are inside the spatial window.");
    }

    @Test
    void constraintSpatialJoinRecursiveFiltersPairsOutsideQueryWindow() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(1, new Point(new double[]{0.1, 0.1}));
        spatial.put(2, new Point(new double[]{0.2, 0.2}));
        spatial.put(3, new Point(new double[]{3.0, 3.0}));
        spatial.put(4, new Point(new double[]{3.1, 3.1}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(1, new ArrayList<>(Collections.singletonList(9102)));
        textual.put(2, new ArrayList<>(Collections.singletonList(9102)));
        textual.put(3, new ArrayList<>(Collections.singletonList(9102)));
        textual.put(4, new ArrayList<>(Collections.singletonList(9102)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        SKJoinQuery windowedQuery = queryWithTerm(9102);
        windowedQuery.setSpatialWindow(new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}));

        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                invertedList,
                windowedQuery,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        Set<String> expectedPairs = new HashSet<>(Collections.singletonList("1-2"));
        assertEquals(expectedPairs, toPairSet(recursive),
                "Recursive should only return pairs where both objects are inside the spatial window.");
    }

    @Test
    void constraintSpatialJoinBothAlgorithmsAgreeOnFilteredPairs() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(1, new Point(new double[]{0.1, 0.1}));
        spatial.put(2, new Point(new double[]{0.2, 0.2}));
        spatial.put(3, new Point(new double[]{3.0, 3.0}));
        spatial.put(4, new Point(new double[]{3.1, 3.1}));
        spatial.put(5, new Point(new double[]{0.5, 0.5}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(1, new ArrayList<>(Collections.singletonList(9103)));
        textual.put(2, new ArrayList<>(Collections.singletonList(9103)));
        textual.put(3, new ArrayList<>(Collections.singletonList(9103)));
        textual.put(4, new ArrayList<>(Collections.singletonList(9103)));
        textual.put(5, new ArrayList<>(Collections.singletonList(9103)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        SKJoinQuery windowedQuery = queryWithTerm(9103);
        windowedQuery.setSpatialWindow(new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}));

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                invertedList,
                windowedQuery,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                invertedList,
                windowedQuery,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        Set<String> bestFirstPairs = toPairSet(bestFirst);
        Set<String> recursivePairs = toPairSet(recursive);
        assertEquals(bestFirstPairs, recursivePairs,
                "Best-first and recursive should agree on spatial window filtered pairs.");
    }

    @Test
    void constraintSpatialJoinEmptyWindowReturnsNoPairs() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(1, new Point(new double[]{5.0, 5.0}));
        spatial.put(2, new Point(new double[]{6.0, 6.0}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(1, new ArrayList<>(Collections.singletonList(9104)));
        textual.put(2, new ArrayList<>(Collections.singletonList(9104)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        SKJoinQuery windowedQuery = queryWithTerm(9104);
        windowedQuery.setSpatialWindow(new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}));

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                invertedList,
                windowedQuery,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                invertedList,
                windowedQuery,
                10,
                new  JoinConfiguration(ThresholdPolicy.STRICT,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
                //joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        assertTrue(bestFirst.isEmpty(), "Best-first should return empty results when all pairs are outside window.");
        assertTrue(recursive.isEmpty(), "Recursive should return empty results when all pairs are outside window.");
    }

    @Test
    void constraintSpatialJoinMixedPairsReturnPartialResults() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(1, new Point(new double[]{0.1, 0.1}));
        spatial.put(2, new Point(new double[]{0.2, 0.2}));
        spatial.put(3, new Point(new double[]{2.0, 2.0}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(1, new ArrayList<>(Collections.singletonList(9105)));
        textual.put(2, new ArrayList<>(Collections.singletonList(9105)));
        textual.put(3, new ArrayList<>(Collections.singletonList(9105)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        SKJoinQuery windowedQuery = queryWithTerm(9105);
        windowedQuery.setSpatialWindow(new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}));

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                invertedList,
                windowedQuery,
                10,
                new  JoinConfiguration(ThresholdPolicy.STRICT,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
                //joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        Set<String> expectedPairs = new HashSet<>(Collections.singletonList("1-2"));
        assertEquals(expectedPairs, toPairSet(bestFirst),
                "Should return only pair (1,2) as object 3 is outside the window.");
    }

    @Test
    void constraintSpatialJoinReturnsEmptyWhenMissingWindow() {
        Map<Integer, IShape> spatial = new LinkedHashMap<>();
        spatial.put(1, new Point(new double[]{0.1, 0.1}));
        spatial.put(2, new Point(new double[]{0.2, 0.2}));

        Map<Integer, ArrayList<Integer>> textual = new LinkedHashMap<>();
        textual.put(1, new ArrayList<>(Collections.singletonList(9106)));
        textual.put(2, new ArrayList<>(Collections.singletonList(9106)));

        IDocumentIndex invertedList = new InvertedListIndex(0);
        IRTree tree = buildTree(spatial, textual, invertedList);
        JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);

        SKJoinQuery queryWithoutWindow = queryWithTerm(9106);
        // Deliberately not setting spatial window

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                invertedList,
                queryWithoutWindow,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                invertedList,
                queryWithoutWindow,
                10,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_SUM,
                        QueryStrategy.CONSTRAINT_SPATIAL_JOIN)
        );

        assertTrue(bestFirst.isEmpty(), "Best-first should return empty results when spatial window is missing for CONSTRAINT_SPATIAL_JOIN.");
        assertTrue(recursive.isEmpty(), "Recursive should return empty results when spatial window is missing for CONSTRAINT_SPATIAL_JOIN.");
    }

}



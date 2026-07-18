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
import org.ual.spatiotextualindex.queries.baseline.join.JoinTopKMultiSetQueryProcessor;

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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinTopKMultiSetQueryProcessorTest {

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
    void bestFirstKeepsDirectionalPairsWhenIdsOverlapAcrossDatasets() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<Integer, IShape>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 1.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<Integer, IShape>();
        secondarySpatial.put(1, new Point(new double[]{0.0, 0.2}));
        secondarySpatial.put(2, new Point(new double[]{0.0, 1.2}));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, docsWithSameTerm(primarySpatial.keySet(), 1001), primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, docsWithSameTerm(secondarySpatial.keySet(), 1001), secondaryIndex);

        JoinTopKMultiSetQueryProcessor processor = new JoinTopKMultiSetQueryProcessor(primaryTree);

        List<SKJoinQuery.Result> results = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(1001),
                4,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_TEXTUAL_JOIN));

        assertEquals(4, results.size());
        assertEquals(new HashSet<String>(Arrays.asList("1-1", "1-2", "2-1", "2-2")), toPairSet(results));
    }

    @Test
    void topKLimitAndOrderingAreRespectedForBestFirstAndRecursive() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<Integer, IShape>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 0.5}));
        primarySpatial.put(3, new Point(new double[]{0.0, 1.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<Integer, IShape>();
        secondarySpatial.put(10, new Point(new double[]{0.0, 0.1}));
        secondarySpatial.put(20, new Point(new double[]{0.0, 0.6}));
        secondarySpatial.put(30, new Point(new double[]{0.0, 1.1}));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, docsWithSameTerm(primarySpatial.keySet(), 2001), primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, docsWithSameTerm(secondarySpatial.keySet(), 2001), secondaryIndex);

        JoinTopKMultiSetQueryProcessor processor = new JoinTopKMultiSetQueryProcessor(primaryTree);
        int topK = 4;

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(2001),
                topK,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_TEXTUAL_JOIN));

        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(2001),
                topK,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_TEXTUAL_JOIN));

        assertEquals(topK, bestFirst.size());
        assertEquals(topK, recursive.size());
        assertSortedAscending(bestFirst);
        assertSortedAscending(recursive);
    }

    @Test
    void bestFirstAndRecursiveAgreeOnTopKPairsUnderSameConfiguration() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<Integer, IShape>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 0.4}));
        primarySpatial.put(3, new Point(new double[]{0.0, 0.9}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<Integer, IShape>();
        secondarySpatial.put(11, new Point(new double[]{0.0, 0.1}));
        secondarySpatial.put(22, new Point(new double[]{0.0, 0.5}));
        secondarySpatial.put(33, new Point(new double[]{0.0, 1.0}));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, docsWithSameTerm(primarySpatial.keySet(), 2101), primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, docsWithSameTerm(secondarySpatial.keySet(), 2101), secondaryIndex);

        JoinTopKMultiSetQueryProcessor processor = new JoinTopKMultiSetQueryProcessor(primaryTree);

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(2101),
                5,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.FULL_JOIN));

        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(2101),
                5,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.FULL_JOIN));

        assertEquals(toPairSet(bestFirst), toPairSet(recursive));
    }

    @Test
    void fullJoinFallbackWorksWhenQueryKeywordIsMissingButPartialJoinPrunes() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<Integer, IShape>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 1.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<Integer, IShape>();
        secondarySpatial.put(100, new Point(new double[]{0.0, 0.3}));
        secondarySpatial.put(200, new Point(new double[]{0.0, 1.3}));

        Map<Integer, ArrayList<Integer>> primaryText = new LinkedHashMap<Integer, ArrayList<Integer>>();
        primaryText.put(1, new ArrayList<Integer>(Arrays.asList(3001, 3101)));
        primaryText.put(2, new ArrayList<Integer>(Collections.singletonList(3102)));

        Map<Integer, ArrayList<Integer>> secondaryText = new LinkedHashMap<Integer, ArrayList<Integer>>();
        secondaryText.put(100, new ArrayList<Integer>(Arrays.asList(3001, 3201)));
        secondaryText.put(200, new ArrayList<Integer>(Collections.singletonList(3202)));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, primaryText, primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, secondaryText, secondaryIndex);

        JoinTopKMultiSetQueryProcessor processor = new JoinTopKMultiSetQueryProcessor(primaryTree);

        List<SKJoinQuery.Result> partialBestFirst = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(9999),
                10,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_TEXTUAL_JOIN));

        List<SKJoinQuery.Result> fullBestFirst = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(9999),
                10,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.FULL_JOIN));

        List<SKJoinQuery.Result> partialRecursive = processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(9999),
                10,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_TEXTUAL_JOIN));

        List<SKJoinQuery.Result> fullRecursive = processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(9999),
                10,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.FULL_JOIN));

        assertTrue(partialBestFirst.isEmpty());
        assertTrue(partialRecursive.isEmpty());
        assertFalse(fullBestFirst.isEmpty());
        assertFalse(fullRecursive.isEmpty());
    }

    @Test
    void bestFirstAndRecursiveHandleAsymmetricTreeHeightAndRespectTopK() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<Integer, IShape>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<Integer, IShape>();
        for (int i = 400; i < 450; i++) {
            secondarySpatial.put(i, new Point(new double[]{(i - 400) * 0.02, 0.0}));
        }

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);

        IRTree primaryTree = buildTreeWithCapacities(
                primarySpatial,
                docsWithSameTerm(primarySpatial.keySet(), 5401),
                primaryIndex,
                4,
                4);

        IRTree secondaryTree = buildTreeWithCapacities(
                secondarySpatial,
                docsWithSameTerm(secondarySpatial.keySet(), 5401),
                secondaryIndex,
                16,
                16);

        JoinTopKMultiSetQueryProcessor processor = new JoinTopKMultiSetQueryProcessor(primaryTree);
        int topK = 7;

        List<SKJoinQuery.Result> bestFirst = assertDoesNotThrow(() -> processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(5401),
                topK,
                joinConfiguration(JoinStrategy.PLANE_SWEEP, QueryStrategy.CONSTRAINT_TEXTUAL_JOIN)));

        List<SKJoinQuery.Result> recursive = assertDoesNotThrow(() -> processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(5401),
                topK,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_TEXTUAL_JOIN)));

        assertEquals(topK, bestFirst.size());
        assertEquals(topK, recursive.size());
        assertTrue(bestFirst.stream().allMatch(r -> r.getPairId1() == 1));
        assertTrue(recursive.stream().allMatch(r -> r.getPairId1() == 1));
        assertTrue(bestFirst.stream().allMatch(r -> r.getPairId2() >= 400 && r.getPairId2() < 450));
        assertTrue(recursive.stream().allMatch(r -> r.getPairId2() >= 400 && r.getPairId2() < 450));
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
        WeightCompute.ComputeTF_IDFWeights(new HashMap<Integer, ArrayList<Integer>>(textual), weightStore, 0.0);
        tree.createIRTree(weightStore, documentIndex);

        return tree;
    }

    private IRTree buildTreeWithCapacities(Map<Integer, IShape> spatial,
                                           Map<Integer, ArrayList<Integer>> textual,
                                           IDocumentIndex documentIndex,
                                           int indexCapacity,
                                           int leafCapacity) {
        PropertySet custom = new PropertySet();
        custom.setProperty("Dimension", 2);
        custom.setProperty("IndexCapacity", indexCapacity);
        custom.setProperty("LeafCapacity", leafCapacity);
        custom.setProperty("FillFactor", 0.7f);
        custom.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
        custom.setProperty("NearMinimumOverlapFactor", 2);

        IStorageManager storageManager = new NodeStorageManager();
        IRTree tree = new IRTree(custom, storageManager, datasetParameters);

        for (Map.Entry<Integer, IShape> entry : spatial.entrySet()) {
            tree.insertData(entry.getKey(), entry.getValue());
        }

        AbstractDocumentStore weightStore = new HashMapDocumentStore();
        WeightCompute.ComputeTF_IDFWeights(new HashMap<Integer, ArrayList<Integer>>(textual), weightStore, 0.0);
        tree.createIRTree(weightStore, documentIndex);

        return tree;
    }

    private static Map<Integer, ArrayList<Integer>> docsWithSameTerm(Set<Integer> ids, int termId) {
        Map<Integer, ArrayList<Integer>> docs = new LinkedHashMap<Integer, ArrayList<Integer>>();
        for (Integer id : ids) {
            docs.put(id, new ArrayList<Integer>(Collections.singletonList(termId)));
        }
        return docs;
    }

    private static SKJoinQuery queryWithTerm(int termId) {
        return new SKJoinQuery(
                1,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<Integer>(Collections.singletonList(termId)),
                new ArrayList<Double>(Collections.singletonList(1.0))
        );
    }

    private static Set<String> toPairSet(List<SKJoinQuery.Result> results) {
        return results.stream().map(r -> r.getPairId1() + "-" + r.getPairId2()).collect(Collectors.toSet());
    }

    private static Set<String> mirroredPairSet(Set<String> pairs) {
        Set<String> mirrored = new HashSet<String>();
        for (String pair : pairs) {
            String[] ids = pair.split("-", 2);
            mirrored.add(ids[1] + "-" + ids[0]);
        }
        return mirrored;
    }

    private static JoinConfiguration joinConfiguration(JoinStrategy joinStrategy, QueryStrategy queryStrategy) {
        return new JoinConfiguration(
                ThresholdPolicy.COMBINED_COST,
                joinStrategy,
                SimilarityType.WEIGHTED_SUM,
                queryStrategy
        );
    }

    private static void assertSortedAscending(List<SKJoinQuery.Result> results) {
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getCombineCost() <= results.get(i).getCombineCost(),
                    "Results must be sorted by ascending combined cost");
        }
    }

    @Test
    void crossDatasetIntersectionIsCommutativeWhenSwappingPrimaryAndSecondary() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<Integer, IShape>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 0.6}));
        primarySpatial.put(3, new Point(new double[]{0.0, 1.2}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<Integer, IShape>();
        secondarySpatial.put(10, new Point(new double[]{0.0, 0.1}));
        secondarySpatial.put(20, new Point(new double[]{0.0, 0.7}));
        secondarySpatial.put(30, new Point(new double[]{0.0, 1.3}));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, docsWithSameTerm(primarySpatial.keySet(), 7001), primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, docsWithSameTerm(secondarySpatial.keySet(), 7001), secondaryIndex);

        JoinTopKMultiSetQueryProcessor processorAB = new JoinTopKMultiSetQueryProcessor(primaryTree);
        JoinTopKMultiSetQueryProcessor processorBA = new JoinTopKMultiSetQueryProcessor(secondaryTree);

        int topK = 9; // Full Cartesian coverage for 3x3 datasets.
        JoinConfiguration configuration = joinConfiguration(JoinStrategy.PLANE_SWEEP, QueryStrategy.CONSTRAINT_TEXTUAL_JOIN);

        List<SKJoinQuery.Result> bestFirstAB = processorAB.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(7001),
                topK,
                configuration);

        List<SKJoinQuery.Result> bestFirstBA = processorBA.selfJoinSKQueryBestFirst(
                secondaryIndex,
                primaryIndex,
                primaryTree,
                queryWithTerm(7001),
                topK,
                configuration);

        List<SKJoinQuery.Result> recursiveAB = processorAB.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                queryWithTerm(7001),
                topK,
                configuration);

        List<SKJoinQuery.Result> recursiveBA = processorBA.selfJoinSKQueryRecursive(
                secondaryIndex,
                primaryIndex,
                primaryTree,
                queryWithTerm(7001),
                topK,
                configuration);

        assertEquals(toPairSet(bestFirstAB), mirroredPairSet(toPairSet(bestFirstBA)),
                "Best-first intersection should be commutative after swapping pair direction.");
        assertEquals(toPairSet(recursiveAB), mirroredPairSet(toPairSet(recursiveBA)),
                "Recursive intersection should be commutative after swapping pair direction.");
    }

    @Test
    void constraintSpatialJoinFiltersPairsOutsideQueryWindow() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<Integer, IShape>();
        primarySpatial.put(1, new Point(new double[]{0.1, 0.1}));
        primarySpatial.put(2, new Point(new double[]{3.0, 3.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<Integer, IShape>();
        secondarySpatial.put(10, new Point(new double[]{0.2, 0.2}));
        secondarySpatial.put(20, new Point(new double[]{3.1, 3.1}));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, docsWithSameTerm(primarySpatial.keySet(), 8101), primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, docsWithSameTerm(secondarySpatial.keySet(), 8101), secondaryIndex);

        JoinTopKMultiSetQueryProcessor processor = new JoinTopKMultiSetQueryProcessor(primaryTree);

        SKJoinQuery windowedQuery = queryWithTerm(8101);
        windowedQuery.setSpatialWindow(new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}));

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                windowedQuery,
                10,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_SPATIAL_JOIN));

        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                windowedQuery,
                10,
                joinConfiguration(JoinStrategy.DEFAULT, QueryStrategy.CONSTRAINT_SPATIAL_JOIN));

        Set<String> expected = new HashSet<String>(Collections.singletonList("1-10"));
        assertEquals(expected, toPairSet(bestFirst));
        assertEquals(expected, toPairSet(recursive));
    }
}

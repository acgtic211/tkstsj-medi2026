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
import org.ual.spatiotextualindex.queries.baseline.join.JoinMultiSetQueryProcessor;

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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinMultiSetQueryProcessorTest {

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
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 1.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        secondarySpatial.put(1, new Point(new double[]{0.0, 0.2}));
        secondarySpatial.put(2, new Point(new double[]{0.0, 1.2}));

        Map<Integer, ArrayList<Integer>> primaryText = singletonTermDocs(1001);
        Map<Integer, ArrayList<Integer>> secondaryText = singletonTermDocs(1001);

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, primaryText, primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, secondaryText, secondaryIndex);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                1,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(1001)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        List<SKJoinQuery.Result> results = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                2.0f,
                0.0f,
                defaultJoinConfiguration()
        );

        Set<String> pairs = toPairSet(results);
        Set<String> expectedPairs = new HashSet<>(Arrays.asList("1-1", "1-2", "2-1", "2-2"));

        assertEquals(4, results.size(), "All directional cross-dataset pairs should be preserved.");
        assertEquals(expectedPairs, pairs);
    }

    @Test
    void recursiveUsesSecondaryTreeForSpatialPruning() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{1.0, 1.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        secondarySpatial.put(1, new Point(new double[]{1000.0, 1000.0}));
        secondarySpatial.put(2, new Point(new double[]{1001.0, 1001.0}));

        Map<Integer, ArrayList<Integer>> primaryText = singletonTermDocs(1002);
        Map<Integer, ArrayList<Integer>> secondaryText = singletonTermDocs(1002);

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, primaryText, primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, secondaryText, secondaryIndex);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                2,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(1002)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        List<SKJoinQuery.Result> bestFirst = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                5.0f,
                0.0f,
                defaultJoinConfiguration()
        );

        List<SKJoinQuery.Result> recursive = processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                5.0f,
                0.0f,
                defaultJoinConfiguration()
        );

        assertTrue(bestFirst.isEmpty(), "Best-first should prune all pairs when datasets are far apart.");
        assertTrue(recursive.isEmpty(), "Recursive traversal should prune using the secondary tree geometry.");
    }

    @Test
    void fullJoinUsesNodeLevelRelevanceFallbackWhenQueryKeywordsDoNotMatch() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 1.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        secondarySpatial.put(1, new Point(new double[]{0.0, 0.3}));
        secondarySpatial.put(2, new Point(new double[]{0.0, 1.3}));

        // Keep one shared term with positive IDF (present in only one document) and one unique term per doc.
        Map<Integer, ArrayList<Integer>> primaryText = new LinkedHashMap<>();
        primaryText.put(1, new ArrayList<>(Arrays.asList(2001, 2101)));
        primaryText.put(2, new ArrayList<>(Collections.singletonList(2102)));

        Map<Integer, ArrayList<Integer>> secondaryText = new LinkedHashMap<>();
        secondaryText.put(1, new ArrayList<>(Arrays.asList(2001, 2201)));
        secondaryText.put(2, new ArrayList<>(Collections.singletonList(2202)));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, primaryText, primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, secondaryText, secondaryIndex);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                3,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(9999)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        List<SKJoinQuery.Result> partialJoinResults = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                3.0f,
                1.0e-6f,
                joinConfiguration(QueryStrategy.PARTIAL_JOIN)
        );

        List<SKJoinQuery.Result> fullJoinResults = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                3.0f,
                1.0e-6f,
                joinConfiguration(QueryStrategy.FULL_JOIN)
        );

        assertTrue(partialJoinResults.isEmpty(),
                "PARTIAL_JOIN should prune when query keywords are absent from both datasets.");
        assertFalse(fullJoinResults.isEmpty(),
                "FULL_JOIN should still return pairs via node-level relevance fallback.");
    }

    @Test
    void fullJoinUsesNodeLevelRelevanceFallbackWhenQueryKeywordsDoNotMatchRecursive() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 1.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        secondarySpatial.put(1, new Point(new double[]{0.0, 0.3}));
        secondarySpatial.put(2, new Point(new double[]{0.0, 1.3}));

        Map<Integer, ArrayList<Integer>> primaryText = new LinkedHashMap<>();
        primaryText.put(1, new ArrayList<>(Arrays.asList(2001, 2101)));
        primaryText.put(2, new ArrayList<>(Collections.singletonList(2102)));

        Map<Integer, ArrayList<Integer>> secondaryText = new LinkedHashMap<>();
        secondaryText.put(1, new ArrayList<>(Arrays.asList(2001, 2201)));
        secondaryText.put(2, new ArrayList<>(Collections.singletonList(2202)));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, primaryText, primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, secondaryText, secondaryIndex);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                4,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(9999)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        List<SKJoinQuery.Result> partialJoinResults = processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                3.0f,
                1.0e-6f,
                joinConfiguration(QueryStrategy.PARTIAL_JOIN)
        );

        List<SKJoinQuery.Result> fullJoinResults = processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                3.0f,
                1.0e-6f,
                joinConfiguration(QueryStrategy.FULL_JOIN)
        );

        assertTrue(partialJoinResults.isEmpty(),
                "Recursive PARTIAL_JOIN should prune when query keywords are absent from both datasets.");
        assertFalse(fullJoinResults.isEmpty(),
                "Recursive FULL_JOIN should still return pairs via node-level relevance fallback.");
    }

    @Test
    void fullJoinWeightedJaccardDoesNotRequireMatchingObjectIdsAcrossDatasets() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 1.0}));

        // Secondary IDs intentionally do not overlap with the primary ID space.
        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        secondarySpatial.put(100, new Point(new double[]{0.0, 0.2}));
        secondarySpatial.put(200, new Point(new double[]{0.0, 1.2}));

        Map<Integer, ArrayList<Integer>> primaryText = new LinkedHashMap<>();
        primaryText.put(1, new ArrayList<>(Arrays.asList(3001, 3101)));
        primaryText.put(2, new ArrayList<>(Collections.singletonList(3102)));

        Map<Integer, ArrayList<Integer>> secondaryText = new LinkedHashMap<>();
        secondaryText.put(100, new ArrayList<>(Arrays.asList(3001, 3201)));
        secondaryText.put(200, new ArrayList<>(Collections.singletonList(3202)));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, primaryText, primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, secondaryText, secondaryIndex);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                5,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(9999)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        List<SKJoinQuery.Result> fullJoinResults = processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                3.0f,
                1.0e-6f,
                new JoinConfiguration(
                        ThresholdPolicy.COMBINED_COST,
                        JoinStrategy.DEFAULT,
                        SimilarityType.WEIGHTED_JACCARD,
                        QueryStrategy.FULL_JOIN)
        );

        assertFalse(fullJoinResults.isEmpty(),
                "FULL_JOIN WEIGHTED_JACCARD should not depend on matching object IDs across datasets.");
    }

    @Test
    void recursiveJoinHandlesAsymmetricTreeHeightWithoutTreatingNodeIdsAsDocIds() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        for (int i = 100; i < 112; i++) {
            secondarySpatial.put(i, new Point(new double[]{(i - 100) * 0.1, 0.0}));
        }

        Map<Integer, ArrayList<Integer>> primaryText = singletonTermDocs(5001);

        Map<Integer, ArrayList<Integer>> secondaryText = new LinkedHashMap<>();
        for (int i = 100; i < 112; i++) {
            secondaryText.put(i, new ArrayList<>(Collections.singletonList(5001)));
        }

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, primaryText, primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, secondaryText, secondaryIndex);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                6,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(5001)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        assertDoesNotThrow(() -> processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                1000.0f,
                0.0f,
                defaultJoinConfiguration()));
    }

    @Test
    void bestFirstJoinHandlesAsymmetricTreeHeightWithoutTreatingNodeIdsAsDocIds() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        for (int i = 100; i < 112; i++) {
            secondarySpatial.put(i, new Point(new double[]{(i - 100) * 0.1, 0.0}));
        }

        Map<Integer, ArrayList<Integer>> primaryText = new LinkedHashMap<>();
        primaryText.put(1, new ArrayList<>(Collections.singletonList(5101)));

        Map<Integer, ArrayList<Integer>> secondaryText = new LinkedHashMap<>();
        for (int i = 100; i < 112; i++) {
            secondaryText.put(i, new ArrayList<>(Collections.singletonList(5101)));
        }

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, primaryText, primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, secondaryText, secondaryIndex);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                7,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(5101)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        List<SKJoinQuery.Result> results = assertDoesNotThrow(() -> processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                1000.0f,
                0.0f,
                defaultJoinConfiguration()));

        assertEquals(12, results.size(),
                "Best-first should return one pair per secondary object when primary has one object.");
        assertTrue(results.stream().allMatch(r -> r.getPairId1() == 1),
                "Primary side should contain only valid primary object IDs.");
        assertTrue(results.stream().allMatch(r -> r.getPairId2() >= 100 && r.getPairId2() < 112),
                "Secondary side should contain only valid secondary object IDs.");
    }

    @Test
    void bestFirstPlaneSweepHandlesAsymmetricHeightWithDifferentFanout() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        for (int i = 400; i < 450; i++) {
            secondarySpatial.put(i, new Point(new double[]{(i - 400) * 0.02, 0.0}));
        }

        Map<Integer, ArrayList<Integer>> primaryText = new LinkedHashMap<>();
        primaryText.put(1, new ArrayList<>(Collections.singletonList(5401)));

        Map<Integer, ArrayList<Integer>> secondaryText = new LinkedHashMap<>();
        for (int i = 400; i < 450; i++) {
            secondaryText.put(i, new ArrayList<>(Collections.singletonList(5401)));
        }

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTreeWithCapacities(primarySpatial, primaryText, primaryIndex, 4, 4);
        IRTree secondaryTree = buildTreeWithCapacities(secondarySpatial, secondaryText, secondaryIndex, 16, 16);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                10,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(5401)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        List<SKJoinQuery.Result> results = assertDoesNotThrow(() -> processor.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                1000.0f,
                0.0f,
                joinConfiguration(JoinStrategy.PLANE_SWEEP, QueryStrategy.PARTIAL_JOIN)));

        assertEquals(50, results.size(),
                "Plane-sweep best-first should preserve valid pairs under asymmetric height.");
        assertTrue(results.stream().allMatch(r -> r.getPairId1() == 1));
        assertTrue(results.stream().allMatch(r -> r.getPairId2() >= 400 && r.getPairId2() < 450));
    }

    @Test
    void recursiveJoinHandlesAsymmetricHeightWithDifferentFanout() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        for (int i = 300; i < 350; i++) {
            secondarySpatial.put(i, new Point(new double[]{(i - 300) * 0.02, 0.0}));
        }

        Map<Integer, ArrayList<Integer>> primaryText = new LinkedHashMap<>();
        primaryText.put(1, new ArrayList<>(Collections.singletonList(5301)));

        Map<Integer, ArrayList<Integer>> secondaryText = new LinkedHashMap<>();
        for (int i = 300; i < 350; i++) {
            secondaryText.put(i, new ArrayList<>(Collections.singletonList(5301)));
        }

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTreeWithCapacities(primarySpatial, primaryText, primaryIndex, 4, 4);
        IRTree secondaryTree = buildTreeWithCapacities(secondarySpatial, secondaryText, secondaryIndex, 16, 16);

        JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(primaryTree);
        SKJoinQuery query = new SKJoinQuery(
                9,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(5301)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        List<SKJoinQuery.Result> results = assertDoesNotThrow(() -> processor.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                1000.0f,
                0.0f,
                defaultJoinConfiguration()));

        assertEquals(50, results.size(), "Recursive traversal should keep valid pair coverage under different fanout.");
        assertTrue(results.stream().allMatch(r -> r.getPairId1() == 1));
        assertTrue(results.stream().allMatch(r -> r.getPairId2() >= 300 && r.getPairId2() < 350));
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
        WeightCompute.ComputeTF_IDFWeights(new HashMap<>(textual), weightStore, 0.0);
        tree.createIRTree(weightStore, documentIndex);

        return tree;
    }

    private static Map<Integer, ArrayList<Integer>> singletonTermDocs(int termId) {
        Map<Integer, ArrayList<Integer>> docs = new LinkedHashMap<>();
        docs.put(1, new ArrayList<>(Collections.singletonList(termId)));
        docs.put(2, new ArrayList<>(Collections.singletonList(termId)));
        return docs;
    }

    private static Set<String> toPairSet(List<SKJoinQuery.Result> results) {
        return results.stream()
                .map(r -> r.getPairId1() + "-" + r.getPairId2())
                .collect(Collectors.toSet());
    }

    private static JoinConfiguration defaultJoinConfiguration() {
        return joinConfiguration(QueryStrategy.PARTIAL_JOIN);
    }

    private static JoinConfiguration joinConfiguration(JoinStrategy joinStrategy, QueryStrategy queryStrategy) {
        return new JoinConfiguration(
                ThresholdPolicy.COMBINED_COST,
                joinStrategy,
                SimilarityType.WEIGHTED_SUM,
                queryStrategy
        );
    }

    private static JoinConfiguration joinConfiguration(QueryStrategy queryStrategy) {
        return joinConfiguration(JoinStrategy.DEFAULT, queryStrategy);
    }

    @Test
    void crossDatasetIntersectionIsCommutativeWhenSwappingPrimaryAndSecondary() {
        Map<Integer, IShape> primarySpatial = new LinkedHashMap<>();
        primarySpatial.put(1, new Point(new double[]{0.0, 0.0}));
        primarySpatial.put(2, new Point(new double[]{0.0, 0.6}));
        primarySpatial.put(3, new Point(new double[]{0.0, 1.2}));

        Map<Integer, IShape> secondarySpatial = new LinkedHashMap<>();
        secondarySpatial.put(10, new Point(new double[]{0.0, 0.1}));
        secondarySpatial.put(20, new Point(new double[]{0.0, 0.7}));
        secondarySpatial.put(30, new Point(new double[]{0.0, 1.3}));

        IDocumentIndex primaryIndex = new InvertedListIndex(0);
        IDocumentIndex secondaryIndex = new InvertedListIndex(0);
        IRTree primaryTree = buildTree(primarySpatial, docsWithSameTerm(primarySpatial.keySet(), 7002), primaryIndex);
        IRTree secondaryTree = buildTree(secondarySpatial, docsWithSameTerm(secondarySpatial.keySet(), 7002), secondaryIndex);

        JoinMultiSetQueryProcessor processorAB = new JoinMultiSetQueryProcessor(primaryTree);
        JoinMultiSetQueryProcessor processorBA = new JoinMultiSetQueryProcessor(secondaryTree);
        SKJoinQuery query = new SKJoinQuery(
                11,
                0.5,
                new Point(new double[]{0.0, 0.0}),
                new ArrayList<>(Collections.singletonList(7002)),
                new ArrayList<>(Collections.singletonList(1.0))
        );

        JoinConfiguration configuration = joinConfiguration(JoinStrategy.PLANE_SWEEP, QueryStrategy.PARTIAL_JOIN);

        List<SKJoinQuery.Result> bestFirstAB = processorAB.selfJoinSKQueryBestFirst(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                5.0f,
                0.0f,
                configuration);

        List<SKJoinQuery.Result> bestFirstBA = processorBA.selfJoinSKQueryBestFirst(
                secondaryIndex,
                primaryIndex,
                primaryTree,
                query,
                5.0f,
                0.0f,
                configuration);

        List<SKJoinQuery.Result> recursiveAB = processorAB.selfJoinSKQueryRecursive(
                primaryIndex,
                secondaryIndex,
                secondaryTree,
                query,
                5.0f,
                0.0f,
                configuration);

        List<SKJoinQuery.Result> recursiveBA = processorBA.selfJoinSKQueryRecursive(
                secondaryIndex,
                primaryIndex,
                primaryTree,
                query,
                5.0f,
                0.0f,
                configuration);

        assertEquals(toPairSet(bestFirstAB), mirroredPairSet(toPairSet(bestFirstBA)),
                "Best-first intersection should be commutative after swapping pair direction.");
        assertEquals(toPairSet(recursiveAB), mirroredPairSet(toPairSet(recursiveBA)),
                "Recursive intersection should be commutative after swapping pair direction.");
    }

    private static Map<Integer, ArrayList<Integer>> docsWithSameTerm(Set<Integer> ids, int termId) {
        Map<Integer, ArrayList<Integer>> docs = new LinkedHashMap<>();
        for (Integer id : ids) {
            docs.put(id, new ArrayList<>(Collections.singletonList(termId)));
        }
        return docs;
    }

    private static Set<String> mirroredPairSet(Set<String> pairs) {
        Set<String> mirrored = new HashSet<>();
        for (String pair : pairs) {
            String[] ids = pair.split("-", 2);
            mirrored.add(ids[1] + "-" + ids[0]);
        }
        return mirrored;
    }
}

package org.ual.spatiotextualindex.cdirtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ual.algorithm.kmean.KMean;
import org.ual.document.WeightCompute;
import org.ual.documentindex.invertedlist.InvertedListIndex;
//import org.ual.documentindex.signedblock.SignedBlockInvertedIndex;
//import org.ual.documentindex.signedblocknew.SignedBlockInvertedIndexNEW;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.rtree.Statistics;
import org.ual.spatialindex.spatialindex.IShape;
import org.ual.spatialindex.spatialindex.Point;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Architecture tests for CDIRTree (Clustered Document Inverted R-tree).
 * Tests tree initialization, structure, cluster integration, document-aware operations, and node management.
 * Follows the same principles as IRTreeArchitectureTest.
 */
@DisplayName("CDIRTree Architecture Tests")
public class CDIRTreeArchitectureTest {
    private static final Logger log = LogManager.getLogger(CDIRTreeArchitectureTest.class);

    /**
     * Tests for basic tree initialization and configuration
     */
    @Nested
    @DisplayName("Tree Initialization")
    class TreeInitialization {
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore documentStore;

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
            propertySet.setProperty("BetaArea", 0.5f);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should create CDIRTree with valid configuration")
        void testCreateCDIRTreeWithValidConfiguration(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            CDIRTree tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);

            assertNotNull(tree, "CDIRTree instance should be created");
            assertNotNull(tree.getStatistics(), "Statistics should be initialized");
            assertEquals(2, tree.getDimension(), "Tree dimension should be 2");
            assertNotNull(tree.getDocumentStore(), "Document store should be initialized");
            assertNotNull(tree.getDocumentNodeMapping(), "Document node mapping should be initialized");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should initialize with empty tree structure")
        void testInitializeEmptyTree(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            CDIRTree tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            Statistics stats = (Statistics) tree.getStatistics();

            assertNotNull(stats, "Statistics should not be null");
            assertEquals(0, stats.getNumberOfData(), "Empty tree should have 0 data points");
            assertTrue(stats.getNumberOfNodes() >= 1, "Tree should have at least root node");
            assertTrue(tree.getDocumentNodeMapping().isEmpty(), "Document mapping should be empty");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should respect tree configuration properties")
        void testTreeConfigurationProperties(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            CDIRTree tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);

            assertNotNull(tree, "Tree should be created");
            assertEquals(2, tree.getDimension(), "Tree dimension should match configuration");
            assertEquals(0.5f, tree.getBetaArea(), 0.001f, "Beta area should match configuration");
        }

        @Test
        @DisplayName("Should validate beta area parameter")
        void testValidateBetaAreaParameter() {
            CDIRTree tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);

            assertThrows(IllegalArgumentException.class, () -> tree.setBetaArea(0.0f),
                    "Should reject beta area of 0");
            assertThrows(IllegalArgumentException.class, () -> tree.setBetaArea(1.0f),
                    "Should reject beta area of 1");
            assertThrows(IllegalArgumentException.class, () -> tree.setBetaArea(-0.1f),
                    "Should reject negative beta area");

            assertDoesNotThrow(() -> tree.setBetaArea(0.5f), "Should accept valid beta area");
        }
    }

    /**
     * Tests for tree growth with document-aware insertions
     */
    @Nested
    @DisplayName("Tree Growth with Document-Aware Data")
    class TreeGrowthWithDocumentData {
        private CDIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore documentStore;
        private InvertedListIndex documentIndex;
        private HashMap<Integer, Integer> clusterTree;
        private int numberOfClusters = 2;

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
            propertySet.setProperty("BetaArea", 0.5f);
            propertySet.setProperty("NumberOfClusters", numberOfClusters);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            documentIndex = new InvertedListIndex(numberOfClusters);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should insert document-aware spatial data")
        void testInsertDocumentAwareSpatialData(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
            spatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
            spatialObjects.put(3, new Point(new double[]{12.0, 22.0}));

            textualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102, 103)));
            textualObjects.put(2, new ArrayList<>(Arrays.asList(102, 103)));
            textualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));

            WeightCompute.ComputeTermWeights(textualObjects, documentStore, 0.5);

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                HashSet<Integer> docIds = documentStore.readSet(entry.getKey());
                tree.insertData(entry.getKey(), entry.getValue(), docIds);
            }

            Statistics stats = (Statistics) tree.getStatistics();
            assertEquals(3, stats.getNumberOfData(), "Tree should contain 3 data points");
            assertTrue(stats.getNumberOfNodes() >= 1, "Tree should have at least one node");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should create CDIRTree with cluster information")
        void testCreateCDIRTreeWithClusterInfo(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            documentIndex = new InvertedListIndex(numberOfClusters);
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
            spatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
            spatialObjects.put(3, new Point(new double[]{12.0, 22.0}));

            textualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102, 103)));
            textualObjects.put(2, new ArrayList<>(Arrays.asList(102, 103)));
            textualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));

            WeightCompute.ComputeTermWeights(textualObjects, documentStore, 0.5);

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                HashSet<Integer> docIds = documentStore.readSet(entry.getKey());
                tree.insertData(entry.getKey(), entry.getValue(), docIds);
            }

            clusterTree = KMean.calculateKMean(documentStore, numberOfClusters, 10);

            assertDoesNotThrow(() -> tree.createCDIRTree(clusterTree, documentStore, documentIndex),
                    "Creating CDIRTree should not throw exception");

            assertNotNull(documentIndex, "Document index should be initialized");
            assertTrue(documentIndex.getTotalDocuments() >= 3,
                    "Document index should contain at least original documents");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should maintain document node mapping")
        void testMaintainDocumentNodeMapping(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            for (int i = 1; i <= 5; i++) {
                spatialObjects.put(i, new Point(new double[]{i * 5.0, i * 10.0}));
                textualObjects.put(i, new ArrayList<>(Arrays.asList(100 + i, 200 + i)));
            }

            WeightCompute.ComputeTermWeights(textualObjects, documentStore, 0.5);

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                HashSet<Integer> docIds = documentStore.readSet(entry.getKey());
                tree.insertData(entry.getKey(), entry.getValue(), docIds);
            }

            HashMap<Integer, HashSet<Integer>> docMapping = tree.getDocumentNodeMapping();
            assertNotNull(docMapping, "Document node mapping should not be null");
            assertTrue(docMapping.size() > 0, "Document node mapping should have entries");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should handle document insertions with empty document sets")
        void testHandleEmptyDocumentSets(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            Point point = new Point(new double[]{10.0, 20.0});
            HashSet<Integer> emptyDocSet = new HashSet<>();

            assertDoesNotThrow(() -> tree.insertData(1, point, emptyDocSet),
                    "Should handle insertion with empty document set");
        }
    }

    /**
     * Tests for node capacity and splitting behavior
     */
    @Nested
    @DisplayName("Node Capacity and Splitting")
    class NodeCapacityAndSplitting {
        private CDIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore documentStore;

        @BeforeEach
        void setUp() {
            storageManager = new NodeStorageManager();
            propertySet = new PropertySet();
            propertySet.setProperty("Dimension", 2);
            propertySet.setProperty("IndexCapacity", 4);
            propertySet.setProperty("LeafCapacity", 4);
            propertySet.setProperty("FillFactor", 0.7f);
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
            propertySet.setProperty("NearMinimumOverlapFactor", 2);
            propertySet.setProperty("BetaArea", 0.5f);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should trigger node split when capacity exceeded")
        void testNodeSplitWhenCapacityExceeded(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            Statistics statsBefore = (Statistics) tree.getStatistics();
            long nodesBefore = statsBefore.getNumberOfNodes();

            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                textualObjects.put(i, new ArrayList<>(Collections.singletonList(100 + i)));
            }
            WeightCompute.ComputeTermWeights(textualObjects, documentStore, 0.5);

            for (int i = 0; i < 10; i++) {
                HashSet<Integer> docIds = documentStore.readSet(i);
                tree.insertData(i, new Point(new double[]{i * 10.0, i * 10.0}), docIds);
            }

            Statistics statsAfter = (Statistics) tree.getStatistics();
            long nodesAfter = statsAfter.getNumberOfNodes();

            assertTrue(nodesAfter > nodesBefore, "Number of nodes should increase after split");
            assertEquals(10, statsAfter.getNumberOfData(), "All data points should be inserted");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cdirtree.CDIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should maintain tree balance after multiple insertions")
        void testTreeBalanceAfterMultipleInsertions(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();
            for (int i = 0; i < 20; i++) {
                textualObjects.put(i, new ArrayList<>(Collections.singletonList(100 + i)));
            }
            WeightCompute.ComputeTermWeights(textualObjects, documentStore, 0.5);

            for (int i = 0; i < 20; i++) {
                HashSet<Integer> docIds = documentStore.readSet(i);
                tree.insertData(i, new Point(new double[]{Math.random() * 100, Math.random() * 100}), docIds);
            }

            Statistics stats = (Statistics) tree.getStatistics();
            assertEquals(20, stats.getNumberOfData(), "All data should be inserted");
            assertTrue(stats.getTreeHeight() >= 0, "Tree height should be valid");
        }
    }

    /**
     * Tests for K-Means clustering integration
     */
    @Nested
    @DisplayName("K-Means Clustering Integration")
    class KMeansClusteringIntegration {
        private CDIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore documentStore;
        private HashMap<Integer, Integer> clusterTree;
        private int numberOfClusters = 3;

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
            propertySet.setProperty("BetaArea", 0.5f);
            propertySet.setProperty("NumberOfClusters", numberOfClusters);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
        }

        @Test
        @DisplayName("Should assign all documents to valid clusters")
        void testAllDocumentsAssignedToValidClusters() {
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            for (int i = 1; i <= 6; i++) {
                spatialObjects.put(i, new Point(new double[]{i * 5.0, i * 5.0}));
                textualObjects.put(i, new ArrayList<>(Arrays.asList(100 + i, 200 + i)));
            }

            WeightCompute.ComputeTermWeights(textualObjects, documentStore, 0.5);

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                HashSet<Integer> docIds = documentStore.readSet(entry.getKey());
                tree.insertData(entry.getKey(), entry.getValue(), docIds);
            }

            clusterTree = KMean.calculateKMean(documentStore, numberOfClusters, 10);

            assertNotNull(clusterTree, "Cluster tree should not be null");
            assertEquals(6, clusterTree.size(), "All documents should be assigned to clusters");

            for (Integer docId : textualObjects.keySet()) {
                assertTrue(clusterTree.containsKey(docId), "Document " + docId + " should be in cluster tree");
                Integer clusterId = clusterTree.get(docId);
                assertNotNull(clusterId, "Cluster ID should not be null");
                assertTrue(clusterId >= 0 && clusterId < numberOfClusters,
                        "Cluster ID should be in valid range [0, " + (numberOfClusters - 1) + "]");
            }
        }
    }

    /**
     * Tests for inverted file index integration
     */
    @Nested
    @DisplayName("Inverted File Index Integration")
    class InvertedFileIndexIntegration {
        private CDIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore documentStore;
        private InvertedListIndex documentIndex;
        private HashMap<Integer, Integer> clusterTree;
        private int numberOfClusters = 2;

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
            propertySet.setProperty("BetaArea", 0.5f);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            documentIndex = new InvertedListIndex(numberOfClusters);
        }

        @Test
        @DisplayName("Should build inverted file index correctly")
        void testBuildInvertedFileIndex() {
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
            spatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
            spatialObjects.put(3, new Point(new double[]{12.0, 22.0}));

            textualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102, 103)));
            textualObjects.put(2, new ArrayList<>(Arrays.asList(102, 103)));
            textualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));

            WeightCompute.ComputeTermWeights(textualObjects, documentStore, 0.5);

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                HashSet<Integer> docIds = documentStore.readSet(entry.getKey());
                tree.insertData(entry.getKey(), entry.getValue(), docIds);
            }

            clusterTree = KMean.calculateKMean(documentStore, numberOfClusters, 10);
            tree.createCDIRTree(clusterTree, documentStore, documentIndex);

            assertNotNull(documentIndex, "Document index should not be null");
            assertTrue(documentIndex.getTotalDocuments() >= 3, "Document index should contain documents");
            assertTrue(documentIndex.getDocumentFrequency(101) > 0, "Term 101 should be indexed");
            assertTrue(documentIndex.getDocumentFrequency(102) > 0, "Term 102 should be indexed");
        }
    }

    @Nested
    @DisplayName("Aggregate Ingestion Regression")
    class AggregateIngestionRegression {
        private CDIRTree tree;
        private AbstractDocumentStore documentStore;

        @BeforeEach
        void setUp() {
            NodeStorageManager storageManager = new NodeStorageManager();
            PropertySet propertySet = new PropertySet();
            propertySet.setProperty("Dimension", 2);
            propertySet.setProperty("IndexCapacity", 3);
            propertySet.setProperty("LeafCapacity", 3);
            propertySet.setProperty("FillFactor", 0.7f);
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
            propertySet.setProperty("NearMinimumOverlapFactor", 2);
            propertySet.setProperty("BetaArea", 0.5f);
            propertySet.setProperty("NumberOfClusters", 2);

            DatasetParameters datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
        }

//        @Test
//        @DisplayName("Should build CDIRTree with signed-block aggregate index without ingestion exception")
//        void createCDIRTreeWithSignedBlockAggregateIndex() {
//            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
//            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();
//            HashMap<Integer, Integer> clusterTree = new HashMap<>();
//
//            for (int id = 1; id <= 6; id++) {
//                spatialObjects.put(id, new Point(new double[]{id * 4.0, id * 6.0}));
//                textualObjects.put(id, new ArrayList<>(Arrays.asList(100 + id, 250 + (id % 2), 350)));
//                clusterTree.put(id, id % 2);
//            }
//
//            WeightCompute.ComputeTermWeights(textualObjects, documentStore, 0.5);
//            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
//                tree.insertData(entry.getKey(), entry.getValue(), documentStore.readSet(entry.getKey()));
//            }
//
//            Statistics stats = (Statistics) tree.getStatistics();
//            assertTrue(stats.getTreeHeight() > 1,
//                    "Tree should have internal nodes so both leaf and internal ingestion paths are executed");
//
//            SignedBlockInvertedIndexNEW signedBlockIndex = new SignedBlockInvertedIndexNEW(tree.getRoot().getMBR());
//            assertDoesNotThrow(() -> tree.createCDIRTree(clusterTree, documentStore, signedBlockIndex));
//
//            int rootId = tree.getRootIdentifier();
//            assertNotNull(signedBlockIndex.getIndexEntry(rootId), "Root entry must be populated");
//
//            Map<Integer, Double> scores = signedBlockIndex.rankingSumClusterEnhance(
//                    rootId,
//                    Collections.singletonList(350),
//                    Collections.singletonList(1.0)
//            );
//            assertFalse(scores.isEmpty(), "Signed-block index should expose searchable root postings");
//        }
    }

    /**
     * Tests for edge cases and boundary conditions
     */
    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditions {
        private CDIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore documentStore;
        private InvertedListIndex documentIndex;

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
            propertySet.setProperty("BetaArea", 0.5f);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            documentStore = new HashMapDocumentStore();
            AbstractDocumentStore.maxWord = 1000;
            tree = new CDIRTree(propertySet, storageManager, documentStore, datasetParameters);
            documentIndex = new InvertedListIndex(2);
        }

        @Test
        @DisplayName("Should handle null parameters in createCDIRTree")
        void testCreateCDIRTreeWithNullParameters() {
            HashMap<Integer, Integer> clusterTree = new HashMap<>();
            clusterTree.put(1, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> tree.createCDIRTree(null, documentStore, documentIndex),
                    "Should throw exception when cluster tree is null");

            assertThrows(IllegalArgumentException.class,
                    () -> tree.createCDIRTree(clusterTree, null, documentIndex),
                    "Should throw exception when document store is null");

            assertThrows(IllegalArgumentException.class,
                    () -> tree.createCDIRTree(clusterTree, documentStore, null),
                    "Should throw exception when inverted file is null");
        }

        @Test
        @DisplayName("Should reject insertData without document parameter")
        void testRejectInsertDataWithoutDocument() {
            Point point = new Point(new double[]{10.0, 20.0});

            assertThrows(UnsupportedOperationException.class,
                    () -> tree.insertData(1, point),
                    "Should reject insertData without document parameter");
        }

        @Test
        @DisplayName("Should handle dimension mismatch")
        void testHandleDimensionMismatch() {
            Point point3D = new Point(new double[]{10.0, 20.0, 30.0});
            HashSet<Integer> docIds = new HashSet<>();

            assertThrows(IllegalArgumentException.class,
                    () -> tree.insertData(1, point3D, docIds),
                    "Should reject shape with wrong dimension");
        }
    }

    static Stream<Arguments> provideRTreeVariants() {
        return Stream.of(
                Arguments.of(Named.of("Linear", SpatialIndex.RtreeVariantLinear)),
                Arguments.of(Named.of("Quadratic", SpatialIndex.RtreeVariantQuadratic)),
                Arguments.of(Named.of("Rstar", SpatialIndex.RtreeVariantRstar))
        );
    }
}

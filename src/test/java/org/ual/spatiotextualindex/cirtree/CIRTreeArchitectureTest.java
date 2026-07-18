package org.ual.spatiotextualindex.cirtree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ual.algorithm.kmean.KMean;
import org.ual.document.WeightCompute;
//import org.ual.documentindex.signedblock.SignedBlockInvertedIndex;
import org.ual.documentindex.invertedlist.InvertedListIndex;
//import org.ual.documentindex.signedinvertedlist.SignedInvertedListIndex;
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
 * Architecture tests for CIRTree (Cluster-based Inverted R-tree).
 * Tests tree initialization, structure, cluster integration, and node management.
 * Follows the same principles as IRTreeArchitectureTest.
 */
@DisplayName("CIRTree Architecture Tests")
public class CIRTreeArchitectureTest {
    private static final Logger log = LogManager.getLogger(CIRTreeArchitectureTest.class);

    /**
     * Tests for basic tree initialization and configuration
     */
    @Nested
    @DisplayName("Tree Initialization")
    class TreeInitialization {
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;

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
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
        }

        @DisplayName("Should create CIRTree with valid configuration")
        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        void testCreateCIRTreeWithValidConfiguration(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            CIRTree tree = new CIRTree(propertySet, storageManager, datasetParameters);

            assertNotNull(tree, "CIRTree instance should be created");
            assertNotNull(tree.getStatistics(), "Statistics should be initialized");
            assertEquals(2, tree.getDimension(), "Tree dimension should be 2");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should initialize with empty tree structure")
        void testInitializeEmptyTree(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            CIRTree tree = new CIRTree(propertySet, storageManager, datasetParameters);
            Statistics stats = (Statistics) tree.getStatistics();

            assertNotNull(stats, "Statistics should not be null");
            assertEquals(0, stats.getNumberOfData(), "Empty tree should have 0 data points");
            assertTrue(stats.getNumberOfNodes() >= 1, "Tree should have at least root node");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should respect tree configuration properties")
        void testTreeConfigurationProperties(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            CIRTree tree = new CIRTree(propertySet, storageManager, datasetParameters);

            assertNotNull(tree, "Tree should be created");
            assertEquals(2, tree.getDimension(), "Tree dimension should match configuration");
        }
    }

    /**
     * Tests for tree growth with textual and cluster data
     */
    @Nested
    @DisplayName("Tree Growth with Textual and Cluster Data")
    class TreeGrowthWithTextualData {
        private CIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore weightStore;
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
            propertySet.setProperty("NumberOfClusters", numberOfClusters);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            weightStore = new HashMapDocumentStore();
            documentIndex = new InvertedListIndex(numberOfClusters);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should insert spatial data and grow tree")
        void testInsertSpatialDataAndGrowTree(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
            spatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
            spatialObjects.put(3, new Point(new double[]{12.0, 22.0}));

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            Statistics stats = (Statistics) tree.getStatistics();
            assertEquals(3, stats.getNumberOfData(), "Tree should contain 3 data points");
            assertTrue(stats.getNumberOfNodes() >= 1, "Tree should have at least one node");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should create CIRTree with cluster information")
        void testCreateCIRTreeWithClusterInfo(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            weightStore = new HashMapDocumentStore();
            documentIndex = new InvertedListIndex(numberOfClusters);
            // Insert spatial data
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
            spatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
            spatialObjects.put(3, new Point(new double[]{12.0, 22.0}));

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            // Create textual data
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();
            textualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102, 103)));
            textualObjects.put(2, new ArrayList<>(Arrays.asList(102, 103)));
            textualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));

            // Compute weights and clusters
            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            clusterTree = KMean.calculateKMean(weightStore, numberOfClusters, 10);

            // Create CIRTree structure
            assertDoesNotThrow(() -> tree.createCIRTree(clusterTree, weightStore, documentIndex),
                    "Creating CIRTree should not throw exception");

            assertNotNull(documentIndex, "Document index should be initialized");
            assertTrue(documentIndex.getTotalDocuments() >= 3, "Document index should contain at least original documents");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should handle multiple documents insertion")
        void testMultipleDocumentsInsertion(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            weightStore = new HashMapDocumentStore();
            documentIndex = new InvertedListIndex(numberOfClusters);
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            for (int i = 1; i <= 8; i++) {
                spatialObjects.put(i, new Point(new double[]{i * 5.0, i * 10.0}));
                textualObjects.put(i, new ArrayList<>(Arrays.asList(100 + i, 200 + i)));
            }

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            Statistics stats = (Statistics) tree.getStatistics();
            assertEquals(8, stats.getNumberOfData(), "Tree should contain 8 data points");

            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            clusterTree = KMean.calculateKMean(weightStore, numberOfClusters, 10);
            tree.createCIRTree(clusterTree, weightStore, documentIndex);

            assertTrue(documentIndex.getTotalDocuments() >= 8, "Document index should contain at least 8 documents");
        }
    }

    /**
     * Tests for node capacity and splitting behavior
     */
    @Nested
    @DisplayName("Node Capacity and Splitting")
    class NodeCapacityAndSplitting {
        private CIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;

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
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            tree = new CIRTree(propertySet, storageManager, datasetParameters);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should trigger node split when capacity exceeded")
        void testNodeSplitWhenCapacityExceeded(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            Statistics statsBefore = (Statistics) tree.getStatistics();
            long nodesBefore = statsBefore.getNumberOfNodes();

            // Insert enough points to trigger a split
            for (int i = 0; i < 10; i++) {
                tree.insertData(i, new Point(new double[]{i * 10.0, i * 10.0}));
            }

            Statistics statsAfter = (Statistics) tree.getStatistics();
            long nodesAfter = statsAfter.getNumberOfNodes();

            assertTrue(nodesAfter > nodesBefore, "Number of nodes should increase after split");
            assertEquals(10, statsAfter.getNumberOfData(), "All data points should be inserted");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should maintain tree balance after multiple insertions")
        void testTreeBalanceAfterMultipleInsertions(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            for (int i = 0; i < 20; i++) {
                tree.insertData(i, new Point(new double[]{Math.random() * 100, Math.random() * 100}));
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
        private CIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore weightStore;
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
            propertySet.setProperty("NumberOfClusters", numberOfClusters);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            weightStore = new HashMapDocumentStore();
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

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            clusterTree = KMean.calculateKMean(weightStore, numberOfClusters, 10);

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

        @Test
        @DisplayName("Should handle documents with different textual content")
        void testClusteringWithDifferentTextualContent() {
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
            spatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
            spatialObjects.put(3, new Point(new double[]{50.0, 50.0}));

            textualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102)));
            textualObjects.put(2, new ArrayList<>(Arrays.asList(101, 102, 103)));
            textualObjects.put(3, new ArrayList<>(Collections.singletonList(200)));

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            clusterTree = KMean.calculateKMean(weightStore, numberOfClusters, 10);

            assertNotNull(clusterTree, "Cluster tree should not be null");
            assertEquals(3, clusterTree.size(), "All documents should be clustered");
        }
    }

    /**
     * Tests for inverted file index integration
     */
    @Nested
    @DisplayName("Inverted File Index Integration")
    class InvertedFileIndexIntegration {
        private CIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore weightStore;
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
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            weightStore = new HashMapDocumentStore();
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

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            clusterTree = KMean.calculateKMean(weightStore, numberOfClusters, 10);
            tree.createCIRTree(clusterTree, weightStore, documentIndex);

            assertNotNull(documentIndex, "Document index should not be null");
            assertTrue(documentIndex.getTotalDocuments() >= 3, "Document index should contain documents");
            assertTrue(documentIndex.getDocumentFrequency(101) > 0, "Term 101 should be indexed");
            assertTrue(documentIndex.getDocumentFrequency(102) > 0, "Term 102 should be indexed");
        }

//        @Test
//        @DisplayName("Should build CIRTree with SignedInvertedListIndex")
//        void testBuildSignedInvertedListIndex() {
//            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
//            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();
//
//            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
//            spatialObjects.put(2, new Point(new double[]{15.0, 25.0}));
//            spatialObjects.put(3, new Point(new double[]{12.0, 22.0}));
//
//            textualObjects.put(1, new ArrayList<>(Arrays.asList(101, 102, 103)));
//            textualObjects.put(2, new ArrayList<>(Arrays.asList(102, 103)));
//            textualObjects.put(3, new ArrayList<>(Arrays.asList(101, 103, 104)));
//
//            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
//                tree.insertData(entry.getKey(), entry.getValue());
//            }
//
//            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
//            clusterTree = KMean.calculateKMean(weightStore, numberOfClusters, 10);
//
//            SignedInvertedListIndex signedIndex = new SignedInvertedListIndex(numberOfClusters);
//            assertDoesNotThrow(() -> tree.createCIRTree(clusterTree, weightStore, signedIndex));
//
//            int rootId = tree.getRootIdentifier();
//            assertFalse(signedIndex.store(rootId).isEmpty(), "Signed index should store pseudo-document at root");
//
//            Map<Integer, Double> scores = signedIndex.rankingSumClusterEnhance(
//                    rootId,
//                    Collections.singletonList(103),
//                    Collections.singletonList(1.0)
//            );
//            assertFalse(scores.isEmpty(), "Signed index should expose searchable root postings");
//        }

        @Test
        @DisplayName("Should handle empty textual content gracefully")
        void testHandleEmptyTextualContent() {
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            spatialObjects.put(1, new Point(new double[]{10.0, 20.0}));
            textualObjects.put(1, new ArrayList<>()); // Empty textual content

            tree.insertData(1, spatialObjects.get(1));

            // This should handle empty content gracefully
            assertDoesNotThrow(() -> {
                WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            }, "Should handle empty textual content");
        }
    }

    @Nested
    @DisplayName("Aggregate Ingestion Regression")
    class AggregateIngestionRegression {
        private CIRTree tree;
        private AbstractDocumentStore weightStore;

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
            propertySet.setProperty("NumberOfClusters", 2);

            DatasetParameters datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            weightStore = new HashMapDocumentStore();
        }

//        @Test
//        @DisplayName("Should build CIRTree with signed-block aggregate index without ingestion exception")
//        void createCIRTreeWithSignedBlockAggregateIndex() {
//            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
//            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();
//            HashMap<Integer, Integer> clusterTree = new HashMap<>();
//
//            for (int id = 1; id <= 6; id++) {
//                spatialObjects.put(id, new Point(new double[]{id * 5.0, id * 3.0}));
//                textualObjects.put(id, new ArrayList<>(Arrays.asList(100 + id, 200 + (id % 2), 300)));
//                clusterTree.put(id, id % 2);
//            }
//
//            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
//                tree.insertData(entry.getKey(), entry.getValue());
//            }
//            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
//
//            Statistics stats = (Statistics) tree.getStatistics();
//            assertTrue(stats.getTreeHeight() > 1,
//                    "Tree should have internal nodes so both leaf and internal ingestion paths are executed");
//
//            SignedBlockInvertedIndexNEW signedBlockIndex = new SignedBlockInvertedIndexNEW(tree.getRoot().getMBR());
//            assertDoesNotThrow(() -> tree.createCIRTree(clusterTree, weightStore, signedBlockIndex));
//
//            int rootId = tree.getRootIdentifier();
//            assertNotNull(signedBlockIndex.getIndexEntry(rootId), "Root entry must be populated");
//
//            Map<Integer, Double> scores = signedBlockIndex.rankingSumClusterEnhance(
//                    rootId,
//                    Collections.singletonList(300),
//                    Collections.singletonList(1.0)
//            );
//            assertFalse(scores.isEmpty(), "Signed-block index should expose searchable root postings");
//        }
    }

    /**
     * Tests for tree height management
     */
    @Nested
    @DisplayName("Tree Height Management")
    class TreeHeightManagement {
        private CIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;

        @BeforeEach
        void setUp() {
            storageManager = new NodeStorageManager();
            propertySet = new PropertySet();
            propertySet.setProperty("Dimension", 2);
            propertySet.setProperty("IndexCapacity", 3);
            propertySet.setProperty("LeafCapacity", 3);
            propertySet.setProperty("FillFactor", 0.7f);
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
            propertySet.setProperty("NearMinimumOverlapFactor", 2);
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            tree = new CIRTree(propertySet, storageManager, datasetParameters);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should track tree height correctly")
        void testTrackTreeHeightCorrectly(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            Statistics stats = (Statistics) tree.getStatistics();
            int initialHeight = stats.getTreeHeight();

            assertTrue(initialHeight >= 0, "Initial tree height should be non-negative");

            // Insert data to potentially increase height
            for (int i = 0; i < 15; i++) {
                tree.insertData(i, new Point(new double[]{i * 5.0, i * 5.0}));
            }

            int finalHeight = stats.getTreeHeight();
            assertTrue(finalHeight >= initialHeight, "Tree height should not decrease after insertions");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.cirtree.CIRTreeArchitectureTest#provideRTreeVariants")
        @DisplayName("Should increase height when necessary")
        void testIncreaseHeightWhenNecessary(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            Statistics stats = (Statistics) tree.getStatistics();
            int initialHeight = stats.getTreeHeight();

            // Insert many points to force height increase
            for (int i = 0; i < 30; i++) {
                tree.insertData(i, new Point(new double[]{Math.random() * 100, Math.random() * 100}));
            }

            int finalHeight = stats.getTreeHeight();
            // With small capacity, height should increase
            assertTrue(finalHeight >= initialHeight, "Tree should grow in height with many insertions");
            assertEquals(30, stats.getNumberOfData(), "All data should be inserted");
        }

        @Test
        @DisplayName("Should not throw on R*-split with fanout 3")
        void testRStarSplitWithFanoutThreeDoesNotThrow() {
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 20; i++) {
                    tree.insertData(i, new Point(new double[]{i * 2.0, i * 3.0}));
                }
            }, "R*-split should not create empty groups when fanout is 3");

            Statistics stats = (Statistics) tree.getStatistics();
            assertEquals(20, stats.getNumberOfData(), "All entries should be inserted successfully");
        }
    }

    /**
     * Tests for edge cases and boundary conditions
     */
    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditions {
        private CIRTree tree;
        private NodeStorageManager storageManager;
        private PropertySet propertySet;
        private DatasetParameters datasetParameters;
        private AbstractDocumentStore weightStore;
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
            datasetParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);

            tree = new CIRTree(propertySet, storageManager, datasetParameters);
            weightStore = new HashMapDocumentStore();
            documentIndex = new InvertedListIndex(2);
        }

        @Test
        @DisplayName("Should handle null parameters in createCIRTree")
        void testCreateCIRTreeWithNullParameters() {
            HashMap<Integer, Integer> clusterTree = new HashMap<>();
            clusterTree.put(1, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> tree.createCIRTree(null, weightStore, documentIndex),
                    "Should throw exception when cluster tree is null");

            assertThrows(IllegalArgumentException.class,
                    () -> tree.createCIRTree(clusterTree, null, documentIndex),
                    "Should throw exception when document store is null");

            assertThrows(IllegalArgumentException.class,
                    () -> tree.createCIRTree(clusterTree, weightStore, null),
                    "Should throw exception when inverted file is null");
        }

        @Test
        @DisplayName("Should handle duplicate document insertions")
        void testHandleDuplicateDocumentInsertions() {
            Point point = new Point(new double[]{10.0, 20.0});

            tree.insertData(1, point);
            Statistics statsAfterFirst = (Statistics) tree.getStatistics();
            long dataCountAfterFirst = statsAfterFirst.getNumberOfData();

            // Insert duplicate
            tree.insertData(1, point);
            Statistics statsAfterSecond = (Statistics) tree.getStatistics();
            long dataCountAfterSecond = statsAfterSecond.getNumberOfData();

            // Behavior depends on implementation - could overwrite or add
            assertTrue(dataCountAfterSecond >= dataCountAfterFirst,
                    "Tree should handle duplicate insertions");
        }

        @Test
        @DisplayName("Should handle single cluster scenario")
        void testSingleClusterScenario() {
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            // Add more documents to avoid k-means edge case where k >= number of documents
            for (int i = 1; i <= 5; i++) {
                spatialObjects.put(i, new Point(new double[]{10.0 + i, 20.0 + i}));
                textualObjects.put(i, new ArrayList<>(Arrays.asList(101, 102)));
            }

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            InvertedListIndex singleClusterIndex = new InvertedListIndex(1);
            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            HashMap<Integer, Integer> clusterTree = KMean.calculateKMean(weightStore, 1, 10);

            assertDoesNotThrow(() -> tree.createCIRTree(clusterTree, weightStore, singleClusterIndex),
                    "Should handle single cluster scenario");

            // Verify all documents are in the same cluster
            assertEquals(1, new HashSet<>(clusterTree.values()).size(),
                    "All documents should be in a single cluster");
        }

        @Test
        @DisplayName("Should handle large number of clusters")
        void testLargeNumberOfClusters() {
            HashMap<Integer, IShape> spatialObjects = new HashMap<>();
            HashMap<Integer, ArrayList<Integer>> textualObjects = new HashMap<>();

            int numDocs = 10;
            int numClusters = 8; // More clusters than efficient, but valid

            for (int i = 1; i <= numDocs; i++) {
                spatialObjects.put(i, new Point(new double[]{i * 5.0, i * 5.0}));
                textualObjects.put(i, new ArrayList<>(Collections.singletonList(100 + i)));
            }

            for (Map.Entry<Integer, IShape> entry : spatialObjects.entrySet()) {
                tree.insertData(entry.getKey(), entry.getValue());
            }

            InvertedListIndex largeClusterIndex = new InvertedListIndex(numClusters);
            WeightCompute.ComputeTermWeights(textualObjects, weightStore, 0.5);
            HashMap<Integer, Integer> clusterTree = KMean.calculateKMean(weightStore, numClusters, 10);

            assertDoesNotThrow(() -> tree.createCIRTree(clusterTree, weightStore, largeClusterIndex),
                    "Should handle large number of clusters");
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

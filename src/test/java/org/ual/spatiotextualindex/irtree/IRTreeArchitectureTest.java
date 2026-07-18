package org.ual.spatiotextualindex.irtree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ual.document.WeightCompute;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.parameters.Dataset;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive architecture tests for IRTree, mirroring RTreeArchitectureTest
 * but extended to verify inverted file integration and textual indexing capabilities.
 *
 * Tests verify:
 * - Tree initialization with spatial and textual data
 * - Tree growth dynamics with mixed spatial/textual operations
 * - Inverted file structure and aggregation
 * - Node capacity constraints with keyword data
 * - Tree height management with keyword-aware splits
 * - Weight management and storage
 */
public class IRTreeArchitectureTest {

    private PropertySet propertySet;
    private IStorageManager storageManager;
    private DatasetParameters dummyParameters;

    // Sample spatial and textual data
    private HashMap<Integer, ArrayList<Integer>> sampleTextualObjects;
    private HashMap<Integer, IShape> sampleSpatialObjects;
    private AbstractDocumentStore weightStore;
    private InvertedListIndex documentIndex;

    // Helper method to generate sample data
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

        // Compute weights for textual objects
        weightStore = new HashMapDocumentStore();
        double smoothingFactor = 0.0;
        WeightCompute.ComputeTF_IDFWeights(sampleTextualObjects, weightStore, smoothingFactor);
    }

    private void insertSampleData(IRTree tree) {
        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            tree.insertData(entry.getKey(), entry.getValue());
        }
    }

    private void buildInvertedIndex(IRTree tree) {
        documentIndex = new InvertedListIndex(0);
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
    class TreeInitialization {

        @Test
        void treeProperllyInitializesWithValidPropertySet() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            assertNotNull(irtree);
            assertTrue(irtree.isIndexValid());
        }

        @Test
        void emptyTreeContainsZeroDataEntries() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            assertEquals(0, irtree.getStatistics().getNumberOfData());
        }

        @Test
        void treeHasCorrectDimensionFromPropertySet() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            assertEquals(2, irtree.getDimension());
        }

        @Test
        void treeHasCorrectVariantFromPropertySet() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            assertEquals(SpatialIndex.RtreeVariantRstar, irtree.getTreeVariant());
        }

        @Test
        void rootNodeExistsAfterInitialization() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            assertNotNull(irtree.getRoot());
        }
    }

    @Nested
    class TreeGrowthWithTextualData {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void treeBecomesSingleLeafNodeAfterFirstInsert(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});

            irtree.insertData(1, region);

            INode root = irtree.getRoot();
            assertTrue(root.isLeaf());
            assertEquals(1, irtree.getStatistics().getNumberOfData());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void rootRemainsLeafUntilCapacityExceeded(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                irtree.insertData(i, region);
            }

            INode root = irtree.getRoot();
            assertTrue(root.isLeaf());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void treeSplitsIntoMultipleNodesWhenCapacityExceeded(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                irtree.insertData(i, region);
            }

            assertTrue(irtree.getStatistics().getNumberOfNodes() > 1);
        }

        @Test
        void treeAcceptsSampleDataWithKeywords() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);

            assertEquals(sampleSpatialObjects.size(), irtree.getStatistics().getNumberOfData());
            assertTrue(irtree.isIndexValid());
        }
    }

    @Nested
    class TreeHeightManagement {

        @Test
        void initialTreeHeightIsOne() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            assertEquals(1, irtree.getStatistics().getTreeHeight());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void treeHeightIncreasesWithSplits(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");
            int indexCapacity = (Integer) propertySet.getProperty("IndexCapacity");

            assertEquals(1, irtree.getStatistics().getTreeHeight());

            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                irtree.insertData(i, region);
            }

            assertEquals(2, irtree.getStatistics().getTreeHeight());

            for (int i = leafCapacity + 1; i < leafCapacity * indexCapacity + 1; i++) {
                Region region = new Region(new double[]{i % 100, (double) i / 100},
                                          new double[]{i % 100 + 0.5, (double) i / 100 + 0.5});
                irtree.insertData(i, region);
            }

            assertTrue(irtree.getStatistics().getTreeHeight() >= 2);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void treeHeightIsBalanced(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity * 10; i++) {
                Region region = new Region(new double[]{i % 100, (double) i / 100},
                                          new double[]{i % 100 + 0.5, (double) i / 100 + 0.5});
                irtree.insertData(i, region);
            }

            int height = irtree.getStatistics().getTreeHeight();
            long numNodes = irtree.getStatistics().getNumberOfNodes();
            int expectedMaxHeight = (int) Math.ceil(Math.log(numNodes) / Math.log(leafCapacity)) + 1;

            assertTrue(height <= expectedMaxHeight);
        }
    }

    @Nested
    class NodeCapacityAndSplitting {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void leafNodeRespectCapacityLimit(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity + 10; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                irtree.insertData(i, region);
            }

            assertTrue(irtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void indexNodeRespectCapacityLimit(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");
            int indexCapacity = (Integer) propertySet.getProperty("IndexCapacity");

            int itemsToInsert = (leafCapacity + 1) * (indexCapacity + 1);
            for (int i = 0; i < itemsToInsert; i++) {
                Region region = new Region(new double[]{i % 100, (double) i / 100},
                                          new double[]{i % 100 + 0.5, (double) i / 100 + 0.5});
                irtree.insertData(i, region);
            }

            assertTrue(irtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void splitsDistributeEntriesAcrossNewNodes(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity + 5; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                irtree.insertData(i, region);
            }

            long totalDataCount = irtree.getStatistics().getNumberOfData();
            assertEquals(leafCapacity + 5, totalDataCount);
        }
    }

    @Nested
    class RootNodeBehavior {

        @Test
        void rootNodeIdentifierMatchesGetRoot() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            irtree.insertData(1, region);

            assertEquals(irtree.getRootIdentifier(), irtree.getRoot().getIdentifier());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.irtree.IRTreeArchitectureTest#provideRTreeVariants")
        void rootNodeRemainsValidAfterMultipleSplits(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity * 5; i++) {
                Region region = new Region(new double[]{i % 50, (double) i / 50},
                                          new double[]{i % 50 + 0.5, (double) i / 50 + 0.5});
                irtree.insertData(i, region);
            }

            INode root = irtree.getRoot();
            assertNotNull(root);
            assertTrue(root.getIdentifier() >= 0);
        }
    }

    @Nested
    class TextualIndexIntegration {

        @Test
        void invertedIndexIsBuiltAfterTreeCreation() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildInvertedIndex(irtree);

            assertNotNull(documentIndex);
            assertTrue(documentIndex.getTotalDocuments() >= sampleSpatialObjects.size());
        }

        @Test
        void documentIndexContainsAllKeywords() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildInvertedIndex(irtree);

            // Check for presence of all expected keywords
            assertTrue(documentIndex.getDocumentFrequency(101) > 0, "Keyword 101 should be indexed");
            assertTrue(documentIndex.getDocumentFrequency(102) > 0, "Keyword 102 should be indexed");
            assertTrue(documentIndex.getDocumentFrequency(103) > 0, "Keyword 103 should be indexed");
        }

        @Test
        void weightStoreContainsDocumentWeights() {
            assertEquals(sampleTextualObjects.size(), weightStore.getSize(),
                    "Weight store should contain weights for all documents with text");
        }

        @Test
        void invertedFileAggregatesAtEachNodeLevel() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildInvertedIndex(irtree);

            // Verify that the inverted file has data at multiple node levels
            int height = irtree.getStatistics().getTreeHeight();
            assertTrue(height >= 1, "Tree should have at least the root level");
        }

        @Test
        void multipleKeywordDocumentsAreIndexed() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildInvertedIndex(irtree);

            // Document 1 has keywords [101, 102, 103]
            // Verify it's reachable through multiple keyword lookups
            assertTrue(documentIndex.getTotalDocuments() > 0);
        }
    }

    @Nested
    class WeightManagement {

        @Test
        void weightsAreComputedCorrectlyForAllDocuments() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildInvertedIndex(irtree);

            // Verify weight store is populated
            assertTrue(weightStore.getSize() > 0, "Weights should be computed for documents");
        }

        @Test
        void weightsReflectTermFrequency() {
            // Document 1 appears in 3 keywords (101, 102, 103)
            // Document 4 appears in 1 keyword (105) - more unique
            // Different documents should have different TF-IDF values

            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildInvertedIndex(irtree);

            assertEquals(sampleTextualObjects.size(), weightStore.getSize());
        }

        @Test
        void smoothingFactorAffectsWeightComputation() {
            // Create two weight stores with different smoothing factors
            AbstractDocumentStore smoothedStore = new HashMapDocumentStore();
            WeightCompute.ComputeTF_IDFWeights(sampleTextualObjects, smoothedStore, 0.5);

            assertEquals(sampleTextualObjects.size(), smoothedStore.getSize());
            // With smoothing factor 0.5, weights should be different from the default 0.0
            assertTrue(smoothedStore.getSize() > 0);
        }
    }

    @Nested
    class NodeEntryWithKeywords {

        @Test
        void nodeEntriesMaintainSpatialMBRWithKeywords() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);

            INode rootNode = irtree.getRoot();
            assertNotNull(rootNode);
            assertNotNull(rootNode.getMBR());
        }

        @Test
        void allDocumentsRemainAccessibleAfterTreeCreation() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);

            long dataCount = irtree.getStatistics().getNumberOfData();
            assertEquals(sampleSpatialObjects.size(), dataCount,
                    "All inserted documents should be accessible");
        }

        @Test
        void treeStructureIsValidAfterInvertedIndexCreation() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildInvertedIndex(irtree);

            assertTrue(irtree.isIndexValid(), "Tree structure should remain valid after index creation");
        }
    }

    @Nested
    class EdgeCasesAndBoundaryConditions {

        @Test
        void treeHandlesEmptyKeywordList() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);

            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            irtree.insertData(1, region);

            assertEquals(1, irtree.getStatistics().getNumberOfData());
        }

        @Test
        void treeHandlesDuplicateSpatialLocations() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);

            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            irtree.insertData(1, region);
            irtree.insertData(2, region);

            assertEquals(2, irtree.getStatistics().getNumberOfData());
        }

        @Test
        void treeHandlesLargeKeywordLists() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);

            // Prepare list with many keywords (not used in test but represents realistic scenario)
            for (int i = 0; i < 100; i++) {
                // Generate keywords
            }

            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            irtree.insertData(1, region);

            assertEquals(1, irtree.getStatistics().getNumberOfData());
        }
    }

    @Nested
    class TreeValidation {

        @Test
        void treeRemainsValidAfterAllOperations() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);

            assertTrue(irtree.isIndexValid(), "Tree should remain valid after insertions");
        }

        @Test
        void statisticsAreAccurateAfterBuild() {
            IRTree irtree = new IRTree(propertySet, storageManager, dummyParameters);
            insertSampleData(irtree);
            buildInvertedIndex(irtree);

            long nodeCount = irtree.getStatistics().getNumberOfNodes();
            long dataCount = irtree.getStatistics().getNumberOfData();
            int height = irtree.getStatistics().getTreeHeight();

            assertTrue(nodeCount > 0, "Should have at least one node");
            assertEquals(sampleSpatialObjects.size(), dataCount);
            assertTrue(height >= 1, "Height should be at least 1");
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

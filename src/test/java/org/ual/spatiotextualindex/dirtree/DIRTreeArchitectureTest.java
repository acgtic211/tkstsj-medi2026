package org.ual.spatiotextualindex.dirtree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ual.document.WeightCompute;
//import org.ual.documentindex.signedblock.SignedBlockInvertedIndex;
//import org.ual.documentindex.signedblocknew.SignedBlockInvertedIndexNEW;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.spatialindex.IShape;
import org.ual.spatialindex.spatialindex.INode;
import org.ual.spatialindex.spatialindex.Point;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.HashMapDocumentStore;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive architecture tests for DIRTree, aligned with IRTree architecture tests
 * and extended with DIR-specific document-aware assertions.
 */
public class DIRTreeArchitectureTest {

    private DatasetParameters dummyParameters;

    private HashMap<Integer, ArrayList<Integer>> sampleTextualObjects;
    private HashMap<Integer, IShape> sampleSpatialObjects;

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

    private AbstractDocumentStore createWeightStore() {
        AbstractDocumentStore weightStore = new HashMapDocumentStore();
        WeightCompute.ComputeTermWeights(sampleTextualObjects, weightStore, 0.5);
        return weightStore;
    }

    private DIRTree createTree(int treeVariant) {
        PropertySet propertySet = createPropertySet(treeVariant);
        IStorageManager storageManager = new NodeStorageManager();
        return new DIRTree(propertySet, storageManager, createWeightStore(), dummyParameters);
    }

    private void insertSampleData(DIRTree tree) {
        for (Map.Entry<Integer, IShape> entry : sampleSpatialObjects.entrySet()) {
            HashSet<Integer> documents = tree.getDocumentStore().readSet(entry.getKey());
            assertNotNull(documents, "Document set must exist for sample object " + entry.getKey());
            tree.insertData(entry.getKey(), entry.getValue(), documents);
        }
    }

    @Nested
    class TreeInitialization {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeArchitectureTest#provideRTreeVariants")
        void treeProperlyInitializesWithValidPropertySet(int treeVariant) {
            DIRTree tree = createTree(treeVariant);
            assertNotNull(tree);
            assertTrue(tree.isIndexValid());
            assertTrue(tree.isDocumentAware());
        }

        @Test
        void emptyTreeContainsZeroDataEntries() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantRstar);
            assertEquals(0, tree.getStatistics().getNumberOfData());
        }

        @Test
        void treeHasConfiguredDimensionAndVariant() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantQuadratic);
            assertEquals(2, tree.getDimension());
            assertEquals(SpatialIndex.RtreeVariantQuadratic, tree.getTreeVariant());
        }

        @Test
        void rootNodeExistsAfterInitialization() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantLinear);
            assertNotNull(tree.getRoot());
            assertEquals(tree.getRootIdentifier(), tree.getRoot().getIdentifier());
        }

        @Test
        void betaAreaPropertyIsApplied() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantRstar);
            assertEquals(0.5f, tree.getBetaArea(), 0.0001);
        }
    }

    @Nested
    class TreeGrowthWithTextualData {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeArchitectureTest#provideRTreeVariants")
        void treeBecomesSingleLeafNodeAfterFirstInsert(int treeVariant) {
            DIRTree tree = createTree(treeVariant);
            tree.insertData(1, new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0}), new HashSet<>(Collections.singletonList(1)));

            INode root = tree.getRoot();
            assertTrue(root.isLeaf());
            assertEquals(1, tree.getStatistics().getNumberOfData());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeArchitectureTest#provideRTreeVariants")
        void rootRemainsLeafUntilCapacityExceeded(int treeVariant) {
            DIRTree tree = createTree(treeVariant);
            int leafCapacity = 6;

            for (int i = 0; i < leafCapacity; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                tree.insertData(i, region, new HashSet<>(Collections.singletonList(i + 1)));
            }

            assertTrue(tree.getRoot().isLeaf());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeArchitectureTest#provideRTreeVariants")
        void treeSplitsWhenCapacityExceeded(int treeVariant) {
            DIRTree tree = createTree(treeVariant);
            int leafCapacity = 6;

            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                tree.insertData(i, region, new HashSet<>(Collections.singletonList(i + 1)));
            }

            assertTrue(tree.getStatistics().getNumberOfNodes() > 1);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeArchitectureTest#provideRTreeVariants")
        void treeAcceptsSampleDataWithDocuments(int treeVariant) {
            DIRTree tree = createTree(treeVariant);
            insertSampleData(tree);

            assertEquals(sampleSpatialObjects.size(), tree.getStatistics().getNumberOfData());
            assertTrue(tree.isIndexValid());
            assertTrue(tree.validateDocumentStructure());
        }
    }

    @Nested
    class TreeHeightManagement {

        @Test
        void initialTreeHeightIsOne() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantRstar);
            assertEquals(1, tree.getStatistics().getTreeHeight());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeArchitectureTest#provideRTreeVariants")
        void treeHeightIncreasesAfterSplit(int treeVariant) {
            DIRTree tree = createTree(treeVariant);
            int leafCapacity = 6;

            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                tree.insertData(i, region, new HashSet<>(Collections.singletonList(i + 1)));
            }

            assertTrue(tree.getStatistics().getTreeHeight() >= 2);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeArchitectureTest#provideRTreeVariants")
        void treeHeightRemainsBalanced(int treeVariant) {
            DIRTree tree = createTree(treeVariant);

            for (int i = 0; i < 80; i++) {
                Region region = new Region(new double[]{i % 20, i / 20.0}, new double[]{i % 20 + 0.5, i / 20.0 + 0.5});
                tree.insertData(i, region, new HashSet<>(Collections.singletonList(i + 1)));
            }

            int height = tree.getStatistics().getTreeHeight();
            assertTrue(height >= 2);
            assertTrue(tree.isIndexValid());
        }
    }

    @Nested
    class NodeCapacityAndSplitting {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatiotextualindex.dirtree.DIRTreeArchitectureTest#provideRTreeVariants")
        void indexAndLeafCapacitiesAreRespected(int treeVariant) {
            DIRTree tree = createTree(treeVariant);
            int itemsToInsert = 60;

            for (int i = 0; i < itemsToInsert; i++) {
                Region region = new Region(new double[]{i % 30, i / 30.0}, new double[]{i % 30 + 0.5, i / 30.0 + 0.5});
                tree.insertData(i, region, new HashSet<>(Collections.singletonList(i + 1)));
            }

            assertEquals(itemsToInsert, tree.getStatistics().getNumberOfData());
            assertTrue(tree.isIndexValid());
            assertTrue(tree.validateDocumentStructure());
        }
    }

    @Nested
    class TextualIndexIntegration {

        @Test
        void invertedIndexIsBuiltAfterDirCreation() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantRstar);
            insertSampleData(tree);

            InvertedListIndex documentIndex = new InvertedListIndex(0);
            tree.createDIRTree(tree.getDocumentStore(), documentIndex);

            assertTrue(documentIndex.getTotalDocuments() >= sampleSpatialObjects.size());
            assertTrue(documentIndex.getDocumentFrequency(101) > 0);
            assertTrue(documentIndex.getDocumentFrequency(102) > 0);
        }

        @Test
        void treeStructureRemainsValidAfterInvertedIndexBuild() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantRstar);
            insertSampleData(tree);

            InvertedListIndex documentIndex = new InvertedListIndex(0);
            tree.createDIRTree(tree.getDocumentStore(), documentIndex);

            assertTrue(tree.isIndexValid());
            assertTrue(tree.validateDocumentStructure());
        }

//        @Test
//        void signedBlockAggregateIndexIsBuiltAfterDirCreation() {
//            DIRTree tree = createTree(SpatialIndex.RtreeVariantRstar);
//            insertSampleData(tree);
//
//            assertTrue(tree.getStatistics().getTreeHeight() > 1,
//                    "Tree should have internal nodes so both leaf and internal ingestion paths are executed");
//
//            SignedBlockInvertedIndexNEW documentIndex = new SignedBlockInvertedIndexNEW(tree.getRoot().getMBR());
//            assertDoesNotThrow(() -> tree.createDIRTree(tree.getDocumentStore(), documentIndex));
//
//            int rootId = tree.getRootIdentifier();
//            assertNotNull(documentIndex.getIndexEntry(rootId), "Root entry must be populated");
//
//            Map<Integer, Double> scores = documentIndex.rankingSum(
//                    rootId,
//                    Collections.singletonList(101),
//                    Collections.singletonList(1.0)
//            );
//            assertFalse(scores.isEmpty(), "Signed-block index should expose searchable root postings");
//        }
    }

    @Nested
    class EdgeCasesAndBoundaryConditions {

        @Test
        void insertDataWithoutDocumentSetIsUnsupported() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantRstar);
            assertThrows(UnsupportedOperationException.class,
                    () -> tree.insertData(99, new Point(new double[]{1.0, 2.0})));
        }

        @Test
        void treeHandlesDuplicateSpatialLocationsWithDifferentIds() {
            DIRTree tree = createTree(SpatialIndex.RtreeVariantRstar);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});

            tree.insertData(1, region, new HashSet<>(Collections.singletonList(101)));
            tree.insertData(2, region, new HashSet<>(Collections.singletonList(102)));

            assertEquals(2, tree.getStatistics().getNumberOfData());
            assertTrue(tree.isIndexValid());
            assertTrue(tree.validateDocumentStructure());
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

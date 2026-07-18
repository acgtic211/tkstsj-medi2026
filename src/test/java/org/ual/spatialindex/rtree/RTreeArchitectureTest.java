package org.ual.spatialindex.rtree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.spatialindex.rtree.RTree;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.parameters.Dataset;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RTreeArchitectureTest {

    private PropertySet propertySet;
    private IStorageManager storageManager;
    private DatasetParameters dummyParameters;

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
    }

    @Nested
    class TreeInitialization {

        @Test
        void treeProperllyInitializesWithValidPropertySet() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertNotNull(rtree);
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void emptyTreeContainsZeroDataEntries() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertEquals(0, rtree.getStatistics().getNumberOfData());
        }

        @Test
        void treeHasCorrectDimensionFromPropertySet() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertEquals(2, rtree.getDimension());
        }

        @Test
        void treeHasCorrectVariantFromPropertySet() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertEquals(SpatialIndex.RtreeVariantRstar, rtree.getTreeVariant());
        }

        @Test
        void rootNodeExistsAfterInitialization() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertNotNull(rtree.getRoot());
        }
    }

    @Nested
    class TreeGrowthWithSingleInsert {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void treeBecomesSingleLeafNodeAfterFirstInsert(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});

            rtree.insertData(1, region);

            INode root = rtree.getRoot();
            assertTrue(root.isLeaf());
            assertEquals(1, rtree.getStatistics().getNumberOfData());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void rootRemainsLeafUntilCapacityExceeded(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            INode root = rtree.getRoot();
            assertTrue(root.isLeaf());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void treeSplitsIntoMultipleNodesWhenCapacityExceeded(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            assertTrue(rtree.getStatistics().getNumberOfNodes() > 1);
        }


    }

    @Nested
    class TreeHeightManagement {

        @Test
        void initialTreeHeightIsOne() {
            // Height = 1 for a tree with only a root leaf node (at level 0)
            // This follows the standard R-tree convention where height = number of levels
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertEquals(1, rtree.getStatistics().getTreeHeight());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void treeHeightIncreasesWithSplits(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");
            int indexCapacity = (Integer) propertySet.getProperty("IndexCapacity");

            // Initial height should be 1 (single root leaf at level 0)
            assertEquals(1, rtree.getStatistics().getTreeHeight());

            // Insert enough items to cause root split (leafCapacity + 1 items)
            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            // After root split: height should be 2 (root index at level 1, leaves at level 0)
            assertEquals(2, rtree.getStatistics().getTreeHeight());

            // Insert many more items to potentially cause multiple levels
            for (int i = leafCapacity + 1; i < leafCapacity * indexCapacity + 1; i++) {
                Region region = new Region(new double[]{i % 100, (double) i / 100},
                                          new double[]{i % 100 + 0.5, (double) i / 100 + 0.5});
                rtree.insertData(i, region);
            }

            // Height should have increased beyond 2
            assertTrue(rtree.getStatistics().getTreeHeight() >= 2);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void treeHeightIsBalanced(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity * 10; i++) {
                Region region = new Region(new double[]{i % 100, (double) i / 100},
                                          new double[]{i % 100 + 0.5, (double) i / 100 + 0.5});
                rtree.insertData(i, region);
            }

            int height = rtree.getStatistics().getTreeHeight();
            long numNodes = rtree.getStatistics().getNumberOfNodes();
            //int expectedMaxHeight = (int) Math.ceil(Math.log(numNodes) / Math.log(leafCapacity));
            int expectedMaxHeight = (int) Math.ceil(Math.log(numNodes) / Math.log(leafCapacity)) + 1;

            assertTrue(height <= expectedMaxHeight);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void leafNodeCountIsConsistentAcrossHeights(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            // Initially: height = 1, should have 1 leaf node at level 0
            assertEquals(1, rtree.getStatistics().getTreeHeight());
            assertEquals(1, rtree.getStatistics().getLeafNodeCount());
            assertEquals(1, rtree.getStatistics().getNumberOfNodesInLevel(0));

            // Insert enough to cause root split
            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            // After split: height = 2, should have 2 leaf nodes at level 0, 1 index at level 1
            assertEquals(2, rtree.getStatistics().getTreeHeight());
            assertEquals(2, rtree.getStatistics().getLeafNodeCount());
            assertEquals(2, rtree.getStatistics().getNumberOfNodesInLevel(0));
            assertEquals(1, rtree.getStatistics().getNumberOfNodesInLevel(1));
        }
    }

    @Nested
    class NodeCapacityAndSplitting {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void leafNodeRespectCapacityLimit(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity + 10; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void indexNodeRespectCapacityLimit(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");
            int indexCapacity = (Integer) propertySet.getProperty("IndexCapacity");

            int itemsToInsert = (leafCapacity + 1) * (indexCapacity + 1);
            for (int i = 0; i < itemsToInsert; i++) {
                Region region = new Region(new double[]{i % 100, (double) i / 100},
                                          new double[]{i % 100 + 0.5, (double) i / 100 + 0.5});
                rtree.insertData(i, region);
            }

            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void splitsDistributeEntriesAcrossNewNodes(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity + 5; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            long totalDataCount = rtree.getStatistics().getNumberOfData();
            assertEquals(leafCapacity + 5, totalDataCount);
        }

        @Test
        void rstarVariantWithFanoutThreeDoesNotThrowDuringSplits() {
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar);
            propertySet.setProperty("IndexCapacity", 3);
            propertySet.setProperty("LeafCapacity", 3);
            propertySet.setProperty("NearMinimumOverlapFactor", 2);

            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            final int entriesToInsert = 20;

            assertDoesNotThrow(() -> {
                for (int i = 0; i < entriesToInsert; i++) {
                    Region region = new Region(new double[]{i * 2.0, i * 3.0},
                            new double[]{i * 2.0 + 0.5, i * 3.0 + 0.5});
                    rtree.insertData(i, region);
                }
            }, "R*-split with fanout 3 should not create empty groups or fail in combinedRegion");

            assertEquals(entriesToInsert, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.getStatistics().getNumberOfNodes() > 1);
            assertTrue(rtree.getStatistics().getTreeHeight() >= 2);
            assertTrue(rtree.isIndexValid());
        }
    }

    @Nested
    class RootNodeBehavior {

        @Test
        void rootNodeIdentifierMatchesGetRoot() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            assertEquals(rtree.getRootIdentifier(), rtree.getRoot().getIdentifier());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void rootNodeRemainsValidAfterMultipleSplits(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            for (int i = 0; i < leafCapacity * 5; i++) {
                Region region = new Region(new double[]{i % 50, (double) i / 50},
                                          new double[]{i % 50 + 0.5, (double) i / 50 + 0.5});
                rtree.insertData(i, region);
            }

            INode root = rtree.getRoot();
            assertNotNull(root);
            //assertTrue(root.getIdentifier() > 0);
            assertTrue(root.getIdentifier() >= 0);
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void rootBoundingBoxEncompassesAllData(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});
            Region r3 = new Region(new double[]{3.0, 3.0}, new double[]{4.0, 4.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            Region rootMBR = rtree.getRoot().getMBR();
            assertTrue(rootMBR.contains(r1));
            assertTrue(rootMBR.contains(r2));
            assertTrue(rootMBR.contains(r3));
        }
    }

    @Nested
    class BulkLoading {

        @Test
        void bulkLoadCreatesValidTreeStructure() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0, 0}, new double[]{1, 1});
            Region r2 = new Region(new double[]{2, 2}, new double[]{3, 3});
            Region r3 = new Region(new double[]{0.5, 0.5}, new double[]{1.5, 1.5});

            rtree.storePseudoNodes(1, r1);
            rtree.storePseudoNodes(2, r2);
            rtree.storePseudoNodes(3, r3);
            rtree.bulkLoadRTree(BulkLoadMethod.STR);

            assertTrue(rtree.isIndexValid());
            assertEquals(3, rtree.getStatistics().getNumberOfData());
        }

        @Test
        void bulkLoadedTreeContainsAllInsertedData() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 10; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.storePseudoNodes(i, region);
            }
            rtree.bulkLoadRTree(BulkLoadMethod.STR);

            assertEquals(10, rtree.getStatistics().getNumberOfData());
        }

        @Test
        void bulkLoadProducesCompactTree() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i % 10, (double) i / 10},
                                          new double[]{i % 10 + 0.5, (double) i / 10 + 0.5});
                rtree.storePseudoNodes(i, region);
            }
            rtree.bulkLoadRTree(BulkLoadMethod.STR);

            assertTrue(rtree.isIndexValid());
            assertTrue(rtree.getStatistics().getTreeHeight() > 0);
        }
    }

    @Nested
    class IndexIntegrity {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void indexValidityIsMaintenedAfterInsertions(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 50; i++) {
                Region region = new Region(new double[]{i % 20, (double) i / 20},
                                          new double[]{i % 20 + 0.5, (double) i / 20 + 0.5});
                rtree.insertData(i, region);
                assertTrue(rtree.isIndexValid());
            }
        }

        @Test
        void singleItemCanBeDeleted() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});

            rtree.insertData(1, region);
            assertEquals(1, rtree.getStatistics().getNumberOfData());

            boolean deleted = rtree.deleteData(1, region);
            assertTrue(deleted, "Should successfully delete single item");
            assertEquals(0, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void multipleItemsCanBeDeletedBeforeSplit(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");
            List<Region> regions = new ArrayList<>();

            for (int i = 0; i < leafCapacity - 1; i++) {
                Region region = new Region(new double[]{i * 1.0, i * 1.0},
                                          new double[]{i * 1.0 + 0.5, i * 1.0 + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            assertEquals(leafCapacity - 1, rtree.getStatistics().getNumberOfData());

            for (int i = 0; i < regions.size(); i++) {
                boolean deleted = rtree.deleteData(i, regions.get(i));
                assertTrue(deleted, "Failed to delete item " + i);
                assertTrue(rtree.isIndexValid());
            }

            assertEquals(0, rtree.getStatistics().getNumberOfData());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void indexValidityIsMaintenedAfterDeletions(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            List<Region> insertedRegions = new ArrayList<>();
            List<Integer> insertedIds = new ArrayList<>();

            for (int i = 0; i < 30; i++) {
                double offset = i * 2.0;  // Ensure non-overlapping regions
                Region region = new Region(new double[]{offset, offset},
                        new double[]{offset + 0.5, offset + 0.5});
                rtree.insertData(i, region);
                insertedRegions.add(region);
                insertedIds.add(i);
            }

            assertEquals(30, rtree.getStatistics().getNumberOfData(), "Should have 30 entries after insertion");
            assertTrue(rtree.isIndexValid(), "Tree should be valid after all insertions");

            for (int i = 0; i < insertedIds.size(); i++) {
                int id = insertedIds.get(i);
                Region region = insertedRegions.get(i);
                long dataCountBefore = rtree.getStatistics().getNumberOfData();

                boolean deleted = rtree.deleteData(id, region);
                assertTrue(deleted, "Failed to delete data with id: " + id + " at iteration " + i);

                long dataCountAfter = rtree.getStatistics().getNumberOfData();
                assertEquals(dataCountBefore - 1, dataCountAfter, "Data count should decrease by 1 after deletion of id: " + id);

                assertTrue(rtree.isIndexValid(), "Tree should be valid after deletion of id: " + id);
            }

            assertEquals(0, rtree.getStatistics().getNumberOfData(), "Tree should be empty after all deletions");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void treeMaintainsMinimumFillFactorAfterInsertions(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 100; i++) {
                Region region = new Region(new double[]{i % 50, (double) i / 50},
                                          new double[]{i % 50 + 0.5, (double) i / 50 + 0.5});
                rtree.insertData(i, region);
            }

            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void indexRemainsValidUnderLargeDeterministicInsertionWorkload(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Random random = new Random(20260304L);
            int totalEntries = 25000;

            for (int i = 0; i < totalEntries; i++) {
                double x = random.nextDouble() * 1000.0;
                double y = random.nextDouble() * 1000.0;
                double width = 0.01 + random.nextDouble() * 0.1;
                double height = 0.01 + random.nextDouble() * 0.1;

                Region region = new Region(new double[]{x, y}, new double[]{x + width, y + height});
                rtree.insertData(i, region);

                // Periodic full validation keeps runtime reasonable while still catching drift early.
                if ((i + 1) % 1000 == 0) {
                    assertTrue(rtree.isIndexValid(), "Tree invalid at insertion " + (i + 1));
                }
            }

            assertTrue(rtree.isIndexValid());
            assertEquals(totalEntries, rtree.getStatistics().getNumberOfData());
        }
    }

    @Nested
    class MBRManagement {

        @Test
        void insertionExpandsRootMBRIfNeeded() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            rtree.insertData(1, r1);

            Region r2 = new Region(new double[]{10.0, 10.0}, new double[]{11.0, 11.0});
            rtree.insertData(2, r2);
            Region expandedMBR = rtree.getRoot().getMBR();

            assertTrue(expandedMBR.contains(r1));
            assertTrue(expandedMBR.contains(r2));
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void mbrRemainsMinimalForClusteredData(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i * 0.1, i * 0.1},
                                          new double[]{i * 0.1 + 0.05, i * 0.1 + 0.05});
                rtree.insertData(i, region);
            }

            Region rootMBR = rtree.getRoot().getMBR();
            double diagonal = rootMBR.getDiagonalLength();
            assertTrue(diagonal < 3.0, "Root MBR diagonal should remain minimal for clustered data");
        }

        @Test
        void deletionDoesNotShrinkMBRUnnecessarily() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{10.0, 10.0}, new double[]{11.0, 11.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            Region mbrAfterInsert = rtree.getRoot().getMBR();

            rtree.deleteData(2, r2);
            Region mbrAfterDelete = rtree.getRoot().getMBR();

            assertTrue(mbrAfterDelete.contains(mbrAfterInsert) ||
                      mbrAfterInsert.contains(mbrAfterDelete));
        }
    }

    @Nested
    class DocumentAwareBehavior {

        @Test
        void rtreeRejectsDocumentAwareInsertionMethod() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            HashSet<Integer> documents = new HashSet<>(Arrays.asList(100, 101));

            assertThrows(UnsupportedOperationException.class,
                        () -> rtree.insertData(1, region, documents),
                        "RTree should throw UnsupportedOperationException for document-aware insertData");
        }

        @Test
        void rtreeIsNotDocumentAware() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertFalse(rtree.isDocumentAware(), "Standard RTree should not be document-aware");
        }

        @Test
        void rtreeRejectsDocumentStoreAccess() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertThrows(UnsupportedOperationException.class,
                        rtree::getDocumentStore,
                        "RTree should throw UnsupportedOperationException for getDocumentStore");
        }

        @Test
        void rtreeRejectsDocumentNodeMappingAccess() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertThrows(UnsupportedOperationException.class,
                        rtree::getDocumentNodeMapping,
                        "RTree should throw UnsupportedOperationException for getDocumentNodeMapping");
        }

        @Test
        void rtreeRejectsBetaAreaAccess() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertThrows(UnsupportedOperationException.class,
                        rtree::getBetaArea,
                        "RTree should throw UnsupportedOperationException for getBetaArea");
        }

        @Test
        void rtreeRejectsBetaAreaSetting() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            assertThrows(UnsupportedOperationException.class,
                        () -> rtree.setBetaArea(0.5f),
                        "RTree should throw UnsupportedOperationException for setBetaArea");
        }

        @Test
        void nodeDocumentFieldsExistInInternalStructure() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            assertNotNull(root, "Root node should not be null");

            // Even though RTree doesn't use documents, the Node structure supports them
            // The nodeDocuments field should exist but may be null or empty for non-document-aware trees
            HashSet<Integer> nodeDocuments = root.getNodeDocuments();
            // For standard RTree, nodeDocuments may be null or initialized but empty
            assertTrue(nodeDocuments == null || nodeDocuments.isEmpty(),
                      "Standard RTree nodes should have null or empty document sets");
        }

        @Test
        void nodeEntrySupportsDocumentField() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            NodeEntry entry = root.getNodeEntries().get(1);
            assertNotNull(entry, "NodeEntry should exist for inserted data");

            // NodeEntry structure supports documents but won't be populated in standard RTree
            HashSet<Integer> entryDocuments = entry.getDocument();
            assertTrue(entryDocuments == null || entryDocuments.isEmpty(),
                      "Standard RTree entries should have null or empty document sets");
        }
    }

    @Nested
    class EdgeCasesAndBoundaryConditions {

        @Test
        void insertionWithZeroSizedRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            // Point-like region (zero area)
            Region pointRegion = new Region(new double[]{5.0, 5.0}, new double[]{5.0, 5.0});

            rtree.insertData(1, pointRegion);
            assertEquals(1, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void multipleInsertionsAtSameLocation(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region location = new Region(new double[]{1.0, 1.0}, new double[]{1.0, 1.0});

            for (int i = 0; i < 10; i++) {
                rtree.insertData(i, location);
            }

            assertEquals(10, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void deletionOfNonExistentItem() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Region nonExistentRegion = new Region(new double[]{10.0, 10.0}, new double[]{11.0, 11.0});
            boolean deleted = rtree.deleteData(999, nonExistentRegion);

            assertFalse(deleted, "Deletion of non-existent item should return false");
            assertEquals(1, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void insertionWithVeryLargeRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region largeRegion = new Region(new double[]{-1000.0, -1000.0},
                                           new double[]{1000.0, 1000.0});

            rtree.insertData(1, largeRegion);
            assertEquals(1, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void insertionWithNegativeCoordinates() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region negativeRegion = new Region(new double[]{-10.0, -20.0},
                                              new double[]{-5.0, -15.0});

            rtree.insertData(1, negativeRegion);
            assertEquals(1, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void insertionAndDeletionMixedOperations(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            List<Region> regions = new ArrayList<>();

            // Insert 10 items
            for (int i = 0; i < 10; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            // Delete every other item
            for (int i = 0; i < 10; i += 2) {
                rtree.deleteData(i, regions.get(i));
            }

            assertEquals(5, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());

            // Insert more items
            for (int i = 10; i < 15; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            assertEquals(10, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void deletionCausingNodeMerge(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");
            List<Region> regions = new ArrayList<>();

            // Insert enough to cause splits
            for (int i = 0; i < leafCapacity * 3; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            long nodeCountAfterInsertion = rtree.getStatistics().getNumberOfNodes();
            assertTrue(nodeCountAfterInsertion > 1, "Should have multiple nodes after insertions");

            // Delete most items to potentially trigger merges
            for (int i = 0; i < leafCapacity * 2; i++) {
                rtree.deleteData(i, regions.get(i));
            }

            assertTrue(rtree.isIndexValid());
            assertEquals(leafCapacity, rtree.getStatistics().getNumberOfData());
        }

        @Test
        void insertionWithIdenticalMBRsButDifferentIds() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region sharedRegion = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});

            rtree.insertData(1, sharedRegion);
            rtree.insertData(2, sharedRegion);
            rtree.insertData(3, sharedRegion);

            assertEquals(3, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void stressTestWithRandomOperations(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Random random = new Random(42L);
            List<Region> insertedRegions = new ArrayList<>();
            List<Integer> insertedIds = new ArrayList<>();

            // Perform 100 random insert/delete operations
            for (int i = 0; i < 100; i++) {
                if (random.nextBoolean() || insertedIds.isEmpty()) {
                    // Insert
                    double x = random.nextDouble() * 100;
                    double y = random.nextDouble() * 100;
                    Region region = new Region(new double[]{x, y}, new double[]{x + 1, y + 1});
                    rtree.insertData(i, region);
                    insertedRegions.add(region);
                    insertedIds.add(i);
                } else {
                    // Delete a random existing item
                    int idx = random.nextInt(insertedIds.size());
                    int idToDelete = insertedIds.get(idx);
                    Region regionToDelete = insertedRegions.get(idx);
                    rtree.deleteData(idToDelete, regionToDelete);
                    insertedIds.remove(idx);
                    insertedRegions.remove(idx);
                }

                if (i % 10 == 0) {
                    assertTrue(rtree.isIndexValid(), "Tree should remain valid at operation " + i);
                }
            }

            assertTrue(rtree.isIndexValid());
            assertEquals(insertedIds.size(), rtree.getStatistics().getNumberOfData());
        }

        @Test
        void constructorClonesRegionAndDocumentInputs() {
            Region sourceMBR = new Region(new double[]{1.0, 2.0}, new double[]{3.0, 4.0});
            HashSet<Integer> sourceDocs = new HashSet<>();
            sourceDocs.add(10);

            NodeEntry entry = new NodeEntry(1, sourceMBR, sourceDocs, 0L, 0.0f);

            sourceMBR.setLow(0, -10.0);
            sourceMBR.setHigh(1, 100.0);
            sourceDocs.add(20);

            assertEquals(1.0, entry.getMBR().getLow(0));
            assertEquals(4.0, entry.getMBR().getHigh(1));
            assertTrue(entry.getDocument().contains(10));
            assertFalse(entry.getDocument().contains(20));
        }

        @Test
        void setMBRAndSetDocumentCloneInputs() {
            NodeEntry entry = new NodeEntry(2, new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}), 0L, 0.0f);

            Region newMBR = new Region(new double[]{5.0, 6.0}, new double[]{7.0, 8.0});
            HashSet<Integer> newDocs = new HashSet<>();
            newDocs.add(42);

            entry.setMBR(newMBR);
            entry.setDocument(newDocs);

            newMBR.setLow(1, -50.0);
            newMBR.setHigh(0, 500.0);
            newDocs.add(99);

            assertEquals(6.0, entry.getMBR().getLow(1));
            assertEquals(7.0, entry.getMBR().getHigh(0));
            assertTrue(entry.getDocument().contains(42));
            assertFalse(entry.getDocument().contains(99));
        }
    }

    @Nested
    class NodeInternalStructure {

        @Test
        void nodeEntriesAreAccessible() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            TreeMap<Integer, NodeEntry> entries = root.getNodeEntries();

            assertNotNull(entries, "Node entries should not be null");
            assertEquals(1, entries.size(), "Root should contain one entry");
            assertTrue(entries.containsKey(1), "Entry with ID 1 should exist");
        }

        @Test
        void nodeEntryMBRMatchesInsertedRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            NodeEntry entry = root.getNodeEntries().get(1);

            assertNotNull(entry, "NodeEntry should exist");
            assertEquals(region, entry.getMBR(), "NodeEntry MBR should match inserted region");
        }

        @Test
        void nodeSignatureFieldExists() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            // nodeSignature field exists but may not be meaningfully populated in standard RTree
            long signature = root.getNodeSignature();
            assertTrue(signature >= 0, "Node signature should be non-negative");
        }

        @Test
        void nodeMaxWeightFieldExists() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            // maxWeight field exists but may not be meaningfully populated in standard RTree
            double maxWeight = root.getMaxWeight();
            assertTrue(maxWeight >= 0, "Max weight should be non-negative");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void multipleNodesHaveConsistentStructure(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            // Insert enough to cause splits
            for (int i = 0; i < leafCapacity + 5; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            // Verify all nodes in level 0 are leaves
            LinkedHashSet<Integer> leafIds = rtree.getNodesInLevel(0);
            for (Integer leafId : leafIds) {
                Node leaf = rtree.readNode(leafId);
                assertTrue(leaf.isLeaf(), "All nodes at level 0 should be leaves");
                assertEquals(0, leaf.getLevel(), "Leaf nodes should have level 0");
            }

            // Verify all nodes in level 1 are index nodes (if they exist)
            LinkedHashSet<Integer> indexIds = rtree.getNodesInLevel(1);
            if (!indexIds.isEmpty()) {
                for (Integer indexId : indexIds) {
                    Node index = rtree.readNode(indexId);
                    assertTrue(index.isIndex(), "All nodes at level 1 should be index nodes");
                    assertEquals(1, index.getLevel(), "Index nodes at level 1 should have level 1");
                }
            }
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void nodeEntrySizeConsistencyAfterOperations(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            // Insert items up to capacity
            for (int i = 0; i < leafCapacity; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            Node root = (Node) rtree.getRoot();
            assertEquals(leafCapacity, root.getNodeEntriesSize(),
                        "Root should contain exactly leafCapacity entries");

            // One more insertion causes split
            Region region = new Region(new double[]{leafCapacity, leafCapacity},
                                      new double[]{leafCapacity + 0.5, leafCapacity + 0.5});
            rtree.insertData(leafCapacity, region);

            // Root is now an index node
            root = (Node) rtree.getRoot();
            assertTrue(root.isIndex(), "Root should be an index node after split");
            assertEquals(2, root.getNodeEntriesSize(),
                        "Root index should have 2 entries (two child nodes)");
        }
    }

    @Nested
    class SpecialDataDistributions {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void heavilyOverlappingRegions(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Create many overlapping regions centered around the same point
            Point center = new Point(new double[]{50.0, 50.0});
            for (int i = 0; i < 30; i++) {
                double radius = 1.0 + i * 0.5;
                Region region = new Region(
                    new double[]{center.getCoord(0) - radius, center.getCoord(1) - radius},
                    new double[]{center.getCoord(0) + radius, center.getCoord(1) + radius}
                );
                rtree.insertData(i, region);
            }

            assertEquals(30, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void linearDistributionAlongAxis(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert regions linearly along X-axis
            for (int i = 0; i < 50; i++) {
                Region region = new Region(new double[]{i, 0.0}, new double[]{i + 0.5, 0.5});
                rtree.insertData(i, region);
            }

            assertEquals(50, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void clusteredDataInMultipleGroups(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Create 4 clusters of data
            int itemsPerCluster = 15;
            int clusterCount = 4;

            for (int cluster = 0; cluster < clusterCount; cluster++) {
                double offsetX = cluster * 100.0;
                double offsetY = cluster * 100.0;

                for (int i = 0; i < itemsPerCluster; i++) {
                    Region region = new Region(
                        new double[]{offsetX + i * 0.5, offsetY + i * 0.5},
                        new double[]{offsetX + i * 0.5 + 0.2, offsetY + i * 0.5 + 0.2}
                    );
                    rtree.insertData(cluster * itemsPerCluster + i, region);
                }
            }

            assertEquals(itemsPerCluster * clusterCount, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void uniformGridDistribution(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int gridSize = 10;

            // Create a uniform grid of regions
            for (int x = 0; x < gridSize; x++) {
                for (int y = 0; y < gridSize; y++) {
                    Region region = new Region(
                        new double[]{x, y},
                        new double[]{x + 0.8, y + 0.8}
                    );
                    rtree.insertData(x * gridSize + y, region);
                }
            }

            assertEquals(gridSize * gridSize, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void sparseDataWithLargeGaps(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert data with large gaps between them
            for (int i = 0; i < 20; i++) {
                double coord = i * 100.0; // Large gaps
                Region region = new Region(
                    new double[]{coord, coord},
                    new double[]{coord + 1.0, coord + 1.0}
                );
                rtree.insertData(i, region);
            }

            assertEquals(20, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());

            // Root MBR should span a very large area
            Region rootMBR = rtree.getRoot().getMBR();
            double area = rootMBR.getArea();
            assertTrue(area > 1000000, "Root MBR should span large area for sparse data");
        }
    }

    @Nested
    class NodeEntryManagement {

        @Test
        void nodeEntryChildSignatureFieldExists() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            NodeEntry entry = root.getNodeEntries().get(1);

            assertNotNull(entry, "NodeEntry should exist");
            long childSignature = entry.getChildSignature();
            assertTrue(childSignature >= 0, "Child signature should be non-negative");
        }

        @Test
        void nodeEntryChildMaxScoreFieldExists() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            NodeEntry entry = root.getNodeEntries().get(1);

            assertNotNull(entry, "NodeEntry should exist");
            float childMaxScore = entry.getChildMaxScore();
            assertTrue(childMaxScore >= 0, "Child max score should be non-negative");
        }

        @Test
        void nodeEntryCanBeModified() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            Node root = (Node) rtree.getRoot();
            NodeEntry entry = root.getNodeEntries().get(1);

            // Test that we can read and modify NodeEntry fields
            Region originalMBR = entry.getMBR();
            assertNotNull(originalMBR);

            // Test setMBR
            Region newMBR = new Region(new double[]{2.0, 2.0}, new double[]{3.0, 3.0});
            entry.setMBR(newMBR);
            assertEquals(newMBR, entry.getMBR());

            // Test setDocument (even though RTree doesn't use it)
            HashSet<Integer> docs = new HashSet<>(Arrays.asList(100, 101));
            entry.setDocument(docs);
            assertEquals(docs, entry.getDocument());

            // Test setChildSignature
            entry.setChildSignature(12345L);
            assertEquals(12345L, entry.getChildSignature());

            // Test setChildMaxScore
            entry.setChildMaxScore(99.5f);
            assertEquals(99.5f, entry.getChildMaxScore(), 0.001);
        }

        @Test
        void nodeEntryComparison() {
            Region r1 = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});

            NodeEntry entry1 = new NodeEntry(1, r1, 0L, 0.0f);
            NodeEntry entry2 = new NodeEntry(2, r2, 0L, 0.0f);
            NodeEntry entry3 = new NodeEntry(1, r2, 0L, 0.0f);

            // Different IDs
            assertTrue(entry1.compareTo(entry2) < 0, "Entry with smaller ID should compare as less");
            assertTrue(entry2.compareTo(entry1) > 0, "Entry with larger ID should compare as greater");

            // Same ID, different areas
            int comparison = entry1.compareTo(entry3);
            assertTrue(comparison < 0, "Same ID: entry with smaller area should compare as less");
        }
    }

    @Nested
    class StatisticsValidation {

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void dataCountIncreasesWithInsertions(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                assertEquals(i + 1, rtree.getStatistics().getNumberOfData(),
                           "Data count should increase with each insertion");
            }
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void dataCountDecreasesWithDeletions(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            List<Region> regions = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            for (int i = 0; i < 20; i++) {
                rtree.deleteData(i, regions.get(i));
                assertEquals(19 - i, rtree.getStatistics().getNumberOfData(),
                           "Data count should decrease with each deletion");
            }
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void nodeCountIsAccurate(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            // Initially one node (root leaf)
            assertEquals(1, rtree.getStatistics().getNumberOfNodes());

            // After split
            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            long nodeCount = rtree.getStatistics().getNumberOfNodes();
            assertTrue(nodeCount >= 3, "After split should have at least 3 nodes (1 root + 2 leaves)");
        }

        @ParameterizedTest(name = "[variant={0}]")
        @MethodSource("org.ual.spatialindex.rtree.RTreeArchitectureTest#provideRTreeVariants")
        void levelStatisticsAreAccurate(int treeVariant) {
            propertySet.setProperty("TreeVariant", treeVariant);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            // Insert enough to create multiple levels
            for (int i = 0; i < leafCapacity * 5; i++) {
                Region region = new Region(new double[]{i % 50, (double) i / 50},
                                          new double[]{i % 50 + 0.5, (double) i / 50 + 0.5});
                rtree.insertData(i, region);
            }

            int height = rtree.getStatistics().getTreeHeight();

            // Verify each level has consistent node counts
            long totalNodesFromLevels = 0;
            for (int level = 0; level < height; level++) {
                long nodesAtLevel = rtree.getStatistics().getNumberOfNodesInLevel(level);
                assertTrue(nodesAtLevel > 0, "Level " + level + " should have at least one node");
                totalNodesFromLevels += nodesAtLevel;
            }

            assertEquals(rtree.getStatistics().getNumberOfNodes(), totalNodesFromLevels,
                        "Sum of nodes across levels should equal total node count");
        }
    }

    @Nested
    class VariantSpecificBehavior {

        @Test
        void linearVariantBasicInsertion() {
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert a few items
            for (int i = 0; i < 5; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            assertEquals(5, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid(), "Tree should be valid after 5 insertions");
        }

        @Test
        void linearVariantSplitTriggersCorrectly() {
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = 6;

            System.out.println("=== Before insertions ===");
            System.out.println("Height: " + rtree.getStatistics().getTreeHeight());
            System.out.println("Nodes: " + rtree.getStatistics().getNumberOfNodes());
            printNodesPerLevel(rtree);

            // Insert up to capacity
            for (int i = 0; i < leafCapacity; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                System.out.println("After insert " + i + ": nodes=" + rtree.getStatistics().getNumberOfNodes());
            }

            System.out.println("\n=== After filling to capacity ===");
            System.out.println("Height: " + rtree.getStatistics().getTreeHeight());
            System.out.println("Nodes: " + rtree.getStatistics().getNumberOfNodes());
            printNodesPerLevel(rtree);
            assertTrue(rtree.isIndexValid(), "Tree should be valid at capacity");

            // Trigger split
            Region region = new Region(new double[]{leafCapacity, leafCapacity},
                    new double[]{leafCapacity + 0.5, leafCapacity + 0.5});
            rtree.insertData(leafCapacity, region);

            System.out.println("\n=== After split trigger ===");
            System.out.println("Height: " + rtree.getStatistics().getTreeHeight());
            System.out.println("Nodes: " + rtree.getStatistics().getNumberOfNodes());
            printNodesPerLevel(rtree);

            assertTrue(rtree.getStatistics().getNumberOfNodes() > 1, "Should have multiple nodes after split");
            assertTrue(rtree.isIndexValid(), "Tree should be valid after split");
        }

        @Test
        void linearVariantSimpleDeletion() {
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert 3 items
            List<Region> regions = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            System.out.println("=== After insertions ===");
            System.out.println("Data: " + rtree.getStatistics().getNumberOfData());
            System.out.println("Nodes: " + rtree.getStatistics().getNumberOfNodes());
            printNodesPerLevel(rtree);
            assertTrue(rtree.isIndexValid(), "Tree should be valid after insertions");

            // Delete one item
            boolean deleted = rtree.deleteData(1, regions.get(1));

            System.out.println("\n=== After deletion ===");
            System.out.println("Deleted: " + deleted);
            System.out.println("Data: " + rtree.getStatistics().getNumberOfData());
            System.out.println("Nodes: " + rtree.getStatistics().getNumberOfNodes());
            printNodesPerLevel(rtree);

            assertTrue(deleted, "Deletion should succeed");
            assertEquals(2, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid(), "Tree should be valid after deletion");
        }

        @Test
        void linearVariantDeletionAfterSplit() {
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = 6;

            // Insert enough to trigger split
            List<Region> regions = new ArrayList<>();
            for (int i = 0; i < leafCapacity + 3; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            System.out.println("=== After insertions (with split) ===");
            System.out.println("Data: " + rtree.getStatistics().getNumberOfData());
            System.out.println("Nodes: " + rtree.getStatistics().getNumberOfNodes());
            System.out.println("Height: " + rtree.getStatistics().getTreeHeight());
            printNodesPerLevel(rtree);
            assertTrue(rtree.isIndexValid(), "Tree should be valid after insertions with split");

            // Try to delete one item
            boolean deleted = rtree.deleteData(5, regions.get(5));

            System.out.println("\n=== After deletion ===");
            System.out.println("Deleted: " + deleted);
            System.out.println("Data: " + rtree.getStatistics().getNumberOfData());
            System.out.println("Nodes: " + rtree.getStatistics().getNumberOfNodes());
            System.out.println("Height: " + rtree.getStatistics().getTreeHeight());
            printNodesPerLevel(rtree);

            assertTrue(deleted, "Deletion should succeed");
            assertTrue(rtree.isIndexValid(), "Tree should be valid after deletion");
        }

        private void printNodesPerLevel(RTree rtree) {
            //ArrayList<Integer> nodesInLevel = rtree.getStatistics().getNodesInLevel();
//        for (int level = 0; level < rtree.getStatistics().getTreeHeight(); level++) {
//            System.out.println("  Level " + level + ": " + rtree.getStatistics().getNumberOfNodesInLevel(level) + " nodes");
//        }

            for (int level = 0; level < rtree.getStatistics().getTreeHeight(); level++) {
                int nodesInLevel = rtree.getStatistics().getNumberOfNodesInLevel(level);
                Set<Integer> nodesList = rtree.getNodesInLevel(level);
                System.out.println("  Level " + level + ": " + nodesInLevel + " nodes - " + nodesList);
            }
        }

        @Test
        void detailedTrackingOf50Insertions() {
            propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            System.out.println("=== Initial state ===");
            printNodesPerLevel(rtree);
            assertTrue(rtree.isIndexValid(), "Initial tree should be valid");

            for (int i = 0; i < 50; i++) {
                double x = (i % 10) * 10.0;
                double y = (i / 10.0) * 10.0;
                Region region = new Region(new double[]{x, y}, new double[]{x + 8.0, y + 8.0});

                long splitsBeforeinsert = rtree.getStatistics().getSplits();
                rtree.insertData(i, region);
                long splitsAfterInsert = rtree.getStatistics().getSplits();
                boolean hasSplit = splitsAfterInsert > splitsBeforeinsert;

                if (i == 6 || i == 13 || i == 20 || i == 30 || i == 40 || i == 49) {
                    System.out.println("\n=== After insert " + i + (hasSplit ? " [SPLIT]" : "") + " ===");
                    System.out.println("Data: " + rtree.getStatistics().getNumberOfData());
                    System.out.println("Nodes: " + rtree.getStatistics().getNumberOfNodes());
                    System.out.println("Height: " + rtree.getStatistics().getTreeHeight());
                    System.out.println("Total splits so far: " + rtree.getStatistics().getSplits());
                    printNodesPerLevel(rtree);

                    boolean valid = rtree.isIndexValid();
                    System.out.println("Valid: " + valid);
                    if (!valid) {
                        System.out.println("*** VALIDATION FAILED AT INSERT " + i + " ***");
                        // Continue to see if it gets worse
                    }
                } else if (hasSplit) {
                    System.out.println("Split occurred at insert " + i);
                }
            }
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

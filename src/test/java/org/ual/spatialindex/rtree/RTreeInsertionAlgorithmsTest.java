package org.ual.spatialindex.rtree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.ual.spatialindex.rtree.RTree;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.parameters.Dataset;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class RTreeInsertionAlgorithmsTest {

    private IStorageManager storageManager;
    private DatasetParameters dummyParameters;

    @BeforeEach
    void setUp() {
        storageManager = new NodeStorageManager();
        dummyParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET);
    }

    private PropertySet createPropertySet(int treeVariant) {
        PropertySet propertySet = new PropertySet();
        propertySet.setProperty("Dimension", 2);
        propertySet.setProperty("IndexCapacity", 6);
        propertySet.setProperty("LeafCapacity", 6);
        propertySet.setProperty("FillFactor", 0.7f);
        propertySet.setProperty("TreeVariant", treeVariant);
        propertySet.setProperty("NearMinimumOverlapFactor", 2);
        return propertySet;
    }

    static class QueryVisitor implements IVisitor {
        public final List<Integer> visitedDataIds = new ArrayList<>();
        public int indexNodesVisited = 0;
        public int leafNodesVisited = 0;

        @Override
        public void visitNode(INode n) {
            if (n.isLeaf()) {
                leafNodesVisited++;
            } else {
                indexNodesVisited++;
            }
        }

        @Override
        public void visitData(IData d) {
            visitedDataIds.add(d.getIdentifier());
        }

        @Override
        public void visitData(ArrayList<IData> v) {
            if (v != null && v.size() >= 2) {
                visitedDataIds.add(v.get(0).getIdentifier());
                visitedDataIds.add(v.get(1).getIdentifier());
            }
        }

        public void reset() {
            visitedDataIds.clear();
            indexNodesVisited = 0;
            leafNodesVisited = 0;
        }
    }

    @Nested
    class LinearVariantInsertion {

        @Test
        void linearVariantTreeInitializesSuccessfully() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            assertNotNull(rtree);
            assertEquals(SpatialIndex.RtreeVariantLinear, rtree.getTreeVariant());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void linearVariantAcceptsMultipleInsertions() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 1.0, i + 1.0});
                rtree.insertData(i, region);
            }

            assertEquals(20, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void linearVariantSplitsNodesProperlyUnderCapacity() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = 6;

            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            assertTrue(rtree.getStatistics().getNumberOfNodes() > 1);
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void linearVariantPointLocationQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{5.0, 5.0}, new double[]{7.0, 7.0});
            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{1.0, 1.0});
            rtree.pointLocationQuery(queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.contains(1));
            assertFalse(visitor.visitedDataIds.contains(2));
        }

        @Test
        void linearVariantIntersectionQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{10.0, 10.0}, new double[]{12.0, 12.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{1.5, 1.5}, new double[]{2.5, 2.5});
            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.contains(1));
            assertTrue(visitor.visitedDataIds.contains(2));
            assertFalse(visitor.visitedDataIds.contains(3));
        }

        @Test
        void linearVariantContainmentQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{0.5, 0.5}, new double[]{0.8, 0.8});
            Region r3 = new Region(new double[]{5.0, 5.0}, new double[]{7.0, 7.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{1.5, 1.5});
            rtree.containmentQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.contains(2));
        }

        @Test
        void linearVariantDeletionMaintainsIndexValidity() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            List<Region> regions = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            for (int i = 0; i < 15; i++) {
                assertTrue(rtree.deleteData(i, regions.get(i)));
                assertTrue(rtree.isIndexValid());
            }

            assertEquals(0, rtree.getStatistics().getNumberOfData());
        }

        @Test
        void linearVariantHandlesClusteredDataInsertion() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 30; i++) {
                double offset = i * 0.1;
                Region region = new Region(new double[]{offset, offset}, new double[]{offset + 0.05, offset + 0.05});
                rtree.insertData(i, region);
            }

            assertEquals(30, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void linearVariantHandlesScatteredDataInsertion() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantLinear);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Random random = new Random(42);
            for (int i = 0; i < 25; i++) {
                double x = random.nextDouble() * 100;
                double y = random.nextDouble() * 100;
                Region region = new Region(new double[]{x, y}, new double[]{x + 1.0, y + 1.0});
                rtree.insertData(i, region);
            }

            assertEquals(25, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }
    }

    @Nested
    class QuadraticVariantInsertion {

        @Test
        void quadraticVariantTreeInitializesSuccessfully() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            assertNotNull(rtree);
            assertEquals(SpatialIndex.RtreeVariantQuadratic, rtree.getTreeVariant());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void quadraticVariantAcceptsMultipleInsertions() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 1.0, i + 1.0});
                rtree.insertData(i, region);
            }

            assertEquals(20, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void quadraticVariantSplitsNodesProperlyUnderCapacity() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = 6;

            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            assertTrue(rtree.getStatistics().getNumberOfNodes() > 1);
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void quadraticVariantPointLocationQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{5.0, 5.0}, new double[]{7.0, 7.0});
            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{1.0, 1.0});
            rtree.pointLocationQuery(queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.contains(1));
            assertFalse(visitor.visitedDataIds.contains(2));
        }

        @Test
        void quadraticVariantIntersectionQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{10.0, 10.0}, new double[]{12.0, 12.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{1.5, 1.5}, new double[]{2.5, 2.5});
            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.contains(1));
            assertTrue(visitor.visitedDataIds.contains(2));
            assertFalse(visitor.visitedDataIds.contains(3));
        }

        @Test
        void quadraticVariantContainmentQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{0.5, 0.5}, new double[]{0.8, 0.8});
            Region r3 = new Region(new double[]{5.0, 5.0}, new double[]{7.0, 7.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{1.5, 1.5});
            rtree.containmentQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.contains(2));
        }

        @Test
        void quadraticVariantDeletionMaintainsIndexValidity() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            List<Region> regions = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            for (int i = 0; i < 15; i++) {
                assertTrue(rtree.deleteData(i, regions.get(i)));
                assertTrue(rtree.isIndexValid());
            }

            assertEquals(0, rtree.getStatistics().getNumberOfData());
        }

        @Test
        void quadraticVariantHandlesClusteredDataInsertion() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 30; i++) {
                double offset = i * 0.1;
                Region region = new Region(new double[]{offset, offset}, new double[]{offset + 0.05, offset + 0.05});
                rtree.insertData(i, region);
            }

            assertEquals(30, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void quadraticVariantHandlesScatteredDataInsertion() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantQuadratic);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Random random = new Random(42);
            for (int i = 0; i < 25; i++) {
                double x = random.nextDouble() * 100;
                double y = random.nextDouble() * 100;
                Region region = new Region(new double[]{x, y}, new double[]{x + 1.0, y + 1.0});
                rtree.insertData(i, region);
            }

            assertEquals(25, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }
    }

    @Nested
    class RstarVariantInsertion {

        @Test
        void rstarVariantTreeInitializesSuccessfully() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            assertNotNull(rtree);
            assertEquals(SpatialIndex.RtreeVariantRstar, rtree.getTreeVariant());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void rstarVariantAcceptsMultipleInsertions() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 1.0, i + 1.0});
                rtree.insertData(i, region);
            }

            assertEquals(20, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void rstarVariantSplitsNodesProperlyUnderCapacity() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = 6;

            for (int i = 0; i < leafCapacity + 1; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            assertTrue(rtree.getStatistics().getNumberOfNodes() > 1);
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void rstarVariantPointLocationQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{5.0, 5.0}, new double[]{7.0, 7.0});
            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{1.0, 1.0});
            rtree.pointLocationQuery(queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.contains(1));
            assertFalse(visitor.visitedDataIds.contains(2));
        }

        @Test
        void rstarVariantIntersectionQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{10.0, 10.0}, new double[]{12.0, 12.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{1.5, 1.5}, new double[]{2.5, 2.5});
            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.contains(1));
            assertTrue(visitor.visitedDataIds.contains(2));
            assertFalse(visitor.visitedDataIds.contains(3));
        }

        @Test
        void rstarVariantContainmentQueryReturnsCorrectResults() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{0.5, 0.5}, new double[]{0.8, 0.8});
            Region r3 = new Region(new double[]{5.0, 5.0}, new double[]{7.0, 7.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{1.5, 1.5});
            rtree.containmentQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.contains(2));
        }

        @Test
        void rstarVariantDeletionMaintainsIndexValidity() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            List<Region> regions = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            for (int i = 0; i < 15; i++) {
                assertTrue(rtree.deleteData(i, regions.get(i)));
                assertTrue(rtree.isIndexValid());
            }

            assertEquals(0, rtree.getStatistics().getNumberOfData());
        }

        @Test
        void rstarVariantHandlesClusteredDataInsertion() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 30; i++) {
                double offset = i * 0.1;
                Region region = new Region(new double[]{offset, offset}, new double[]{offset + 0.05, offset + 0.05});
                rtree.insertData(i, region);
            }

            assertEquals(30, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void rstarVariantHandlesScatteredDataInsertion() {
            PropertySet propertySet = createPropertySet(SpatialIndex.RtreeVariantRstar);
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Random random = new Random(42);
            for (int i = 0; i < 25; i++) {
                double x = random.nextDouble() * 100;
                double y = random.nextDouble() * 100;
                Region region = new Region(new double[]{x, y}, new double[]{x + 1.0, y + 1.0});
                rtree.insertData(i, region);
            }

            assertEquals(25, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }
    }

    @Nested
    class VariantComparison {

        @Test
        void allVariantsProduceSameDataCountAfterInsertion() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                for (int i = 0; i < 20; i++) {
                    Region region = new Region(new double[]{i, i}, new double[]{i + 1.0, i + 1.0});
                    rtree.insertData(i, region);
                }

                assertEquals(20, rtree.getStatistics().getNumberOfData(),
                        "Variant " + SpatialIndex.getTreeVariantString(variant) + " should have 20 data entries");
            }
        }

        @Test
        void allVariantsProduceSameResultsForPointLocationQuery() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{5.0, 5.0}, new double[]{7.0, 7.0});
            Point queryPoint = new Point(new double[]{1.0, 1.0});

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                rtree.insertData(1, r1);
                rtree.insertData(2, r2);

                QueryVisitor visitor = new QueryVisitor();
                rtree.pointLocationQuery(queryPoint, visitor);

                assertTrue(visitor.visitedDataIds.contains(1),
                        "Variant " + SpatialIndex.getTreeVariantString(variant) + " should find id 1");
                assertFalse(visitor.visitedDataIds.contains(2),
                        "Variant " + SpatialIndex.getTreeVariantString(variant) + " should not find id 2");
            }
        }

        @Test
        void allVariantsProduceSameResultsForIntersectionQuery() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{10.0, 10.0}, new double[]{12.0, 12.0});
            Region queryRegion = new Region(new double[]{1.5, 1.5}, new double[]{2.5, 2.5});

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                rtree.insertData(1, r1);
                rtree.insertData(2, r2);
                rtree.insertData(3, r3);

                QueryVisitor visitor = new QueryVisitor();
                rtree.intersectionQuery(queryRegion, visitor);

                assertTrue(visitor.visitedDataIds.contains(1),
                        "Variant " + SpatialIndex.getTreeVariantString(variant) + " should find id 1");
                assertTrue(visitor.visitedDataIds.contains(2),
                        "Variant " + SpatialIndex.getTreeVariantString(variant) + " should find id 2");
                assertFalse(visitor.visitedDataIds.contains(3),
                        "Variant " + SpatialIndex.getTreeVariantString(variant) + " should not find id 3");
            }
        }

        @Test
        void allVariantsProduceSameResultsForDeletion() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                List<Region> regions = new ArrayList<>();
                for (int i = 0; i < 15; i++) {
                    Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                    rtree.insertData(i, region);
                    regions.add(region);
                }

                for (int i = 0; i < 15; i++) {
                    boolean deleted = rtree.deleteData(i, regions.get(i));
                    assertTrue(deleted, "Variant " + SpatialIndex.getTreeVariantString(variant) + " should successfully delete id " + i);
                }

                assertEquals(0, rtree.getStatistics().getNumberOfData(),
                        "Variant " + SpatialIndex.getTreeVariantString(variant) + " should be empty after all deletions");
            }
        }

        @Test
        void allVariantsMaintainIndexValidityAfterComplexOperations() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                List<Region> regions = new ArrayList<>();
                for (int i = 0; i < 40; i++) {
                    Region region = new Region(new double[]{i % 20, (double) i / 20},
                            new double[]{i % 20 + 0.8, (double) i / 20 + 0.8});
                    rtree.insertData(i, region);
                    regions.add(region);
                }

                for (int i = 0; i < 20; i++) {
                    rtree.deleteData(i, regions.get(i));
                }

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should maintain validity");
                assertEquals(20, rtree.getStatistics().getNumberOfData(),
                        "Variant " + variant + " should have 20 entries remaining");
            }
        }
    }

    @Nested
    class EdgeCasesAcrossVariants {

        @Test
        void allVariantsHandleSingleDataPointInsertion() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                Region region = new Region(new double[]{0.0, 0.0}, new double[]{0.0, 0.0});
                rtree.insertData(1, region);

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should handle point data");
                assertEquals(1, rtree.getStatistics().getNumberOfData());
            }
        }

        @Test
        void allVariantsHandleAdjacentButNonOverlappingRegions() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
                Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});

                rtree.insertData(1, r1);
                rtree.insertData(2, r2);

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should handle adjacent regions");
                assertEquals(2, rtree.getStatistics().getNumberOfData());
            }
        }

        @Test
        void allVariantsHandleIdenticalRegions() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                Region region = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
                rtree.insertData(1, region);
                rtree.insertData(2, region);

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should handle identical regions");
                assertEquals(2, rtree.getStatistics().getNumberOfData());
            }
        }

        @Test
        void allVariantsHandleInterleavedInsertionAndDeletion() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                for (int i = 0; i < 20; i++) {
                    Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                    rtree.insertData(i, region);

                    if (i % 2 == 0 && i > 0) {
                        Region deleteRegion = new Region(new double[]{i - 1, i - 1},
                                new double[]{i - 0.5, i - 0.5});
                        rtree.deleteData(i - 1, deleteRegion);
                    }
                }

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should maintain validity with interleaved ops");
            }
        }

        @Test
        void allVariantsProduceValidResultsWithVeryLargeCoordinates() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                Region r1 = new Region(new double[]{1e6, 1e6}, new double[]{1e6 + 1.0, 1e6 + 1.0});
                Region r2 = new Region(new double[]{1e7, 1e7}, new double[]{1e7 + 1.0, 1e7 + 1.0});

                rtree.insertData(1, r1);
                rtree.insertData(2, r2);

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should handle large coordinates");
                assertEquals(2, rtree.getStatistics().getNumberOfData());
            }
        }

        @Test
        void allVariantsProduceValidResultsWithNegativeCoordinates() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                Region r1 = new Region(new double[]{-100.0, -100.0}, new double[]{-99.0, -99.0});
                Region r2 = new Region(new double[]{-10.0, -10.0}, new double[]{-5.0, -5.0});

                rtree.insertData(1, r1);
                rtree.insertData(2, r2);

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should handle negative coordinates");
                assertEquals(2, rtree.getStatistics().getNumberOfData());
            }
        }
    }

    @Nested
    class VolumeAndDepthVariation {

        @Test
        void allVariantsHandleGrowingTreeWithDifferentInsertionPatterns() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                for (int i = 0; i < 50; i++) {
                    double x = (i % 10) * 10.0;
                    double y = (i / 10.0) * 10.0;
                    Region region = new Region(new double[]{x, y}, new double[]{x + 8.0, y + 8.0});
                    rtree.insertData(i, region);
                }

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should handle 50 entries");
                assertEquals(50, rtree.getStatistics().getNumberOfData());
                assertTrue(rtree.getStatistics().getTreeHeight() >= 2);
            }
        }

        @Test
        void allVariantsMaintainValidityUnderMixedSizeRegions() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                int id = 0;
                for (int i = 0; i < 5; i++) {
                    double size = Math.pow(2, i);
                    for (int j = 0; j < 5; j++) {
                        Region region = new Region(
                                new double[]{j * 100.0, i * 100.0},
                                new double[]{j * 100.0 + size, i * 100.0 + size});
                        rtree.insertData(id++, region);
                    }
                }

                assertTrue(rtree.isIndexValid(), "Variant " + SpatialIndex.getTreeVariantString(variant) + " should handle mixed-size regions");
                assertEquals(25, rtree.getStatistics().getNumberOfData());
            }
        }

        @Test
        void allVariantsProduceConsistentRootMBRs() {
            int[] variants = {
                SpatialIndex.RtreeVariantLinear,
                SpatialIndex.RtreeVariantQuadratic,
                SpatialIndex.RtreeVariantRstar
            };

            for (int variant : variants) {
                PropertySet propertySet = createPropertySet(variant);
                RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

                Region[] regions = {
                        new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0}),
                        new Region(new double[]{5.0, 5.0}, new double[]{10.0, 10.0}),
                        new Region(new double[]{-5.0, -5.0}, new double[]{0.0, 0.0})
                };

                for (int i = 0; i < regions.length; i++) {
                    rtree.insertData(i, regions[i]);
                }

                Region rootMBR = rtree.getRoot().getMBR();
                for (Region region : regions) {
                    assertTrue(rootMBR.contains(region),
                            "Variant " + SpatialIndex.getTreeVariantString(variant) + " root MBR should contain all inserted regions");
                }
            }
        }
    }
}


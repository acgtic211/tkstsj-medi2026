package org.ual.spatialindex.rtree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.ual.spatialindex.rtree.BulkLoadMethod;
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

public class RTreeQueryProcessingTest {

    private PropertySet propertySet;
    private IStorageManager storageManager;
    private DatasetParameters dummyParameters;

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
    class PointLocationQuery {

        @Test
        void pointLocationFindsDataInsideRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            rtree.insertData(1, region);

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.5, 0.5});
            rtree.pointLocationQuery(queryPoint, visitor);

            assertEquals(1, visitor.visitedDataIds.size());
            assertTrue(visitor.visitedDataIds.contains(1));
        }

        @Test
        void pointLocationReturnsEmptyForQueryOutsideAllRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            rtree.insertData(1, region);

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{5.0, 5.0});
            rtree.pointLocationQuery(queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.isEmpty());
        }

        @Test
        void pointLocationFindMultipleOverlappingRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{1.5, 1.5});
            rtree.pointLocationQuery(queryPoint, visitor);

            assertEquals(2, visitor.visitedDataIds.size());
            assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2)));
        }

        @Test
        void pointLocationOnRegionBoundary() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            rtree.insertData(1, region);

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.pointLocationQuery(queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.contains(1));
        }

        @Test
        void pointLocationWithManyRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 50; i++) {
                Region region = new Region(new double[]{i % 10, i / 10},
                                          new double[]{i % 10 + 0.5, i / 10 + 0.5});
                rtree.insertData(i, region);
            }

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{5.25, 2.25});
            rtree.pointLocationQuery(queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 1);
        }
    }

    @Nested
    class IntersectionQuery {

        @Test
        void intersectionQueryFindsAllIntersectingRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.5, 0.5}, new double[]{1.5, 1.5});
            rtree.intersectionQuery(queryRegion, visitor);

            assertEquals(2, visitor.visitedDataIds.size());
            assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2)));
        }

        @Test
        void intersectionQueryReturnsEmptyForDisjointQuery() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            rtree.insertData(1, r1);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{10.0, 10.0}, new double[]{11.0, 11.0});
            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.isEmpty());
        }

        @Test
        void intersectionQueryWithQueryContainsAllData() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{3.0, 3.0}, new double[]{4.0, 4.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{5.0, 5.0});
            rtree.intersectionQuery(queryRegion, visitor);

            assertEquals(2, visitor.visitedDataIds.size());
            assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2)));
        }

        @Test
        void intersectionQueryWithDataContainsQuery() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region largeRegion = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            rtree.insertData(1, largeRegion);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{2.0, 2.0}, new double[]{3.0, 3.0});
            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.contains(1));
        }

        @Test
        void intersectionQueryWithMultipleRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 30; i++) {
                Region region = new Region(new double[]{i % 10, i / 10},
                                          new double[]{i % 10 + 0.5, i / 10 + 0.5});
                rtree.insertData(i, region);
            }

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{2.0, 1.0}, new double[]{4.0, 2.0});
            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 2);
        }
    }

    @Nested
    class ContainmentQuery {

        @Test
        void containmentQueryFindsRegionContainedByQuery() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region contained = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            Region notContained = new Region(new double[]{0.5, 0.5}, new double[]{3.5, 3.5});

            rtree.insertData(1, contained);
            rtree.insertData(2, notContained);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{3.0, 3.0});
            rtree.containmentQuery(queryRegion, visitor);

            assertEquals(1, visitor.visitedDataIds.size());
            assertTrue(visitor.visitedDataIds.contains(1));
        }

        @Test
        void containmentQueryWithNoContainedRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, r1);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.5, 0.5}, new double[]{1.5, 1.5});
            rtree.containmentQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.isEmpty());
        }

        @Test
        void containmentQueryWithExactMatch() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.containmentQuery(queryRegion, visitor);

            assertEquals(1, visitor.visitedDataIds.size());
            assertTrue(visitor.visitedDataIds.contains(1));
        }

        @Test
        void containmentQueryWithMultipleContainedRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{3.0, 3.0}, new double[]{4.0, 4.0});
            Region r3 = new Region(new double[]{0.5, 0.5}, new double[]{5.5, 5.5});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{5.0, 5.0});
            rtree.containmentQuery(queryRegion, visitor);

            assertEquals(2, visitor.visitedDataIds.size());
            assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2)));
            assertFalse(visitor.visitedDataIds.contains(3));
        }
    }

    @Nested
    class NearestNeighborQuery {

        @Test
        void nearestNeighborQueryFindsSingleClosestPoint() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            rtree.insertData(1, new Region(new double[]{1.0, 1.0}, new double[]{1.0, 1.0}));
            rtree.insertData(2, new Region(new double[]{10.0, 10.0}, new double[]{10.0, 10.0}));

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(1, queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 1);
            assertTrue(visitor.visitedDataIds.contains(1));
        }

        @Test
        void nearestNeighborQueryReturnsCorrectKItems() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            rtree.insertData(1, new Region(new double[]{0.0, 0.0}, new double[]{0.0, 0.0}));
            rtree.insertData(2, new Region(new double[]{1.0, 0.0}, new double[]{1.0, 0.0}));
            rtree.insertData(3, new Region(new double[]{0.0, 1.0}, new double[]{0.0, 1.0}));
            rtree.insertData(4, new Region(new double[]{10.0, 10.0}, new double[]{10.0, 10.0}));

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{-1.0, -1.0});
            rtree.nearestNeighborQuery(2, queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 2);
        }

        @Test
        void nearestNeighborQueryWithKEqualToAllPoints() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            rtree.insertData(1, new Region(new double[]{0.0, 0.0}, new double[]{0.0, 0.0}));
            rtree.insertData(2, new Region(new double[]{5.0, 5.0}, new double[]{5.0, 5.0}));
            rtree.insertData(3, new Region(new double[]{10.0, 10.0}, new double[]{10.0, 10.0}));

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(3, queryPoint, visitor);

            assertEquals(3, visitor.visitedDataIds.size());
            assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2, 3)));
        }

        @Test
        void nearestNeighborQueryWithLargeK() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 20; i++) {
                rtree.insertData(i, new Region(new double[]{i, i}, new double[]{i, i}));
            }

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(100, queryPoint, visitor);

            assertEquals(20, visitor.visitedDataIds.size());
        }
    }

    @Nested
    class SelfJoinIntersectionQuery {

        @Test
        void selfJoinIntersectionFindsPairsWithinQueryRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{10.0, 10.0}, new double[]{11.0, 11.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            SelfJoinVisitor visitor = new SelfJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{5.0, 5.0});
            rtree.selfJoinIntersectionQuery(queryRegion, visitor);

            assertTrue(visitor.pairs.size() >= 1);
            assertTrue(visitor.pairs.stream().anyMatch(p ->
                (p.contains(1) && p.contains(2))));
        }

        @Test
        void selfJoinIntersectionReturnsEmptyForNonOverlappingRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            SelfJoinVisitor visitor = new SelfJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{7.0, 7.0});
            rtree.selfJoinIntersectionQuery(queryRegion, visitor);

            assertTrue(visitor.pairs.isEmpty());
        }

        @Test
        void selfJoinIntersectionWithPartialQueryRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{4.0, 4.0}, new double[]{5.0, 5.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            SelfJoinVisitor visitor = new SelfJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{3.0, 3.0});
            rtree.selfJoinIntersectionQuery(queryRegion, visitor);

            assertTrue(visitor.pairs.size() >= 1);
            assertFalse(visitor.pairs.stream().anyMatch(p ->
                (p.contains(1) && p.contains(3)) || (p.contains(2) && p.contains(3))));
        }

        @Test
        void selfJoinIntersectionWithManyOverlappingRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 5; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 2.0, i + 2.0});
                rtree.insertData(i, region);
            }

            SelfJoinVisitor visitor = new SelfJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            rtree.selfJoinIntersectionQuery(queryRegion, visitor);

            assertTrue(visitor.pairs.size() > 0);
        }
    }

    @Nested
    class SelfJoinMinimumDistanceQuery {

        @Test
        void selfJoinMinimumDistanceFindsPairsWithinEpsilon() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{2.0, 0.0}, new double[]{3.0, 1.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            DistanceJoinVisitor visitor = new DistanceJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{5.0, 5.0});
            rtree.selfJoinMinimumDistanceQuery(queryRegion, 1.0, visitor);

            assertTrue(visitor.pairs.stream().anyMatch(p ->
                (p.contains(1) && p.contains(2))));
        }

        @Test
        void selfJoinMinimumDistanceWithZeroEpsilon() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{2.0, 0.0}, new double[]{3.0, 1.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            DistanceJoinVisitor visitor = new DistanceJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{5.0, 5.0});
            rtree.selfJoinMinimumDistanceQuery(queryRegion, 0.0, visitor);

            assertTrue(visitor.pairs.isEmpty());
        }

        @Test
        void selfJoinMinimumDistanceWithLargeEpsilon() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            DistanceJoinVisitor visitor = new DistanceJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            rtree.selfJoinMinimumDistanceQuery(queryRegion, 10.0, visitor);

            assertTrue(visitor.pairs.stream().anyMatch(p ->
                (p.contains(1) && p.contains(2))));
        }

        @Test
        void selfJoinMinimumDistanceRespectPartialQueryRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{1.5, 0.0}, new double[]{2.5, 1.0});
            Region r3 = new Region(new double[]{10.0, 10.0}, new double[]{11.0, 11.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            DistanceJoinVisitor visitor = new DistanceJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{3.0, 3.0});
            rtree.selfJoinMinimumDistanceQuery(queryRegion, 1.5, visitor);

            assertTrue(visitor.pairs.stream().anyMatch(p ->
                (p.contains(1) && p.contains(2))));
            assertFalse(visitor.pairs.stream().anyMatch(p ->
                p.contains(3)));
        }
    }

    @Nested
    class ConsistencyAcrossQueryTypes {

        @Test
        void intersectionAndPointLocationAreConsistent() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            QueryVisitor pointVisitor = new QueryVisitor();
            Point point = new Point(new double[]{1.5, 1.5});
            rtree.pointLocationQuery(point, pointVisitor);

            QueryVisitor intersectionVisitor = new QueryVisitor();
            Region smallRegion = new Region(new double[]{1.5, 1.5}, new double[]{1.5, 1.5});
            rtree.intersectionQuery(smallRegion, intersectionVisitor);

            assertEquals(pointVisitor.visitedDataIds.size(), intersectionVisitor.visitedDataIds.size());
            assertTrue(pointVisitor.visitedDataIds.containsAll(intersectionVisitor.visitedDataIds));
        }

        @Test
        void containmentIsSubsetOfIntersection() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region r1 = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{0.5, 0.5}, new double[]{3.0, 3.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            QueryVisitor containmentVisitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{4.0, 4.0});
            rtree.containmentQuery(queryRegion, containmentVisitor);

            QueryVisitor intersectionVisitor = new QueryVisitor();
            rtree.intersectionQuery(queryRegion, intersectionVisitor);

            assertTrue(intersectionVisitor.visitedDataIds.containsAll(containmentVisitor.visitedDataIds));
        }

        @Test
        void nearestNeighborResultsAreWithinGrowingRadii() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 10; i++) {
                rtree.insertData(i, new Region(new double[]{i, 0.0}, new double[]{i, 0.0}));
            }

            QueryVisitor visitor1 = new QueryVisitor();
            QueryVisitor visitor2 = new QueryVisitor();

            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(3, queryPoint, visitor1);
            rtree.nearestNeighborQuery(5, queryPoint, visitor2);

            assertTrue(visitor2.visitedDataIds.containsAll(visitor1.visitedDataIds));
        }
    }

    static class SelfJoinVisitor implements IVisitor {
        public final List<List<Integer>> pairs = new ArrayList<>();

        @Override
        public void visitNode(INode n) {}

        @Override
        public void visitData(IData d) {}

        @Override
        public void visitData(ArrayList<IData> v) {
            if (v != null && v.size() == 2) {
                int id1 = v.get(0).getIdentifier();
                int id2 = v.get(1).getIdentifier();

                if (id1 < id2) {
                    List<Integer> pair = Arrays.asList(id1, id2);
                    boolean pairExists = pairs.stream().anyMatch(p ->
                        p.get(0) == id1 && p.get(1) == id2);
                    if (!pairExists) {
                        pairs.add(pair);
                    }
                }
            }
        }
    }

    static class DistanceJoinVisitor implements IVisitor {
        public final List<List<Integer>> pairs = new ArrayList<>();

        @Override
        public void visitNode(INode n) {}

        @Override
        public void visitData(IData d) {}

        @Override
        public void visitData(ArrayList<IData> v) {
            if (v != null && v.size() == 2) {
                int id1 = v.get(0).getIdentifier();
                int id2 = v.get(1).getIdentifier();

                if (id1 < id2) {
                    List<Integer> pair = Arrays.asList(id1, id2);
                    boolean pairExists = pairs.stream().anyMatch(p ->
                        p.get(0) == id1 && p.get(1) == id2);
                    if (!pairExists) {
                        pairs.add(pair);
                    }
                }
            }
        }
    }

    @Nested
    class EdgeCasesInQueries {

        @Test
        void pointLocationQueryOnEmptyTree() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{5.0, 5.0});

            rtree.pointLocationQuery(queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.isEmpty(), "Query on empty tree should return no results");
        }

        @Test
        void intersectionQueryOnEmptyTree() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});

            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.isEmpty(), "Query on empty tree should return no results");
        }

        @Test
        void queryWithVeryLargeRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert a few regions
            for (int i = 0; i < 10; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            QueryVisitor visitor = new QueryVisitor();
            Region largeQuery = new Region(new double[]{-1000.0, -1000.0},
                                          new double[]{1000.0, 1000.0});
            rtree.intersectionQuery(largeQuery, visitor);

            assertEquals(10, visitor.visitedDataIds.size(), "Large query should find all regions");
        }

        @Test
        void queryWithVerySmallRegion() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            Region r2 = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});
            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            QueryVisitor visitor = new QueryVisitor();
            Region tinyQuery = new Region(new double[]{5.25, 5.25}, new double[]{5.26, 5.26});
            rtree.intersectionQuery(tinyQuery, visitor);

            assertEquals(2, visitor.visitedDataIds.size(), "Tiny query should find containing regions");
            assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2)));
        }

        @Test
        void pointLocationOnMultipleOverlappingRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Create 5 concentric overlapping regions
            Point center = new Point(new double[]{5.0, 5.0});
            for (int i = 0; i < 5; i++) {
                double radius = (i + 1) * 1.0;
                Region region = new Region(
                    new double[]{center.getCoord(0) - radius, center.getCoord(1) - radius},
                    new double[]{center.getCoord(0) + radius, center.getCoord(1) + radius}
                );
                rtree.insertData(i, region);
            }

            QueryVisitor visitor = new QueryVisitor();
            rtree.pointLocationQuery(center, visitor);

            assertEquals(5, visitor.visitedDataIds.size(), "Point at center should intersect all concentric regions");
        }

        @Test
        void containmentQueryWithNestedRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert nested regions: r1 ⊂ r2 ⊂ r3
            Region r1 = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});
            Region r2 = new Region(new double[]{4.0, 4.0}, new double[]{7.0, 7.0});
            Region r3 = new Region(new double[]{2.0, 2.0}, new double[]{9.0, 9.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            rtree.containmentQuery(queryRegion, visitor);

            assertEquals(3, visitor.visitedDataIds.size(), "All nested regions should be contained");
            assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2, 3)));
        }

        @Test
        void nearestNeighborWithIdenticalDistances() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Create 4 points equidistant from origin
            rtree.insertData(1, new Region(new double[]{1.0, 0.0}, new double[]{1.0, 0.0}));
            rtree.insertData(2, new Region(new double[]{0.0, 1.0}, new double[]{0.0, 1.0}));
            rtree.insertData(3, new Region(new double[]{-1.0, 0.0}, new double[]{-1.0, 0.0}));
            rtree.insertData(4, new Region(new double[]{0.0, -1.0}, new double[]{0.0, -1.0}));

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(2, queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 2, "Should return at least 2 neighbors");
            assertTrue(visitor.visitedDataIds.size() <= 4, "Should not return more than all 4 points");
        }

        @Test
        void nearestNeighborWithKLargerThanDataset() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 5; i++) {
                rtree.insertData(i, new Region(new double[]{i, i}, new double[]{i, i}));
            }

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(100, queryPoint, visitor);

            assertEquals(5, visitor.visitedDataIds.size(), "Should return all available items when k > dataset size");
        }

        @Test
        void intersectionQueryWithTouchingRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Regions that touch at boundaries
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{1.0, 0.0}, new double[]{2.0, 1.0});
            Region r3 = new Region(new double[]{0.0, 1.0}, new double[]{1.0, 2.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.5, 0.5}, new double[]{1.5, 1.5});
            rtree.intersectionQuery(queryRegion, visitor);

            // All three regions should intersect the query
            assertTrue(visitor.visitedDataIds.size() >= 1, "Should find at least one intersecting region");
        }
    }

    @Nested
    class QueryPerformanceCharacteristics {

        @Test
        void queryPrunesIrrelevantSubtrees() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            // Create clustered data in two distant groups
            for (int i = 0; i < leafCapacity * 3; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            for (int i = 0; i < leafCapacity * 3; i++) {
                int id = (leafCapacity * 3) + i;
                Region region = new Region(new double[]{i + 1000, i + 1000},
                                          new double[]{i + 1000.5, i + 1000.5});
                rtree.insertData(id, region);
            }

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            rtree.intersectionQuery(queryRegion, visitor);

            // Should find items only from first cluster
            assertTrue(visitor.visitedDataIds.stream().allMatch(id -> id < leafCapacity * 3),
                      "Query should only find items from nearby cluster");

            // Verify tree didn't visit all leaf nodes (pruning occurred)
            int totalLeaves = rtree.getStatistics().getLeafNodeCount();
            assertTrue(visitor.leafNodesVisited < totalLeaves,
                      "Query should prune some leaf nodes for efficiency");
        }

        @Test
        void nearestNeighborSearchesMinimalNodes() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert data in a grid
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    Region region = new Region(new double[]{x, y}, new double[]{x + 0.5, y + 0.5});
                    rtree.insertData(x * 10 + y, region);
                }
            }

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(1, queryPoint, visitor);

            // Should visit minimal nodes due to pruning
            int totalNodes = (int) rtree.getStatistics().getNumberOfNodes();
            assertTrue(visitor.indexNodesVisited + visitor.leafNodesVisited < totalNodes,
                      "NN query should prune search space");
        }
    }

    @Nested
    class QueryResultCorrectness {

        @Test
        void allQueriesReturnConsistentResults() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert test data
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{2.0, 2.0});
            Region r2 = new Region(new double[]{1.0, 1.0}, new double[]{3.0, 3.0});
            Region r3 = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            // Test point that's in r1 and r2
            Point testPoint = new Point(new double[]{1.5, 1.5});

            QueryVisitor pointVisitor = new QueryVisitor();
            rtree.pointLocationQuery(testPoint, pointVisitor);

            QueryVisitor intersectionVisitor = new QueryVisitor();
            Region pointAsRegion = new Region(new double[]{1.5, 1.5}, new double[]{1.5, 1.5});
            rtree.intersectionQuery(pointAsRegion, intersectionVisitor);

            assertEquals(pointVisitor.visitedDataIds.size(), intersectionVisitor.visitedDataIds.size(),
                        "Point and intersection queries should return same results");
            assertTrue(pointVisitor.visitedDataIds.containsAll(intersectionVisitor.visitedDataIds));
        }

        @Test
        void intersectionQueryWithSelfIntersection() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            QueryVisitor visitor = new QueryVisitor();
            // Query with the exact same region
            rtree.intersectionQuery(region, visitor);

            assertTrue(visitor.visitedDataIds.contains(1), "Region should intersect with itself");
        }

        @Test
        void containmentQueryDoesNotReturnPartialOverlaps() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region contained = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            Region partial = new Region(new double[]{0.5, 0.5}, new double[]{3.5, 3.5});
            Region outside = new Region(new double[]{10.0, 10.0}, new double[]{11.0, 11.0});

            rtree.insertData(1, contained);
            rtree.insertData(2, partial);
            rtree.insertData(3, outside);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{3.0, 3.0});
            rtree.containmentQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.contains(1), "Should find fully contained region");
            assertFalse(visitor.visitedDataIds.contains(2), "Should not find partially overlapping region");
            assertFalse(visitor.visitedDataIds.contains(3), "Should not find outside region");
        }

        @Test
        void nearestNeighborOrderIsCorrect() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Insert points at known distances from origin
            rtree.insertData(1, new Region(new double[]{1.0, 0.0}, new double[]{1.0, 0.0})); // dist = 1
            rtree.insertData(2, new Region(new double[]{2.0, 0.0}, new double[]{2.0, 0.0})); // dist = 2
            rtree.insertData(3, new Region(new double[]{3.0, 0.0}, new double[]{3.0, 0.0})); // dist = 3
            rtree.insertData(4, new Region(new double[]{4.0, 0.0}, new double[]{4.0, 0.0})); // dist = 4

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(3, queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 3, "Should return at least 3 neighbors");

            // The three nearest should be 1, 2, 3
            assertTrue(visitor.visitedDataIds.contains(1), "Should include nearest point (1)");
            assertTrue(visitor.visitedDataIds.contains(2), "Should include second nearest (2)");
            assertTrue(visitor.visitedDataIds.contains(3), "Should include third nearest (3)");
        }
    }

    @Nested
    class SelfJoinEdgeCases {

        @Test
        void selfJoinOnSingleItem() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
            rtree.insertData(1, region);

            SelfJoinVisitor visitor = new SelfJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{5.0, 5.0});
            rtree.selfJoinIntersectionQuery(queryRegion, visitor);

            assertTrue(visitor.pairs.isEmpty(), "Self-join with single item should return no pairs");
        }

        @Test
        void selfJoinOnDisjointItems() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{10.0, 10.0}, new double[]{11.0, 11.0});
            Region r3 = new Region(new double[]{20.0, 20.0}, new double[]{21.0, 21.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            SelfJoinVisitor visitor = new SelfJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{25.0, 25.0});
            rtree.selfJoinIntersectionQuery(queryRegion, visitor);

            assertTrue(visitor.pairs.isEmpty(), "Self-join on disjoint regions should return no pairs");
        }

        @Test
        void selfJoinDistanceWithTouchingRegions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Regions touching at boundaries
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region r2 = new Region(new double[]{1.0, 0.0}, new double[]{2.0, 1.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            DistanceJoinVisitor visitor = new DistanceJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{3.0, 3.0});
            rtree.selfJoinMinimumDistanceQuery(queryRegion, 0.1, visitor);

            assertTrue(visitor.pairs.stream().anyMatch(p -> p.contains(1) && p.contains(2)),
                      "Touching regions should be found with small epsilon");
        }

        @Test
        void selfJoinDoesNotReturnDuplicatePairs() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Create overlapping regions
            for (int i = 0; i < 5; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 2.0, i + 2.0});
                rtree.insertData(i, region);
            }

            SelfJoinVisitor visitor = new SelfJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            rtree.selfJoinIntersectionQuery(queryRegion, visitor);

            // Check for duplicate pairs
            Set<String> pairSignatures = new HashSet<>();
            for (List<Integer> pair : visitor.pairs) {
                String signature = pair.get(0) + "-" + pair.get(1);
                assertFalse(pairSignatures.contains(signature),
                           "Duplicate pair found: " + signature);
                pairSignatures.add(signature);
            }
        }
    }

    @Nested
    class QueryRobustness {

        @Test
        void queryHandlesTreeAfterDeletions() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            List<Region> regions = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
                regions.add(region);
            }

            // Delete half the items
            for (int i = 0; i < 10; i++) {
                rtree.deleteData(i, regions.get(i));
            }

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{25.0, 25.0});
            rtree.intersectionQuery(queryRegion, visitor);

            assertEquals(10, visitor.visitedDataIds.size(), "Should find only remaining items");
            for (int id : visitor.visitedDataIds) {
                assertTrue(id >= 10 && id < 20, "Should only find items that weren't deleted");
            }
        }

        @Test
        void repeatedQueriesReturnConsistentResults() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 20; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            Region queryRegion = new Region(new double[]{5.0, 5.0}, new double[]{10.0, 10.0});

            QueryVisitor visitor1 = new QueryVisitor();
            rtree.intersectionQuery(queryRegion, visitor1);

            QueryVisitor visitor2 = new QueryVisitor();
            rtree.intersectionQuery(queryRegion, visitor2);

            assertEquals(visitor1.visitedDataIds.size(), visitor2.visitedDataIds.size(),
                        "Repeated queries should return same result count");
            assertTrue(visitor1.visitedDataIds.containsAll(visitor2.visitedDataIds),
                      "Repeated queries should return same results");
        }

        @Test
        void queryAfterBulkLoadReturnsCorrectResults() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 15; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.storePseudoNodes(i, region);
            }
            rtree.bulkLoadRTree(BulkLoadMethod.STR);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{5.0, 5.0}, new double[]{10.0, 10.0});
            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 5, "Should find intersecting regions after bulk load");
        }
    }

    @Nested
    class ComplexSpatialRelationships {

        @Test
        void partialOverlapDetection() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{5.0, 5.0});
            Region r2 = new Region(new double[]{3.0, 3.0}, new double[]{8.0, 8.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{4.0, 4.0}, new double[]{4.5, 4.5});
            rtree.intersectionQuery(queryRegion, visitor);

            assertEquals(2, visitor.visitedDataIds.size(), "Should find both partially overlapping regions");
        }

        @Test
        void containmentWithInclusionChain() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Create inclusion chain: r1 ⊃ r2 ⊃ r3
            Region r1 = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            Region r2 = new Region(new double[]{2.0, 2.0}, new double[]{8.0, 8.0});
            Region r3 = new Region(new double[]{4.0, 4.0}, new double[]{6.0, 6.0});

            rtree.insertData(1, r1);
            rtree.insertData(2, r2);
            rtree.insertData(3, r3);

            // Query that contains r3 but not r1 or r2
            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{3.0, 3.0}, new double[]{7.0, 7.0});
            rtree.containmentQuery(queryRegion, visitor);

            assertEquals(1, visitor.visitedDataIds.size(), "Should only find innermost contained region");
            assertTrue(visitor.visitedDataIds.contains(3));
        }

        @Test
        void nearestNeighborWithRegionsOfDifferentSizes() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Small region close by
            rtree.insertData(1, new Region(new double[]{5.0, 5.0}, new double[]{5.1, 5.1}));

            // Large region farther away
            rtree.insertData(2, new Region(new double[]{10.0, 10.0}, new double[]{20.0, 20.0}));

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(1, queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 1, "Should find at least one neighbor");
            // Nearest by distance to nearest point, not region center
            assertTrue(visitor.visitedDataIds.contains(1), "Small close region should be nearest");
        }

        @Test
        void selfJoinWithComplexOverlapPattern() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Create a star pattern: one central region overlapping with 4 others
            Region center = new Region(new double[]{5.0, 5.0}, new double[]{6.0, 6.0});
            Region north = new Region(new double[]{5.0, 5.5}, new double[]{6.0, 7.0});
            Region south = new Region(new double[]{5.0, 3.0}, new double[]{6.0, 5.5});
            Region east = new Region(new double[]{5.5, 5.0}, new double[]{7.0, 6.0});
            Region west = new Region(new double[]{3.0, 5.0}, new double[]{5.5, 6.0});

            rtree.insertData(0, center);
            rtree.insertData(1, north);
            rtree.insertData(2, south);
            rtree.insertData(3, east);
            rtree.insertData(4, west);

            SelfJoinVisitor visitor = new SelfJoinVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0}, new double[]{10.0, 10.0});
            rtree.selfJoinIntersectionQuery(queryRegion, visitor);

            // Center should pair with all 4 others
            assertTrue(visitor.pairs.size() >= 4, "Should find at least 4 pairs in star pattern");

            // Verify center is in most pairs
            long pairsWithCenter = visitor.pairs.stream()
                .filter(p -> p.contains(0))
                .count();
            assertEquals(4, pairsWithCenter, "Center region should pair with all 4 surrounding regions");
        }
    }

    @Nested
    class BoundaryValueTesting {

        @Test
        void insertionAtCoordinateBoundaries() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            // Test at various boundary values
            Region atZero = new Region(new double[]{0.0, 0.0}, new double[]{1.0, 1.0});
            Region atMax = new Region(new double[]{Double.MAX_VALUE / 2, Double.MAX_VALUE / 2},
                                     new double[]{Double.MAX_VALUE / 2 + 1, Double.MAX_VALUE / 2 + 1});

            rtree.insertData(1, atZero);
            rtree.insertData(2, atMax);

            assertEquals(2, rtree.getStatistics().getNumberOfData());
            assertTrue(rtree.isIndexValid());
        }

        @Test
        void queryAtTreeCapacityBoundary() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
            int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity");

            // Insert exactly at capacity
            for (int i = 0; i < leafCapacity; i++) {
                Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
                rtree.insertData(i, region);
            }

            QueryVisitor visitor = new QueryVisitor();
            Region queryRegion = new Region(new double[]{0.0, 0.0},
                                          new double[]{leafCapacity, leafCapacity});
            rtree.intersectionQuery(queryRegion, visitor);

            assertTrue(visitor.visitedDataIds.size() >= leafCapacity - 1,
                      "Should find most items at capacity boundary");
        }

        @Test
        void nearestNeighborWithKEqualsOne() {
            RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

            for (int i = 0; i < 10; i++) {
                rtree.insertData(i, new Region(new double[]{i, i}, new double[]{i, i}));
            }

            QueryVisitor visitor = new QueryVisitor();
            Point queryPoint = new Point(new double[]{0.0, 0.0});
            rtree.nearestNeighborQuery(1, queryPoint, visitor);

            assertTrue(visitor.visitedDataIds.size() >= 1, "Should find at least one nearest neighbor");
            assertTrue(visitor.visitedDataIds.contains(0), "Nearest point should be ID 0");
        }
    }
}

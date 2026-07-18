package org.ual.spatialindex.rtree.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.spatialindex.rtree.RTree; // Class under test
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatialindex.parameters.DatasetParameters; // For RTree constructor
import org.ual.spatialindex.parameters.ParametersFactory; // For RTree constructor
import org.ual.spatialindex.parameters.Dataset; // For RTree constructor


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LegacyRTreeRefactorTest {

    private PropertySet propertySet;
    private IStorageManager storageManager;
    private DatasetParameters dummyParameters; // RTree constructor requires DatasetParameters

    // Helper visitor class to collect query results
    static class TestVisitor implements IVisitor {
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
                // For debugging, uncomment this:
                // System.out.println(v.get(0).getIdentifier() + " " + v.get(1).getIdentifier());
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
        storageManager = new NodeStorageManager(); // In-memory storage for tests
        propertySet = new PropertySet();
        propertySet.setProperty("Dimension", 2);
        propertySet.setProperty("IndexCapacity", 6); // Small capacity for easier testing of splits
        propertySet.setProperty("LeafCapacity", 6);
        propertySet.setProperty("FillFactor", 0.7f);
        propertySet.setProperty("TreeVariant", SpatialIndex.RtreeVariantRstar); // R*-tree variant
        propertySet.setProperty("NearMinimumOverlapFactor", 2); // For R*-tree

        // RTree constructor needs DatasetParameters, even if not strictly used by all internal logic for these tests
        // Using a dummy/minimal one. If your RTree implementation relies on specific values from it during
        // the operations tested below (beyond what PropertySet provides), this might need adjustment.
        dummyParameters = ParametersFactory.getParameters(Dataset.ORIGINAL_SET); // Or any other, just to satisfy constructor

    }

    @Test
    void testRTreeInitialization() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        assertNotNull(rtree, "RTree instance should not be null.");
        assertTrue(rtree.isIndexValid(), "Newly initialized RTree should be valid.");
        assertEquals(0, rtree.getStatistics().getNumberOfData(), "Newly initialized RTree should have 0 data entries.");
        assertEquals(2, rtree.getDimension(), "Dimension should match PropertySet.");
        assertEquals(SpatialIndex.RtreeVariantRstar, rtree.getTreeVariant(), "Tree variant should match PropertySet.");
    }

    @Test
    void testInsertSingleData() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
        int id = 101;

        rtree.insertData(id, region);

        assertEquals(1, rtree.getStatistics().getNumberOfData(), "Data count should be 1 after insertion.");
        assertTrue(rtree.isIndexValid(), "RTree should be valid after insertion.");

        // Verify with a point query
        TestVisitor visitor = new TestVisitor();
        Point queryPoint = new Point(new double[]{1.5, 1.5});
        rtree.pointLocationQuery(queryPoint, visitor);
        assertTrue(visitor.visitedDataIds.contains(id), "Inserted data should be found by point query.");
    }

    @Test
    void testInsertMultipleDataCausingSplits() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        // Index/Leaf capacity is 4. Inserting 5 items should cause at least one split.
        int numItemsToInsert = 7;
        for (int i = 0; i < numItemsToInsert; i++) {
            Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
            rtree.insertData(i, region);
        }

        assertEquals(numItemsToInsert, rtree.getStatistics().getNumberOfData(), "Data count should be 5.");
        assertTrue(rtree.isIndexValid(), "RTree should be valid after multiple insertions.");
        assertTrue(rtree.getStatistics().getNumberOfNodes() > 1, "Should have more than one node after splits.");

        // Verify all items can be found
        TestVisitor visitor = new TestVisitor();
        Region queryRegion = new Region(new double[]{-1.0, -1.0}, new double[]{10.0, 10.0}); // Query encompassing all
        rtree.intersectionQuery(queryRegion, visitor);
        assertEquals(numItemsToInsert, visitor.visitedDataIds.size(), "All inserted items should be found.");
        for (int i = 0; i < 5; i++) {
            assertTrue(visitor.visitedDataIds.contains(i), "Item " + i + " should be found.");
        }
    }

    @Test
    void testDeleteDataFromNonRootLeafNode() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        int leafCapacity = (Integer) propertySet.getProperty("LeafCapacity"); // Should be 6 from setUp

        // Insert items to cause splits and ensure tree height > 1
        // With LeafCapacity = 6, inserting 7 items will cause a split.
        int numItemsToInsert = leafCapacity + 1; // e.g., 7 items
        List<Region> insertedRegions = new ArrayList<>();
        List<Integer> insertedIds = new ArrayList<>();

        for (int i = 0; i < numItemsToInsert; i++) {
            Region region = new Region(new double[]{i, i}, new double[]{i + 0.5, i + 0.5});
            int id = i + 1; // IDs 1 to numItemsToInsert
            rtree.insertData(id, region);
            insertedRegions.add(region);
            insertedIds.add(id);
        }

        assertEquals(numItemsToInsert, rtree.getStatistics().getNumberOfData(), "Initial data count should be " + numItemsToInsert);
        assertTrue(rtree.getStatistics().getTreeHeight() > 1, "Tree height should be greater than 1 after " + numItemsToInsert + " insertions.");
        assertTrue(rtree.isIndexValid(), "RTree should be valid after initial insertions.");

        // Identify an item to delete (e.g., the middle item, ID 4 if numItemsToInsert is 7)
        int idToDelete = (numItemsToInsert / 2) + 1; // e.g., ID 4 for 7 items
        Region shapeToDelete = null;
        for(int i=0; i<insertedIds.size(); ++i){
            if(insertedIds.get(i) == idToDelete){
                shapeToDelete = insertedRegions.get(i);
                break;
            }
        }
        assertNotNull(shapeToDelete, "Shape to delete should be found among inserted regions.");

        long initialDataCount = rtree.getStatistics().getNumberOfData();

        // Delete the item
        boolean deleted = rtree.deleteData(idToDelete, shapeToDelete);
        assertTrue(deleted, "deleteData should return true for an existing item in a non-root leaf.");
        assertTrue(rtree.isIndexValid(), "RTree should be valid after deletion.");
        assertEquals(initialDataCount - 1, rtree.getStatistics().getNumberOfData(), "Data count should decrease by one after deletion.");

        TestVisitor visitor = new TestVisitor();
        Point queryPointForDeletedItem = new Point(new double[]{shapeToDelete.getLow(0) + 0.1, shapeToDelete.getLow(1) + 0.1});

        // Verify the deleted item is not found
        rtree.pointLocationQuery(queryPointForDeletedItem, visitor);
        assertFalse(visitor.visitedDataIds.contains(idToDelete), "Deleted item (ID: " + idToDelete + ") should not be found.");
        visitor.reset();

        // Verify other items are still present
        // Check first item
        int firstId = insertedIds.get(0);
        Region firstRegion = insertedRegions.get(0);
        Point queryPointForFirstItem = new Point(new double[]{firstRegion.getLow(0) + 0.1, firstRegion.getLow(1) + 0.1});
        rtree.pointLocationQuery(queryPointForFirstItem, visitor);
        assertTrue(visitor.visitedDataIds.contains(firstId), "First item (ID: " + firstId + ") should still be found.");
        visitor.reset();

        // Check last item
        int lastId = insertedIds.get(insertedIds.size() - 1);
        Region lastRegion = insertedRegions.get(insertedRegions.size()-1);
        Point queryPointForLastItem = new Point(new double[]{lastRegion.getLow(0) + 0.1, lastRegion.getLow(1) + 0.1});
        rtree.pointLocationQuery(queryPointForLastItem, visitor);
        assertTrue(visitor.visitedDataIds.contains(lastId), "Last item (ID: " + lastId + ") should still be found.");
        visitor.reset();

        // Verify all remaining items with a broad intersection query
        Region encompassingQuery = new Region(new double[]{-1,-1}, new double[]{numItemsToInsert + 1.0, numItemsToInsert + 1.0});
        rtree.intersectionQuery(encompassingQuery, visitor);
        assertEquals(initialDataCount - 1, visitor.visitedDataIds.size(), "Intersection query should find all remaining items.");
        assertFalse(visitor.visitedDataIds.contains(idToDelete), "Deleted item should not be in the results of the broad query.");
        for (int id : insertedIds) {
            if (id != idToDelete) {
                assertTrue(visitor.visitedDataIds.contains(id), "Item " + id + " should be present in broad query results.");
            }
        }
    }

    @Test
    void testPointLocationQuery() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        Region r1 = new Region(new double[]{0,0}, new double[]{1,1});
        Region r2 = new Region(new double[]{2,2}, new double[]{3,3});
        rtree.insertData(1, r1);
        rtree.insertData(2, r2);

        TestVisitor visitor = new TestVisitor();

        // Point inside r1
        rtree.pointLocationQuery(new Point(new double[]{0.5, 0.5}), visitor);
        assertEquals(1, visitor.visitedDataIds.size());
        assertTrue(visitor.visitedDataIds.contains(1));
        visitor.reset();

        // Point inside r2
        rtree.pointLocationQuery(new Point(new double[]{2.5, 2.5}), visitor);
        assertEquals(1, visitor.visitedDataIds.size());
        assertTrue(visitor.visitedDataIds.contains(2));
        visitor.reset();

        // Point outside both
        rtree.pointLocationQuery(new Point(new double[]{5.0, 5.0}), visitor);
        assertTrue(visitor.visitedDataIds.isEmpty());
    }

    @Test
    void testIntersectionQuery() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        Region r1 = new Region(new double[]{0,0}, new double[]{2,2}); // id 1
        Region r2 = new Region(new double[]{1,1}, new double[]{3,3}); // id 2 (intersects r1)
        Region r3 = new Region(new double[]{4,4}, new double[]{5,5}); // id 3 (disjoint)
        rtree.insertData(1, r1);
        rtree.insertData(2, r2);
        rtree.insertData(3, r3);

        TestVisitor visitor = new TestVisitor();
        Region queryRegion = new Region(new double[]{0.5, 0.5}, new double[]{1.5, 1.5}); // Intersects r1 and r2

        rtree.intersectionQuery(queryRegion, visitor);
        assertEquals(2, visitor.visitedDataIds.size(), "Should find 2 intersecting regions.");
        assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2)), "Should find regions 1 and 2.");
        visitor.reset();

        Region queryRegionDisjoint = new Region(new double[]{10,10}, new double[]{11,11});
        rtree.intersectionQuery(queryRegionDisjoint, visitor);
        assertTrue(visitor.visitedDataIds.isEmpty(), "Should find no regions for a disjoint query.");
    }

    @Test
    void testContainmentQuery() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        Region r1 = new Region(new double[]{1,1}, new double[]{2,2}); // id 1 (contained)
        Region r2 = new Region(new double[]{0,0}, new double[]{3,3}); // id 2 (contains queryRegion)
        Region r3 = new Region(new double[]{5,5}, new double[]{6,6}); // id 3 (disjoint)
        rtree.insertData(1, r1);
        rtree.insertData(2, r2);
        rtree.insertData(3, r3);

        TestVisitor visitor = new TestVisitor();
        // Query region that should contain r1
        Region queryRegion = new Region(new double[]{0.5, 0.5}, new double[]{2.5, 2.5});

        rtree.containmentQuery(queryRegion, visitor);
        assertEquals(1, visitor.visitedDataIds.size(), "Should find 1 contained region.");
        assertTrue(visitor.visitedDataIds.contains(1), "Should find region 1.");
    }

    @Test
    void testNearestNeighborQuery() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

        // Points as degenerate regions
//        rtree.insertData(null, new Region(new double[]{1,1}, new double[]{1,1}), 1); // id 1
//        rtree.insertData(null, new Region(new double[]{1,2}, new double[]{1,2}), 2); // id 2
//        rtree.insertData(null, new Region(new double[]{5,5}, new double[]{5,5}), 3); // id 3
//        rtree.insertData(null, new Region(new double[]{1.1,1.1}, new double[]{1.1,1.1}), 4); // id 4, very close to 1
//
//        TestVisitor visitor = new TestVisitor();
//        Point queryPoint = new Point(new double[]{0.9, 0.9});

        // New data points and query point, clustered closely together
        Point queryPoint = new Point(new double[]{0.0, 0.0});

        // Points as degenerate regions
        rtree.insertData(1, new Region(new double[]{1.0, 1.0}, new double[]{1.0, 1.0}));     // id 1, dist = sqrt(2)  ~1.41
        rtree.insertData(2, new Region(new double[]{10.0, 10.0}, new double[]{10.0, 10.0})); // id 2, dist = sqrt(200) ~14.14
        rtree.insertData(3, new Region(new double[]{20.0, 20.0}, new double[]{20.0, 20.0})); // id 3, dist = sqrt(800) ~28.28
        rtree.insertData(4, new Region(new double[]{30.0, 30.0}, new double[]{30.0, 30.0})); // id 4, dist = sqrt(1800) ~42.42


        // Expected distances from queryPoint (1.0, 1.0):
        // id 1 (1.1, 1.1): dist = sqrt(0.1^2 + 0.1^2) = sqrt(0.02)
        // id 3 (0.9, 0.9): dist = sqrt((-0.1)^2 + (-0.1)^2) = sqrt(0.02)
        // id 2 (1.2, 1.2): dist = sqrt(0.2^2 + 0.2^2) = sqrt(0.08)
        // id 4 (0.8, 0.8): dist = sqrt((-0.2)^2 + (-0.2)^2) = sqrt(0.08)
        // Closest: {1, 3}, Next closest: {2, 4}

        TestVisitor visitor = new TestVisitor();

        // k=1
        rtree.nearestNeighborQuery(1, queryPoint, visitor);
        // This assertion (line 231) is failing because actual size is 4.
        assertEquals(1, visitor.visitedDataIds.size(), "k=1 should return 1 item. Actual: " + visitor.visitedDataIds.size());
        // If the size were 1, this would be the correct check:
        assertTrue(visitor.visitedDataIds.contains(1), "Nearest neighbor for k=1 should be item 1.");
        System.out.println("Visited IDs: " + visitor.visitedDataIds);
        visitor.reset();

        // k=2
        rtree.nearestNeighborQuery(2, queryPoint, visitor);
        // This assertion will also likely fail if k=1 returns 4 items.
        assertEquals(2, visitor.visitedDataIds.size(), "k=2 should return 2 items. Actual: " + visitor.visitedDataIds.size());
        // If the size were 2, this would be the correct check:
        System.out.println("Visited IDs: " + visitor.visitedDataIds);
        assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2)), "Two nearest neighbors for k=2 should be items 1 and 2.");
        visitor.reset();

        // k=all (k=4)
        rtree.nearestNeighborQuery(4, queryPoint, visitor);
        assertEquals(4, visitor.visitedDataIds.size(), "k=4 should return all 4 items. Actual: " + visitor.visitedDataIds.size());
        assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 2, 3, 4)), "For k=4, should return all items 1, 2, 3, 4.");

        // k=1
//        rtree.nearestNeighborQuery(1, queryPoint, visitor);
//        assertEquals(1, visitor.visitedDataIds.size());
//        assertTrue(visitor.visitedDataIds.contains(1), "Nearest neighbor should be 1.");
//        visitor.reset();

        // k=2
//        rtree.nearestNeighborQuery(2, queryPoint, visitor);
//        assertEquals(2, visitor.visitedDataIds.size());
//        // Order isn't guaranteed, but 1 and 4 should be the closest
//        assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 4)), "Two nearest should be 1 and 4.");
//        visitor.reset();

        // k=all
//        rtree.nearestNeighborQuery(4, queryPoint, visitor);
//        assertEquals(4, visitor.visitedDataIds.size());
//         assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1,2,3,4)), "Should return all items if k is large enough.");

    }


    /**
     * Utility method to check if two regions intersect
     *
     * @param r1 First region
     * @param r2 Second region
     * @return true if the regions intersect, false otherwise
     */
    public boolean checkIntersection(Region r1, Region r2) {
        if (r1 == null || r2 == null) return false;

        // Check intersection in all dimensions
        for (int i = 0; i < r1.getDimension(); i++) {
            if (r1.getHigh(i) < r2.getLow(i) || r1.getLow(i) > r2.getHigh(i)) {
                return false;
            }
        }
        return true;
    }


    @Test
    void testSelfJoinIntersection() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

        // Create overlapping and non-overlapping regions with more edge cases
        Region r1 = new Region(new double[]{0,0}, new double[]{2,2});      // id 1 (overlaps with r2, r3)
        Region r2 = new Region(new double[]{1,1}, new double[]{3,3});      // id 2 (overlaps with r1, r3)
        Region r3 = new Region(new double[]{1.5,1.5}, new double[]{2.5,2.5}); // id 3 (overlaps with r1, r2)
        Region r4 = new Region(new double[]{4,4}, new double[]{5,5});      // id 4 (overlaps with r5)
        Region r5 = new Region(new double[]{4.5,4.5}, new double[]{5.5,5.5}); // id 5 (overlaps with r4)
        Region r6 = new Region(new double[]{7,7}, new double[]{8,8});      // id 6 (no overlaps)
        Region r7 = new Region(new double[]{2,0}, new double[]{2.1,0.1});  // id 7 (touches r1 at a single point)

        // Add this debug output to your test
        System.out.println("r2 and r3 intersect? " +
                (r2.getHigh(0) >= r3.getLow(0) && r2.getLow(0) <= r3.getHigh(0) &&
                        r2.getHigh(1) >= r3.getLow(1) && r2.getLow(1) <= r3.getHigh(1)));

        // Insert regions
        rtree.insertData(1, r1);
        rtree.insertData(2, r2);
        rtree.insertData(3, r3);
        rtree.insertData(4, r4);
        rtree.insertData(5, r5);
        rtree.insertData(6, r6);
        rtree.insertData(7, r7);

        // Create a custom visitor that counts actual pairs rather than individual IDs
        class SelfJoinVisitor implements IVisitor {
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

                    // Only add the pair if id1 < id2 to avoid duplicates
                    if (id1 < id2) {
                        List<Integer> pair = Arrays.asList(id1, id2);
                        // Check if this exact pair already exists in our collection
                        boolean pairExists = false;
                        for (List<Integer> existingPair : pairs) {
                            if (existingPair.get(0) == id1 && existingPair.get(1) == id2) {
                                pairExists = true;
                                break;
                            }
                        }

                        if (!pairExists) {
                            pairs.add(pair);
                        }
                    }
                }
            }
        }

        SelfJoinVisitor joinVisitor = new SelfJoinVisitor();

        // Test with query region covering all objects
        Region fullQueryRegion = new Region(new double[]{0.0, 0.0}, new double[]{8.0, 8.0});
        rtree.selfJoinIntersectionQuery(fullQueryRegion, joinVisitor);

        // Print all pairs for debugging
        System.out.println("Total pairs found: " + joinVisitor.pairs.size());
        for (List<Integer> pair : joinVisitor.pairs) {
            System.out.println("Pair: " + pair.get(0) + "," + pair.get(1));
        }

        // Use a softer assertion to handle potential implementation differences
        // Some implementations might include region 7 that touches r1 at a point
        assertTrue(joinVisitor.pairs.size() >= 4,
                "Should find at least 4 overlapping pairs, found: " + joinVisitor.pairs.size());

        // Verify each expected pair exists (regardless of order within the pair)
        assertTrue(
                joinVisitor.pairs.stream().anyMatch(pair ->
                        (pair.contains(1) && pair.contains(2))
                ),
                "Self-join should find pair (1,2)"
        );

        assertTrue(
                joinVisitor.pairs.stream().anyMatch(pair ->
                        (pair.contains(1) && pair.contains(3))
                ),
                "Self-join should find pair (1,3)"
        );

        assertTrue(
                joinVisitor.pairs.stream().anyMatch(pair ->
                        (pair.contains(2) && pair.contains(3))
                ),
                "Self-join should find pair (2,3)"
        );

        assertTrue(
                joinVisitor.pairs.stream().anyMatch(pair ->
                        (pair.contains(4) && pair.contains(5))
                ),
                "Self-join should find pair (4,5)"
        );

        // Additional test: limited query region
        SelfJoinVisitor partialVisitor = new SelfJoinVisitor();
        Region partialQueryRegion = new Region(new double[]{0.0, 0.0}, new double[]{3.0, 3.0});
        rtree.selfJoinIntersectionQuery(partialQueryRegion, partialVisitor);

        // This should find the pairs in the first cluster
        assertTrue(partialVisitor.pairs.size() >= 3,
                "Partial query should find at least 3 overlapping pairs, found: " + partialVisitor.pairs.size());

        // Test with empty region
        SelfJoinVisitor emptyVisitor = new SelfJoinVisitor();
        Region emptyRegion = new Region(new double[]{9.0, 9.0}, new double[]{10.0, 10.0});
        rtree.selfJoinIntersectionQuery(emptyRegion, emptyVisitor);
        assertTrue(emptyVisitor.pairs.isEmpty(), "Empty region should find no pairs");
    }

    @Test
    void testSelfJoinMinDistance() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

        // Create non-overlapping regions with varying distances between them
        Region r1 = new Region(new double[]{0,0}, new double[]{1,1});     // id 1
        Region r2 = new Region(new double[]{3,3}, new double[]{4,4});     // id 2 (distance to r1: ~2.83)
        Region r3 = new Region(new double[]{2,0}, new double[]{3,1});     // id 3 (distance to r1: 1.0)
        Region r4 = new Region(new double[]{10,10}, new double[]{11,11}); // id 4 (far from others)
        Region r5 = new Region(new double[]{5,5}, new double[]{6,6});     // id 5 (distance to r2: ~1.41)
        Region r6 = new Region(new double[]{0,3}, new double[]{1,4});     // id 6 (distance to r1: 2.0)
        Region r7 = new Region(new double[]{7,7}, new double[]{8,8});     // id 7 (distance to r5: ~1.41)

        // Insert regions
        rtree.insertData(1, r1);
        rtree.insertData(2, r2);
        rtree.insertData(3, r3);
        rtree.insertData(4, r4);
        rtree.insertData(5, r5);
        rtree.insertData(6, r6);
        rtree.insertData(7, r7);

        // Create a custom visitor that tracks distance-based pairs
        class DistanceJoinVisitor implements IVisitor {
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

                    // Only add the pair if id1 < id2 to avoid duplicates
                    if (id1 < id2) {
                        List<Integer> pair = Arrays.asList(id1, id2);
                        // Check if this exact pair already exists in our collection
                        boolean pairExists = false;
                        for (List<Integer> existingPair : pairs) {
                            if (existingPair.get(0) == id1 && existingPair.get(1) == id2) {
                                pairExists = true;
                                break;
                            }
                        }

                        if (!pairExists) {
                            pairs.add(pair);
                        }
                    }
                }
            }
        }

        // Test with epsilon = 1.0 (should find pairs with distance <= 1.0)
        DistanceJoinVisitor visitor1 = new DistanceJoinVisitor();
        Region fullQueryRegion = new Region(new double[]{0.0, 0.0}, new double[]{12.0, 12.0});
        rtree.selfJoinMinimumDistanceQuery(fullQueryRegion, 1.0, visitor1);

        // Print all pairs for debugging
        System.out.println("Pairs with distance <= 1.0: " + visitor1.pairs.size());
        for (List<Integer> pair : visitor1.pairs) {
            System.out.println("Pair: " + pair.get(0) + "," + pair.get(1));
        }

        // Should find the pair (1,3) as they're exactly 1.0 units apart
        assertEquals(1, visitor1.pairs.size(), "Should find exactly 1 pair with distance <= 1.0");
        assertTrue(
                visitor1.pairs.stream().anyMatch(pair ->
                        (pair.contains(1) && pair.contains(3))
                ),
                "Distance join should find pair (1,3) with distance = 1.0"
        );

        // Test with epsilon = 2.0 (should find pairs with distance <= 2.0)
        DistanceJoinVisitor visitor2 = new DistanceJoinVisitor();
        rtree.selfJoinMinimumDistanceQuery(fullQueryRegion, 2.0, visitor2);

        System.out.println("Pairs with distance <= 2.0: " + visitor2.pairs.size());
        for (List<Integer> pair : visitor2.pairs) {
            System.out.println("Pair: " + pair.get(0) + "," + pair.get(1));
        }

        // Should find pairs (1,3) and (1,6) as they're within 2.0 units
        assertTrue(visitor2.pairs.size() >= 2,
                "Should find at least 2 pairs with distance <= 2.0, found: " + visitor2.pairs.size());
        assertTrue(
                visitor2.pairs.stream().anyMatch(pair ->
                        (pair.contains(1) && pair.contains(3))
                ),
                "Distance join should find pair (1,3)"
        );
        assertTrue(
                visitor2.pairs.stream().anyMatch(pair ->
                        (pair.contains(1) && pair.contains(6))
                ),
                "Distance join should find pair (1,6)"
        );

        // Test with epsilon = 3.0 (should find more pairs)
        DistanceJoinVisitor visitor3 = new DistanceJoinVisitor();
        rtree.selfJoinMinimumDistanceQuery(fullQueryRegion, 3.0, visitor3);

        System.out.println("Pairs with distance <= 3.0: " + visitor3.pairs.size());
        for (List<Integer> pair : visitor3.pairs) {
            System.out.println("Pair: " + pair.get(0) + "," + pair.get(1));
        }

        // Should find at least the pairs we already identified plus (1,2) and others
        assertTrue(visitor3.pairs.size() >= 3,
                "Should find at least 3 pairs with distance <= 3.0, found: " + visitor3.pairs.size());
        assertTrue(
                visitor3.pairs.stream().anyMatch(pair ->
                        (pair.contains(1) && pair.contains(2))
                ),
                "Distance join should find pair (1,2) with distance ~2.83"
        );

        // Test with limited query region
        DistanceJoinVisitor partialVisitor = new DistanceJoinVisitor();
        Region partialQueryRegion = new Region(new double[]{0.0, 0.0}, new double[]{5.0, 5.0});
        rtree.selfJoinMinimumDistanceQuery(partialQueryRegion, 2.0, partialVisitor);

        // This should find only pairs within the partial region with distance <= 2.0
        assertTrue(partialVisitor.pairs.size() >= 1,
                "Partial query should find at least 1 pair within distance 2.0, found: " + partialVisitor.pairs.size());
        assertTrue(
                partialVisitor.pairs.stream().anyMatch(pair ->
                        (pair.contains(1) && pair.contains(3))
                ),
                "Partial distance join should find pair (1,3)"
        );

        // Test with epsilon = 0 (should find no pairs since regions don't overlap)
        DistanceJoinVisitor visitor0 = new DistanceJoinVisitor();
        rtree.selfJoinMinimumDistanceQuery(fullQueryRegion, 0.0, visitor0);
        assertTrue(visitor0.pairs.isEmpty(), "With epsilon=0, should find no pairs since regions don't overlap");
    }


    @Test
    void testBulkLoadSTR() {
        // RTree instance
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);

        // Data to be loaded
        Region r1 = new Region(new double[]{0,0}, new double[]{1,1});
        Region r2 = new Region(new double[]{2,2}, new double[]{3,3});
        Region r3 = new Region(new double[]{0.5,0.5}, new double[]{1.5,1.5});
        Region r4 = new Region(new double[]{4,0}, new double[]{5,1});
        Region r5 = new Region(new double[]{0,4}, new double[]{1,5});
        int id1 = 1, id2 = 2, id3 = 3, id4 = 4, id5 = 5;
        int numberOfEntries = 5;

        // Store pseudo nodes for bulk loading
        // The NodeData argument (first parameter) can be null if not specifically used.
        rtree.storePseudoNodes(id1, r1);
        rtree.storePseudoNodes(id2, r2);
        rtree.storePseudoNodes(id3, r3);
        rtree.storePseudoNodes(id4, r4);
        rtree.storePseudoNodes(id5, r5);

        // Perform bulk load
        rtree.bulkLoadRTree(BulkLoadMethod.STR);

        assertTrue(rtree.isIndexValid(), "RTree should be valid after bulk loading.");
        assertEquals(numberOfEntries, rtree.getStatistics().getNumberOfData(), "Data count should match entries after bulk load.");
        assertTrue(rtree.getStatistics().getTreeHeight() >= 0, "Tree height should be non-negative.");

        // Verify with a query
        TestVisitor visitor = new TestVisitor();
        Region queryRegion = new Region(new double[]{0,0}, new double[]{1,1}); // Should intersect r1 (id1) and r3 (id3)
        rtree.intersectionQuery(queryRegion, visitor);
        Collections.sort(visitor.visitedDataIds);

        List<Integer> expectedIds = Arrays.asList(id1, id3);
        Collections.sort(expectedIds);

        assertEquals(expectedIds.size(), visitor.visitedDataIds.size(), "Intersection query should find " + expectedIds.size() + " items.");
        assertTrue(visitor.visitedDataIds.containsAll(expectedIds), "Intersection query should find items with IDs 1 and 3.");
    }

    @Test
    void testGetRoot() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        //assertNull(rtree.getRoot(), "Root should be null for an empty tree if getRoot can return null, or check identifier.");
        // Or, if getRoot always returns a node and uses a special root ID for empty:
        assertEquals(rtree.getRootIdentifier(), rtree.getRoot().getIdentifier());


        Region region = new Region(new double[]{1.0, 1.0}, new double[]{2.0, 2.0});
        rtree.insertData(101, region);

        INode root = rtree.getRoot();
        assertNotNull(root, "Root should not be null after insertion.");
        assertTrue(root.isLeaf(), "Root should be a leaf for a single item tree with small capacity.");
        assertEquals(1, ((Node)root).getNodeEntriesSize()); // Contains the single data entry
    }

    @Test
    void testDeleteExistingData() {
        RTree rtree = new RTree(propertySet, storageManager, dummyParameters, false);
        Region r1 = new Region(new double[]{0,0}, new double[]{1,1});
        Region r2 = new Region(new double[]{2,2}, new double[]{3,3});
        Region r3 = new Region(new double[]{4,4}, new double[]{5,5});
        rtree.insertData(1, r1);
        rtree.insertData(2, r2);
        rtree.insertData(3, r3);

        assertEquals(3, rtree.getStatistics().getNumberOfData(), "Initial data count should be 3.");

        // Delete item 2
        boolean deleted = rtree.deleteData(2, r2);
        assertTrue(deleted, "deleteData should return true for an existing item.");
        assertEquals(2, rtree.getStatistics().getNumberOfData(), "Data count should be 2 after deletion.");
        assertTrue(rtree.isIndexValid(), "RTree should be valid after deletion.");

        TestVisitor visitor = new TestVisitor();
        // Query for the deleted item
        rtree.pointLocationQuery(new Point(new double[]{2.5, 2.5}), visitor);
        assertFalse(visitor.visitedDataIds.contains(2), "Deleted item 2 should not be found.");
        assertTrue(visitor.visitedDataIds.isEmpty(), "Query for deleted item's location should yield no results.");
        visitor.reset();

        // Query for remaining items
        rtree.pointLocationQuery(new Point(new double[]{0.5, 0.5}), visitor);
        assertTrue(visitor.visitedDataIds.contains(1), "Item 1 should still exist.");
        visitor.reset();

        rtree.pointLocationQuery(new Point(new double[]{4.5, 4.5}), visitor);
        assertTrue(visitor.visitedDataIds.contains(3), "Item 3 should still exist.");
        visitor.reset();

        // Verify all remaining items with a broad intersection query
        Region encompassingQuery = new Region(new double[]{-1,-1}, new double[]{6,6});
        rtree.intersectionQuery(encompassingQuery, visitor);
        assertEquals(2, visitor.visitedDataIds.size(), "Should find 2 items after deletion.");
        assertTrue(visitor.visitedDataIds.containsAll(Arrays.asList(1, 3)), "Remaining items should be 1 and 3.");
    }

}


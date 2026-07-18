package org.ual.spatialindex.rtreebase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.spatialindex.NodeEntry;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;

/**
  * A leaf node in an R-Tree structure that stores actual data entries.
  * <p>
  * Leaf nodes are the terminal nodes in the R-Tree hierarchy that contain the actual
  * data entries (points, rectangles, or polygons). Each entry in a leaf node consists
  * of a tuple (id, mbr) where id is a unique identifier and mbr is the minimum
  * bounding region of the spatial object.
  * <p>
  * This class provides leaf-specific implementations for R-Tree operations including:
  * <ul>
  *   <li>Inserting new data entries at the leaf level</li>
  *   <li>Finding and retrieving specific data entries</li>
  *   <li>Splitting overflowed nodes using different algorithms (Linear, Quadratic, R*)</li>
  *   <li>Handling deletions and subsequent tree reorganization</li>
  *   <li>Managing document associations for entries (for text-indexed R-trees)</li>
  * </ul>
  * <p>
  * The maximum number of entries a leaf can hold is determined by the R-Tree's
  * configured leaf capacity parameter. When this capacity is exceeded, the node
  * splits according to the tree's variant strategy.
  *
  * @see Node The base node class that this leaf extends
  * @see AbstractRTree The main R-Tree implementation
  */
public class Leaf extends Node {
    private static final Logger logger = LogManager.getLogger(Leaf.class);  // Logger for this class

    /**
      * Constructs a new Leaf node with the specified R-Tree and identifier.
      * The node is initialized with a level of 0 (indicating it is a leaf) and
      * the leaf capacity defined in the R-Tree configuration.
      *
      * @param rTree The R-Tree this leaf belongs to.
      * @param identifier The unique identifier for this leaf node.
      */
    public Leaf(AbstractRTree rTree, int identifier) {
        super(rTree, identifier, 0, rTree.leafCapacity);
    }


    /**
     * Chooses the appropriate subtree for insertion at this leaf node. As leaf nodes are terminal nodes
     * in the R-tree structure, this method always returns the current node instance.
     *
     * @param mbr The minimum bounding region of the entry to be inserted
     * @param level The target level for insertion (ignored in leaf nodes since they are always at level 0)
     * @param pathBuffer A stack that tracks the traversal path from root to leaf (modified by parent nodes)
     * @return {@code this} leaf node, as leaf nodes have no subtrees to choose from
     * @see Node#chooseSubtree(Region, int, Stack)
     */
    @Override
    public Node chooseSubtree(Region mbr, int level, Stack<Integer> pathBuffer) {
        // Leaf nodes have no children to choose from, so they always return themselves
        return this;
    }


    /**
     * Chooses the subtree to insert into, considering document associations. Since this is a leaf node,
     * it always returns itself as there are no further subtrees to traverse.
     *
     * @param mbr         The minimum bounding region to compare against
     * @param level      The target level for insertion (ignored in leaf nodes)
     * @param pathBuffer A stack that tracks the traversal path (modified by parent nodes)
     * @param doc        A set of document IDs associated with the entry being inserted
     * @return           This leaf node, as leaf nodes have no subtrees to choose from
     * @see #chooseSubtree(Region, int, Stack)
     */
    @Override
    public Node chooseSubtree(Region mbr, int level, Stack<Integer> pathBuffer, HashSet<Integer> doc) {
        // Leaf nodes have no children to choose from, similar to the non-document version above
        return this;
    }


    /**
      * Finds the leaf node that contains the given entry by matching both ID and region.
      * For performance optimization, this method implements a two-step validation:
      * <ol>
      *   <li>First checks if an entry with the given ID exists in this node</li>
      *   <li>Then compares the provided region with the entry's MBR if ID matches</li>
      * </ol>
      *
      * @param mbr The minimum bounding region to match against the entry's MBR
      * @param id The identifier of the entry to search for
      * @param pathBuffer A stack tracking the traversal path (not used in leaf nodes)
      * @return this leaf node if an entry with matching ID and region is found, null otherwise
      * @see Node#findLeaf(int, Region, Stack)
      */
    @Override
    public Leaf findLeaf(int id, Region mbr, Stack<Integer> pathBuffer) {
        // Check if the entry exists in this node
        NodeEntry entry = nodeEntries.get(id);
        if (entry != null && mbr.equals(entry.getMBR())) {
            return this;
        }
        return null;
    }


    /**
     * Splits an overfull leaf node into two new nodes during an insertion operation.
     * This operation is triggered when adding a new entry would exceed the node's capacity.
     * The split algorithm distributes entries between two new nodes to maintain balance
     * and optimal space utilization.
     *
     * <p>The distribution of entries is performed according to the configured R-tree variant:
     * <ul>
     *   <li>Linear Split - Chooses split axis and distributions based on extreme coordinates</li>
     *   <li>Quadratic Split - Picks seeds that would waste the most area if grouped together</li>
     *   <li>R*-tree Split - Minimizes overlap and considers different sort dimensions</li>
     * </ul>
     *
     * @param id the unique identifier for the new entry
     * @param mbr the minimum bounding region of the new entry
     * @return a two-element array containing the newly created leaf nodes where
     *         index 0 is the "left" node containing first partition and
     *         index 1 is the "right" node containing second partition
     * @see SpatialIndex#RtreeVariantLinear
     * @see SpatialIndex#RtreeVariantQuadratic
     * @see SpatialIndex#RtreeVariantRstar
     */
    @Override
    protected Node[] split(int id, Region mbr) {
        return split(id, mbr, null);
    }


    /**
      * Splits an overfull leaf node into two nodes when insertion would exceed capacity,
      * considering document associations.
      * <p>
      * The split process uses different strategies based on the RTree variant:
      * <ul>
      *   <li>Linear/Quadratic - Uses {@code rtreeSplit} with document ID optimization</li>
      *   <li>R*-Tree - Uses {@code rstarSplit} focusing on spatial relationships</li>
      * </ul>
      *
      * @param id The identifier of the new entry
      * @param mbr The minimum bounding region of the new entry
      * @param doc The set of document IDs associated with the entry
      * @return A two-element array containing the new nodes:
      *         <ul>
      *           <li>[0] - left node with first partition of entries</li>
      *           <li>[1] - right node with second partition of entries</li>
      *         </ul>
      * @see SpatialIndex#RtreeVariantLinear
      * @see SpatialIndex#RtreeVariantQuadratic
      * @see SpatialIndex#RtreeVariantRstar
      */
    @Override
    protected Node[] split(int id, Region mbr, HashSet<Integer> doc) {
        rTree.stats.splits++;

        ArrayList<Integer> g1 = new ArrayList<>(), g2 = new ArrayList<>();

        switch (rTree.treeVariant) {
            case SpatialIndex.RtreeVariantLinear:
            case SpatialIndex.RtreeVariantQuadratic:
                if (doc == null) {
                    rtreeSplit(id, mbr, g1, g2);
                } else {
                    rtreeSplit(id, mbr, g1, g2, doc);
                }
                break;
            case SpatialIndex.RtreeVariantRstar:
                rstarSplit(id, mbr, g1, g2);
                break;
            default:
                logger.error("Unknown RTree variant: {}", rTree.treeVariant);
                throw new IllegalStateException("Unknown RTree variant: " + rTree.treeVariant);
        }

        // Create new leaf nodes
        Node left = new Leaf(rTree, -1);
        Node right = new Leaf(rTree, -1);

        // Transfer entries to the first node (left)
        for (Integer entryId : g1) {
            NodeEntry entry = nodeEntries.get(entryId);
            if (entry != null) {
                if (entry.getDocument() != null) {
                    left.insertEntry(entry.getIdentifier(), entry.getMBR(), entry.getDocument());
                } else {
                    left.insertEntry(entry.getIdentifier(), entry.getMBR());
                }
            }
        }

        // Transfer entries to the second node (right)
        for (Integer entryId : g2) {
            NodeEntry entry = nodeEntries.get(entryId);
            if (entry != null) {
                if (entry.getDocument() != null) {
                    right.insertEntry(entry.getIdentifier(), entry.getMBR(), entry.getDocument());
                } else {
                    right.insertEntry(entry.getIdentifier(), entry.getMBR());
                }
            }
        }

        // Return the split nodes
        return new Node[]{left, right};
    }


    /**
      * Deletes a data entry with the specified ID from this leaf node and rebalances the tree.
      * <p>
      * The deletion process follows these steps:
      * <ol>
      *   <li>Verifies entry existence in the current leaf node</li>
      *   <li>Removes the entry and updates node statistics</li>
      *   <li>Writes the modified node back to storage</li>
      *   <li>Performs tree condensing to handle potential underflow</li>
      *   <li>Reinserts orphaned entries from eliminated nodes</li>
      * </ol>
      *
      * @param id The identifier of the data entry to delete
      * @param pathBuffer Stack containing the traversal path from root to this leaf node,
      *                   used for upward traversal during tree condensing
      * @see #condenseTree(Stack, Stack) For tree reorganization after deletion
      * @see #deleteEntry(int) For entry removal from the node
      * @see AbstractRTree#writeNode(Node) For persisting node changes
      */
    public void deleteData(int id, Stack<Integer> pathBuffer) {
        // Check if the entry exists in this node
        if (!nodeEntries.containsKey(id)) {
            return;
        }

        // Remove the entry and update the node in storage
        deleteEntry(id);
        rTree.writeNode(this);

        // Collect nodes that need to be reinserted during tree condensing
        Stack<Node> toReinsert = new Stack<>();
        condenseTree(toReinsert, pathBuffer);

        // Re-insert entries from eliminated nodes
        while (!toReinsert.isEmpty()) {
            Node node = toReinsert.pop();
            rTree.deleteNode(node);

            for (Map.Entry<Integer, NodeEntry> entry : node.nodeEntries.entrySet()) {
                // Create overflow table for each insertion
                HashMap<Integer, Boolean> overflowTableMap = new HashMap<>(rTree.stats.treeHeight);

                NodeEntry nodeEntry = entry.getValue();

                rTree.insertDataImpl(
                        nodeEntry.getIdentifier(),
                        nodeEntry.getMBR(),
                        node.level,
                        overflowTableMap
                );
            }
        }
    }


    /**
     * Deletes a data entry with the specified ID and document ID from this leaf node and performs tree rebalancing.
     * This method extends the basic deleteData functionality by considering document associations.
     *
     * @param id The identifier of the data entry to delete
     * @param pathBuffer Stack containing the path from root to this node, used for upward traversal
     *                   during tree condensing
     * @param doc The set of document IDs associated with the entry to be deleted
     */
    public void deleteData(int id, Stack<Integer> pathBuffer, HashSet<Integer> doc) {
        // Check if the entry exists in this node
        if (!nodeEntries.containsKey(id)) {
            return;
        }

        // Remove the entry and update the node in storage
        deleteEntry(id);

        // Decrement the total data count after successful deletion
        if (rTree.stats != null) {
            rTree.stats.decrementData();
        }

        rTree.writeNode(this);

        // Collect nodes that need to be reinserted during tree condensing
        Stack<Node> toReinsert = new Stack<>();
        condenseTree(toReinsert, pathBuffer);

        // Re-insert entries from eliminated nodes
        while (!toReinsert.isEmpty()) {
            Node node = toReinsert.pop();
            rTree.deleteNode(node); // Delete the underflowed node from storage

            for (Map.Entry<Integer, NodeEntry> entry : node.nodeEntries.entrySet()) {
                int entryId = entry.getKey();
                NodeEntry nodeEntry = entry.getValue();

                // Reinsert the entry
                // Check if the node is a leaf and has document sets for this entry
                if (node.isLeaf() && nodeEntry.getDocument() != null) {
                    // Use document-aware insertion for data entries from leaves
                    rTree.insertDataImpl(
                            entryId,
                            nodeEntry.getMBR(),
                            nodeEntry.getDocument()
                    );
                } else {
                    // Fallback to non-document insertion (for leaf entries without docs)
                    // or for reinserting children of internal nodes.
                    HashMap<Integer, Boolean> overflowTableMap = new HashMap<>(rTree.stats.treeHeight);
                    int levelOfEntryToReinsert;

                    if (node.isLeaf()) { // Reinserting data from a leaf node
                        levelOfEntryToReinsert = 0; // Data entries are at level 0
                    } else { // Reinserting a child MBR from an internal node
                        levelOfEntryToReinsert = node.level - 1; // Level of the child node itself
                    }

                    rTree.insertDataImpl(
                            entryId,
                            nodeEntry.getMBR(),
                            levelOfEntryToReinsert,
                            overflowTableMap
                    );
                }
            }
        }
    }
}

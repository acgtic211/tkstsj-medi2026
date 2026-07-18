package org.ual.spatialindex.rtreebase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.spatialindex.*;

import java.util.*;
import java.util.stream.Collectors;

/**
  * Node is an abstract class that implements the INode interface.
  * It serves as a foundation for R-tree nodes, providing common properties and methods
  * for both leaf and index nodes in the R-tree structure.
  *
  * <p>Key characteristics:
  * <ul>
  *   <li>Supports spatial indexing through minimum bounding rectangles (MBRs)</li>
  *   <li>Maintains node hierarchy with parent-child relationships</li>
  *   <li>Handles both leaf nodes (containing actual data) and index nodes (internal structure)</li>
  *   <li>Implements R*-tree optimizations for improved query performance</li>
  *   <li>Supports document-aware operations for text-based spatial queries</li>
  * </ul>
  *
  * <p>Node types:
  * <ul>
  *   <li>Leaf nodes (level 0): Store actual spatial data entries and associated documents</li>
  *   <li>Index nodes (level > 0): Maintain tree structure and aggregate document information</li>
  * </ul>
  */
public abstract class Node implements INode {
    //==========================================================================================
    //====================================== Class Fields ======================================
    //==========================================================================================

    // Core node properties
    protected AbstractRTree rTree;                  // Parent of all nodes
    protected int identifier = -1;                  // The unique ID of this node
    protected int level = -1;                       // The level of the node in the tree (leaves at 0)
    protected int nodeType = 0;                     // The node type (leaf or index)
    protected int ancestor = -1;                    // The parent node ID

    // Node capacity
    protected int capacity = -1;                    // Specifies the node capacity

    // Spatial data structures
    protected Region nodeMBR;                       // The minimum bounding region for all data

    // Node data management
    TreeMap<Integer, NodeEntry> nodeEntries;        // Map of node entries, where key is entry ID and value is NodeEntry object

    /** Set of unique document IDs for this node. Contains union of entry
     * document IDs for leaves, or union of child node document IDs for
     * internal nodes. Enables efficient document-aware operations like:
     * <ul>
     *   <li>Quick filtering of irrelevant subtrees in queries</li>
     *   <li>Document-based similarity in node splits</li>
     *   <li>Document distribution statistics tracking</li>
     * </ul>
     */
    protected HashSet<Integer> nodeDocuments;

    protected long nodeSignature;    // Bloom filter signature (bitwise OR of all child signatures)
    protected double maxWeight;      // Maximum TF-IDF weight in this subtree (used for score-based pruning)

    private static final Logger logger = LogManager.getLogger(Node.class);  // Logger for this class


    //==========================================================================================
    //==================================== Abstract Methods ====================================
    //==========================================================================================

    public abstract Node chooseSubtree(Region mbr, int level, Stack<Integer> pathBuffer);
    public abstract Leaf findLeaf(int id, Region mbr, Stack<Integer> pathBuffer);
    protected abstract Node[] split(int id, Region mbr);

    // Document-aware tree specific methods
    public abstract Node chooseSubtree(Region mbr, int level, Stack<Integer> pathBuffer, HashSet<Integer> doc);
    protected abstract Node[] split(int id, Region mbr, HashSet<Integer> doc);


    //==========================================================================================
    //===================================== Core Properties ====================================
    //==========================================================================================

    // Basic node information
    @Override
    public int getIdentifier() {
        return identifier;
    }

    public void setIdentifier(int identifier) {
        this.identifier = identifier;
    }

    @Override
    public int getLevel() {
        return level;
    }

    public int getNodeType() {
        return nodeType;
    }

    public void setRTree(AbstractRTree rTree) {
        this.rTree = rTree;
    }

    // Node type checks
    @Override
    public boolean isLeaf() {
        return (level == 0);
    }

    @Override
    public boolean isIndex() {
        return (level != 0);
    }

    @Override
    public long getNodeSignature() {
        return nodeSignature;
    }

    public void setNodeSignature(long nodeSignature) {
        this.nodeSignature = nodeSignature;
    }

    public double getMaxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(double maxWeight) {
        this.maxWeight = maxWeight;
    }

    //==========================================================================================
    //================================== Spatial Data Methods ==================================
    //==========================================================================================

    /**
     * Returns a defensive copy of this node's Minimum Bounding Rectangle (MBR).
     * This method ensures encapsulation by returning a deep clone of the node's
     * spatial extent, preventing external modifications to the internal state.
     *
     * <p>The returned shape is suitable for read-only operations like:
     * <ul>
     *   <li>Querying the node's spatial extent</li>
     *   <li>Computing intersections with other shapes</li>
     *   <li>Calculating spatial relationships</li>
     * </ul>
     *
     * @return An {@link IShape} instance representing a clone of the node's MBR
     * @see Region#clone()
     * @see #getMBR()
     */
    @Override
    public IShape getShape() {
        return nodeMBR.clone();
    }

    /**
     * Returns the Minimum Bounding Rectangle (MBR) of the node.
     * This method provides direct access to the node's internal MBR reference, which is used
     * for spatial queries and tree structure management.
     *
     * <p><b>Note:</b> This method returns a direct reference to the node's internal MBR.
     * Modifications to the returned object will affect the node's state. For a safe copy,
     * use {@link #getShape()} instead.
     *
     * @return A reference to the node's internal MBR state - modifications will affect the node
     * @see #getShape()
     */
    @Override
    public Region getMBR() {
        return nodeMBR;
    }

    public TreeMap<Integer, NodeEntry> getNodeEntries() {
        return nodeEntries;
    }

    public NodeEntry getNodeEntry(int entryId) {
        return nodeEntries.get(entryId);
    }

    public Region getNodeEntryMBR(int entryId) {
        NodeEntry entry = nodeEntries.get(entryId);
        if (entry == null) {
            throw new IllegalArgumentException("Entry with ID " + entryId + " not found in node.");
        }
        return entry.getMBR();
    }

    public IShape getNodeEntryShape(int entryId) {
        NodeEntry entry = nodeEntries.get(entryId);
        if (entry == null) {
            throw new IllegalArgumentException("Entry with ID " + entryId + " not found in node.");
        }
        return entry.getMBR().clone();
    }


    //==========================================================================================
    //=============================== Node Data Management =====================================
    //==========================================================================================


    public Set<Integer> getNodeEntryIdentifiers() {
        return nodeEntries.keySet();
    }


    //==========================================================================================
    //=============================== Children Management ======================================
    //==========================================================================================

    public int getNodeEntriesSize() {
        return nodeEntries.size();
    }

    // Return all node entries MBRs
    public Collection<Region> getNodeEntriesMBRs() {
        return nodeEntries.values().stream()
                .map(NodeEntry::getMBR)
                .collect(Collectors.toList());
    }

    //==========================================================================================
    //================================ Document Management =====================================
    //==========================================================================================


    public HashSet<Integer> getNodeEntryDocumentSet(int entryId) {
        NodeEntry entry = nodeEntries.get(entryId);
        if (entry == null) {
            throw new IllegalArgumentException("Entry with ID " + entryId + " not found in node.");
        }
        return entry.getDocument(); // Returns the document set associated with this entry
    }

    public HashSet<Integer> getNodeDocuments() {
        return nodeDocuments;
    }


    //==========================================================================================
    //============================= Aggregate Data Management ==================================
    //==========================================================================================
    // TODO Initial method stubs
    public long computeTermSignature(int termID) {
        return 1L << (termID % 64);
    }

    public void aggregateFromChildren(List<Node> children) {
        this.nodeSignature = 0L;
        this.maxWeight = 0.0f;
        for (Node child : children) {
            this.nodeSignature |= child.getNodeSignature();
            this.maxWeight = Math.max(this.maxWeight, child.getMaxWeight());
        }
    }

    //==========================================================================================
    //======================================= Internals ========================================
    //==========================================================================================

    /**
     * Constructs a new node in the R-tree structure.
     *
     * <p>The constructor initializes node properties and allocates storage structures for managing entries.
     * It sets up the node's core attributes and initializes the nodeEntries TreeMap for efficient
     * entry management by ID. For document-aware R-trees, also initializes document tracking structures.
     *
     * <p><b>Initialization sequence:</b>
     * <ol>
     *   <li>Sets basic node properties (id, level, capacity)</li>
     *   <li>Creates an infinite MBR that will be recalculated upon first insertion</li>
     *   <li>Initializes empty nodeEntries map for storing spatial entries</li>
     *   <li>If document-aware, initializes nodeDocuments set for document tracking</li>
     * </ol>
     *
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>Node has zero entries ({@code nodeEntries.size() == 0})</li>
     *   <li>Node MBR is set to infinite region</li>
     *   <li>For leaves: {@code level == 0}, for index nodes: {@code level > 0}</li>
     *   <li>Node identifier matches the provided id parameter</li>
     * </ul>
     *
     * @param rTree    The R-tree instance this node belongs to (must not be null)
     * @param id       Unique identifier for this node (use -1 for new unassigned nodes, or >= 0 for existing nodes)
     * @param level    Level in the tree hierarchy (0 for leaf nodes, increases towards root)
     * @param capacity Maximum number of entries this node can hold (must be > 0)
     * @throws IllegalArgumentException if rTree is null or capacity is invalid
     */
    protected Node(AbstractRTree rTree, int id, int level, int capacity) {
        // Validate input parameters
        if (rTree == null) {
            throw new IllegalArgumentException("R-tree instance cannot be null");
        }

        if (capacity <= 0) {
            throw new IllegalArgumentException("Node capacity must be > 0, got: " + capacity);
        }

        this.rTree = rTree;
        this.level = level;
        this.identifier = id;
        this.capacity = capacity;
        this.nodeMBR = rTree.infiniteRegion.clone();

        this.nodeEntries = new TreeMap<>(); // Initialize the node entries map

        // Initialize document tracking structures for document-aware R-trees
        if (rTree.isDocumentAware()) {
            this.nodeDocuments = new HashSet<>();
        } else {
            this.nodeDocuments = null;
        }
    }

    /**
     * Inserts a new entry into the node with its associated MBR.
     * This method is used for both leaf and index nodes to add spatial entries.
     * It checks if the node has room for the new entry and updates the node's
     * Minimum Bounding Rectangle (MBR) accordingly.
     *
     * @param id  The unique identifier for this entry (must be non-negative)
     * @param mbr The Minimum Bounding Rectangle (MBR) that encloses the entry's geometry
     *            (must not be null and must match the tree's dimensionality)
     * @throws IllegalStateException if the node has reached its capacity (children >= capacity)
     * @throws NullPointerException if mbr is null
     * @throws IllegalArgumentException if id is negative
     */
    public void insertEntry(int id, Region mbr) {
        // Delegate to the more general insertEntry method with null documents for non-document-aware R-trees
        insertEntry(id, mbr, null);
    }

    /**
     * Inserts a new entry into the node with its associated MBR and document set.
     * This method is used for both leaf and index nodes to add spatial entries
     * while also tracking associated documents for document-aware R-trees.
     *
     * <p>The method performs the following operations:
     * <ol>
     *   <li>Validates node capacity constraints</li>
     *   <li>Creates a new NodeEntry with spatial and textual metadata</li>
     *   <li>Updates the node's document set if documents are provided</li>
     *   <li>Expands the node's MBR to include the new entry</li>
     * </ol>
     *
     * <p><b>Thread Safety:</b> This method is not thread-safe. External synchronization
     * is required if accessed concurrently.
     *
     * @param id          The unique identifier for this entry (must be non-negative)
     * @param mbr         The Minimum Bounding Rectangle (MBR) that encloses the entry's geometry
     *                    (must not be null and must match the tree's dimensionality)
     * @param documents   A set of document IDs associated with this entry (can be null if not applicable).
     *                    If null, the entry is created without document tracking.
     * @throws IllegalStateException if the node has reached its capacity (children >= capacity)
     * @throws NullPointerException if mbr is null
     * @throws IllegalArgumentException if id is negative
     */
    protected void insertEntry(int id, Region mbr, HashSet<Integer> documents) {
        // Validate capacity constraints before insertion
        if (nodeEntries.size() >= capacity) {
            logger.error("Attempted to insert entry with ID {} into node {} at level {}, but capacity of {} has been reached",
                    id, identifier, level, capacity);
            throw new IllegalStateException(
                    String.format("Node capacity exceeded: current size=%d, capacity=%d",
                            nodeEntries.size(), capacity)
            );
        }

        // Create and store the new entry with appropriate metadata
        NodeEntry newEntry = new NodeEntry(id, mbr, documents, nodeSignature, (float) maxWeight);
        nodeEntries.put(id, newEntry);

        // Update node-level document tracking for document-aware R-trees
        if (documents != null && nodeDocuments != null) {
            nodeDocuments.addAll(documents);
        }

        // Expand node's MBR to encompass the new entry
        Region.combinedRegion(nodeMBR, mbr);
    }

    /**
     * Deletes an entry from the node by its unique identifier.
     * This method removes the specified entry and updates the node's
     * Minimum Bounding Rectangle (MBR) if necessary.
     *
     * <p>MBR Update Strategy:
     * <ul>
     *   <li>If node becomes empty: MBR reset to infinite bounds</li>
     *   <li>If deleted entry touched MBR boundary: Full MBR recalculation</li>
     *   <li>Otherwise: MBR remains unchanged (entry was interior)</li>
     * </ul>
     *
     * <p>For document-aware R-trees, this method does NOT update the node's
     * document set. Callers must manage document consistency separately if needed.
     *
     * <p><b>Time Complexity:</b>
     * <ul>
     *   <li>O(1) if entry doesn't touch boundary</li>
     *   <li>O(n) if MBR recalculation required (n = remaining entries)</li>
     * </ul>
     *
     * @param entryId The unique identifier of the entry to delete (must be >= 0)
     * @throws IllegalArgumentException if no entry with the specified ID exists in this node
     * @see #recalculateNodeMBR()
     * @see Region#touches(Region)
     */
    protected void deleteEntry(int entryId) {
        NodeEntry entry = nodeEntries.remove(entryId);

        if (entry == null) {
            logger.error("Attempted to delete entry with ID {} from node {}, but no such entry exists. Current entries: {}",
                    entryId, identifier, nodeEntries.keySet());
            throw new IllegalArgumentException("Entry with ID " + entryId + " not found in node.");
        }

        // Check if the deleted entry affects the node MBR boundaries
        boolean touches = nodeMBR.touches(entry.getMBR());

        // Update the node's MBR
        if (nodeEntries.isEmpty()) {
            // Empty node - reset MBR to infinite
            nodeMBR = rTree.infiniteRegion.clone();
        } else if (touches) {
            // Recalculate MBR efficiently
            recalculateNodeMBR();
        }
    }

    /**
     * Inserts a new entry into the node, handling various cases based on the R-tree variant and node state.
     * This method implements the insertion logic for both standard R-trees and R*-trees, including:
     * <ul>
     *   <li>Direct insertion if space is available</li>
     *   <li>Forced reinsertions for R*-tree variants</li>
     *   <li>Node splitting when capacity is exceeded</li>
     * </ul>
     *
     * <p><b>Algorithm Overview:</b>
     * <ol>
     *   <li>Check if MBR contains the new entry and if pathBuffer is empty (optimization)</li>
     *   <li>If space available: insert directly and adjust tree if MBR expanded</li>
     *   <li>If R*-tree and not yet reinserted at this level: perform forced reinsertion</li>
     *   <li>Otherwise: split the node and propagate changes upward</li>
     * </ol>
     *
     * <p><b>Return Value Semantics:</b>
     * <ul>
     *   <li>{@code true}: Tree structure was modified (split occurred, reinsertions performed, or MBR adjusted)</li>
     *   <li>{@code false}: Entry was added without structural changes (node had space, MBR didn't expand)</li>
     * </ul>
     *
     * @param id The unique identifier for the entry (must be non-negative)
     * @param mbr The Minimum Bounding Rectangle of the entry (must not be null, dimensionality must match tree)
     * @param pathBuffer Stack containing node IDs from root to current node (used for tree traversal upward)
     * @param overflowTable Tracks which levels have already performed forced reinsertion (key: level, value: reinserted)
     * @return {@code true} if tree structure was modified; {@code false} if entry was added without structural changes
     * @throws IllegalStateException if insertion fails due to invalid tree state
     * @throws IllegalArgumentException if mbr or pathBuffer is null
     */
    public boolean insertData(int id, Region mbr, Stack<Integer> pathBuffer, HashMap<Integer, Boolean> overflowTable) {
        // Validate inputs
        if (mbr == null) {
            logger.error("Attempted to insert entry with ID {} into node {}, but MBR is null. Path buffer state: {}, overflow table state: {}",
                    id, identifier, pathBuffer.empty(), overflowTable);
            throw new IllegalArgumentException("MBR cannot be null");
        }
        if (pathBuffer == null) {
            logger.error("Attempted to insert entry with ID {} into node {}, but path buffer is null. MBR state: {}, overflow table state: {}",
                    id, identifier, mbr, overflowTable);
            throw new IllegalArgumentException("Path buffer cannot be null");
        }

        // Quick check for containing the MBR and path buffer state to avoid repeated calls
        boolean containsMBR = nodeMBR.contains(mbr);
        boolean isPathBufferEmpty = pathBuffer.empty();

        // Case 1: Node has space - direct insertion
        if (nodeEntries.size() < capacity) {
            insertEntry(id, mbr);
            rTree.writeNode(this);

            // Only adjust tree if MBR expanded and parent exists
            if (!containsMBR && !isPathBufferEmpty) {
                int parent = pathBuffer.pop();
                Index p = (Index) rTree.readNode(parent);
                p.adjustTree(this, pathBuffer);
                return true;
            }
            return false;
        }

        // Case 2: R*-tree variant with forced reinsertions
        // Only perform reinsertion if: (a) R*-tree variant, (b) parent exists, (c) not already reinserted at this level
        if (rTree.treeVariant == SpatialIndex.RtreeVariantRstar &&
                !isPathBufferEmpty &&
                !overflowTable.getOrDefault(level, false)) {

            overflowTable.put(level, true);
            return handleRStarReinsert(id, mbr, pathBuffer, overflowTable);
        }

        // Case 3: Split required (overflow handling or regular R-tree)
        return handleNodeSplit(id, mbr, pathBuffer, overflowTable, isPathBufferEmpty);
    }

    /**
     * Inserts a new entry into the node, handling various cases based on the R-tree variant and node state.
     * This method implements the insertion logic for document-aware R-trees, including:
     * <ul>
     *   <li>Direct insertion if space is available</li>
     *   <li>Node splitting when capacity is exceeded</li>
     * </ul>
     *
     * <p><b>Algorithm Overview:</b>
     * <ol>
     *   <li>Validate input parameters (MBR and pathBuffer)</li>
     *   <li>Check if node has space and if MBR/documents already contained</li>
     *   <li>If space available: insert directly and adjust parent only if spatial/textual bounds changed</li>
     *   <li>If at capacity: split node and propagate changes upward</li>
     * </ol>
     *
     * <p><b>Return Value Semantics:</b>
     * <ul>
     *   <li>{@code true}: Tree structure was modified (split occurred or parent adjusted)</li>
     *   <li>{@code false}: Entry was added without structural changes</li>
     * </ul>
     *
     * <p><b>Document-Aware Behavior:</b>
     * If {@code documents} is {@code null}, node-level document tracking is not updated.
     * The entry is still inserted normally. Document set consistency is the responsibility
     * of the caller.
     *
     * <p><b>Note on R*-tree Reinsertion:</b>
     * Forced reinsertion for R\*-tree variants is not yet implemented for document-aware trees.
     * All overflow cases currently use standard node splitting.
     *
     * @param id The unique identifier for the entry (must be non-negative)
     * @param mbr The Minimum Bounding Rectangle of the entry (must not be null)
     * @param pathBuffer Stack containing node IDs from root to current node (must not be null)
     * @param overflowTable Tracks which levels have performed forced reinsertion (currently unused)
     * @param documents Optional set of document IDs associated with this entry (may be null)
     * @return {@code true} if tree structure was modified; {@code false} otherwise
     * @throws IllegalArgumentException if mbr or pathBuffer is null
     * @throws IllegalStateException if tree state becomes invalid during split
     */
    public boolean insertData(int id, Region mbr, Stack<Integer> pathBuffer, HashMap<Integer, Boolean> overflowTable, HashSet<Integer> documents) {
        // Validate inputs
        if (mbr == null) {
            logger.error("Attempted to insert entry with ID {} into node {}, but MBR is null. Path buffer state: {}, overflow table state: {}, documents: {}",
                    id, identifier, pathBuffer.empty(), overflowTable, documents);
            throw new IllegalArgumentException("MBR cannot be null");
        }
        if (pathBuffer == null) {
            logger.error("Attempted to insert entry with ID {} into node {}, but path buffer is null. MBR state: {}, overflow table state: {}, documents: {}",
                    id, identifier, mbr, overflowTable, documents);
            throw new IllegalArgumentException("Path buffer cannot be null");
        }

        // Quick check for spatial containment and document set containment
        boolean containsMBR = nodeMBR.contains(mbr);
        boolean containsAllDocs = nodeDocuments == null || documents == null || nodeDocuments.containsAll(documents);
        boolean isPathBufferEmpty = pathBuffer.empty();

        // Case 1: Node has space - direct insertion
        if (nodeEntries.size() < capacity) {
            insertEntry(id, mbr, documents);
            rTree.writeNode(this);

            // Only adjust tree if spatial or textual bounds changed
            if ((!containsMBR || !containsAllDocs) && !isPathBufferEmpty) {
                int parent = pathBuffer.pop();
                Index p = (Index) rTree.readNode(parent);
                p.adjustTree(this, pathBuffer);
                return true;
            }
            return false;
        }

        // Case 2: Overflow handling - node is at capacity
        // TODO: Check literature to implement R*-tree forced reinsertion for document-aware trees
        // Currently, all overflow cases use standard node splitting
        return handleNodeSplit(id, mbr, pathBuffer, overflowTable, isPathBufferEmpty, documents);
    }

    /**
     * Handles reinsertion of entries in an R*-tree when a node overflows.
     * This method implements the R*-tree reinsertion strategy, which redistributes
     * entries between nodes to minimize overlap and improve spatial distribution.
     *
     * <p>The process involves:
     * <ol>
     *   <li>Partitioning current and incoming entries into reinsert and keep lists</li>
     *   <li>Removing reinsert entries from the current node</li>
     *   <li>Recalculating the MBR for remaining entries</li>
     *   <li>Propagating node changes upward via adjustTree</li>
     *   <li>Reinserting removed entries into the tree at the current level</li>
     * </ol>
     *
     * <p><b>Algorithm Correctness:</b> This method ensures that entries marked for
     * reinsertion are properly removed from the node before reinsertion to avoid
     * duplicate entries and maintain tree consistency.
     *
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>Current node contains only entries from keepList</li>
     *   <li>Node MBR is recalculated to fit remaining entries</li>
     *   <li>Parent node is adjusted for MBR changes</li>
     *   <li>All reinserted entries are returned to the tree</li>
     *   <li>Tree structure remains valid and consistent</li>
     * </ul>
     *
     * @param id The unique identifier for the entry being inserted (must be non-negative)
     * @param mbr The Minimum Bounding Rectangle of the entry (must not be null)
     * @param pathBuffer Stack containing the path from root to current node (must not be empty)
     * @param overflowTable Tracks reinsertion attempts per level to prevent infinite loops
     * @return Always returns true (reinsertion is always performed when this method is called)
     * @throws IllegalStateException if an entry in the reinsert list is missing from the node
     * @throws EmptyStackException if pathBuffer is empty (parent node required)
     * @see #reinsertData(int, Region, ArrayList, ArrayList)
     * @see Index#adjustTree(Node, Stack)
     */
    private boolean handleRStarReinsert(int id, Region mbr, Stack<Integer> pathBuffer, HashMap<Integer, Boolean> overflowTable) {
        // Partition entries into reinsert and keep lists
        ArrayList<Integer> reinsertList = new ArrayList<>();
        ArrayList<Integer> keepList = new ArrayList<>();
        reinsertData(id, mbr, reinsertList, keepList);

        // Separate node entries into two groups
        TreeMap<Integer, NodeEntry> keepNodeEntries = new TreeMap<>();
        TreeMap<Integer, NodeEntry> reinsertNodeEntries = new TreeMap<>();

        for (int entryId : keepList) {
            keepNodeEntries.put(entryId, nodeEntries.get(entryId));
        }
        for (int entryId : reinsertList) {
            reinsertNodeEntries.put(entryId, nodeEntries.get(entryId));
        }

        // Update node with kept entries and recalculate MBR
        nodeEntries = keepNodeEntries;
        recalculateNodeMBR();
        rTree.writeNode(this);

        // Propagate changes to parent
        int parent = pathBuffer.pop();
        Index parentNode = (Index) rTree.readNode(parent);
        parentNode.adjustTree(this, pathBuffer);

        // Reinsert removed entries at the current level
        for (int entryId : reinsertList) {
            NodeEntry entry = reinsertNodeEntries.get(entryId);
            if (entry == null) {
                logger.error("Entry with ID {} was expected in reinsert list for node {}, but was not found. Current node entries: {}",
                        entryId, identifier, nodeEntries.keySet());
                throw new IllegalStateException(
                    "Entry with ID " + entryId + " disappeared from reinsert list. " +
                    "Tree structure may be corrupted.");
            }
            rTree.insertDataImpl(entry.getIdentifier(), entry.getMBR(), level, overflowTable);
        }

        return true;
    }

    /**
     * Recalculates the node's Minimum Bounding Rectangle (MBR) to tightly enclose all entries.
     * This method iterates through all child entries and computes the smallest possible
     * bounding box by finding the minimum and maximum coordinates in each dimension.
     *
     * <p><b>When to use:</b> This method is invoked when:
     * <ul>
     *   <li>An entry is deleted that touches the node's MBR boundary</li>
     *   <li>Nodes are consolidated during R-tree maintenance</li>
     *   <li>The node's spatial structure has been modified</li>
     *   <li>After forced reinsertions in R*-tree variants</li>
     * </ul>
     *
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Resets MBR bounds to infinity for each dimension</li>
     *   <li>Scans all child entries to find extremes</li>
     *   <li>Adjusts MBR coordinates to tightly contain all entries</li>
     * </ol>
     *
     * <p><b>Special Cases:</b>
     * <ul>
     *   <li>If node has no entries, MBR remains at infinity values</li>
     *   <li>For single entry, MBR equals entry's MBR</li>
     *   <li>Method preserves tree validity through proper boundary calculation</li>
     * </ul>
     *
     * <p><b>Time Complexity:</b> O(n × d), where n = number of entries, d = dimensionality
     *
     * <p><b>Postcondition:</b> nodeMBR tightly encloses all entries or remains infinite if empty
     *
     * @see #deleteEntry(int)
     * @see #condenseTree(Stack, Stack)
     * @see #handleRStarReinsert(int, Region, Stack, HashMap)
     */
    protected void recalculateNodeMBR() {
        // Early return optimization: if no entries, reset to infinite region
        if (nodeEntries.isEmpty()) {
            nodeMBR = rTree.infiniteRegion.clone();
            return;
        }

        // Reset MBR boundaries to prepare for recalculation
        for (int dim = 0; dim < rTree.dimension; dim++) {
            nodeMBR.setLow(dim, Double.POSITIVE_INFINITY);
            nodeMBR.setHigh(dim, Double.NEGATIVE_INFINITY);
        }

        // Rebuild MBR by combining all entry bounds
        for (NodeEntry entry : nodeEntries.values()) {
            Region entryMBR = entry.getMBR();
            for (int dim = 0; dim < rTree.dimension; dim++) {
                nodeMBR.setLow(dim, Math.min(nodeMBR.getLow(dim), entryMBR.getLow(dim)));
                nodeMBR.setHigh(dim, Math.max(nodeMBR.getHigh(dim), entryMBR.getHigh(dim)));
            }
        }
    }

    /**
     * Handles the node split operation when the node exceeds its capacity (non-document-aware version).
     * This is a convenience method that delegates to the document-aware version by passing {@code null}
     * for the documents parameter.
     *
     * <p><b>When invoked:</b> This method is called during insertion operations on non-document-aware
     * R-trees when a node reaches capacity and must be split into two nodes.
     *
     * <p><b>Split Strategy:</b> The actual splitting algorithm (R*-tree, quadratic, or linear) is determined
     * by the tree's variant. The new entries are distributed between two nodes to minimize overlap and
     * spatial waste.
     *
     * @param id The unique identifier for the entry being inserted (must be non-negative)
     * @param mbr The Minimum Bounding Rectangle of the entry (must not be null, dimensionality must match tree)
     * @param pathBuffer Stack containing node IDs from root to current node; modified during execution
     *                   to reflect upward propagation of the split
     * @param overflowTable Tracks which levels have performed forced reinsertion to prevent infinite loops
     * @param isPathBufferEmpty {@code true} if splitting the root node; {@code false} if splitting an internal node
     * @return {@code true} if split was successful and tree structure was modified; {@code false} only in exceptional cases
     * @see #handleNodeSplit(int, Region, Stack, HashMap, boolean, HashSet)
     */
    private boolean handleNodeSplit(int id, Region mbr, Stack<Integer> pathBuffer,
                                    HashMap<Integer, Boolean> overflowTable, boolean isPathBufferEmpty) {
        return handleNodeSplit(id, mbr, pathBuffer, overflowTable, isPathBufferEmpty, null);
    }

    /**
     * Handles the node split operation when the node exceeds its capacity.
     * This method implements the R*-tree split logic to redistribute entries between two nodes,
     * which may involve creating a new root node or adjusting the parent index node.
     *
     * <p><b>Algorithm Overview:</b>
     * <ol>
     *   <li>Selects appropriate split algorithm based on tree variant (linear, quadratic, or R*-tree)</li>
     *   <li>Partitions current entries and the new entry into two groups</li>
     *   <li>Creates a new sibling node and distributes partitioned entries</li>
     *   <li>Recalculates MBRs for both affected nodes</li>
     *   <li>Creates a new root node if splitting the current root</li>
     *   <li>Updates parent index node entries to reference both split nodes</li>
     *   <li>Persists both split nodes to storage</li>
     * </ol>
     *
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>Current node (leftNode) contains first partition of entries with recalculated MBR</li>
     *   <li>New sibling node (rightNode) contains remaining entries with recalculated MBR</li>
     *   <li>Both nodes are persisted to underlying storage</li>
     *   <li>Parent node is updated with entries for both split nodes</li>
     *   <li>Tree structure remains valid and consistent</li>
     * </ul>
     *
     * <p><b>Special Cases:</b>
     * <ul>
     *   <li>If splitting root (pathBuffer empty): creates new root with two children</li>
     *   <li>If splitting internal node: updates parent's entries and MBR</li>
     * </ul>
     *
     * @param id The unique identifier for the entry being inserted (must be non-negative)
     * @param mbr The Minimum Bounding Rectangle of the entry (must not be null, dimensionality must match tree)
     * @param pathBuffer Stack containing the path from root to current node; modified during execution (must not be null)
     * @param overflowTable Tracks reinsertion attempts per level to prevent infinite loops (must not be null)
     * @param isPathBufferEmpty {@code true} if splitting the root node; {@code false} if splitting an internal node
     * @param documents Optional set of document IDs associated with this entry. If {@code null}, the entry is treated as non-document-aware.
     *                  If non-null, both split nodes will inherit and maintain document tracking.
     * @return Always returns {@code true} indicating tree structure was modified by the split operation
     * @throws IllegalStateException if the split algorithm fails, node state becomes invalid, or required nodes cannot be created
     * @throws IllegalArgumentException if pathBuffer, overflowTable, or mbr is null
     * @throws NullPointerException if the split operation returns null or incomplete results
     * @see #split(int, Region)
     * @see #split(int, Region, HashSet)
     * @see #handleRootNodeSplit(Node, Node)
     * @see #handleInternalNodeSplit(Node, Node, Stack, HashMap)
     */
    private boolean handleNodeSplit(int id, Region mbr, Stack<Integer> pathBuffer, HashMap<Integer, Boolean> overflowTable,
                                    boolean isPathBufferEmpty, HashSet<Integer> documents) {
        // Validate input parameters
        if (pathBuffer == null) {
            throw new IllegalArgumentException("Path buffer cannot be null");
        }
        if (overflowTable == null) {
            throw new IllegalArgumentException("Overflow table cannot be null");
        }
        if (mbr == null) {
            throw new IllegalArgumentException("MBR cannot be null");
        }

        // Split the node using the appropriate algorithm
        Node[] nodes = documents != null ? split(id, mbr, documents) : split(id, mbr);

        if (nodes == null || nodes.length != 2 || nodes[0] == null || nodes[1] == null) {
            logger.error("Split operation failed for node {}: returned invalid node array", identifier);
            throw new IllegalStateException("Split operation returned invalid nodes");
        }

        Node leftNode = nodes[0];
        Node rightNode = nodes[1];

        if (isPathBufferEmpty) {
            handleRootNodeSplit(leftNode, rightNode);
        } else {
            handleInternalNodeSplit(leftNode, rightNode, pathBuffer, overflowTable);
        }

        return true;
    }

    /**
     * Handles the split of the root node by creating a new root index node.
     * This increases the tree height by one level and establishes the split nodes as children.
     *
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>Tree height increases by 1</li>
     *   <li>Both split nodes are assigned new identifiers and persisted</li>
     *   <li>New root node references both split children</li>
     *   <li>Statistics reflect updated node counts per level</li>
     * </ul>
     *
     * @param leftNode First node from the split (receives new identifier)
     * @param rightNode Second node from the split (receives new identifier)
     */
    private void handleRootNodeSplit(Node leftNode, Node rightNode) {
        // Assign new identifiers to split nodes
        leftNode.identifier = -1;
        rightNode.identifier = -1;
        rTree.writeNode(leftNode);
        rTree.writeNode(rightNode);

        // Create new root at one level higher
        Index newRoot = new Index(rTree, rTree.rootID, level + 1);
        newRoot.insertEntry(leftNode.identifier, leftNode.nodeMBR.clone(), leftNode.nodeDocuments);
        newRoot.insertEntry(rightNode.identifier, rightNode.nodeMBR.clone(), rightNode.nodeDocuments);
        rTree.writeNode(newRoot);

        // Update tree statistics for new structure
        rTree.stats.nodesInLevel.set(level, 2);
        rTree.stats.nodesInLevel.add(1);
        rTree.stats.treeHeight = level + 2;
    }

    /**
     * Handles the split of a non-root node by updating the parent index.
     * The left node retains the original identifier while the right node receives a new one.
     *
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>Left node retains original identifier</li>
     *   <li>Right node receives new identifier</li>
     *   <li>Both nodes are persisted to storage</li>
     *   <li>Parent index is adjusted to reference both nodes</li>
     * </ul>
     *
     * @param leftNode First node from split (keeps original identifier)
     * @param rightNode Second node from split (receives new identifier)
     * @param pathBuffer Stack containing path from root to parent
     * @param overflowTable Tracks reinsertion state per level
     */
    private void handleInternalNodeSplit(Node leftNode, Node rightNode, Stack<Integer> pathBuffer,
                                        HashMap<Integer, Boolean> overflowTable) {
        // Preserve original node identity for left node
        leftNode.identifier = identifier;
        rightNode.identifier = -1;

        rTree.writeNode(leftNode);
        rTree.writeNode(rightNode);

        // Update parent to reference both split nodes
        int parentId = pathBuffer.pop();
        Index parentIndex = (Index) rTree.readNode(parentId);
        parentIndex.adjustTree(leftNode, rightNode, pathBuffer, overflowTable);
    }


    /**
     * Reinsert data into the node based on the R*-tree reinsertion strategy.
     * This method identifies entries to reinsert based on their distances from the node's center.
     *
     * <p><b>Algorithm (per Beckmann et al., 1990, §4.3 "Forced Reinsert"):</b>
     * <ol>
     *   <li>Temporarily adds the new entry to the node for distance calculations</li>
     *   <li>Computes the center of the combined MBR of all M+1 entries (including the new one)</li>
     *   <li>Calculates distances from that center for every entry</li>
     *   <li>Sorts entries by distance in <b>descending</b> order (farthest first)</li>
     *   <li>Selects the p% entries that are <b>farthest</b> from the center for reinsertion —
     *       these are "misplaced" entries most likely to find a better home elsewhere</li>
     *   <li>Remaining (closest) entries stay in the original node</li>
     * </ol>
     *
     * <p><b>Important:</b> The temporarily added entry is <b>not removed</b> from nodeEntries.
     * The caller ({@link #handleRStarReinsert}) is responsible for removing all entries in the
     * reinsert list before reinserting them to avoid duplicates.
     *
     * <p><b>Time Complexity:</b> O(n log n) where n = capacity + 1 (dominated by sorting)
     *
     * @param id The unique identifier for the new entry (must be non-negative)
     * @param mbr The Minimum Bounding Rectangle of the new entry (must not be null)
     * @param reinsert List to hold identifiers of entries to be reinserted (populated by this method);
     *                 entries are ordered farthest-first (decreasing distance) for correct reinsertion
     * @param keep List to hold identifiers of entries to be kept in this node (populated by this method)
     * @throws IllegalArgumentException if mbr is null, rTree.dimension is invalid, or reinsertFactor is out of range
     * @throws NullPointerException if the expanded MBR center calculation returns null
     * @see #handleRStarReinsert(int, Region, Stack, HashMap)
     */
    protected void reinsertData(int id, Region mbr, ArrayList<Integer> reinsert, ArrayList<Integer> keep) {
        if (mbr == null) {
            throw new IllegalArgumentException("MBR cannot be null");
        }
        if (rTree.dimension <= 0) {
            throw new IllegalArgumentException("Tree dimension must be positive, got: " + rTree.dimension);
        }
        if (rTree.reinsertFactor < 0.0 || rTree.reinsertFactor > 1.0) {
            throw new IllegalArgumentException("Reinsert factor must be in [0, 1], got: " + rTree.reinsertFactor);
        }

        // Temporarily add the new entry to this node for distance calculations
        nodeEntries.put(id, new NodeEntry(id, mbr, nodeSignature, (float) maxWeight));

        // R*-tree §4.3: the center must be that of the bounding rectangle of ALL M+1 entries,
        // including the newly added one. Expand a copy of nodeMBR to include the new entry's MBR.
        Region expandedMBR = nodeMBR.clone();
        Region.combinedRegion(expandedMBR, mbr);
        double[] nodeMBRCenter = expandedMBR.getCenter();
        if (nodeMBRCenter == null) {
            throw new NullPointerException("Expanded MBR center calculation returned null");
        }

        // Create list to hold entry distances
        List<ReinsertEntry> reinsertEntriesList = new ArrayList<>(capacity + 1);

        // Calculate distances from center for all entries (including the new one)
        for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
            double[] entryCenter = entry.getValue().getMBR().getCenter();
            double squaredDistance = 0.0;

            // Calculate squared Euclidean distance (avoiding unnecessary sqrt operation)
            for (int dim = 0; dim < rTree.dimension; dim++) {
                double d = nodeMBRCenter[dim] - entryCenter[dim];
                squaredDistance += d * d;
            }

            reinsertEntriesList.add(new ReinsertEntry(entry.getKey(), squaredDistance));
        }

        // Sort entries by distance in DESCENDING order (farthest from center first).
        // R*-tree §4.3: the p entries whose centers are FARTHEST from the node center
        // are reinserted (in decreasing order of distance) so they can find a better home.
        reinsertEntriesList.sort(new ReinsertEntryComparator());

        // Calculate how many entries to reinsert based on reinsertFactor
        int reinsertSize = (int) Math.floor((capacity + 1) * rTree.reinsertFactor);

        // Populate the reinsert list with the FARTHEST entries (first in descending sort)
        for (int i = 0; i < reinsertSize && i < reinsertEntriesList.size(); i++) {
            reinsert.add(reinsertEntriesList.get(i).id);
        }

        // Populate the keep list with the remaining (closest) entries
        for (int i = reinsertSize; i < reinsertEntriesList.size(); i++) {
            keep.add(reinsertEntriesList.get(i).id);
        }
    }

    /**
     * Splits the node into two groups using an R*-tree split algorithm.
     * This method partitions all entries (including the new one) into two balanced groups
     * to minimize overlap and area enlargement.
     *
     * <p><b>Algorithm Steps:</b>
     * <ol>
     *   <li>Adds the new entry temporarily to nodeEntries for partitioning</li>
     *   <li>Selects two seed entries that are farthest apart</li>
     *   <li>Iteratively assigns remaining entries to the group with minimum area enlargement</li>
     *   <li>Enforces minimum load constraints via forced assignments</li>
     * </ol>
     *
     * <p><b>Important Postcondition:</b> The new entry (id, mbr) is added to nodeEntries
     * and included in one of the output groups. The caller must not duplicate this entry.
     *
     * <p><b>Time Complexity:</b> O(n² × d) where n = capacity + 1, d = dimensionality
     *
     * @param id The unique identifier for the new entry (must be non-negative)
     * @param mbr The Minimum Bounding Rectangle of the new entry (must not be null)
     * @param group1 List populated with entry IDs in the first group (non-null)
     * @param group2 List populated with entry IDs in the second group (non-null)
     * @throws IllegalStateException if pickSeeds() returns invalid seeds or no unprocessed entry exists for assignment
     * @throws IllegalArgumentException if id is negative or mbr is null
     * @see #pickSeeds()
     * @see #split(int, Region)
     */
    protected void rtreeSplit(int id, Region mbr, ArrayList<Integer> group1, ArrayList<Integer> group2) {
        int totalCapacity = capacity + 1;
        int minimumLoad = (int) Math.floor(capacity * rTree.fillFactor);

        // Initialize mask array to track processed entries
        HashMap<Integer, Boolean> processed = new HashMap<>();
        for (Integer entryId : nodeEntries.keySet()) {
            processed.put(entryId, false);
        }

         // Insert new data for easier manipulation
         nodeEntries.put(id, new NodeEntry(id, mbr, nodeSignature, (float) maxWeight));

         // Add the new entry to processed map so it gets distributed
         processed.put(id, false);

        // Pick seeds for initial groups
        int[] seeds = pickSeeds();
        if (seeds == null || seeds.length != 2) {
                logger.error("pickSeeds() returned invalid seeds for node {}: {}", identifier, seeds);
            throw new IllegalStateException("Invalid seeds returned from pickSeeds()");
        }

        group1.add(seeds[0]);
        group2.add(seeds[1]);
        // Mark seeds as processed
        processed.replace(seeds[0], true);
        processed.replace(seeds[1], true);

        // Initialize MBRs for each group
        Region mbr1 = nodeEntries.get(seeds[0]).getMBR().clone();
        Region mbr2 = nodeEntries.get(seeds[1]).getMBR().clone();

        int remaining = totalCapacity - 2;

        while (remaining > 0) {
            // Handle forced assignment to satisfy minimum load requirements
            if (minimumLoad - group1.size() == remaining) {
                // Assign all remaining entries to group1 to meet minimum load
                assignRemainingEntries(group1, mbr1, null, processed);
                break;
            } else if (minimumLoad - group2.size() == remaining) {
                // Assign all remaining entries to group2 to meet minimum load
                assignRemainingEntries(group2, mbr2, null, processed);
                break;
            } else {
                // Select next entry to add based on area enlargement difference
                remaining = assignNextEntrySpatial(group1, group2, mbr1, mbr2, processed, remaining);
            }
        }
    }

    /**
      * Assigns all remaining unprocessed entries to the specified group and updates the group's MBR.
      * This method ensures complete assignment of entries when minimum load requirements are satisfied
      * during node splitting in the R*-tree algorithm. Supports both spatial-only and document-aware splits.
      *
      * <p><b>Algorithm:</b>
      * <ol>
      *   <li>Iterates through all entries in the processed map</li>
      *   <li>Identifies unprocessed entries (value == false)</li>
      *   <li>Adds each unprocessed entry to the target group</li>
      *   <li>Expands the group's MBR to encompass the added entry</li>
      *   <li>For document-aware trees: merges document sets into group document set</li>
      *   <li>Marks entry as processed to prevent duplicate assignment</li>
      * </ol>
      *
      * <p><b>Document-Aware Behavior:</b>
      * When {@code groupDocs} is non-null, this method maintains the union of all document IDs
      * from assigned entries, enabling document-based split optimization and pruning strategies.
      * If {@code groupDocs} is null, document tracking is skipped (spatial-only split).
      *
      * <p><b>Time Complexity:</b> O(n × (d + m)) where n = number of remaining entries,
      * d = dimensionality, m = average documents per entry (for document-aware operations)
      *
      * <p><b>Postcondition:</b> All entries in processed map are marked as processed, group
      * contains all previously unprocessed entries with groupMbr updated accordingly, and
      * groupDocs (if provided) contains union of all assigned entry documents.
      *
      * @param group The target group to receive remaining entries (must not be null)
      * @param groupMbr The MBR of the group, updated in-place to include all assigned entries (must not be null)
      * @param groupDocs Optional set to accumulate document IDs from assigned entries (may be null for spatial-only splits)
      * @param processed Map tracking assignment status of entries: key = entry ID, value = processed flag
      *                  (must not be null; all unprocessed entries will be marked true upon completion)
      * @throws NullPointerException if group, groupMbr, or processed is null
      * @throws NullPointerException if nodeEntries does not contain an entry referenced in processed map
      * @see #rtreeSplit(int, Region, ArrayList, ArrayList)
      * @see #assignNextEntrySpatial(ArrayList, ArrayList, Region, Region, HashMap, int)
      */
    private void assignRemainingEntries(ArrayList<Integer> group, Region groupMbr, HashSet<Integer> groupDocs, HashMap<Integer, Boolean> processed) {
        for (Map.Entry<Integer, Boolean> entry : processed.entrySet()) {
            if (!entry.getValue()) {
                int entryId = entry.getKey();
                group.add(entryId);
                Region.combinedRegion(groupMbr, nodeEntries.get(entryId).getMBR());
                // If this is a document-aware tree, also combine document sets
                if (groupDocs != null && nodeEntries.get(entryId).getDocument() != null) {
                    groupDocs.addAll(nodeEntries.get(entryId).getDocument());
                }
                processed.put(entryId, true);
            }
        }
    }

    /**
     * Selects and assigns exactly one unprocessed entry to one of the two split groups.
     *
     * <p>This method applies the split-assignment heuristic (primarily area enlargement,
     * with variant-specific tie-breaking), then:
     * <ul>
     * <li>Adds the selected entry ID to {@code group1} or {@code group2}.</li>
     * <li>Expands {@code mbr1} or {@code mbr2} to include the selected entry MBR.</li>
     * <li>Marks the selected entry as processed in {@code processed}.</li>
     * </ul>
     *
     * <p>The method returns the updated number of remaining unassigned entries (typically {@code remaining -1}).
     *
     * @param group1 entry IDs currently assigned to the first split group
     * @param group2 entry IDs currently assigned to the second split group
     * @param mbr1 running MBR of {@code group1}; updated in place if selected
     * @param mbr2 running MBR of {@code group2}; updated in place if selected
     * @param processed assignment status map ({@code entryId -> processed})
     * @param remaining number of unassigned entries before this call
     * @return updated number of unassigned entries after one assignment
     * @throws IllegalStateException if no unprocessed entry can be selected while {@code remaining >0}
     */
    private int assignNextEntrySpatial(ArrayList<Integer> group1, ArrayList<Integer> group2,
                                       Region mbr1, Region mbr2, HashMap<Integer, Boolean> processed, int remaining) {
        double area1 = mbr1.getArea();
        double area2 = mbr2.getArea();

        int selectedEntryId = -1;
        double maxAreaDifference = Double.NEGATIVE_INFINITY;
        double bestEnlargement1 = 0.0, bestEnlargement2 = 0.0;
        boolean earlyExit = rTree.treeVariant == SpatialIndex.RtreeVariantLinear ||
                rTree.treeVariant == SpatialIndex.RtreeVariantRstar;

        // Find entry with maximum difference in area enlargement
        for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
            int entryId = entry.getKey();
            if (processed.containsKey(entryId) && !processed.get(entryId)) {
                Region childRegion = entry.getValue().getMBR();

                Region combined1 = mbr1.combinedRegion(childRegion);
                Region combined2 = mbr2.combinedRegion(childRegion);

                double areaEnlargement1 = combined1.getArea() - area1;
                double areaEnlargement2 = combined2.getArea() - area2;
                double diff = Math.abs(areaEnlargement1 - areaEnlargement2);

                if (diff > maxAreaDifference) {
                    maxAreaDifference = diff;
                    bestEnlargement1 = areaEnlargement1;
                    bestEnlargement2 = areaEnlargement2;
                    selectedEntryId = entryId;

                    if (earlyExit && maxAreaDifference > 0) {
                        break;
                    }
                }
            }
        }

        if (selectedEntryId == -1) {
            logger.error("No unprocessed entry found for assignment during node split in node {}. Processed map: {}, node entries: {}",
                    identifier, processed, nodeEntries.keySet());
            throw new IllegalStateException("No unprocessed entry found for assignment");
        }

        // Assign the entry to the group that enlarges least
        if (bestEnlargement1 < bestEnlargement2) {
            group1.add(selectedEntryId);
            Region.combinedRegion(mbr1, nodeEntries.get(selectedEntryId).getMBR());
        } else {
            group2.add(selectedEntryId);
            Region.combinedRegion(mbr2, nodeEntries.get(selectedEntryId).getMBR());
        }

        processed.replace(selectedEntryId, true);
        return remaining - 1;
    }

    /**
     * Chooses and assigns exactly one unprocessed entry to one of the two split groups
     * during node partitioning in a document-aware R-tree.
     *
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Evaluates all unprocessed entries using hybrid cost function</li>
     *   <li>Hybrid cost combines spatial (area enlargement) and textual (document distance) metrics</li>
     *   <li>Selects entry with maximum difference in hybrid cost between groups</li>
     *   <li>Assigns entry to group with lower hybrid cost</li>
     *   <li>Updates both spatial and document metadata for the selected group</li>
     * </ol>
     *
     * <p><b>Hybrid Cost Function:</b>
     * {@code hybrid = betaArea × areaEnlargement + (1 - betaArea) × documentDistance}
     * <ul>
     *   <li>betaArea: weight parameter controlling spatial vs. textual importance (0.0 to 1.0)</li>
     *   <li>areaEnlargement: increase in group's MBR area after adding entry</li>
     *   <li>documentDistance: measure of document set dissimilarity between entry and group</li>
     * </ul>
     *
     * <p><b>Side Effects:</b>
     * <ul>
     *   <li>Adds one entry identifier to either group1 or group2</li>
     *   <li>Updates mbr1 or mbr2 to include the assigned entry's spatial bounds</li>
     *   <li>Updates doc1 or doc2 to include the assigned entry's documents</li>
     *   <li>Marks the assigned entry as processed to prevent duplicate assignment</li>
     * </ul>
     *
     * <p><b>Early Exit Optimization:</b> For linear and R*-tree variants, exits as soon as
     * an entry with positive area difference is found, reducing computation time.
     *
     * <p><b>Time Complexity:</b> O(n × (d + m)) where n = number of unprocessed entries,
     * d = dimensionality, m = average documents per entry
     *
     * <p><b>Postcondition:</b> Exactly one entry is added to either group1 or group2,
     * corresponding spatial and document metadata are updated, and the entry is marked as processed.
     *
     * @param group1 identifiers currently assigned to the first split group (must not be null)
     * @param group2 identifiers currently assigned to the second split group (must not be null)
     * @param mbr1 running MBR of group1; updated in-place if entry assigned to group1 (must not be null)
     * @param mbr2 running MBR of group2; updated in-place if entry assigned to group2 (must not be null)
     * @param doc1 running document set of group1; updated in-place if entry assigned to group1 (must not be null)
     * @param doc2 running document set of group2; updated in-place if entry assigned to group2 (must not be null)
     * @param processed assignment-status map (entryId → processed flag); updated to mark assigned entry as true (must not be null)
     * @param remaining number of entries still unassigned before this call (must be > 0)
     * @return updated unassigned-entry count after one assignment (typically {@code remaining - 1})
     * @throws IllegalStateException if no unprocessed entry can be selected while remaining > 0
     * @throws NullPointerException if any required parameter is null or if entry document set is null
     * @see #rtreeSplit(int, Region, ArrayList, ArrayList)
     * @see #docDistance(HashSet, HashSet)
     * @see #determineGroupAssignment(double, double, double, double, double, double, int, int)
     */
    private int assignNextEntryHybrid(ArrayList<Integer> group1, ArrayList<Integer> group2,
                                       Region mbr1, Region mbr2, HashSet<Integer> doc1, HashSet<Integer> doc2,
                                       HashMap<Integer, Boolean> processed, int remaining) {
        double area1 = mbr1.getArea();
        double area2 = mbr2.getArea();

        int selectedEntryId = -1;
        double maxDifference = Double.NEGATIVE_INFINITY;
        double bestEnlargement1 = 0.0, bestEnlargement2 = 0.0;
        double bestHybrid1 = 0.0, bestHybrid2 = 0.0;
        boolean earlyExit = rTree.treeVariant == SpatialIndex.RtreeVariantLinear ||
                rTree.treeVariant == SpatialIndex.RtreeVariantRstar;

        // Iterate through all entries to find the one with maximum hybrid cost difference
        for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
            int entryId = entry.getKey();
            if (processed.containsKey(entryId) && !processed.get(entryId)) {
                Region childRegion = entry.getValue().getMBR();
                HashSet<Integer> entryDocs = entry.getValue().getDocument();

                Region combined1 = mbr1.combinedRegion(childRegion);
                Region combined2 = mbr2.combinedRegion(childRegion);

                double areaEnlargement1 = combined1.getArea() - area1;
                double areaEnlargement2 = combined2.getArea() - area2;

                // Calculate hybrid cost: weighted combination of spatial and textual metrics
                double hybrid1 = rTree.getBetaArea() * areaEnlargement1 +
                        (1.0 - rTree.getBetaArea()) * docDistance(entryDocs, doc1);

                double hybrid2 = rTree.getBetaArea() * areaEnlargement2 +
                        (1.0 - rTree.getBetaArea()) * docDistance(entryDocs, doc2);

                double diff = Math.abs(hybrid1 - hybrid2);

                // Update selection if this entry has greater cost difference
                if (diff > maxDifference) {
                    maxDifference = diff;
                    bestHybrid1 = hybrid1;
                    bestHybrid2 = hybrid2;
                    bestEnlargement1 = areaEnlargement1;
                    bestEnlargement2 = areaEnlargement2;
                    selectedEntryId = entryId;

                    // Early exit for linear and R*-tree variants (greedy approach)
                    if (earlyExit && maxDifference > 0.0) {
                        break;
                    }
                }
            }
        }

        // Verify that an entry was selected
        if (selectedEntryId == -1) {
            logger.error("No unprocessed entry found for assignment during hybrid node split in node {}. " +
                    "Processed map size: {}, node entries: {}",
                    identifier, processed.size(), nodeEntries.keySet());
            throw new IllegalStateException("No unprocessed entry found for assignment");
        }

        // Determine optimal group assignment based on hybrid metrics
        int assignmentGroup = determineGroupAssignment(
                bestHybrid1, bestHybrid2, bestEnlargement1, bestEnlargement2, area1, area2,
                group1.size(), group2.size()
        );

        // Assign the selected entry to the optimal group with updated metadata
        NodeEntry selectedEntry = nodeEntries.get(selectedEntryId);
        if (assignmentGroup == 1) {
            group1.add(selectedEntryId);
            Region.combinedRegion(mbr1, selectedEntry.getMBR());
            doc1.addAll(selectedEntry.getDocument());
        } else {
            group2.add(selectedEntryId);
            Region.combinedRegion(mbr2, selectedEntry.getMBR());
            doc2.addAll(selectedEntry.getDocument());
        }

        processed.put(selectedEntryId, true);
        return remaining - 1;
    }

    /**
      * Splits this node into two groups using a document-aware R*-tree strategy.
      * The partitioning objective combines spatial quality (MBR area enlargement and overlap)
      * and textual cohesion (Jaccard similarity over document sets).
      *
      * <p><b>Algorithm Overview:</b></p>
      * <ol>
      *   <li>Adds the incoming entry to the candidate set for partitioning</li>
      *   <li>Selects two seed entries that are farthest apart (spatially and textually)</li>
      *   <li>Iteratively assigns remaining entries using a hybrid cost function that balances:
      *       <ul>
      *         <li>Spatial enlargement: increase in group's MBR area</li>
      *         <li>Document distance: dissimilarity between entry and group document sets</li>
      *       </ul>
      *   </li>
      *   <li>Enforces minimum load constraints via forced assignments when needed</li>
      *   <li>Updates both group MBRs and aggregated document sets</li>
      * </ol>
      *
      * <p><b>Hybrid Cost Function:</b><br>
      * {@code cost = betaArea × areaEnlargement + (1 - betaArea) × documentDistance}<br>
      * The {@code betaArea} parameter (0.0 to 1.0) controls the relative weight of spatial
      * versus textual metrics during entry assignment.
      *
      * <p><b>Postcondition:</b> Every candidate entry is assigned to exactly one of {@code group1}
      * or {@code group2}, and both groups satisfy minimum fill-factor constraints.
      *
      * <p><b>Time Complexity:</b> O(n² × (d + m)) where n = capacity + 1, d = dimensionality,
      * m = average document set size
      *
      * @param id identifier of the incoming entry (must be non-negative)
      * @param mbr spatial envelope of the incoming entry (must not be null; dimensionality must match tree)
      * @param documents document IDs associated with the incoming entry (may be null if document-aware
      *                  scoring is disabled; if non-null, must contain valid document identifiers)
      * @param group1 output list populated with entry IDs assigned to the first group (must not be null;
      *               will be cleared and refilled by this method)
      * @param group2 output list populated with entry IDs assigned to the second group (must not be null;
      *               will be cleared and refilled by this method)
      * @throws IllegalArgumentException if {@code id} is negative, {@code mbr} is null, or output lists are null
      * @throws IllegalStateException if a valid split cannot be produced under capacity and fill-factor constraints
      * @see #assignNextEntryHybrid(ArrayList, ArrayList, Region, Region, HashSet, HashSet, HashMap, int)
      * @see #pickSeeds()
      * @see #docDistance(HashSet, HashSet)
      */
    protected void rtreeSplit(int id, Region mbr, ArrayList<Integer> group1,
                              ArrayList<Integer> group2, HashSet<Integer> documents) {
        int totalCapacity = capacity + 1;
        int minimumLoad = (int) Math.floor(capacity * rTree.fillFactor);

        // Initialize mask array to track processed entries
        HashMap<Integer, Boolean> processed = new HashMap<>();
        for (Integer entryId : nodeEntries.keySet()) {
            processed.put(entryId, false);
        }

        // Insert new data for easier manipulation
        nodeEntries.put(id, new NodeEntry(id, mbr, documents, nodeSignature, (float) maxWeight));

        // Add the new entry to processed map so it gets distributed
        processed.put(id, false);

        // Pick seeds for initial groups
        int[] seeds = pickSeeds();
        if (seeds == null || seeds.length != 2) {
            logger.error("pickSeeds() returned invalid seeds for node {}: {}", identifier, seeds);
            throw new IllegalStateException("Invalid seeds returned from pickSeeds()");
        }

        group1.add(seeds[0]);
        group2.add(seeds[1]);
        // Mark seeds as processed
        processed.replace(seeds[0], true);
        processed.replace(seeds[1], true);

        // Initialize MBRs and document sets for each group
        Region mbr1 = nodeEntries.get(seeds[0]).getMBR().clone();
        Region mbr2 = nodeEntries.get(seeds[1]).getMBR().clone();
        HashSet<Integer> doc1 = new HashSet<>(nodeEntries.get(seeds[0]).getDocument());
        HashSet<Integer> doc2 = new HashSet<>(nodeEntries.get(seeds[1]).getDocument());

        int remaining = totalCapacity - 2;

        while (remaining > 0) {
            // Handle forced assignment to satisfy minimum load requirements
            if (minimumLoad - group1.size() == remaining) {
                // Assign all remaining entries to group1 to meet minimum load
                assignRemainingEntries(group1, mbr1, doc1, processed);
                break;
            } else if (minimumLoad - group2.size() == remaining) {
                // Assign all remaining entries to group2 to meet minimum load
                assignRemainingEntries(group2, mbr2, doc2, processed);
                break;
            } else {
                // Select next entry to add based on hybrid cost function
                 remaining = assignNextEntryHybrid(group1, group2, mbr1, mbr2, doc1, doc2, processed, remaining);
            }
        }
    }

    /**
     * Determines the optimal group assignment for a new entry during R-tree node splitting.
     * Uses a multi-level decision cascade to break ties between groups in the following order:
     * <ol>
     *   <li>Hybrid cost (combined spatial and textual metrics)</li>
     *   <li>Area enlargement required for insertion</li>
     *   <li>Total area after insertion</li>
     *   <li>Group size (as final tiebreaker)</li>
     * </ol>
     *
     * <p>The method implements the R*-tree optimization criteria while also considering
     * document-based metrics for document-aware R-tree variants.
     *
     * @param hybrid1 Combined spatial-textual cost metric for adding to first group
     * @param hybrid2 Combined spatial-textual cost metric for adding to second group
     * @param areaEnlargement1 Required increase in MBR area if added to first group
     * @param areaEnlargement2 Required increase in MBR area if added to second group
     * @param area1 Current total area of first group's MBR
     * @param area2 Current total area of second group's MBR
     * @param size1 Current number of entries in first group
     * @param size2 Current number of entries in second group
     * @return 1 if the entry should be assigned to the first group, 2 for the second group
     */
    private int determineGroupAssignment(double hybrid1, double hybrid2,
                                         double areaEnlargement1, double areaEnlargement2,
                                         double area1, double area2,
                                         int size1, int size2) {
        // Primary criterion: hybrid cost
        if (hybrid1 < hybrid2) return 1;
        if (hybrid2 < hybrid1) return 2;

        // First tiebreaker: area enlargement
        if (areaEnlargement1 < areaEnlargement2) return 1;
        if (areaEnlargement2 < areaEnlargement1) return 2;

        // Second tiebreaker: area after insertion
        if (area1 < area2) return 1;
        if (area2 < area1) return 2;

        // Third tiebreaker: group with fewer entries
        if (size1 < size2) return 1;
        if (size2 < size1) return 2;

        // Default: group 1
        return 1;
    }


    /**
      * Splits the node into two groups using the R*-tree split algorithm.
      * This method implements the R*-tree's topological split strategy which aims to
      * minimize both overlap between nodes and perimeter of resulting MBRs.
      *
      * <p>Key steps of the R*-tree split algorithm:
      * <ol>
      *   <li>Sort entries along each dimension by both lower and upper bounds</li>
      *   <li>Choose split axis that minimizes margin-value sum</li>
      *   <li>Find optimal split index along chosen axis that minimizes overlap</li>
      *   <li>Distribute entries between groups while maintaining minimum fill</li>
      * </ol>
      *
      * @param id The unique identifier for the new entry being inserted
      * @param mbr The Minimum Bounding Rectangle of the new entry
      * @param group1 List to store entry identifiers assigned to the first group
      * @param group2 List to store entry identifiers assigned to the second group
      * @throws IllegalStateException if minimum fill requirements cannot be met
      * @see RstarSplitEntryComparatorLow
      * @see RstarSplitEntryComparatorHigh
      */
    protected void rstarSplit(int id, Region mbr, ArrayList<Integer> group1, ArrayList<Integer> group2) {
        // Create arrays for sorting entries by their MBR coordinates
        final int totalEntries = capacity + 1;

        ArrayList<RstarSplitEntry> dataLow = new ArrayList<>(totalEntries);
        ArrayList<RstarSplitEntry> dataHigh = new ArrayList<>(totalEntries);

        // Add the new entry to the node for split calculation first
        nodeEntries.put(id, new NodeEntry(id, mbr, nodeSignature, (float) maxWeight));

        // Calculate minimum entries per split group. If configuration is too restrictive,
        // fall back to a non-empty partition range to keep split logic safe.
        final int requestedMinEntries = (int) Math.floor(totalEntries * rTree.splitDistributionFactor);
        int minSplitPosition = Math.max(1, requestedMinEntries);
        int maxSplitPosition = totalEntries - minSplitPosition;

        if (minSplitPosition > maxSplitPosition) {
            minSplitPosition = 1;
            maxSplitPosition = totalEntries - 1;
        }

        // Initialize entries for all dimensions
        for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
            int entryId = entry.getKey();
            RstarSplitEntry splitEntry = new RstarSplitEntry(entryId, entry.getValue().getMBR(), 0);
            dataLow.add(splitEntry);
            dataHigh.add(splitEntry);
        }

        // Variables to track the best split axis and ordering
        double minimumMargin = Double.POSITIVE_INFINITY;
        int bestSplitAxis = -1;
        int bestSortOrder = -1;  // 0 for low values, 1 for high values

        // Phase 1: Choose split axis - find the dimension with minimum margin value
        for (int dim = 0; dim < rTree.dimension; dim++) {
            // Set the current dimension for comparison
            for (int i = 0; i < totalEntries; i++) {
                dataLow.get(i).sortDim = dim;
                dataHigh.get(i).sortDim = dim;
            }

            // Sort entries by lower and upper bounds in this dimension
            dataLow.sort(new RstarSplitEntryComparatorLow());
            dataHigh.sort(new RstarSplitEntryComparatorHigh());

            // Calculate sum of margins for all possible distributions
            double marginL = 0.0;
            double marginH = 0.0;

            for (int splitPos = minSplitPosition; splitPos <= maxSplitPosition; splitPos++) {
                // Create regions for the first and second groups (low sort)
                Region bbl1 = createGroupMBR(dataLow, 0, splitPos);
                Region bbl2 = createGroupMBR(dataLow, splitPos, totalEntries);

                // Create regions for the first and second groups (high sort)
                Region bbh1 = createGroupMBR(dataHigh, 0, splitPos);
                Region bbh2 = createGroupMBR(dataHigh, splitPos, totalEntries);

                // Add margins of both groups to the cumulative margin sum
                marginL += bbl1.getMargin() + bbl2.getMargin();
                marginH += bbh1.getMargin() + bbh2.getMargin();
            }

            // Choose the sort order with the minimum margin
            double margin = Math.min(marginL, marginH);

            // If this dimension has a better margin, update the best split axis
            if (margin < minimumMargin) {
                minimumMargin = margin;
                bestSplitAxis = dim;
                bestSortOrder = (marginL < marginH) ? 0 : 1;
            }
        }

        // Phase 2: Choose split point using the best axis and sort order

        // Prepare entries for the chosen dimension
        for (int i = 0; i < totalEntries; i++) {
            dataLow.get(i).sortDim = bestSplitAxis;
        }

        // Sort according to the best sort order (low or high)
        if (bestSortOrder == 0) {
            dataLow.sort(new RstarSplitEntryComparatorLow());
        } else {
            dataLow.sort(new RstarSplitEntryComparatorHigh());
        }

        // Initialize variables to find best split point
        double minOverlap = Double.POSITIVE_INFINITY;
        double minArea = Double.POSITIVE_INFINITY;
        int bestSplitPosition = -1;

        // Find the split point that minimizes overlap, then area as tiebreaker
        for (int splitPos = minSplitPosition; splitPos <= maxSplitPosition; splitPos++) {
            // Create MBRs for the two potential groups
            Region bb1 = createGroupMBR(dataLow, 0, splitPos);
            Region bb2 = createGroupMBR(dataLow, splitPos, totalEntries);

            // Calculate overlap between the two groups
            double overlap = bb1.getIntersectingArea(bb2);
            double area = bb1.getArea() + bb2.getArea();

            // Update best split point if we found less overlap or equal overlap with smaller area
            if (overlap < minOverlap || (overlap == minOverlap && area < minArea)) {
                bestSplitPosition = splitPos;
                minOverlap = overlap;
                minArea = area;
            }
        }

        if (bestSplitPosition < 1 || bestSplitPosition >= totalEntries) {
            throw new IllegalStateException("R*-split failed to find a valid non-empty partition.");
        }

        // Apply the split by adding entries to the appropriate groups
        for (int i = 0; i < bestSplitPosition; i++) {
            group1.add(dataLow.get(i).id);
        }

        for (int i = bestSplitPosition; i < totalEntries; i++) {
            group2.add(dataLow.get(i).id);
        }

        if (group1.isEmpty() || group2.isEmpty()) {
            throw new IllegalStateException("R*-split produced an empty group, which is invalid.");
        }
    }


    /**
     * Creates a minimum bounding region (MBR) that encloses a specified range of entries.
     * The MBR is computed by iteratively combining the regions of all entries within the
     * given range [start, end) to form the smallest possible rectangle that contains all
     * of them.
     *
     * @param entries Array of RstarSplitEntry objects whose regions will be combined
     * @param start   Starting index (inclusive) in the array, must be >= 0
     * @param end     Ending index (exclusive) in the array, must be <= entries.length
     * @return Region A new Region object representing the combined MBR, with coordinates
     *                set to encompass all entry regions in the specified range
     * @throws IllegalArgumentException if:
     *         - start is negative
     *         - end exceeds the array length
     *         - start is >= end
     *         - entries array is null
     * @see Region#combinedRegion(Region, Region)
     */
    private Region createGroupMBR(ArrayList<RstarSplitEntry> entries, int start, int end) {
        if (entries == null) {
            throw new IllegalArgumentException("R*-split entries cannot be null.");
        }
        if (start < 0 || end > entries.size() || start >= end) {
            throw new IllegalArgumentException(
                    String.format("Invalid R*-split group range: start=%d, end=%d, size=%d", start, end, entries.size())
            );
        }

        Region[] regions = new Region[end - start];
        for (int i = start; i < end; i++) {
            regions[i - start] = entries.get(i).region;
        }
        return Region.combinedRegion(regions);
    }


    /**
      * Picks two seed entries for initializing node split groups based on the R-tree variant.
      * Different variants use distinct criteria for seed selection to optimize split quality:
      *
      * <ul>
      *   <li>Linear variant: Chooses entries with maximum normalized separation along a
      *       single dimension, using O(n) comparisons</li>
      *   <li>R*-tree variant: Selects entries that maximize normalized separation while
      *       considering all dimensions, using O(n) comparisons</li>
      *   <li>Quadratic variant: Picks entries that would waste maximum space if grouped
      *       together, using O(n²) comparisons to evaluate all pairs</li>
      * </ul>
      *
      * @return An int array of length 2 containing the identifiers of the selected seed entries
      * @throws IllegalStateException if node has fewer than 2 entries or no valid seeds found
      * @see #rtreeSplit(int, Region, ArrayList, ArrayList)
      * @see #rstarSplit(int, Region, ArrayList, ArrayList)
      */
    protected int[] pickSeeds() {
        int i1 = -1, i2 = -1;

        switch (rTree.treeVariant) {
            case SpatialIndex.RtreeVariantLinear:
            case SpatialIndex.RtreeVariantRstar:
                // Find the dimension with greatest normalized separation
                double maxNormalizedSeparation = Double.NEGATIVE_INFINITY;

                for (int dim = 0; dim < rTree.dimension; dim++) {
                    // First pass: find extremes for normalization
                    double dimensionLow = Double.POSITIVE_INFINITY;
                    double dimensionHigh = Double.NEGATIVE_INFINITY;

                    // Find entries with min low and max high for normalization
                    for (NodeEntry entry : nodeEntries.values()) {
                        dimensionLow = Math.min(dimensionLow, entry.getMBR().getLow(dim));
                        dimensionHigh = Math.max(dimensionHigh, entry.getMBR().getHigh(dim));
                    }

                    // Prevent division by zero
                    double dimensionWidth = Math.max(dimensionHigh - dimensionLow, 1.0);

                    // Second pass: find entries with the greatest low and least high
                    int entryWithGreatestLow = -1;
                    int entryWithLeastHigh = -1;
                    double greatestLowValue = Double.NEGATIVE_INFINITY;
                    double leastHighValue = Double.POSITIVE_INFINITY;

                    for (Map.Entry<Integer, NodeEntry> mapEntry : nodeEntries.entrySet()) {
                        int entryId = mapEntry.getKey();
                        Region mbr = mapEntry.getValue().getMBR();

                        if (mbr.getLow(dim) > greatestLowValue) {
                            greatestLowValue = mbr.getLow(dim);
                            entryWithGreatestLow = entryId;
                        }

                        if (mbr.getHigh(dim) < leastHighValue) {
                            leastHighValue = mbr.getHigh(dim);
                            entryWithLeastHigh = entryId;
                        }
                    }

                    // Calculate normalized separation along this dimension
                    double normalizedSeparation = (greatestLowValue - leastHighValue) / dimensionWidth;

                    // Update best pair if this dimension has better separation
                    if (normalizedSeparation > maxNormalizedSeparation) {
                        maxNormalizedSeparation = normalizedSeparation;
                        i1 = entryWithLeastHigh;
                        i2 = entryWithGreatestLow;
                    }
                }

                // Handle case where both indices are the same
                if (i1 == i2) {
                    // Find a different entry
                    for (int entryId : nodeEntries.keySet()) {
                        if (entryId != i1) {
                            i2 = entryId;
                            break;
                        }
                    }
                }
                break;

            case SpatialIndex.RtreeVariantQuadratic:
                // Find the pair with maximum dead space in their combined MBR
                double maxDeadSpace = Double.NEGATIVE_INFINITY;

                for (Map.Entry<Integer, NodeEntry> entryA : nodeEntries.entrySet()) {
                    Region mbrA = entryA.getValue().getMBR();
                    double area1 = mbrA.getArea();

                    for (Map.Entry<Integer, NodeEntry> entryB : nodeEntries.entrySet()) {
                        if (!entryA.getKey().equals(entryB.getKey())) {
                            Region mbrB = entryB.getValue().getMBR();
                            double area2 = mbrB.getArea();

                            Region combinedRegion = mbrA.combinedRegion(mbrB);

                            // Calculate dead space (wasted area) in the combined MBR
                            double deadSpace = combinedRegion.getArea() - area1 - area2;

                            if (deadSpace > maxDeadSpace) {
                                maxDeadSpace = deadSpace;
                                i1 = entryA.getKey();
                                i2 = entryB.getKey();
                            }
                        }
                    }
                }
                break;

            default:
                logger.error("Unknown R-tree variant during seed selection in node {}: {}", identifier, rTree.treeVariant);
                throw new IllegalStateException("Unknown R-tree variant: " + rTree.treeVariant);
        }

        return new int[]{i1, i2};
    }


    /**
     * Condenses the tree after a deletion by walking upward from the current node and
     * removing nodes that violate minimum occupancy constraints.
     *
     * <p>This is the standard R\*-tree condense phase used to preserve query quality
     * and space utilization after removals.
     *
     * <p>Behavior summary:
     * <ol>
     * <li>Evaluates current node occupancy against the minimum fill requirement.</li>
     * <li>If underfull, detaches the node from its parent and pushes it to {@code toReinsert}.</li>
     * <li>If not underfull, recomputes/adjusts parent entry bounds for this node.</li>
     * <li>Repeats the same process for ancestors until the root is reached.</li>
     * </ol>
     *
     * <p>Postconditions:
     * <ul>
     * <li>All underfull nodes encountered on the upward path are removed from the tree and queued for reinsertion.</li>
     * <li>Ancestor MBRs on the processed path are consistent with remaining children.</li>
     * <li>The root is reached without violating structural invariants.</li>
     * </ul>
     *
     * @param toReinsert stack that accumulates removed nodes for later reinsertion,
     *                   ordered from lower levels to higher levels
     * @param pathBuffer stack of ancestor node identifiers from root to current node;
     *                   consumed while traversing upward during condensing
     * @throws IllegalStateException if structural invariants are violated during condensing
     */
    protected void condenseTree(Stack<Node> toReinsert, Stack<Integer> pathBuffer) {
        if (toReinsert == null) {
            logger.error("toReinsert stack is null during condenseTree in node {}", identifier);
            throw new IllegalArgumentException("toReinsert stack cannot be null");
        }
        if (pathBuffer == null) {
            logger.error("pathBuffer stack is null during condenseTree in node {}", identifier);
            throw new IllegalArgumentException("pathBuffer cannot be null");
        }

        int minimumLoad = (int) Math.floor(capacity * rTree.fillFactor);

        if (pathBuffer.isEmpty()) {
            // Eliminate root if it has only one child
            if (level != 0 && nodeEntries.size() == 1) {
                // Get the first (and only) entry from nodeEntries
                //Integer childId = nodeEntries.keySet().iterator().next();
                Node n = rTree.readNode(nodeEntries.firstKey());
                rTree.deleteNode(n);
                n.identifier = rTree.rootID;
                rTree.writeNode(n);

                rTree.stats.nodesInLevel.remove(rTree.stats.nodesInLevel.size() - 1);
                rTree.stats.treeHeight--;
                // Compensate for pending deleteNode that will decrease nodesInLevel
                rTree.stats.nodesInLevel.set(rTree.stats.treeHeight - 1, 2);
            }
        } else {
            int parentId = pathBuffer.pop();
            Index parent = (Index) rTree.readNode(parentId);

            // Recalculate the MBR of this node
            if (nodeEntries.size() < minimumLoad) {
                // Used space is less than the minimum
                // 1. Eliminate node entry from the parent using node identifier
                parent.deleteEntry(this.identifier);
                // 2. Add this node to the stack to reinsert its entries
                toReinsert.push(this);
            } else {
                // Adjust the entry in parent to contain the new bounding region of this node
                parent.getNodeEntries().get(this.identifier).setMBR(nodeMBR.clone());

                // Recalculate parent's MBR using the optimized method
                parent.nodeMBR = Region.recalculateMBR(parent.getNodeEntriesMBRs());
            }

            // Write parent node back to storage
            rTree.writeNode(parent);

            // Continue condensing up the tree
            parent.condenseTree(toReinsert, pathBuffer);
        }
    }


    /**
     * Loads the state of this node from another node instance.
     * This method is used to copy the contents of a source node into this node,
     * including its entries, MBR, and level.
     *
     * <p><b>Deep Copy Behavior:</b>
     * <ul>
     *   <li>All node entries are deeply copied from source to this node</li>
     *   <li>MBR is recalculated based on copied entries</li>
     *   <li>Node level is preserved from the source node</li>
     *   <li>Document sets are synchronized if tree is document-aware</li>
     * </ul>
     *
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>This node contains exact replica of source node's state</li>
     *   <li>Node identifier and tree reference remain unchanged</li>
     *   <li>Document tracking is consistent with copied entries</li>
     * </ul>
     *
     * @param node The source node from which to load data (must not be null)
     * @throws IllegalArgumentException if the source node is null or in an invalid state
     * @throws NullPointerException if source node entries or MBR are null
     */
    public void load(Node node) {
        if (node == null) {
            throw new IllegalArgumentException("Source node cannot be null");
        }

        // Reset node state
        this.nodeMBR = rTree.infiniteRegion.clone();
        this.level = node.level;
        this.nodeSignature = node.nodeSignature;
        this.maxWeight = node.maxWeight;

        // Clear existing entries and documents
        this.nodeEntries.clear();
        if (rTree.isDocumentAware() && this.nodeDocuments != null) {
            this.nodeDocuments.clear();
        }

        // Deep copy all entries from source node
        for (Map.Entry<Integer, NodeEntry> entry : node.nodeEntries.entrySet()) {
            NodeEntry sourceEntry = entry.getValue();
            NodeEntry newEntry = new NodeEntry(
                    entry.getKey(),
                    sourceEntry.getMBR().clone(),
                    sourceEntry.getDocument() != null ? new HashSet<>(sourceEntry.getDocument()) : null,
                    sourceEntry.getChildSignature(),
                    sourceEntry.getChildMaxScore()
            );

            this.nodeEntries.put(entry.getKey(), newEntry);
            Region.combinedRegion(nodeMBR, newEntry.getMBR());

            // Update node documents if document-aware
            if (rTree.isDocumentAware() && this.nodeDocuments != null && newEntry.getDocument() != null) {
                this.nodeDocuments.addAll(newEntry.getDocument());
            }
        }
    }


    /**
     * Stores the current state of this node in the R-tree and prepares it for persistence.
     * This method handles the following tasks:
     * <ul>
     *   <li>Sets the node type (leaf or index) based on the node's level</li>
     *   <li>For document-aware R-trees:
     *     <ul>
     *       <li>Updates document mappings for index nodes</li>
     *       <li>Maintains parent-child document relationships</li>
     *       <li>Ensures document set consistency across tree levels</li>
     *     </ul>
     *   </li>
     *   <li>Validates node state before storage</li>
     * </ul>
     *
     * <p><b>Node Type Assignment:</b> Leaf nodes have `level == 0`, while index nodes have `level > 0`.
     * This distinction determines how the node is serialized and accessed during tree operations.
     *
     * <p><b>Document-Aware Trees:</b> If enabled via `rTree.isDocumentAware()`, this method
     * synchronizes document sets from child entries (for index nodes) or leaf documents,
     * ensuring consistency for document-based pruning and filtering operations.
     *
     * <p><b>Postcondition:</b> The node's state is persisted to storage and ready for
     * retrieval in subsequent tree operations.
     *
     * <p><b>Time Complexity:</b> O(n) where n = number of entries in the node
     * (for document synchronization in document-aware trees)
     *
     * @return The current node instance after storing its state
     * @throws IllegalStateException if the node's state is invalid for storage (e.g., empty non-root node)
     * @throws NullPointerException if node entries or MBR is null
     * @see #load(Node)
     * @see Index
     * @see Leaf
     */
    protected Node store() {
        // Set the node type based on the level
        nodeType = (level == 0) ? SpatialIndex.PersistentLeaf : SpatialIndex.PersistentIndex;

        // Update document mappings for document-aware R-trees
        if (rTree.isDocumentAware()) {
            Map<Integer, HashSet<Integer>> documentNodeMapping = rTree.getDocumentNodeMapping();

            for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                Integer entryId = entry.getKey();
                HashSet<Integer> documents = entry.getValue().getDocument();

                // Store a copy of the document set to prevent external mutations
                if (documents != null && !documents.isEmpty()) {
                    documentNodeMapping.put(entryId, new HashSet<>(documents));
                } else {
                    // Remove stale mappings when an entry has no documents
                    documentNodeMapping.remove(entryId);
                }
            }
        }

        return this;
    }



    /**
     * Calculates the Jaccard distance between two document sets.
     * The distance is defined as: 1 - |A ∩ B| / |A ∪ B|
     * where A and B are the input sets.
     *
     * <p>Properties of the Jaccard distance:
     * <ul>
     *   <li>Ranges from 0 (identical sets) to 1 (disjoint sets)</li>
     *   <li>Symmetric: distance(A,B) = distance(B,A)</li>
     *   <li>Satisfies triangle inequality for metric spaces</li>
     * </ul>
     *
     * <p>Edge cases:
     * <ul>
     *   <li>Returns 0.0 if both sets are empty</li>
     *   <li>Returns 1.0 if exactly one set is empty</li>
     *   <li>Returns 1.0 if sets have no elements in common</li>
     * </ul>
     *
     * @param set1 The first document set, may be null or empty
     * @param set2 The second document set, may be null or empty
     * @return The Jaccard distance as a double between 0 (identical) and 1 (completely different)
     * @throws NullPointerException if either set is null
     * @see #rtreeSplit(int, Region, ArrayList, ArrayList)
     */
    protected double docDistance(HashSet<Integer> set1, HashSet<Integer> set2) {
        // If either set is empty, they are completely different
        if (set1.isEmpty() && set2.isEmpty())
            return 0.0;  // Both empty = identical
        if (set1.isEmpty() || set2.isEmpty())
            return 1.0;

        // For performance, iterate through the smaller set
        HashSet<Integer> smaller = (set1.size() <= set2.size()) ? set1 : set2;
        HashSet<Integer> larger = (set1.size() <= set2.size()) ? set2 : set1;

        // Calculate intersection size efficiently
        int intersectionSize = 0;
        for (Integer element : smaller) {
            if (larger.contains(element)) {
                intersectionSize++;
            }
        }

        double unionSize = set1.size() + set2.size() - intersectionSize;

        // Return normalized distance (1 - similarity)
        return 1.0 - (intersectionSize / unionSize);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Node{");
        sb.append("id=").append(identifier);
        sb.append(", level=").append(level);
        sb.append(", type=").append(nodeType);
        sb.append(", entries=").append(nodeEntries.size());
        sb.append(", capacity=").append(capacity);
        sb.append(", mbr=").append(nodeMBR);
        if (nodeDocuments != null) {
            sb.append(", documents=").append(nodeDocuments.size());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Prints the node's details in a structured format for debugging.
     * This method provides a comprehensive view of the node's state, including:
     * <ul>
     *   <li>Node level and identifier</li>
     *   <li>Number of children and their MBRs</li>
     *   <li>Capacity and total data length</li>
     *   <li>Data identifiers and lengths</li>
     * </ul>
     *
     * @return A formatted string representation of the node's state
     */
    public String printNode() {
        StringBuilder sb = new StringBuilder();
        sb.append("Node{\n")
                .append("\t level=").append(level).append("\n")
                .append("\t identifier=").append(identifier).append("\n")
                .append("\t children=").append(getNodeEntriesSize()).append("\n")
                .append("\t capacity=").append(capacity).append("\n")
                .append("\t nodeMBR=").append(nodeMBR).append("\n");

        if (rTree.isDocumentAware() && nodeDocuments != null) {
            sb.append("\t nodeDocuments.size=").append(nodeDocuments.size()).append("\n");
        }

        sb.append("\t type=").append(nodeType).append("\n}\n");

        // Print each entry's details
        int childIndex = 0;
        for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
            NodeEntry nodeEntry = entry.getValue();
            sb.append("Child ").append(childIndex).append(": ")
              .append("id=").append(entry.getKey())
              .append(", mbr=").append(nodeEntry.getMBR());

            if (nodeEntry.getDocument() != null && !nodeEntry.getDocument().isEmpty()) {
                sb.append(", docs=").append(nodeEntry.getDocument().size());
            }

            sb.append("\n");
            childIndex++;
        }
        return sb.toString();
    }




    //====================================================================================
    //================================= Inner Classes ====================================
    //====================================================================================

    /**
     * Represents an entry during R*-tree forced reinsertions, storing the entry's
     * identifier and its distance from the center of its containing node.
     *
     * <p>Used during the R*-tree optimization process to:
     * <ul>
     *   <li>Track entries selected for reinsertion</li>
     *   <li>Store calculated distances from node center</li>
     *   <li>Enable sorting of entries by distance for selective reinsertion</li>
     * </ul>
     *
     * @see #reinsertData(int, Region, ArrayList, ArrayList)
     */
    static class ReinsertEntry {
        int id;
        double dist;

        public ReinsertEntry(int id, double dist) {
            this.id = id;
            this.dist = dist;
        }
    }


    /**
     * Comparator for sorting {@link ReinsertEntry} objects by their distance from the node center,
     * in <b>descending</b> order (farthest entry first).
     *
     * <p>Per R*-tree §4.3 (Beckmann et al., 1990), the <em>p</em> entries whose centers are
     * <b>farthest</b> from the center of the node's bounding rectangle are selected for forced
     * reinsertion. Sorting descending ensures the first {@code p} elements in the list are
     * always the farthest ones, so the selection loop in
     * {@link #reinsertData(int, Region, ArrayList, ArrayList)} can simply take the front slice.
     *
     * <ul>
     *   <li>Entries are sorted by <b>descending</b> distance (farthest first)</li>
     *   <li>The first p entries (p = {@code reinsertFactor × (capacity + 1)}) are reinserted</li>
     *   <li>Remaining (closest) entries stay in the original node</li>
     * </ul>
     *
     * @see ReinsertEntry
     * @see #reinsertData(int, Region, ArrayList, ArrayList)
     */
    static class ReinsertEntryComparator implements Comparator<ReinsertEntry> {
        @Override
        public int compare(ReinsertEntry o1, ReinsertEntry o2) {
            // Descending: farthest-from-center entry sorts first
            return Double.compare(o2.dist, o1.dist);
        }
    }


    /**
     * Encapsulates entry data for the R*-tree split algorithm, storing:
     * <ul>
     *   <li>A spatial region (MBR) representing the entry's geometry</li>
     *   <li>An entry identifier for node reference</li>
     *   <li>A dimension index for sorting during axis selection</li>
     * </ul>
     *
     * <p>Used during node splitting to:
     * <ul>
     *   <li>Sort entries by their coordinates on different axes</li>
     *   <li>Track original entry positions after sorting</li>
     *   <li>Support both lower/upper bound distribution calculations</li>
     * </ul>
     *
     * @see #rstarSplit(int, Region, ArrayList, ArrayList)
     * @see RstarSplitEntryComparatorLow
     * @see RstarSplitEntryComparatorHigh
     */
    static class RstarSplitEntry {
        Region region;
        int id;
        int sortDim;

        RstarSplitEntry(int id, Region region, int sortDim) {
            this.id = id;
            this.region = region;
            this.sortDim = sortDim;
        }
    }


    /**
     * Comparator for sorting RstarSplitEntry objects by their lower bounds along a specified dimension.
     * Used during the R*-tree split algorithm's axis selection phase to:
     * <ul>
     *   <li>Order entries by their lower MBR coordinates</li>
     *   <li>Support optimal split axis selection through margin-value calculations</li>
     *   <li>Enable balanced distribution evaluations based on lower bounds</li>
     * </ul>
     *
     * <p>Comparison is performed on the sortDim dimension of each entry's region,
     * with ties broken by entry identifier to ensure consistent ordering.
     *
     * @see #rstarSplit(int, Region, ArrayList, ArrayList)
     * @see RstarSplitEntry
     */
    static class RstarSplitEntryComparatorLow implements Comparator<RstarSplitEntry> {
        @Override
        public int compare(RstarSplitEntry o1, RstarSplitEntry o2) {
            return Double.compare(o1.region.getLow(o1.sortDim), o2.region.getLow(o2.sortDim));
        }
    }


    /**
     * Comparator for sorting RstarSplitEntry objects by their upper bounds along a specified dimension.
     * Used during the R*-tree split algorithm's axis selection phase to:
     * <ul>
     *   <li>Order entries by their upper MBR coordinates</li>
     *   <li>Support optimal split axis selection through margin-value calculations</li>
     *   <li>Enable balanced distribution evaluations based on upper bounds</li>
     * </ul>
     *
     * <p>Comparison is performed on the sortDim dimension of each entry's region,
     * with ties broken by entry identifier to ensure consistent ordering.
     *
     * @see #rstarSplit(int, Region, ArrayList, ArrayList)
     * @see RstarSplitEntry
     */
    static class RstarSplitEntryComparatorHigh implements Comparator<RstarSplitEntry> {
        @Override
        public int compare(RstarSplitEntry o1, RstarSplitEntry o2) {
            return Double.compare(o1.region.getHigh(o1.sortDim), o2.region.getHigh(o2.sortDim));
        }
    }
}


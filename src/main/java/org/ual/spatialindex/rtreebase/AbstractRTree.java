package org.ual.spatialindex.rtreebase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.InvalidPageException;
import org.ual.spatialindex.storagemanager.PropertySet;

import java.util.*;


/**
  * Abstract base class for R-Tree implementations, providing shared functionality and structure.
  *
  * <p>This class defines the core architecture for R-Tree variants, including node management,
  * parameter handling, and query operations. It encapsulates common properties and methods
  * used by all R-Tree types.
  *
  * <p>Subclasses must implement specific R-Tree algorithms and provide concrete implementations
  * for the abstract methods declared here.
  */
public abstract class AbstractRTree implements ISpatialIndex {
    protected static final Logger logger = LogManager.getLogger(AbstractRTree.class);
    protected int rootID;
    protected IStorageManager storageManager;
    protected DatasetParameters datasetParameters;
    protected float alphaDistribution;
    protected int numOfVisitedNodes;
    protected int treeVariant;
    protected float fillFactor;
    protected int indexCapacity;
    protected int leafCapacity;

    /**
     * The R*-tree 'p' constant used in the nearly minimum overlap cost algorithm.
     * This factor determines how many entries are considered in the more detailed
     * overlap check during node splits, typically a small subset of all entries.
     *
     * <p>Reference: Beckmann, N., Kriegel, H. P., Schneider, R., & Seeger, B. (1990).
     * The R*-tree: an efficient and robust access method for points and rectangles.
     * In Proceedings of ACM SIGMOD, Section 4.1
     */
    protected int nearMinimumOverlapFactor;

    /**
     * The split distribution factor (M) used in R*-tree node splitting.
     * This factor determines the number of different distribution combinations
     * to evaluate when splitting a node.
     *
     * <p>Typically set to a small fraction of the node capacity to balance split
     * quality with computational cost. A larger value results in more thorough
     * evaluation of possible splits but increases computational overhead.
     *
     * <p>Reference: Beckmann, N., Kriegel, H. P., Schneider, R., & Seeger, B. (1990).
     * The R*-tree: An efficient and robust access method for points and rectangles.
     * In Proceedings of ACM SIGMOD, Section 4.2
     */
    protected float splitDistributionFactor;

    /**
     * The reinsert factor used in R*-tree forced reinsertions ('p' constant).
     * This factor determines what percentage of entries should be removed and
     * reinserted when handling node overflows.
     *
     * <p>The value represents a fraction (typically 0.3) of entries that are
     * removed from an overflowing node and reinserted into the tree. This
     * technique helps optimize space utilization and query performance.
     *
     * <p>Reference: Beckmann, N., Kriegel, H. P., Schneider, R., & Seeger, B. (1990).
     * The R*-tree: An efficient and robust access method for points and rectangles.
     * In Proceedings of ACM SIGMOD, Section 4.3
     */
    protected float reinsertFactor;

    protected int dimension;
    protected Region infiniteRegion;
    protected AbstractStatistics stats;

    /**
     * Indicates whether document-aware features are enabled for this R-Tree.
     * When enabled, the R-Tree maintains additional mappings between documents
     * and spatial objects, supporting document-based queries and optimizations.
     *
     * <p>Document-aware features include:
     * <ul>
     *   <li>Document-node mappings for efficient document-based lookups
     *   <li>Document-specific splitting criteria during node overflow
     *   <li>Document-aware insertion and deletion operations
     * </ul>
     */
    protected boolean documentAwareEnabled = false;

    protected final Map<String, Object> defaultValues = new HashMap<>();


    //====================================================================================
    //================================ Variable Accessors ================================
    //====================================================================================

    /**
     * Checks if document-aware features are enabled for this R-Tree.
     * @return true if document-aware features are enabled, false otherwise.
     */
    public boolean isDocumentAware() {
        return documentAwareEnabled;
    }

    /**
     * Sets the document-aware flag for this R-Tree.
     * @param documentAwareEnabled true to enable document-aware features, false to disable.
     */
    public void setDocumentAware(boolean documentAwareEnabled) {
        this.documentAwareEnabled = documentAwareEnabled;
    }


    /**
     * Gets the betaArea parameter, used in document-aware splitting.
     * Subclasses that are document-aware should override this.
     *
     * @return The betaArea value.
     * @throws UnsupportedOperationException if the R-Tree is not document-aware.
     */
    public float getBetaArea() {
        if (!isDocumentAware()) {
            throw new UnsupportedOperationException("getBetaArea() is not supported by this R-Tree type.");
        }

        // This is a placeholder implementation that should be overridden by document-aware subclasses.
        throw new UnsupportedOperationException("getBetaArea() must be implemented by document-aware subclasses.");
    }


    /**
     * Sets the betaArea parameter, used in document-aware splitting.
     * Subclasses that are document-aware should override this.
     * @param betaArea The betaArea value to set.
     * @throws UnsupportedOperationException if the R-Tree is not document-aware.
     */
    public void setBetaArea(float betaArea) {
        if (!isDocumentAware()) {
            throw new UnsupportedOperationException("setBetaArea() is not supported by this R-Tree type.");
        }
        // Document-aware subclasses (e.g., DIRTree) should override this to set their specific betaArea value.
    }


    /**
     * Sets the alpha distribution parameter.
     * Alpha distribution controls how entries are distributed during node splits.
     *
     * @param alphaDistribution The alpha distribution value to set (typically between 0.0 and 1.0)
     * @throws IllegalArgumentException if the value is negative
     */
    public void setAlphaDistribution(float alphaDistribution) {
        if (alphaDistribution < 0.0f) {
            throw new IllegalArgumentException("Alpha distribution must be non-negative");
        }
        this.alphaDistribution = alphaDistribution;
    }


    /**
     * Gets the alpha distribution parameter used in node splitting operations.
     * This parameter controls how entries are distributed during node splits,
     * with values typically between 0.0 and 1.0.
     *
     * @return The alpha distribution value.
     */
    public float getAlphaDistribution() {
        return alphaDistribution;
    }


    /**
     * Gets the AbstractDocumentStore instance, used in document-aware trees.
     * Subclasses that are document-aware should override this.
     * @return The AbstractDocumentStore instance.
     * @throws UnsupportedOperationException if the R-Tree is not document-aware.
     */
    public AbstractDocumentStore getDocumentStore() {
        if (!isDocumentAware()) {
            throw new UnsupportedOperationException("getObjectStore() is not supported by this R-Tree type.");
        }
        // Document-aware subclasses (e.g., DIRTree) should override this to return their specific objstore instance.
        throw new UnsupportedOperationException("getDocumentStore() must be implemented by document-aware subclasses.");
    }

    /**
     * Gets the document-node mapping, used in document-aware trees.
     * Subclasses that are document-aware should override this.
     * @return The document-node mapping.
     * @throws UnsupportedOperationException if the R-Tree is not document-aware.
     */
    public HashMap<Integer, HashSet<Integer>> getDocumentNodeMapping() {
        if (!isDocumentAware()) {
            throw new UnsupportedOperationException("getDocumentNodeMapping() is not supported by this R-Tree type.");
        }
        // Document-aware subclasses (e.g., DIRTree) should override this to return their specific docTree instance.
        throw new UnsupportedOperationException("getDocumentNodeMapping() must be implemented by document-aware subclasses.");
    }

    /**
     * Gets the root node of the R-tree.
     * This method retrieves the root node based on the stored root identifier.
     *
     * @return The root node of the R-tree, or null if the root ID is not set.
     */
    public INode getRoot() {
        return readNode(rootID);
    }

    public Integer getRootIdentifier() {
        return rootID;
    }

    public int getDimension() {
        return dimension;
    }

    public Region getInfiniteRegion() {
        return infiniteRegion;
    }

    public void setRootIdentifier(int identifier) {
        rootID = identifier;
    }

    public double getFillFactor() {
        return fillFactor;
    }

    public int getTreeVariant() {
        return treeVariant;
    }

    public int getLeafCapacity() {
        return leafCapacity;
    }

    public Map<Integer, LinkedHashSet<Integer>> getNodesInLevel() {
        return storageManager.getNodesInLevel();
    }

    public LinkedHashSet<Integer> getNodesInLevel(int level) {
        return storageManager.getNodesInLevel().getOrDefault(level, new LinkedHashSet<>());
    }


    //====================================================================================
    //================================ Constructor Method ================================
    //====================================================================================

    /**
     * Constructs a new BaseRTree instance with the specified storage manager and dataset parameters.
     *
     * <p>This constructor initializes the R-tree with the provided storage manager and dataset parameters,
     * setting up the necessary infrastructure for spatial indexing operations.
     *
     * @param storageManager The storage manager responsible for persisting R-tree nodes
     * @param datasetParameters Parameters defining the dataset characteristics, such as dimension and tree variant
     */
    public AbstractRTree(IStorageManager storageManager, DatasetParameters datasetParameters) {
        this.storageManager = storageManager;
        this.datasetParameters = datasetParameters;
    }

    //====================================================================================
    //================================ Abstract Methods ==================================
    //====================================================================================

    /**
     * Loads core R-tree configuration parameters from a property set into this instance.
     *
     * <p>This method initializes essential R-tree parameters such as:
     * <ul>
     *   <li>Dimension - The number of spatial dimensions
     *   <li>Tree variant - The type of R-tree implementation
     *   <li>Fill factor - The minimum node fill percentage
     *   <li>Index/leaf capacities - Maximum entries in internal/leaf nodes
     *   <li>Split parameters - Factors controlling node splitting behavior
     * </ul>
     *
     * <p>Subclasses may extend this to load additional implementation-specific parameters.
     *
     * @param propertySet A set of key-value pairs containing R-tree configuration parameters
     * @throws IllegalArgumentException if required parameters are missing or invalid
     */
    protected abstract void loadParameters(PropertySet propertySet);

    /**
     * Inserts data into the R-tree using the R-tree insertion algorithm.
     *
     * <p>The insertion process includes:
     * <ol>
     *   <li>Finding the best leaf node for insertion using the R-tree's traversal policy
     *   <li>Inserting the data entry into the chosen leaf node
     *   <li>Splitting nodes if necessary when they exceed capacity
     *   <li>Propagating changes upward to maintain the tree's properties
     * </ol>
     *
     * @param shape The spatial shape (e.g., point, rectangle) representing the entry's
     *             location and extent in the dimensional space
     * @param objectId A unique identifier for this data entry. Must be unique within the tree
     * @throws IllegalArgumentException if the shape's dimension doesn't match the tree's
     *         dimension or if the id is not unique
     */
    public abstract void insertData(int objectId, final IShape shape);

    /**
     * Implements the core logic for inserting data into the R-tree. This method handles
     * the recursive process of finding the appropriate leaf node, inserting the data,
     * and managing any resulting node splits or overflows.
     *
     * <p>The insertion process follows these steps:
     * <ol>
     *   <li>Choose the best subtree for insertion at the current level
     *   <li>Insert data into the chosen node, potentially causing splits
     *   <li>Handle node overflows according to the R-tree variant's policies
     *   <li>Update MBRs and propagate changes upward
     * </ol>
     *
     * @param region The minimum bounding region (MBR) of the data
     * @param objectId Unique identifier for the data entry
     * @param level Current level in the tree where insertion is being performed
     * @param overflowTable Boolean array tracking which nodes have already overflowed
     *                     during this insertion to prevent infinite recursion
     * @throws IllegalStateException if node splits or insertions fail
     */
    protected abstract void insertDataImpl(int objectId, Region region, int level, HashMap<Integer, Boolean> overflowTable);


    /**
     * Inserts data into the R-tree with document-aware features.
     *
     * <p>This method is an extension of the basic insertData method, allowing for
     * additional handling of document identifiers and associated data. Document-aware
     * features enable the R-tree to track which documents contain each spatial object,
     * supporting document-based queries and optimizations.
     *
     * <p>The insertion process includes:
     * <ol>
     *   <li>Validating the document-aware capability is enabled
     *   <li>Storing the document associations in the document store
     *   <li>Updating document-node mappings
     *   <li>Performing the spatial insertion using document-aware splitting criteria
     * </ol>
     *
     * @param shape The spatial shape representing the entry's location and extent
     * @param obejectId A unique identifier for this data entry
     * @param doc A set of document identifiers associated with this data entry
     * @throws UnsupportedOperationException if document-aware features are not enabled
     * @throws IllegalArgumentException if shape's dimension doesn't match the tree's dimension
     * @see #isDocumentAware()
     * @see #getDocumentStore()
     * @see #getDocumentNodeMapping()
     */
    public abstract void insertData(int obejectId, final IShape shape, HashSet<Integer> doc);

    /**
     * Implements the core logic for inserting data into the R-tree with document-aware features.
     * This method handles the recursive process of finding the appropriate leaf node, inserting
     * the data, and managing any resulting node splits or overflows while also maintaining
     * document associations.
     *
     * <p>The insertion process follows these steps:
     * <ol>
     *   <li>Choose the best subtree for insertion at the current level
     *   <li>Insert data into the chosen node, potentially causing splits
     *   <li>Handle node overflows according to the R-tree variant's policies
     *   <li>Update MBRs and propagate changes upward
     * </ol>
     *
     * @param mbr The minimum bounding region (MBR) of the data
     * @param objectId Unique identifier for the data entry
     * @param doc Set of document identifiers associated with this data entry
     */
    protected abstract void insertDataImpl(int objectId, Region mbr, HashSet<Integer> doc);

    /**
     * Deletes data from the R-tree identified by its shape and unique identifier.
     *
     * <p>The deletion process includes:
     * <ol>
     *   <li>Finding the leaf node containing the entry to delete
     *   <li>Removing the entry from the leaf node
     *   <li>Adjusting MBRs of affected nodes upward to the root
     *   <li>Merging/rebalancing nodes if they become underfull
     * </ol>
     *
     * <p>The method uses exact matching on both shape and ID to ensure the correct
     * entry is deleted. If multiple entries share the same shape, only the one
     * with the matching ID will be removed.
     *
     * @param shape The shape of the data entry to delete. Must have the same
     *             dimension as the R-tree
     * @param objectId The unique identifier of the data entry to delete
     * @return true if the entry was found and deleted, false if no matching
     *         entry was found
     * @throws IllegalArgumentException if shape's dimension doesn't match the tree's dimension
     */
    public abstract boolean deleteData(int objectId, final IShape shape);



    //====================================================================================
    //=============================== Auxiliary Methods ==================================
    //====================================================================================

    /**
     * Returns the current configuration properties of this R-tree instance as a PropertySet.
     * The returned properties reflect the actual values being used by the tree for its operations.
     *
     * <p>The property set includes the following parameters:
     * <ul>
     *   <li>{@code Dimension} - Number of spatial dimensions in the tree's coordinate space
     *   <li>{@code IndexCapacity} - Maximum number of entries that can be stored in non-leaf nodes
     *   <li>{@code LeafCapacity} - Maximum number of entries that can be stored in leaf nodes
     *   <li>{@code TreeVariant} - The specific R-tree variant implementation (e.g., R*-tree)
     *   <li>{@code FillFactor} - Minimum node occupancy as a fraction (0.0 to 1.0)
     *   <li>{@code NearMinimumOverlapFactor} - Factor used in R*-tree to optimize node splitting
     *   <li>{@code SplitDistributionFactor} - Controls distribution of entries during node splits
     *   <li>{@code ReinsertFactor} - Percentage of entries to force reinsert during node overflow
     * </ul>
     *
     * <p>These properties can be used to:
     * <ul>
     *   <li>Inspect the current tree configuration
     *   <li>Clone R-tree instances with identical parameters
     *   <li>Debug and validate tree behavior
     * </ul>
     *
     * @return A PropertySet containing the current values of all R-tree parameters
     * @see PropertySet
     * @see #loadParameters(PropertySet)
     */
    public PropertySet getIndexProperties() {
        PropertySet properties = new PropertySet();

        // Add all R-tree parameters to the property set
        properties.setProperty("Dimension", dimension);
        properties.setProperty("IndexCapacity", indexCapacity);
        properties.setProperty("LeafCapacity", leafCapacity);
        properties.setProperty("TreeVariant", treeVariant);
        properties.setProperty("FillFactor", fillFactor);
        properties.setProperty("NearMinimumOverlapFactor", nearMinimumOverlapFactor);
        properties.setProperty("SplitDistributionFactor", splitDistributionFactor);
        properties.setProperty("ReinsertFactor", reinsertFactor);

        return properties;
    }

    /**
       * Validates the internal consistency of this R-tree.
       *
       * <p>This check traverses the tree and verifies:
       * <ol>
       *   <li><b>Root invariants</b>: root identifier, existence, and expected level constraints.</li>
       *   <li><b>Spatial invariants</b>: parent/child MBR containment and dimensional consistency.</li>
       *   <li><b>Structural invariants</b>: valid node occupancy, level ordering, and leaf/internal layout.</li>
       *   <li><b>Statistics invariants</b>: per-level node counts and total entry counters (when available).</li>
       * </ol>
       *
       * <p>The method returns a boolean for recoverable inconsistencies and may throw when a
       * non-recoverable storage or structural condition is encountered.
       *
       * @return {@code true} if the index passes validation; otherwise {@code false}.
       * @throws IllegalStateException if validation cannot proceed due to a critical error.
       */
    public boolean isIndexValid() {
        boolean isValid = true;
        Stack<ValidateEntry> entryStack = new Stack<>();
        Node root = readNode(rootID);

        if (root.getLevel() != stats.treeHeight - 1) {
            logger.error("Invalid root level");
            return false;
        }

        Map<Integer, Integer> nodesInLevel = new HashMap<>();
        nodesInLevel.put(root.getLevel(), 1);

        // For root node, use its own MBR as parent MBR
        entryStack.push(new ValidateEntry(root.getMBR().clone(), root));

        while (!entryStack.isEmpty()) {
            ValidateEntry validateEntry = entryStack.pop();
            Node node = validateEntry.node;

            // Calculate the actual MBR from child nodes
            Region calculatedMBR = calculateNodeMBR(node);

            // Check if actual MBR equals node's stored MBR
            if (!calculatedMBR.equals(node.getMBR())) {
                logger.error("Invalid node MBR information for node {}: expected {}, actual {}",
                        node.getIdentifier(), calculatedMBR, node.getMBR());
                isValid = false;
            }

            // For non-root nodes, validate parent MBR
            if (node.getIdentifier() != rootID) {
                // Use a tolerance-based comparison instead of exact equals
                if (!areMBRsEquivalent(node.getMBR(), validateEntry.parentMBR)) {
                    logger.error("Invalid parent MBR information for node {}: expected {}, actual {}",
                            node.getIdentifier(), validateEntry.parentMBR, node.getMBR());
                    isValid = false;
                }
            }

            // If not a leaf node, add children to stack for verification
            if (node.getLevel() > 0) {
                TreeMap<Integer, NodeEntry> nodeEntries = node.getNodeEntries();
                for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                    Node childNode = readNode(entry.getKey());
                    nodesInLevel.merge(childNode.getLevel(), 1, Integer::sum);

                    // Clone the MBR to avoid reference issues
                    Region parentMBR = entry.getValue().getMBR().clone();
                    entryStack.push(new ValidateEntry(parentMBR, childNode));
                }
            }
        }

        // Verify node count statistics
        int totalNodes = 0;
        for (int level = 0; level < stats.treeHeight; level++) {
            int nodesInCurrentLevel = nodesInLevel.getOrDefault(level, 0);
            int expectedNodesInLevel = stats.nodesInLevel.get(level);
            if (nodesInCurrentLevel != expectedNodesInLevel) {
                logger.error("Invalid number of nodes in level {}: found {}, expected {}",
                        level, nodesInCurrentLevel, expectedNodesInLevel);
                isValid = false;
            }
            totalNodes += expectedNodesInLevel;
        }

        if (totalNodes != stats.nodes) {
            logger.error("Invalid number of nodes: expected {}, found {} in tree",
                    stats.nodes, totalNodes);
            isValid = false;
        }

        return isValid;
    }

    /**
      * Compares two minimum bounding rectangles (MBRs) for spatial equivalence,
      * allowing for floating-point precision errors.
      *
      * <p>This method checks whether the corresponding low and high coordinates
      * of each dimension in the two regions are equal within a small tolerance.
      * This is necessary because floating-point arithmetic can introduce minor
      * inaccuracies, making exact equality checks unreliable for geometric data.
      *
      * @param mbr1 The first region (MBR) to compare.
      * @param mbr2 The second region (MBR) to compare.
      * @return true if all corresponding coordinates are equal within the tolerance; false otherwise.
      */
    private boolean areMBRsEquivalent(Region mbr1, Region mbr2) {
        if (mbr1.getDimension() != mbr2.getDimension()) {
            return false;
        }

        final double TOLERANCE = 1e-10;
        for (int i = 0; i < mbr1.getDimension(); i++) {
            if (Math.abs(mbr1.getLow(i) - mbr2.getLow(i)) > SpatialIndex.EPSILON ||
                Math.abs(mbr1.getHigh(i) - mbr2.getHigh(i)) > SpatialIndex.EPSILON) {
                return false;
            }
        }
        return true;
    }

    /**
      * Calculates the minimum bounding rectangle (MBR) that encloses all child entries of a node.
      * The MBR is computed by finding the minimum and maximum coordinates across all dimensions
      * for all child entries in the node.
      *
      * <p>The calculation process:
      * <ol>
      *   <li>Clones the infinite region as the initial MBR.
      *   <li>For each dimension:
      *     <ul>
      *       <li>Finds the minimum low coordinate among all child MBRs.
      *       <li>Finds the maximum high coordinate among all child MBRs.
      *     </ul>
      *   <li>Updates the cloned region with these minimum/maximum coordinates.
      * </ol>
      *
      * @param node The node whose MBR needs to be calculated. Must not be null and
      *             must have valid child MBRs.
      * @return A new Region object representing the calculated MBR.
      * @see Region
      */
    private Region calculateNodeMBR(Node node) {
        Region calculatedMBR = infiniteRegion.clone();

        TreeMap<Integer, NodeEntry> nodeEntries = node.getNodeEntries();

        if (nodeEntries.isEmpty()) {
            return calculatedMBR;
        }

        for (int dim = 0; dim < dimension; dim++) {
            double minLow = Double.POSITIVE_INFINITY;
            double maxHigh = Double.NEGATIVE_INFINITY;

            for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                Region childMBR = entry.getValue().getMBR();
                minLow = Math.min(minLow, childMBR.getLow(dim));
                maxHigh = Math.max(maxHigh, childMBR.getHigh(dim));
            }

            calculatedMBR.setLow(dim, minLow);
            calculatedMBR.setHigh(dim, maxHigh);
        }

        return calculatedMBR;
    }

    /**
     * Captures aggregate tree-quality metrics that are useful for diagnosing
     * performance regressions caused by MBR drift or sibling-overlap explosion.
     *
     * <p>Metrics are grouped per level and include:
     * <ul>
     *   <li>node count and entry count</li>
     *   <li>sum of node MBR areas</li>
     *   <li>sum of pairwise sibling overlap areas (internal nodes only)</li>
     * </ul>
     *
     * <p>The snapshot also includes two global integrity counters:
     * <ul>
     *   <li>parent-child containment violations</li>
     *   <li>parent-entry vs child-node MBR mismatches</li>
     * </ul>
     */
    public static final class TreeQualitySnapshot {
        public static final class LevelMetrics {
            private int nodeCount;
            private int entryCount;
            private double sumNodeArea;
            private double sumSiblingOverlapArea;

            public int getNodeCount() {
                return nodeCount;
            }

            public int getEntryCount() {
                return entryCount;
            }

            public double getSumNodeArea() {
                return sumNodeArea;
            }

            public double getSumSiblingOverlapArea() {
                return sumSiblingOverlapArea;
            }
        }

        private final int totalNodes;
        private final int totalEntries;
        private final int parentContainmentViolations;
        private final int parentEntryMismatchViolations;
        private final SortedMap<Integer, LevelMetrics> levelMetrics;

        private TreeQualitySnapshot(int totalNodes,
                                    int totalEntries,
                                    int parentContainmentViolations,
                                    int parentEntryMismatchViolations,
                                    SortedMap<Integer, LevelMetrics> levelMetrics) {
            this.totalNodes = totalNodes;
            this.totalEntries = totalEntries;
            this.parentContainmentViolations = parentContainmentViolations;
            this.parentEntryMismatchViolations = parentEntryMismatchViolations;
            this.levelMetrics = Collections.unmodifiableSortedMap(levelMetrics);
        }

        public int getTotalNodes() {
            return totalNodes;
        }

        public int getTotalEntries() {
            return totalEntries;
        }

        public int getParentContainmentViolations() {
            return parentContainmentViolations;
        }

        public int getParentEntryMismatchViolations() {
            return parentEntryMismatchViolations;
        }

        public SortedMap<Integer, LevelMetrics> getLevelMetrics() {
            return levelMetrics;
        }
    }

    /**
     * Builds a diagnostics snapshot for the current tree instance.
     *
     * <p>This is intended for regression diagnosis and should be called from
     * benchmarks/tests, not hot query paths.
     */
    public TreeQualitySnapshot collectTreeQualitySnapshot() {
        Node root = readNode(rootID);
        if (root == null) {
            return new TreeQualitySnapshot(0, 0, 0, 0, new TreeMap<>());
        }

        int totalNodes = 0;
        int totalEntries = 0;
        int parentContainmentViolations = 0;
        int parentEntryMismatchViolations = 0;

        SortedMap<Integer, TreeQualitySnapshot.LevelMetrics> levelMetrics = new TreeMap<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            totalNodes++;
            totalEntries += current.getNodeEntriesSize();

            TreeQualitySnapshot.LevelMetrics metrics = levelMetrics.computeIfAbsent(
                    current.getLevel(), ignored -> new TreeQualitySnapshot.LevelMetrics());
            metrics.nodeCount++;
            metrics.entryCount += current.getNodeEntriesSize();
            metrics.sumNodeArea += current.getMBR().getArea();

            if (current.getLevel() <= 0) {
                continue;
            }

            ArrayList<NodeEntry> children = new ArrayList<>(current.getNodeEntries().values());

            // Pairwise sibling-overlap accumulation (internal-node quality signal).
            for (int i = 0; i < children.size(); i++) {
                Region left = children.get(i).getMBR();
                for (int j = i + 1; j < children.size(); j++) {
                    metrics.sumSiblingOverlapArea += left.getIntersectingArea(children.get(j).getMBR());
                }
            }

            for (Map.Entry<Integer, NodeEntry> childEntry : current.getNodeEntries().entrySet()) {
                Node childNode = readNode(childEntry.getKey());
                Region parentEntryMBR = childEntry.getValue().getMBR();
                Region childMBR = childNode.getMBR();

                if (!areMBRsEquivalent(parentEntryMBR, childMBR)) {
                    parentEntryMismatchViolations++;
                }
                if (!current.getMBR().contains(childMBR)) {
                    parentContainmentViolations++;
                }

                stack.push(childNode);
            }
        }

        return new TreeQualitySnapshot(
                totalNodes,
                totalEntries,
                parentContainmentViolations,
                parentEntryMismatchViolations,
                levelMetrics
        );
    }

    /**
      * Reads a node from the storage manager and returns a concrete node instance based on its type.
      * This method acts as a factory, constructing either an index node or a leaf node
      * depending on the stored node type in the storage manager.
      *
      * <p>The method performs the following operations:
      * <ol>
      *   <li>Loads the raw node data from the storage manager using the provided ID.</li>
      *   <li>Determines the node type (index or leaf) from the loaded data.</li>
      *   <li>Creates the appropriate concrete node instance.</li>
      *   <li>Initializes the node with the loaded data.</li>
      *   <li>Updates the tree's read statistics.</li>
      * </ol>
      *
      * @param id The unique identifier of the node to read from storage.
      * @return A fully initialized {@link Node} instance: either an {@link Index} for internal nodes or a {@link Leaf} for leaf nodes.
      * @throws IllegalStateException if the node data cannot be read from storage or if the stored node type is invalid.
      * @see IStorageManager#loadNode(int)
      * @see SpatialIndex#PersistentIndex
      * @see SpatialIndex#PersistentLeaf
      */
    public Node readNode(int id) {
        try {
            Node node = (Node) storageManager.loadNode(id);
            // Initialize nodeDocuments for document-aware trees
            if (this.documentAwareEnabled && node.nodeDocuments == null) {
                node.nodeDocuments = new HashSet<>();
                logger.warn("Node {} had null nodeDocuments; initialized to empty set", id);
            }
            stats.incrementReads();
            return node;
        } catch (InvalidPageException e) {
            logger.error("Failed to read node with ID {}: {}", id, e.getMessage());
            throw new IllegalStateException("Failed to read node with ID " + id, e);
        }
    }


    /**
      * Writes a node to the storage manager and updates relevant tree statistics.
      * Handles both new node insertion and updates to existing nodes.
      *
      * <p>Node writing process:
      * <ol>
      *   <li><b>For new nodes</b> (identifier &lt; 0):
      *     <ul>
      *       <li>Allocates a new page in storage</li>
      *       <li>Updates the node's identifier with the new page ID</li>
      *       <li>Increments the total node count statistic</li>
      *       <li>Updates the node count for the corresponding tree level</li>
      *     </ul>
      *   </li>
      *   <li><b>For existing nodes</b>:
      *     <ul>
      *       <li>Updates the node data in its current page location</li>
      *       <li>Keeps the same page ID/identifier</li>
      *     </ul>
      *   </li>
      * </ol>
      *
      * @param node The node to persist in storage. Must not be null.
      * @return The page ID where the node was stored (new ID for new nodes, current ID for updates).
      * @throws IllegalStateException if storage operations fail due to {@link InvalidPageException}.
      * @throws NullPointerException if the provided node is null.
      * @see IStorageManager#storeNode(int, INode)
      * @see Node#store()
      * @see IStorageManager#NewPage
      */
    public int writeNode(Node node) throws IllegalStateException {
        // Determine if this is a new node or an update to existing node
        int page = node.identifier < 0 ? IStorageManager.NewPage : node.identifier;
        boolean isNewNode = node.identifier < 0;

        try {
            // Store the node data and get the page ID
            page = storageManager.storeNode(page, node.store());

            // Update node identifier and statistics for new nodes
            if (isNewNode) {
                node.identifier = page;
                stats.nodes++;
                stats.nodesInLevel.set(node.level, stats.nodesInLevel.get(node.level) + 1);
            }

            stats.writes++;
            return page;
        } catch (InvalidPageException e) {
            logger.error("Failed to write node: {}", e.getMessage(), e);
            throw new IllegalStateException("writeNode failed with InvalidPageException", e);
        }
    }

    /**
      * Deletes a node from the storage manager and updates corresponding tree statistics.
      *
      * <p>This method performs the following operations:
      * <ol>
      *   <li>Removes the node's physical data from the storage manager</li>
      *   <li>Decrements the total node count in tree statistics</li>
      *   <li>Updates the level-specific node counter for the node's tree level</li>
      * </ol>
      *
      * <p>The method maintains consistency between the physical storage state and the
      * logical tree structure by ensuring both the node data and associated statistics
      * are updated atomically.
      *
      * @param node The node to be deleted from storage. Must be a valid {@link Node}
      *             instance with a non-negative identifier.
      * @throws NullPointerException if the node parameter is null.
      * @throws IllegalArgumentException if the node has an invalid identifier (negative).
      * @throws IllegalStateException if the storage manager fails to delete the node
      *         due to an {@link InvalidPageException}.
      * @see IStorageManager#deleteNode(int)
      * @see AbstractStatistics#nodes
      * @see AbstractStatistics#nodesInLevel
      */
    public void deleteNode(Node node) {
        try {
            // Delete node from storage manager
            storageManager.deleteNode(node.identifier);

            // Update tree statistics
            stats.nodes--;
            stats.nodesInLevel.set(node.level, stats.nodesInLevel.get(node.level) - 1);
        } catch (InvalidPageException e) {
            logger.error("Failed to delete node {}: {}", node.identifier, e.getMessage(), e);
            throw new IllegalStateException("deleteNode failed with InvalidPageException", e);
        }
    }


    /**
      * Returns the current statistics of the R-tree, including read/write counts,
      * node counts, and other performance metrics.
      *
      * <p>This method returns the internal statistics object, which may be mutable.
      * The returned statistics can be used for monitoring, debugging, or performance analysis.
      * If thread safety or immutability is required, callers should clone the returned object.
      *
      * @return The current IStatistics object containing R-tree metrics
      * @see IStatistics
      */
    public IStatistics getStatistics() {
        return stats;
    }



    //====================================================================================
    //================================= Query Methods ====================================
    //====================================================================================

    /**
     * Performs a containment query on the R-tree to find all indexed shapes that are
     * completely contained within the given query shape. A shape is considered contained
     * if every point of the shape lies within the query shape's boundaries.
     *
     * @param query   The query shape used to test containment. All returned shapes
     *               must fit entirely within this shape's boundaries
     * @param visitor The visitor implementation that will process each matching entry.
     *               The visitor's visitNode() method is called for traversed nodes,
     *               and visitData() is called for matching entries
     * @throws IllegalArgumentException if the query shape's dimension does not match
     *         the R-tree's dimension ({@link #dimension})
     * @see #rangeQuery(int, IShape, IVisitor)
     * @see SpatialIndex#ContainmentQuery
     */
    public void containmentQuery(final IShape query, final IVisitor visitor) {
        if (query.getDimension() != dimension) {
            throw new IllegalArgumentException("containmentQuery: Shape has the wrong number of dimensions.");
        }
        // Delegate to the general range query with containment type
        rangeQuery(SpatialIndex.ContainmentQuery, query, visitor);
    }


    /**
     * Performs an intersection query to find all indexed shapes that intersect with the given query shape.
     * Two shapes intersect if they share at least one common point. This operation is more efficient than
     * a containment query since it only needs to verify partial overlap rather than complete containment.
     *
     * @param query   The query shape to test intersection against. Must have the same number of dimensions
     *               as the R-tree ({@link #dimension})
     * @param visitor The visitor implementation that processes matching entries. Its visitNode() method is
     *               called for each traversed node, and visitData() for each matching entry
     * @throws IllegalArgumentException if the query shape's dimension does not match the R-tree's dimension
     * @see #rangeQuery(int, IShape, IVisitor)
     * @see SpatialIndex#IntersectionQuery
     */
    public void intersectionQuery(final IShape query, final IVisitor visitor) {
        // Validate dimensions match before proceeding
        if (query.getDimension() != dimension) {
            throw new IllegalArgumentException("intersectionQuery: Shape has the wrong number of dimensions.");
        }
        // Delegate to the general range query with containment type
        rangeQuery(SpatialIndex.IntersectionQuery, query, visitor);
    }


    /**
     * Performs a point location query to find all indexed shapes that contain the given query shape.
     *
     * <p>This method supports two types of queries:
     * <ul>
     *   <li>Point queries - finds all shapes that contain the query point
     *   <li>Region queries - finds all shapes that intersect with the query region
     * </ul>
     *
     * <p>For point queries, the method finds exact matches where the query point lies within
     * indexed shapes. For region queries, it performs an intersection test to find all indexed
     * shapes that overlap with the query region.
     *
     * @param query   The query shape, must be either {@link Point} or {@link Region}
     * @param visitor The visitor that processes each matching entry through its visitData() method
     * @throws IllegalArgumentException if query is not a Point or Region, or if its dimension
     *         doesn't match the R-tree's dimension
     * @see #intersectionQuery(IShape, IVisitor)
     * @see Region
     * @see Point
     */
    public void pointLocationQuery(final IShape query, final IVisitor visitor) {
        // Validate dimensions match before proceeding
        if (query.getDimension() != dimension) {
            throw new IllegalArgumentException("pointLocationQuery: Shape has the wrong number of dimensions.");
        }

        // Convert Point to Region if needed for consistent processing
        Region region;
        if (query instanceof Point) {
            Point point = (Point) query;
            region = new Region(point, point); // Zero-area region for exact point location
        } else if (query instanceof Region) {
            region = (Region) query;
        } else {
            throw new IllegalArgumentException("pointLocationQuery: IShape must be Point or Region only.");
        }

        // Use intersection query for efficient implementation
        rangeQuery(SpatialIndex.IntersectionQuery, region, visitor);
    }


    /**
      * Performs a k-nearest neighbor (kNN) query on the R-tree to find the k closest objects
      * to a given query shape. The search uses a priority queue-based best-first traversal
      * strategy to efficiently find the nearest neighbors.
      *
      * @param k       The number of nearest neighbors to find. Must be positive.
      * @param query   The query shape used as the reference point for distance calculations.
      *                Must have the same dimension as the R-tree.
      * @param visitor A visitor implementation that processes the nodes and data entries
      *                encountered during the search. Used for collecting results and statistics.
      * @param nnc     A nearest neighbor comparator that defines how distances between shapes
      *                are calculated. This allows for custom distance metrics.
      * @throws IllegalArgumentException if the query shape's dimension does not match the R-tree's dimension
      *                                  or if k is less than 1
      * @see NNComparator
      * @see IVisitor
      * @see INearestNeighborComparator
      */
    public void nearestNeighborQuery(int k, final IShape query, final IVisitor visitor, final INearestNeighborComparator nnc) {
        if (query.getDimension() != dimension) {
            throw new IllegalArgumentException("nearestNeighborQuery: Shape has the wrong number of dimensions.");
        }

        // Use initial capacity hint and explicit comparator for better performance
        PriorityQueue<NNEntry> queue = new PriorityQueue<>(100, Comparator.comparingDouble(NNEntry::getSpatialCost));

        // Read the root node
        Node rootNode = readNode(rootID);
        queue.add(new NNEntry(rootNode, 0.0));

        int resultsFound = 0;
        double kthDistance = 0.0;

        while (!queue.isEmpty()) {
            NNEntry current = queue.poll();

            if (current.entry instanceof Node) {
                // Process internal node
                Node node = (Node) current.entry;
                visitor.visitNode(node);

                // Process each child of the node using nodeEntries TreeMap
                TreeMap<Integer, NodeEntry> nodeEntries = node.getNodeEntries();
                for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                    int childId = entry.getKey();
                    NodeEntry nodeEntry = entry.getValue();
                    IEntry childEntry;

                    // Create appropriate entry based on node level
                    if (node.getLevel() == 0) {
                        // Leaf node - create data entry
                        childEntry = new Data(childId, nodeEntry.getMBR());
                    } else {
                        // Internal node - read child node
                        childEntry = readNode(childId);
                    }

                    // Calculate distance and add to queue
                    double distance = nnc.getMinimumDistance(query, childEntry);
                    queue.add(new NNEntry(childEntry, distance));
                }
            } else {
                // Early termination - we've found k results and current distance exceeds kth distance
                if (resultsFound >= k && current.getSpatialCost() > kthDistance) {
                    break;
                }

                // Process data entry
                visitor.visitData((IData) current.entry);
                stats.queryResults++;
                resultsFound++;
                kthDistance = current.getSpatialCost();
            }
        }
    }


    /**
     * Performs a k-nearest neighbor (kNN) query using the standard Euclidean distance metric.
     * This is a convenience method that uses the default {@link NNComparator} implementation
     * to find the k closest objects to the query shape.
     *
     * @param k       Number of nearest neighbors to find (must be greater than 0)
     * @param query   Query shape used as the reference point for distance calculations.
     *               Must have the same number of dimensions as the R-tree
     * @param visitor Visitor implementation that processes each result during the search.
     *               The visitor's visitData() method is called for each matching entry,
     *               and visitNode() is called for each traversed node
     * @throws IllegalArgumentException if k is less than 1 or if query's dimension
     *         doesn't match the R-tree's dimension
     * @see NNComparator#instance()
     * @see #nearestNeighborQuery(int, IShape, IVisitor, INearestNeighborComparator)
     */
    public void nearestNeighborQuery(int k, final IShape query, final IVisitor visitor) {
        if (k < 1) {
            throw new IllegalArgumentException("nearestNeighborQuery: k must be greater than 0");
        }
        if (query.getDimension() != dimension) {
            throw new IllegalArgumentException("nearestNeighborQuery: Shape has the wrong number of dimensions.");
        }
        // Use cached comparator instance instead of creating a new one each time
        nearestNeighborQuery(k, query, visitor, NNComparator.instance());
    }


    /**
      * Performs a range query on the R-tree to find all objects that satisfy the specified spatial relationship
      * with the query shape. The query can be either a containment query (where objects must be completely
      * contained within the query shape) or an intersection query (where objects must overlap with the query shape).
      *
      * @param type    The type of query to perform:
      *                {@link SpatialIndex#ContainmentQuery} for containment queries,
      *                {@link SpatialIndex#IntersectionQuery} for intersection queries.
      * @param query   The query shape used to define the spatial region of interest.
      *                Must have the same number of dimensions as the R-tree.
      * @param visitor A visitor object that processes each matching node and data entry.
      *                Its visitNode() method is called for each traversed node,
      *                and visitData() is called for each matching entry.
      * @throws IllegalArgumentException if the query shape's dimension does not match the R-tree's dimension.
      * @see IVisitor
      * @see #containmentQuery(IShape, IVisitor)
      * @see #intersectionQuery(IShape, IVisitor)
      */
    private void rangeQuery(int type, final IShape query, final IVisitor visitor) {
        // Use ArrayDeque instead of Stack for better performance
        Deque<Node> nodeQueue = new ArrayDeque<>();
        Node root = readNode(rootID);

        // Null check for root node
        if (root == null) {
            logger.warn("Root node is null, cannot perform range query");
            return;
        }

        // Early check to avoid unnecessary processing
        if (root.getNodeEntriesSize() > 0 && query.intersects(root.getMBR())) {
            nodeQueue.push(root);
        }

        while (!nodeQueue.isEmpty()) {
            Node currentNode = nodeQueue.pop();

            // Visit node before processing its entries
            visitor.visitNode(currentNode);

            // Process leaf nodes (data nodes)
            TreeMap<Integer, NodeEntry> nodeEntries = currentNode.getNodeEntries();
            if (currentNode.level == 0) {
                // Process each entry in the leaf node
                for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                    int childId = entry.getKey();
                    NodeEntry nodeEntry = entry.getValue();

                    // Check if entry matches query criteria
                    boolean matches = (type == SpatialIndex.ContainmentQuery)
                            ? query.contains(nodeEntry.getMBR())
                            : query.intersects(nodeEntry.getMBR());

                    if (matches) {
                        Data data = new Data(childId, nodeEntry.getMBR());
                        visitor.visitData(data);
                        stats.queryResults++;
                    }
                }
            }
            // Process internal nodes
            else {
                // Add children whose MBRs intersect with query
                for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                    int childId = entry.getKey();
                    NodeEntry nodeEntry = entry.getValue();

                    if (query.intersects(nodeEntry.getMBR())) {
                        nodeQueue.push(readNode(childId));
                    }
                }
            }
        }
    }


    /**
      * Performs a self-join intersection query on the R-tree to find all pairs of indexed objects
      * whose minimum bounding rectangles (MBRs) intersect within a given query region.
      * This is useful for spatial collision detection, overlap analysis, and discovering
      * relationships between objects in the same dataset.
      *
      * <p>For any two objects A and B in the tree, the pair is reported if and only if all
      * these conditions are met:
      * <ul>
      *   <li>A and B have overlapping spatial extents (their MBRs intersect)</li>
      *   <li>Both A and B lie within or intersect the query region</li>
      *   <li>A's identifier is less than B's identifier (to avoid duplicate pairs)</li>
      * </ul>
      *
      * <p>Performance characteristics:
      * <ul>
      *   <li>Average case: O(n * log n), where n is the number of objects in the query region</li>
      *   <li>Worst case: O(n²) when there is high spatial overlap between objects</li>
      *   <li>Space complexity: O(h), where h is the height of the tree (stack space)</li>
      * </ul>
      *
      * <p>The algorithm uses a depth-first traversal strategy with MBR-based pruning to
      * minimize unnecessary node comparisons.
      *
      * @param query The query region that defines the spatial bounds for the join operation.
      *              Must have the same dimensionality as the R-tree.
      * @param visitor Processes the matching object pairs. For each pair (A,B), receives:
      *                - visitNode() calls during tree traversal
      *                - visitData() calls with the matching object pairs
      * @throws IllegalArgumentException if the query's dimension does not match the R-tree's dimension
      * @see ISpatialIndex#intersectionQuery
      * @see Region#intersects(IShape)
      */
    public void selfJoinIntersectionQuery(final IShape query, IVisitor visitor) {
        if (query.getDimension() != dimension) {
            throw new IllegalArgumentException("selfJoinQuery: Shape has the wrong number of dimensions.");
        }

        Region mbr = query.getMBR();
        selfJoinIntersectionQuery(rootID, rootID, mbr, visitor);
    }


    /**
          * Internal recursive method to perform a self-join intersection query on the R-tree.
          * Finds all pairs of spatial objects whose minimum bounding rectangles (MBRs) intersect
          * within a given query region.
          *
          * <p>For each pair of objects (A, B) in the tree, the pair is included in the result if:
          * <ul>
          *   <li>A and B have overlapping spatial extents (their MBRs intersect)</li>
          *   <li>Both A and B intersect or are contained within the query region</li>
          *   <li>A's identifier is less than B's identifier (to avoid duplicate pairs)</li>
          * </ul>
          *
          * <p>Implementation characteristics:
          * <ul>
          *   <li>Uses depth-first traversal to minimize memory usage</li>
          *   <li>Employs MBR-based pruning to reduce unnecessary node comparisons</li>
          *   <li>Maintains symmetric ordering (A.id &lt; B.id) to avoid duplicate pairs</li>
          *   <li>Time complexity: O(n * log n) average case with good spatial distribution</li>
          *   <li>Space complexity: O(h), where h is the tree height (stack space)</li>
          * </ul>
          *
          * @param id1         Identifier of the first node in the current comparison
          * @param id2         Identifier of the second node in the current comparison
          * @param queryRegion Spatial bounds that constrain the search space
          * @param visitor     Callback object that processes matching pairs and traversed nodes
          *
          * @see #selfJoinIntersectionQuery(IShape, IVisitor)
          * @see Region#intersects(IShape)
          */
    private void selfJoinIntersectionQuery(int id1, int id2, final Region queryRegion, IVisitor visitor) {
        Node node1 = readNode(id1);
        Node node2 = readNode(id2);
        visitor.visitNode(node1);
        visitor.visitNode(node2);

        TreeMap<Integer, NodeEntry> entries1 = node1.getNodeEntries();
        TreeMap<Integer, NodeEntry> entries2 = node2.getNodeEntries();

        for (Map.Entry<Integer, NodeEntry> entry1 : entries1.entrySet()) {
            int childId1 = entry1.getKey();
            NodeEntry nodeEntry1 = entry1.getValue();
            Region mbr1 = nodeEntry1.getMBR();

            // Early filtering using the query region
            if (!queryRegion.intersects(mbr1)) {
                continue;
            }

            for (Map.Entry<Integer, NodeEntry> entry2 : entries2.entrySet()) {
                int childId2 = entry2.getKey();
                NodeEntry nodeEntry2 = entry2.getValue();
                Region mbr2 = nodeEntry2.getMBR();

                // Skip if second child doesn't intersect with query region or first child
                if (!queryRegion.intersects(mbr2) || !mbr1.intersects(mbr2)) {
                    continue;
                }

                if (node1.getLevel() == 0) { // Leaf nodes - report data pairs
                    // Verify both nodes are at the same level
                    assert node2.getLevel() == 0 : "Both nodes should be leaves in self-join.";

                    // Process pairs only once (avoid duplicates and self-pairs)
                    if (childId1 < childId2) {
                        ArrayList<IData> pair = new ArrayList<>(2);
                        pair.add(new Data(childId1, mbr1));
                        pair.add(new Data(childId2, mbr2));
                        visitor.visitData(pair);
                        stats.queryResults++; // Update query statistics
                    }
                } else { // Internal nodes - recursive descent
                    // Verify both nodes are at the same level
                    assert node1.getLevel() == node2.getLevel() : "Nodes in self-join should be at the same level.";

                    // Calculate intersection of query region with the two node MBRs
                    Region intersection = mbr1.getIntersectingRegion(mbr2);
                    Region refinedQueryRegion = queryRegion.getIntersectingRegion(intersection);

                    // Only recurse if there's a meaningful intersection
                    if (refinedQueryRegion.getArea() > 0.0) {
                        selfJoinIntersectionQuery(
                                childId1,
                                childId2,
                                refinedQueryRegion,
                                visitor
                        );
                    }
                }
            }
        }
    }


    /**
     * Performs a self-join spatial query on the R-tree to find all object pairs
     * within a specified maximum distance of each other in a given query region.
     *
     * <p>For any two objects A and B in the tree, the pair is reported if:
     * <ul>
     *   <li>Both A and B lie within the query region or within maxDistance of it
     *   <li>The minimum distance between A and B is less than or equal to maxDistance
     *   <li>A's identifier is less than B's identifier (to avoid duplicate pairs)
     * </ul>
     *
     * @param query The query region that defines the spatial bounds for the join operation
     * @param maxDistance The maximum distance between objects to be considered a match
     * @param visitor Processes the matching object pairs through visitData() method
     * @throws IllegalArgumentException if query's dimension doesn't match the R-tree's dimension
     */
    public void selfJoinMinimumDistanceQuery(final IShape query, double maxDistance, IVisitor visitor) {
        if (query.getDimension() != dimension) {
            throw new IllegalArgumentException("selfJoinQuery: Shape has the wrong number of dimensions.");
        }

        Region mbr = query.getMBR();
        selfJoinMinimumDistanceQuery(rootID, rootID, mbr, visitor, maxDistance);
    }


    /**
      * Internal recursive method to perform a self-join minimum distance query on the R-tree.
      * Finds all pairs of spatial objects whose minimum distance is within a specified maximum distance
      * in a given query region.
      *
      * <p>For each pair of objects (A, B) in the tree, the pair is included in the result if:
      * <ul>
      *   <li>Both A and B lie within the query region or within {@code maxDistance} of it</li>
      *   <li>The minimum distance between A and B is less than or equal to {@code maxDistance}</li>
      *   <li>A's identifier is less than B's identifier (to avoid duplicate pairs)</li>
      * </ul>
      *
      * <p>This method is called recursively to traverse the tree and compare all relevant pairs.
      *
      * @param id1         Identifier of the first node in the current comparison
      * @param id2         Identifier of the second node in the current comparison
      * @param queryRegion Spatial bounds that constrain the search space
      * @param visitor     Callback object that processes matching pairs and traversed nodes
      * @param maxDistance Maximum distance between objects to be considered a match
      */
    private void selfJoinMinimumDistanceQuery(int id1, int id2, final Region queryRegion, IVisitor visitor, double maxDistance) {
        Node node1 = readNode(id1);
        Node node2 = readNode(id2);
        visitor.visitNode(node1);
        visitor.visitNode(node2);

        TreeMap<Integer, NodeEntry> entries1 = node1.getNodeEntries();
        TreeMap<Integer, NodeEntry> entries2 = node2.getNodeEntries();

        for (Map.Entry<Integer, NodeEntry> entry1 : entries1.entrySet()) {
            int childId1 = entry1.getKey();
            NodeEntry nodeEntry1 = entry1.getValue();
            Region mbr1 = nodeEntry1.getMBR();

            // Early filtering using the query region and max distance
            if (queryRegion.getMinimumDistance(mbr1) > maxDistance) {
                continue;
            }

            for (Map.Entry<Integer, NodeEntry> entry2 : entries2.entrySet()) {
                int childId2 = entry2.getKey();
                NodeEntry nodeEntry2 = entry2.getValue();
                Region mbr2 = nodeEntry2.getMBR();

                // Skip if second child is too far from query region or first child
                if (queryRegion.getMinimumDistance(mbr2) > maxDistance ||
                        mbr1.getMinimumDistance(mbr2) > maxDistance) {
                    continue;
                }

                if (node1.getLevel() == 0) { // Leaf nodes - report data pairs
                    // Verify both nodes are at the same level
                    assert node2.getLevel() == 0 : "Both nodes should be leaves in self-join.";

                    // Skip self-comparison at leaf level.
                    if (id1 == id2 && childId1 == childId2) {
                        continue;
                    }

                    // Process pairs only once (avoid duplicates and self-pairs)
                    if (childId1 < childId2) {
                        ArrayList<IData> pair = new ArrayList<>(2);
                        pair.add(new Data(childId1, mbr1));
                        pair.add(new Data(childId2, mbr2));
                        visitor.visitData(pair);
                        stats.queryResults++; // Update query statistics
                    }
                } else { // Internal nodes - recursive descent
                    // Verify both nodes are at the same level
                    assert node1.getLevel() == node2.getLevel() : "Nodes in self-join should be at the same level.";

                    // Continue recursion for nodes that are within max distance
                    selfJoinMinimumDistanceQuery(
                            childId1,
                            childId2,
                            queryRegion,
                            visitor,
                            maxDistance
                    );
                }
            }
        }
    }

    /**
     * Executes a custom query strategy on the R-tree, enabling flexible tree traversal patterns.
     * This method allows implementation of specialized search algorithms by letting the query
     * strategy determine the traversal order of nodes.
     *
     * <p>The strategy implementation can maintain its own state and use custom logic to:
     * <ul>
     *   <li>Choose which nodes to visit next in the tree hierarchy
     *   <li>Process node data during traversal
     *   <li>Control when to terminate the traversal
     * </ul>
     *
     * <p>The traversal continues until either:
     * <ul>
     *   <li>The strategy indicates completion by returning false in {@code continueSearch}
     *   <li>There are no more nodes to visit
     * </ul>
     *
     * @param queryStrategy Implementation of {@link IQueryStrategy} that controls the
     *                     traversal logic and node processing. Must not be null
     * @throws IllegalArgumentException if queryStrategy is null
     * @see IQueryStrategy#getNextEntry
     */
    public void queryStrategy(final IQueryStrategy queryStrategy) {
        if (queryStrategy == null) {
            throw new IllegalArgumentException("Query strategy cannot be null");
        }

        // Track current node ID and traversal state
        int currentNodeId = rootID;
        boolean shouldContinue = true;

        // QueryResult holds the next node ID and whether to continue searching
        class QueryResult {
            int nextNodeId;
            boolean continueSearch;
        }

        while (shouldContinue) {
            Node node = readNode(currentNodeId);
            if (node == null) {
                throw new IllegalStateException("Failed to read node with ID: " + currentNodeId);
            }

            try {
                // Use wrapper class to avoid single-element arrays
                QueryResult result = new QueryResult();
                queryStrategy.getNextEntry(node,
                        new int[] { currentNodeId }, // Default to current if not set
                        new boolean[] { false }      // Default to stop if not set
                );

                // Update traversal state
                currentNodeId = result.nextNodeId;
                shouldContinue = result.continueSearch;

            } catch (Exception e) {
                logger.error("Query strategy execution failed", e);
                throw new IllegalStateException("Query strategy execution failed", e);
            }
        }
    }


    //====================================================================================
    //================================= Inner Classes ====================================
    //====================================================================================


    /**
     * A singleton comparator that calculates minimum distances between spatial shapes.
     * Used in nearest neighbor queries to determine the traversal order of entries
     * by comparing their distances to a query shape.
     *
     * <p>This implementation uses the minimum distance metric defined by the {@link IShape}
     * interface to compute distances between shapes. The comparator ensures consistent
     * ordering of entries during priority queue-based nearest neighbor searches.
     */
    static class NNComparator implements INearestNeighborComparator {
        private static NNComparator instance;

        private NNComparator() {
            // Private constructor to prevent instantiation
        }

        public static synchronized NNComparator instance() {
            if (instance == null) {
                instance = new NNComparator();
            }
            return instance;
        }

        /**
         * Calculates the minimum distance between a query shape and an entry.
         * This method is used to determine how far apart two shapes are in the spatial index.
         *
         * @param query The query shape for which the distance is calculated
         * @param e     The entry whose shape is compared against the query
         * @return The minimum distance between the query shape and the entry's shape
         */
        @Override
        public double getMinimumDistance(IShape query, IEntry e) {
            return query.getMinimumDistance(e.getShape());
        }
    }


    /**
     * Represents an entry in the nearest neighbor search priority queue.
     * Contains a reference to the spatial entry and its associated cost (distance).
     *
     * <p>This class is used to efficiently manage and retrieve the closest entries
     * during k-nearest neighbor queries.
     */
    static class ValidateEntry {
        Region parentMBR;
        Node node;

        ValidateEntry(Region parentMBR, Node node) {
            this.parentMBR = parentMBR;
            this.node = node;
        }
    }


    /**
     * Represents a data entry in the R-tree, encapsulating its identifier,
     * spatial shape (MBR), and associated node data.
     *
     * <p>This class implements the {@link IData} interface and provides methods
     * to access the entry's identifier, shape, and data.
     */
    public static class Data implements IData {
        int id;
        Region shape;

        public Data(int id, Region mbr) {
            this.id = id;
            this.shape = mbr;
        }

        @Override
        public int getIdentifier() {
            return id;
        }

        @Override
        public IShape getShape() {
            return new Region(shape);
        }

        @Override
        public String toString() {
            return "Data[id=" + id + ", shape=" + (shape == null ? "null" : shape) + "]";
        }
    }
}

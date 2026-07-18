package org.ual.spatialindex.rtreebase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.spatialindex.NodeEntry;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.SpatialIndex;

import java.util.*;

/**
  * An index node in an R-tree spatial indexing structure that manages internal nodes.
  * <p>
  * This class implements the core R-tree indexing logic including:
  * <ul>
  *   <li>Subtree selection for optimal insertion paths</li>
  *   <li>Region insertion with minimum overlap/area increase</li>
  *   <li>Node splitting based on R-tree variant (Linear, Quadratic, R*)</li>
  *   <li>Document-aware indexing for text-spatial queries</li>
  * </ul>
  * <p>
  * Each index node maintains:
  * <ul>
  *   <li>A minimum bounding rectangle (MBR) containing all child nodes</li>
  *   <li>References to child nodes with their respective MBRs</li>
  *   <li>Optional document associations for text-spatial indexing</li>
  * </ul>
  *
  * @see Node
  * @see AbstractRTree
  */
public class Index extends Node {
    private static final Logger logger = LogManager.getLogger(Index.class);  // Logger for this class

    /**
      * Constructs a new Index node for the R-tree.
      * This constructor initializes the node with the given R-tree, identifier, level.
      * The node's capacity is set to the R-tree's indexCapacity value.
      *
      * @param rTree The R-tree instance to which this index belongs
      * @param identifier Unique identifier for this index node
      * @param level The level of this node in the R-tree hierarchy (0 = leaf)
      */
    public Index(AbstractRTree rTree, int identifier, int level) {
        super(rTree, identifier, level, rTree.indexCapacity);
    }


    /**
     * Chooses the optimal subtree node for inserting a given region at a specified level.
     *
     * <p>This method implements the R-tree's ChooseSubtree algorithm, which is critical
     * for maintaining tree quality and query performance.
     *
     * <h3>Algorithm Selection</h3>
     * <ul>
     *   <li><b>R*-tree at level 1:</b> Uses {@link #findLeastOverlap(Region)} to minimize
     *       overlap between sibling leaf nodes, improving point and range query performance.</li>
     *   <li><b>All other cases:</b> Uses {@link #findLeastEnlargement(Region)} to minimize
     *       MBR area expansion, maintaining compact node boundaries.</li>
     * </ul>
     *
     * <h3>Complexity</h3>
     * <ul>
     *   <li>Time: O(h × n) where h = tree height, n = node fanout</li>
     *   <li>Space: O(h) for the path buffer</li>
     * </ul>
     *
     * @param mbr        the minimum bounding region to insert; must not be {@code null}
     * @param level      target insertion level (0 = leaf level)
     * @param pathBuffer stack recording traversal path for subsequent adjustments;
     *                   must not be {@code null}
     * @return the node at the target level optimal for insertion; never {@code null}
     * @throws NullPointerException if {@code mbr} or {@code pathBuffer} is {@code null}
     * @see #findLeastOverlap(Region)
     * @see #findLeastEnlargement(Region)
     */
    public Node chooseSubtree(Region mbr, int level, Stack<Integer> pathBuffer) {
        return chooseSubtree(mbr, level, pathBuffer, null);
    }


    /**
     * Chooses the optimal subtree node for inserting a region with document associations.
     * <p>
     * This method implements the R-tree's ChooseSubtree algorithm with document-aware extensions,
     * selecting insertion paths that balance spatial optimization with textual relevance.
     *
     * <h3>Selection Strategy</h3>
     * The algorithm varies based on the R-tree variant and current tree level:
     * <ul>
     *   <li><b>R*-tree at level 1 (parent of leaves):</b>
     *     <ul>
     *       <li>Uses pure spatial overlap minimization via {@link #findLeastOverlap(Region)}</li>
     *       <li>Ignores document associations to maintain R*-tree spatial properties</li>
     *       <li>Optimizes for point and range query performance</li>
     *     </ul>
     *   </li>
     *   <li><b>All other cases:</b>
     *     <ul>
     *       <li>Uses hybrid spatial-document approach via {@link #findLeastEnlargement(Region, HashSet)}</li>
     *       <li>Balances MBR enlargement with document set similarity</li>
     *       <li>Favors subtrees with similar document associations when provided</li>
     *       <li>Falls back to spatial-only criteria if documents are null</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * <h3>Complexity</h3>
     * <ul>
     *   <li>Time: O(h × n) where h = tree height, n = node fanout</li>
     *   <li>Space: O(h) for the path buffer</li>
     * </ul>
     *
     * @param mbr The minimum bounding region to be inserted; must not be {@code null}
     * @param level The target level for insertion in the tree hierarchy (0 = leaf level)
     * @param pathBuffer Stack recording the traversal path for later adjustments; must not be {@code null}
     * @param documents Set of document IDs associated with this spatial entry (may be {@code null})
     * @return The optimal node at the target level for inserting the region; never {@code null}
     * @throws NullPointerException if {@code mbr} or {@code pathBuffer} is {@code null}
     * @see #findLeastOverlap(Region)
     * @see #findLeastEnlargement(Region, HashSet)
     * @see #chooseSubtree(Region, int, Stack)
     */
    public Node chooseSubtree(Region mbr, int level, Stack<Integer> pathBuffer, HashSet<Integer> documents) {
        if (mbr == null) {
            logger.error("MBR cannot be null when choosing subtree. Node ID: {}, Level: {}", identifier, level);
            throw new IllegalArgumentException("MBR cannot be null");
        }
        if (pathBuffer == null) {
            logger.error("Path buffer cannot be null when choosing subtree. Node ID: {}, Level: {}", identifier, level);
            throw new IllegalArgumentException("Path buffer cannot be null");
        }

        // Early return if we've reached the target level
        if (this.level == level) {
            return this;
        }

        // Record path for tracking traversal
        pathBuffer.push(identifier);

        // Select appropriate child based on tree variant and level
        int childId;
        if (rTree.treeVariant == SpatialIndex.RtreeVariantRstar && this.level == 1) {
            // For R*-tree when pointing to leaves, use pure spatial criteria
            childId = findLeastOverlap(mbr);
        } else {
            if (documents != null) {
                // For all other cases, use hybrid spatial-document criteria
                childId = findLeastEnlargement(mbr, documents);
            } else {
                // If no documents provided, fallback to spatial-only criteria
                childId = findLeastEnlargement(mbr);
            }
        }

        // Continue traversal with selected child
        Node nextNode = rTree.readNode(childId);
        return nextNode.chooseSubtree(mbr, level, pathBuffer, documents);
    }


    /**
      * Splits the current node into two new nodes to accommodate a new entry.
      * <p>
      * The split algorithm varies based on the R-tree variant:
      * <ul>
      *   <li><b>Linear Split:</b> Simple and fast, splits along a single dimension with O(n) complexity</li>
      *   <li><b>Quadratic Split:</b> More sophisticated, minimizes area enlargement with O(n²) complexity</li>
      *   <li><b>R* Split:</b> Most advanced, optimizes multiple criteria like overlap and utilization</li>
      * </ul>
      *
      * <p>The split operation maintains these invariants:
      * <ul>
      *   <li>Both resulting nodes contain at least minEntries entries</li>
      *   <li>The sum of entries in both nodes equals n + 1 (where n was the original count)</li>
      *   <li>The split minimizes the total area of the resulting nodes' MBRs</li>
      * </ul>
      *
      * @param id Unique identifier for the new entry
      * @param mbr Minimum bounding region of the new entry
      * @return Array containing two nodes:
      *         [0] = left node (retains original identifier)
      *         [1] = right node (assigned new identifier)
      * @see SpatialIndex#RtreeVariantLinear
      * @see SpatialIndex#RtreeVariantQuadratic
      * @see SpatialIndex#RtreeVariantRstar
      */
    protected Node[] split(int id, Region mbr) {
            return split(id, mbr, null);
    }


    /**
      * Splits the current node into two new nodes while preserving document associations.
      * This specialized version implements a document-aware split algorithm that maintains
      * both spatial and textual relationships in the R-tree structure.
      *
      * <p>The split process follows these steps:
      * <ol>
      *   <li>Selects split strategy based on R-tree variant:
      *     <ul>
      *       <li>Linear Split: O(n) complexity, basic axis-based distribution</li>
      *       <li>Quadratic Split: O(n²) complexity, minimizes area enlargement</li>
      *       <li>R*-tree Split: Optimizes multiple criteria (overlap, margin, utilization)</li>
      *     </ul>
      *   </li>
      *   <li>Creates two nodes with appropriate capacity allocation</li>
      *   <li>Distributes entries ensuring:
      *     <ul>
      *       <li>Node utilization meets minimum fill requirements</li>
      *       <li>Document references remain consistent</li>
      *       <li>Spatial overlap is minimized</li>
      *       <li>Document sets are properly propagated</li>
      *     </ul>
      *   </li>
      * </ol>
      *
      * @param id Unique identifier for the new entry
      * @param mbr Minimum bounding region of the new entry
      * @param documents Set of document IDs associated with this entry
      * @return A two-element array containing the split nodes where:
      *         <ul>
      *           <li>[0] = Left node (retains original identifier)</li>
      *           <li>[1] = Right node (assigned new identifier)</li>
      *         </ul>
      * @see #split(int, Region)
      * @see SpatialIndex#RtreeVariantLinear
      * @see SpatialIndex#RtreeVariantQuadratic
      * @see SpatialIndex#RtreeVariantRstar
      */
    protected Node[] split(int id, Region mbr, HashSet<Integer> documents) {
        rTree.stats.splits++;

        // Initialize distribution groups with adequate capacity
        ArrayList<Integer> g1 = new ArrayList<>(capacity / 2 + 1);
        ArrayList<Integer> g2 = new ArrayList<>(capacity / 2 + 1);

        // Choose split algorithm based on tree variant
        switch (rTree.treeVariant) {
            case SpatialIndex.RtreeVariantLinear:
            case SpatialIndex.RtreeVariantQuadratic:
                if (documents != null) {
                    rtreeSplit(id, mbr, g1, g2, documents);
                } else {
                    rtreeSplit(id, mbr, g1, g2);
                }
                break;
            case SpatialIndex.RtreeVariantRstar:
                rstarSplit(id, mbr, g1, g2);
                break;
            default:
                throw new IllegalStateException("Unknown RTree variant: " + rTree.treeVariant);
        }

        // Create new nodes for the split result
        Node left = new Index(rTree, identifier, level);
        Node right = new Index(rTree, -1, level);

        // Distribute entries to left node
        for (int entryId : g1) {
            NodeEntry entry = nodeEntries.get(entryId);
            if (entry != null) {
                if (entry.getDocument() != null) {
                    left.insertEntry(entryId, entry.getMBR(), entry.getDocument());
                } else {
                    left.insertEntry(entryId, entry.getMBR());
                }
            }
        }

        // Distribute entries to right node
        for (int entryId : g2) {
            NodeEntry entry = nodeEntries.get(entryId);
            if (entry != null) {
                if (entry.getDocument() != null) {
                    right.insertEntry(entryId, entry.getMBR(), entry.getDocument());
                } else {
                    right.insertEntry(entryId, entry.getMBR());
                }
            }
        }

        // Return the two new nodes
        return new Node[]{left, right};
    }


    /**
      * Finds the child node requiring the least enlargement to accommodate a new region.
      * <p>
      * Implements a critical R-tree optimization heuristic that minimizes tree degradation
      * during insertions by selecting the most space-efficient path. This helps maintain
      * compact node boundaries and reduces query path overlap.
      * <p>
      * Selection process details:
      * <ol>
      *   <li>Iterates through all child nodes calculating potential area increase</li>
      *   <li>Tracks both enlargement required and current node area for tie-breaking</li>
      *   <li>Chooses child with minimal enlargement requirement</li>
      *   <li>On equal enlargement, selects child with smallest existing area</li>
      *   <li>Returns immediately if finding a perfect containment (zero enlargement)</li>
      * </ol>
      * <p>
      * Time complexity: O(n) where n is number of child entries
      *
      * @param region Region to be inserted into the tree
      * @return Identifier of the child node requiring minimal spatial enlargement
      * @see Region#combinedRegion(Region)
      * @see Region#getArea()
      */
    protected int findLeastEnlargement(Region region) {
        double minEnlargement = Double.POSITIVE_INFINITY;
        double minArea = Double.POSITIVE_INFINITY;
        int best = -1;

        for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
            int entryId = entry.getKey();
            Region entryMBR = entry.getValue().getMBR();

            Region combined = entryMBR.combinedRegion(region);
            double currentArea = entryMBR.getArea();
            double enlargement = combined.getArea() - currentArea;

            // Select entry with least enlargement, or with smallest area as tie-breaker
            if (enlargement < minEnlargement || (enlargement == minEnlargement && currentArea < minArea)) {
                minEnlargement = enlargement;
                minArea = currentArea;
                best = entryId;
            }
        }

        return best;
    }


    /**
      * Finds the optimal child node for insertion by combining spatial and document criteria.
      * <p>
      * Selection process uses a weighted hybrid scoring approach:
      * <ul>
      *   <li>Spatial Score: Area enlargement needed to include new region</li>
      *   <li>Document Score: Jaccard similarity between document sets</li>
      * </ul>
      * <p>
      * The final score is calculated as:
      * <pre>
      *    score = (betaArea * enlargement) + ((1 - betaArea) * documentDistance)
      * </pre>
      * where betaArea controls the balance between spatial and textual criteria.
      * Higher betaArea values favor spatial optimization, while lower values prioritize
      * document similarity.
      *
      * @param r The region to be inserted into the tree
      * @param documents Set of document IDs associated with the region (may be null)
      * @return Identifier of the child node minimizing the combined spatial and document score
      * @see #findLeastEnlargement(Region)
      * @see #docDistance(HashSet, HashSet)
      * @see AbstractRTree#getBetaArea()
      */
    protected int findLeastEnlargement(Region r, HashSet<Integer> documents) {
        double minScore = Double.POSITIVE_INFINITY;
        double minEnlargement = Double.POSITIVE_INFINITY;
        double minArea = Double.POSITIVE_INFINITY;
        int best = -1;

        for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
            int entryId = entry.getKey();
            NodeEntry nodeEntry = entry.getValue();

            // Calculate spatial enlargement
            Region combined = nodeEntry.getMBR().combinedRegion(r);
            double enlargement = combined.getArea() - nodeEntry.getMBR().getArea();
            double currentArea = nodeEntry.getMBR().getArea();

            // Calculate combined score based on spatial and document criteria
            double docSimilarity = (documents == null || nodeEntry.getDocument() == null) ? 1.0 :
                    docDistance(documents, nodeEntry.getDocument());
            double score = rTree.getBetaArea() * enlargement +
                    (1 - rTree.getBetaArea()) * docSimilarity;

            // Select entry with best score, using enlargement and area as tie-breakers
            if (score < minScore || (score == minScore && enlargement < minEnlargement) ||
                    (score == minScore && enlargement == minEnlargement && currentArea < minArea)) {
                minScore = score;
                minEnlargement = enlargement;
                minArea = currentArea;
                best = entryId;
            }
        }

        return best;
    }


    /**
      * Finds the leaf node containing a region with a specific identifier, using depth-first traversal.
      * <p>
      * This method recursively searches the R-tree by:
      * <ul>
      *   <li>Examining only child nodes whose MBRs fully contain the target region</li>
      *   <li>Recording the traversal path for potential tree adjustments</li>
      *   <li>Using spatial containment tests to prune the search space</li>
      * </ul>
      * <p>
      * Performance characteristics:
      * <ul>
      *   <li>Best case: O(log n) when target is found in first traversal path</li>
      *   <li>Worst case: O(n) when multiple paths must be explored</li>
      * </ul>
      *
      * @param mbr The target region to locate in the R-tree
      * @param id The unique identifier of the target entry
      * @param pathBuffer A stack tracking the traversal path from root to current node.
      *                   Contains node identifiers in order of visitation.
      *                   Used for tree rebalancing after modifications.
      * @return The leaf node containing the region with matching ID,
      *         or {@code null} if no matching region is found
      * @see Leaf For the structure and operations of leaf nodes
      * @see Region#contains(Region) For the spatial containment test used in traversal
      */
    public Leaf findLeaf(int id, Region mbr, Stack<Integer> pathBuffer) {
        // Record current node in traversal path
        pathBuffer.push(identifier);

        // Examine each child that could contain the target region
        for (NodeEntry entry : nodeEntries.values()) {
            if (entry.getMBR().contains(mbr)) {
                // Read the child node and continue search recursively
                Node childNode = rTree.readNode(entry.getIdentifier());
                Leaf leaf = childNode.findLeaf(id, mbr, pathBuffer);

                // Return immediately if found to avoid unnecessary traversals
                if (leaf != null) {
                    return leaf;
                }
            }
        }

        // Remove this node from path when backtracking
        pathBuffer.pop();
        return null;
    }


    /**
      * Finds the child node that minimizes overlap when inserting a new region in an R*-tree.
      * <p>
      * The selection process consists of three phases:
      * <ol>
      *   <li>Pre-selection (O(n)):
      *     <ul>
      *       <li>Computes enlargement and combined regions for all entries</li>
      *       <li>Uses minimum enlargement as fast path optimization</li>
      *     </ul>
      *   </li>
      *   <li>Sorting (O(n log k)):
      *     <ul>
      *       <li>Orders entries by enlargement where k is nearMinimumOverlapFactor</li>
      *       <li>Identifies top k candidates for detailed analysis</li>
      *     </ul>
      *   </li>
      *   <li>Detailed Analysis (O(k * n)):
      *     <ul>
      *       <li>Calculates precise overlap metrics for top candidates</li>
      *       <li>Uses area as tiebreaker when overlaps are equal</li>
      *     </ul>
      *   </li>
      * </ol>
      *
      * @param region Region to be inserted into the R*-tree
      * @return Index of the child node that minimizes overlap with siblings
      * @see SpatialIndex#RtreeVariantRstar
      */
    protected int findLeastOverlap(Region region) {
        HashMap<Integer, OverlapEntry> entries = new HashMap<>(nodeEntries.size());

        // Pre-selection phase: Find entry with minimum enlargement in linear time
        double minEnlargement = Double.POSITIVE_INFINITY;
        int bestByEnlargement = 0;
        boolean isFirstEntry = true;

        // First pass: Calculate combined region and enlargement for each entry
        for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
            int entryId = entry.getKey();
            NodeEntry nodeEntry = entry.getValue();

            OverlapEntry e = new OverlapEntry();
            e.id = entryId;
            e.original = nodeEntry.getMBR();
            e.combined = nodeEntry.getMBR().combinedRegion(region);
            e.originalArea = e.original.getArea();
            e.combinedArea = e.combined.getArea();
            e.enlargement = e.combinedArea - e.originalArea;
            entries.put(entryId, e);

            // Handle the first entry specially to avoid uninitialized variable issues
            if (isFirstEntry) {
                minEnlargement = e.enlargement;
                bestByEnlargement = entryId;
                isFirstEntry = false;
            }
            // Update minimum enlargement entry (with area as tie-breaker)
            else if (e.enlargement < minEnlargement ||
                    (e.enlargement == minEnlargement && e.originalArea < entries.get(bestByEnlargement).originalArea)) {
                minEnlargement = e.enlargement;
                bestByEnlargement = entryId;
            }
        }

        // Fast path: If enlargement is zero (perfect fit), return immediately
        if (Math.abs(minEnlargement) <= SpatialIndex.EPSILON) {
            return bestByEnlargement;
        }

        // Detailed evaluation phase: Calculate overlap for most promising entries
        double leastOverlap = Double.POSITIVE_INFINITY;
        int bestByOverlap = 0;
        int iterations;
        ArrayList<OverlapEntry> sortedEntries = new ArrayList<>(entries.values());

        // Determine how many entries to evaluate in detail
        if (nodeEntries.size() > rTree.nearMinimumOverlapFactor) {
            // Sort only when we have more entries than our evaluation limit
            sortedEntries.sort(new OverlapEntryComparator());
            iterations = rTree.nearMinimumOverlapFactor;
        } else {
            iterations = nodeEntries.size();
        }

        // Calculate overlap difference for the most promising entries
        for (int index = 0; index < iterations; index++) {
            OverlapEntry e = sortedEntries.get(index);
            double overlapDiff = 0.0;

            // Calculate how much this entry's overlap with others would increase
            for (Map.Entry<Integer, NodeEntry> entry : nodeEntries.entrySet()) {
                int i = entry.getKey();
                NodeEntry nodeEntry = entry.getValue();
                if (e.id != i) {
                    double newOverlap = e.combined.getIntersectingArea(nodeEntry.getMBR());
                    if (newOverlap > 0.0) {
                        overlapDiff += newOverlap - e.original.getIntersectingArea(nodeEntry.getMBR());
                    }
                }
            }

            // Use first entry as initial best value
            if (index == 0) {
                leastOverlap = overlapDiff;
                bestByOverlap = 0;
            }
            // Update best entry if this one has less overlap increase
            else if (overlapDiff < leastOverlap) {
                leastOverlap = overlapDiff;
                bestByOverlap = index;
            }
            // Handle tie-breaking based on secondary criteria
            else if (Math.abs(overlapDiff - leastOverlap) <= SpatialIndex.EPSILON) {
                OverlapEntry current = e;
                OverlapEntry best = sortedEntries.get(bestByOverlap);

                if (current.enlargement < best.enlargement ||
                        (Math.abs(current.enlargement - best.enlargement) <= SpatialIndex.EPSILON &&
                                current.originalArea < best.originalArea)) {
                    bestByOverlap = index;
                }
            }
        }

        return sortedEntries.get(bestByOverlap).id;
    }


    /**
      * Updates this node and its ancestors' MBRs after a child node modification.
      * <p>
      * This method maintains tree consistency through the following steps:
      * <ol>
      *   <li>Updates the modified child node's MBR in this node's entry</li>
      *   <li>Recalculates this node's MBR if impacted by child changes</li>
      *   <li>Updates document reference collections if enabled</li>
      *   <li>Persists changes to storage</li>
      *   <li>Recursively propagates adjustments up the tree hierarchy</li>
      * </ol>
      * <p>
      * MBR recalculation occurs if either condition is met:
      * <ul>
      *   <li>The new child MBR extends beyond this node's current MBR</li>
      *   <li>The old child MBR was coincident with this node's MBR boundaries</li>
      * </ul>
      *
      * @param node The modified child node requiring adjustment
      * @param pathBuffer Stack containing node IDs from root to current node,
      *                   used for recursive propagation of updates
      * @see #recalculateMBR()
      */
    public void adjustTree(Node node, Stack<Integer> pathBuffer) {
        rTree.stats.adjustments++;

        // Find the entry for this child node
        NodeEntry childEntry = nodeEntries.get(node.identifier);
        if (childEntry == null) {
            logger.error("Child node {} not found in parent node {} during adjustment", node.identifier, identifier);
            throw new IllegalStateException("Child node " + node.identifier + " not found in parent");
        }

        // Snapshot old bounds before updating the entry.
        Region oldChildMBR = childEntry.getMBR().clone();

        // Check if MBR recalculation is needed:
        // 1. The new child MBR is not contained in this node's MBR
        // 2. The old child MBR touches the boundary of this node's MBR
        boolean needsRecalculation = !nodeMBR.contains(node.nodeMBR) || nodeMBR.touches(oldChildMBR);

        // Update the child's MBR
        childEntry.setMBR(node.nodeMBR);

        // Update document references if available
        if (rTree.isDocumentAware()) {
            childEntry.setDocument(node.nodeDocuments == null ? new HashSet<>() : new HashSet<>(node.nodeDocuments));
        }

        // Recalculate this node's MBR if necessary
        if (needsRecalculation) {
            recalculateMBR();
        }

        if (rTree.isDocumentAware()) {
            // Update document collection by aggregating from all child entries
            if (nodeDocuments == null) {
                nodeDocuments = new HashSet<>();
            } else {
                nodeDocuments.clear(); // Start fresh to avoid duplicates
            }
            for (NodeEntry entry : nodeEntries.values()) {
                if (entry.getDocument() != null) {
                    nodeDocuments.addAll(entry.getDocument());
                }
            }
        }

        // Write updated node to storage
        rTree.writeNode(this);

        // Continue adjustment up the tree if necessary
        if (needsRecalculation && !pathBuffer.empty()) {
            int parentId = pathBuffer.pop();
            Index parentNode = (Index) rTree.readNode(parentId);
            parentNode.adjustTree(this, pathBuffer);
        }
    }


    /**
      * Adjusts the tree structure after a node split operation to maintain R-tree integrity.
      * This method performs critical post-split operations to ensure the R-tree remains balanced
      * and optimized.
      *
      * <p>The adjustment process includes:
      * <ol>
      *   <li>Updating the existing node's ({@code n1}) MBR to reflect its new contents</li>
      *   <li>Integrating the newly created node ({@code n2}) into the tree structure</li>
      *   <li>Recursively propagating MBR adjustments up the tree hierarchy</li>
      *   <li>Maintaining document reference collections if document-aware indexing is enabled</li>
      *   <li>Managing split cascades through overflow tracking to prevent infinite recursion</li>
      * </ol>
      *
      * <p>The method handles both spatial adjustments (MBR updates) and document-aware
      * operations when the R-tree is configured for text-spatial indexing.
      *
      * @param n1 The original node that was split, retaining its identifier and position in the tree
      * @param n2 The newly created node resulting from the split operation
      * @param pathBuffer Stack containing node identifiers from root to current node for traversal
      * @param overflowTable Map tracking which nodes have previously split to prevent cascading
      * @see #adjustTree(Node, Stack)
      * @see #insertData(int, Region, Stack, HashMap)
      */
    public void adjustTree(Node n1, Node n2, Stack<Integer> pathBuffer, HashMap<Integer, Boolean> overflowTable) {
        rTree.stats.adjustments++;

        // Find n1's entry in this node for efficient updating
        NodeEntry n1Entry = nodeEntries.get(n1.identifier);
        if (n1Entry == null) {
            logger.error("Child node {} not found in parent node {} during split adjustment", n1.identifier, identifier);
            throw new IllegalStateException("Child node " + n1.identifier + " not found in parent");
        }

        // Snapshot old bounds before replacing n1's entry MBR.
        Region oldN1MBR = n1Entry.getMBR().clone();

        // Determine if MBR recalculation is required by checking two conditions:
        // 1. Spatial containment: n1's new MBR extends beyond current node's boundaries
        // 2. Boundary dependency: n1's old MBR was defining the current node's boundary
        boolean needsRecalculation = !nodeMBR.contains(n1.nodeMBR) || nodeMBR.touches(oldN1MBR);

        // Update n1's entry in this node with its post-split MBR
        n1Entry.setMBR(n1.nodeMBR);
        if (rTree.isDocumentAware()) {
            // Update document associations for text-spatial indexing
            n1Entry.setDocument(n1.nodeDocuments == null ? null : new HashSet<>(n1.nodeDocuments));
        }

        // Recompute this node's MBR if spatial properties changed
        if (needsRecalculation) {
            recalculateMBR();
        }

        if (rTree.isDocumentAware()) {
            // Reconstruct this node's document set from updated children
            if (nodeDocuments == null) {
                nodeDocuments = new HashSet<>();
            } else {
                nodeDocuments.clear();  // Reset for clean aggregation
            }
            for (NodeEntry entry : nodeEntries.values()) {
                if (entry.getDocument() != null) {
                    nodeDocuments.addAll(entry.getDocument());  // Aggregate child documents
                }
            }
        }

        // Insert n2 (the new split node) into the tree structure
        // Returns true if parent adjustments were already handled by insertData
        boolean adjusted = insertData(n2.identifier, n2.nodeMBR, pathBuffer, overflowTable);

        // Propagate changes upward if all conditions are met:
        // - insertData didn't already handle parent adjustments
        // - MBR changes occurred at this level
        // - Parent nodes exist in the path
        if (!adjusted && needsRecalculation && !pathBuffer.empty()) {
            int parentId = pathBuffer.pop();
            Index parentNode = (Index) rTree.readNode(parentId);
            parentNode.adjustTree(this, pathBuffer);  // Recursive adjustment
        }
    }


    /**
      * Recalculates this node's Minimum Bounding Rectangle (MBR) to tightly enclose all child MBRs.
      * Called during tree adjustments to maintain the R-tree's spatial hierarchy consistency.
      * <p>
      * The process involves:
      * <ul>
      *   <li>If children exist:
      *     <ul>
      *       <li>Initialize boundaries using the first child's MBR</li>
      *       <li>Expand boundaries by including each remaining child's MBR</li>
      *     </ul>
      *   </li>
      *   <li>If no children exist:
      *     <ul>
      *       <li>Set all coordinates to zero to represent an empty node</li>
      *     </ul>
      *   </li>
      * </ul>
      * <p>
      * Performance characteristics:
      * <ul>
      *   <li>Time complexity: O(n×d) where n = number of children, d = dimensions</li>
      *   <li>Space complexity: O(1) using in-place operations</li>
      * </ul>
      *
      * @see #adjustTree(Node, Stack)
      * @see #adjustTree(Node, Node, Stack, HashMap)
      */
    private void recalculateMBR() {
        // Initialize boundaries with first entry's MBR if available
        if (!nodeEntries.isEmpty()) {
            // Get the first entry to initialize boundaries
            NodeEntry firstEntry = nodeEntries.firstEntry().getValue();
            Region firstMBR = firstEntry.getMBR();

            for (int dim = 0; dim < rTree.dimension; dim++) {
                nodeMBR.setLow(dim, firstMBR.getLow(dim));
                nodeMBR.setHigh(dim, firstMBR.getHigh(dim));
            }

            // Expand boundaries to include remaining entries
            for (NodeEntry entry : nodeEntries.values()) {
                if(entry.getIdentifier() == firstEntry.getIdentifier()) {
                    continue; // Skip the first entry as it's already included
                }
                Region entryMBR = entry.getMBR();
                for (int dim = 0; dim < rTree.dimension; dim++) {
                    nodeMBR.setLow(dim, Math.min(nodeMBR.getLow(dim), entryMBR.getLow(dim)));
                    nodeMBR.setHigh(dim, Math.max(nodeMBR.getHigh(dim), entryMBR.getHigh(dim)));
                }
            }
        } else {
            // Set to infinite region when node has no entries
            nodeMBR = rTree.infiniteRegion.clone();
        }
    }



    //====================================================================================
    //================================= Inner Classes ====================================
    //====================================================================================

    /**
      * A helper class for R*-tree node splitting and overlap calculations that stores
      * metrics about region combinations.
      * <p>
      * This class maintains information about:
      * <ul>
      *   <li>Original and combined regions</li>
      *   <li>Area metrics before and after combination</li>
      *   <li>Enlargement required for region merging</li>
      * </ul>
      * <p>
      * Used primarily in {@link #findLeastOverlap(Region)} to track and compare
      * different insertion possibilities.
      *
      * @see #findLeastOverlap(Region)
      * @see Region#combinedRegion(Region)
      */
    static class OverlapEntry {
        int id;
        double enlargement;
        Region original;
        Region combined;
        double originalArea;
        double combinedArea;
    }


    /**
      * Comparator implementation for sorting {@link OverlapEntry} objects by their enlargement values.
      * <p>
      * This comparator enables efficient sorting of overlap entries during R*-tree node split
      * operations, particularly when selecting the top-k entries for detailed overlap analysis.
      * The comparison is based solely on the {@code enlargement} field, which represents the
      * additional area required when expanding a node to include a new region.
      * <p>
      * Note: This comparator imposes a total ordering that is consistent with equals.
      *
      * @see OverlapEntry
      * @see #findLeastOverlap(Region)
      * @see Comparator
      */
    static class OverlapEntryComparator implements Comparator<OverlapEntry> {
        @Override
        public int compare(OverlapEntry o1, OverlapEntry o2) {
            return Double.compare(o1.enlargement, o2.enlargement);
        }
    }
}

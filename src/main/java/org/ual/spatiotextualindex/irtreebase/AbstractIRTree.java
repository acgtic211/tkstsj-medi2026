package org.ual.spatiotextualindex.irtreebase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.RankingSumMode;
import org.ual.documentindex.SimilarityType;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtree.RTree;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.queries.baseline.AggregateQueryProcessor;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.spatiotextualindex.queries.baseline.RankingQueryProcessor;

import java.util.*;

/**
 * Base class for IR-Tree implementations that combines spatial indexing (R-tree) with
 * textual indexing capabilities. The IR-tree extends the R-tree structure by incorporating
 * inverted files at each node to enable efficient processing of both spatial and keyword-based
 * queries.
 *
 * <p>This class provides core functionality for:
 * <ul>
 *   <li>Spatial indexing through R-tree structure inheritance
 *   <li>Textual indexing through inverted file integration
 *   <li>Combined spatio-textual query processing
 *   <li>Support for boolean and ranking-based keyword search
 *   <li>Various query types including Boolean Range, Boolean KNN, Top-k KNN, GNNK, and SGNNK
 * </ul>
 *
 * <p>The IR-Tree structure maintains inverted files at each node level, allowing efficient
 * pruning based on both spatial proximity and keyword relevance simultaneously. This enables
 * faster query processing compared to sequential filtering approaches.
 *
 * @see RTree
 * @see ISpatioTextualIndex
 * @see IDocumentIndex
 */
public abstract class AbstractIRTree extends RTree implements ISpatioTextualIndex {
    private static final Logger logger = LogManager.getLogger(AbstractIRTree.class);

    private static final int DEFAULT_NUMBER_CLUSTERS = 0;
    private static final double DEFAULT_BETA_AREA = 0.1;

    protected final int numberOfClusters;

    /**
     * Constructs an IRTreeBase instance with the specified properties, storage manager, and dataset parameters.
     * This base constructor initializes the IR-tree infrastructure by setting up both the spatial (R-tree)
     * and textual indexing components.
     *
     * @param propertySet The set of properties defining the configuration of the IR-tree, including:
     *                   - NumberOfClusters: Number of text clusters (default: 0)
     *                   - BetaArea: Area coefficient for node splitting (default: 0.1)
     * @param storageManager The storage manager responsible for handling persistent storage operations
     *                      and managing the tree's node data on disk.
     * @param datasetParameters Parameters describing the dataset characteristics, including:
     *                         - Dimension bounds
     *                         - Maximum distances
     *                         - Statistical information
     * @throws IllegalArgumentException if the NumberOfClusters property is negative
     */
    public AbstractIRTree(PropertySet propertySet, IStorageManager storageManager, DatasetParameters datasetParameters, boolean isDocumentAware) {
        super(propertySet, storageManager, datasetParameters, isDocumentAware);

        this.numberOfClusters = this.getIntegerProperty(propertySet, "NumberOfClusters", i -> i >= 0,
                "Property NumberOfClusters must be >= 0");
    }


    /**
     * Registers default values for properties specific to the IR-tree implementation.
     * This method is called during initialization to set up class-specific defaults
     * like NumberOfClusters and BetaArea that extend the base R-tree properties.
     *
     * @see #defaultValues
     * @see RTree#registerSubclassDefaultValues()
     */
    @Override
    protected void registerSubclassDefaultValues() {
        super.registerSubclassDefaultValues();

        // Register defaults for properties specific to this SubClass
        defaultValues.put("NumberOfClusters", DEFAULT_NUMBER_CLUSTERS);
        defaultValues.put("BetaArea", DEFAULT_BETA_AREA);

        // Example: Subclass might intentionally override a base default, if allowed by design.
        // defaultValues.put("Tolerance", 0.05); // Overriding base default for "Tolerance"

        logger.debug("SubClass specific default values registered.");
    }


    public int getNumberOfClusters() {
        return numberOfClusters;
    }

    // Explicit pass-through keeps query processors decoupled from superclass hierarchy details.
    public Integer getRootIdentifier() {
        return super.getRootIdentifier();
    }


    //==========================================================================================
    //====================================== Query methods =====================================
    //==========================================================================================

    //==========================================================================================
    //================================== GNNK and SGNNK Queries ================================
    //==========================================================================================

    /**
     * Performs a Group Nearest Neighbor with Keywords (GNNK) query using a baseline approach.
     * This algorithm finds top-k objects that minimize the aggregate distance to a group of queries,
     * considering both spatial proximity and textual relevance.
     *
     * <p>The implementation uses a best-first traversal strategy where:
     * <ul>
     *   <li>Nodes are processed in order of increasing aggregate cost</li>
     *   <li>A candidate set of k best results is maintained</li>
     *   <li>Pruning occurs when node costs exceed the kth best result</li>
     * </ul>
     *
     * @param invertedFile Document index used for textual relevance calculation
     * @param gnnkQuery Query object containing multiple sub-queries and aggregation method
     * @param topk Number of results to return
     * @return List of results sorted by ascending aggregate cost (best matches first)
     */
    public List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk) {
        return gnnkBaseline(invertedFile, gnnkQuery, topk, RankingSumMode.defaultMode());
    }

    public List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery,
                                                        int topk, RankingSumMode scoringMode) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        List<AggregateSKNNQuery.Result> results = processor.gnnkBaseline(invertedFile, gnnkQuery, topk, scoringMode);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }


    /**
     * Performs a Subgroup Nearest Neighbor with Keywords (SGNNK) query using a baseline approach.
     * This algorithm finds top-k objects that minimize the aggregate distance to a subgroup of queries,
     * considering both spatial proximity and textual relevance.
     *
     * <p>The implementation uses a best-first traversal strategy where:
     * <ul>
     *   <li>Nodes are processed in order of increasing aggregate cost</li>
     *   <li>A candidate set of k best results is maintained</li>
     *   <li>Pruning occurs when node costs exceed the kth best result</li>
     *   <li>For each node, queries are sorted by cost and the subgroup of lowest-cost queries is selected</li>
     * </ul>
     *
     * @param invertedList Document index used for textual relevance calculation
     * @param sgnnkQuery Query object containing multiple sub-queries, aggregation method, and subgroup size
     * @param topk Number of results to return
     * @return List of results sorted by ascending aggregate cost (best matches first), where each result
     *         contains an object ID, its aggregate cost, and the IDs of the subgroup queries used
     */
    public List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
        return sgnnkBaseline(invertedList, sgnnkQuery, topk, RankingSumMode.defaultMode());
    }

    public List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery,
                                                         int topk, RankingSumMode scoringMode) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        List<AggregateSKNNQuery.Result> results = processor.sgnnkBaseline(invertedList, sgnnkQuery, topk, scoringMode);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }


    /**
     * Performs a Group Nearest Neighbor with Keywords (GNNK) query using an efficient best-first traversal approach.
     * This algorithm finds the top-k objects that minimize the aggregate distance to a group of queries,
     * considering both spatial proximity and textual relevance simultaneously.
     *
     * <p>Key features of this implementation:
     * <ul>
     *   <li>Uses a min-heap priority queue to efficiently traverse the tree in best-first order</li>
     *   <li>Computes aggregate costs across all queries for each node/object</li>
     *   <li>Applies early termination when enough results have been found</li>
     *   <li>Preserves query identifiers for each result to enable traceability</li>
     * </ul>
     *
     * <p>This approach is more efficient than the baseline implementation ({@link #gnnkBaseline})
     * as it properly maintains the search frontier and avoids unnecessary node visits.
     *
     * @param invertedList Document index used for textual relevance calculation
     * @param gnnkQuery Query object containing multiple sub-queries, their weights, and aggregation method
     * @param topk Number of results to return (k value)
     * @return List of results sorted by ascending aggregate cost (best matches first)
     * @see AggregateSKNNQuery
     * @see NNEntry
     */
    public List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedList, AggregateSKNNQuery gnnkQuery, int topk) {
        return gnnk(invertedList, gnnkQuery, topk, RankingSumMode.defaultMode());
    }

    public List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedList, AggregateSKNNQuery gnnkQuery,
                                                int topk, RankingSumMode scoringMode) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        List<AggregateSKNNQuery.Result> results = processor.gnnk(invertedList, gnnkQuery, topk, scoringMode);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }


    /**
     * Performs a Subgroup Nearest Neighbor with Keywords (SGNNK) query using a best-first traversal approach.
     * This algorithm finds top-k objects that minimize the aggregate distance to a selected subgroup of queries,
     * considering both spatial proximity and textual relevance.
     *
     * <p>The implementation:
     * <ul>
     *   <li>Uses a priority queue to traverse the tree in best-first order</li>
     *   <li>For each node, selects the subgroup of queries with lowest costs</li>
     *   <li>Maintains the aggregate cost for each potential result</li>
     *   <li>Returns results sorted by ascending aggregate cost (best matches first)</li>
     * </ul>
     *
     * @param invertedList Document index used for textual relevance calculation
     * @param sgnnkQuery Query object containing multiple sub-queries, aggregation method, and subgroup size
     * @param topk Number of results to return
     * @return List of results sorted by ascending aggregate cost (best matches first)
     * @see AggregateSKNNQuery.Result
     */
    public List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
        return sgnnk(invertedList, sgnnkQuery, topk, RankingSumMode.defaultMode());
    }

    public List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery,
                                                 int topk, RankingSumMode scoringMode) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        List<AggregateSKNNQuery.Result> results = processor.sgnnk(invertedList, sgnnkQuery, topk, scoringMode);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }


    /**
     * Performs an extended Subgroup Nearest Neighbor with Keywords (SGNNK) query that finds top-k objects
     * for all possible subgroup sizes between the specified minimum and maximum.
     *
     * <p>This extended implementation:
     * <ul>
     *   <li>Computes results for all subgroup sizes from subGroupSize to groupSize in one traversal</li>
     *   <li>Uses a best-first traversal strategy to minimize node accesses</li>
     *   <li>Applies aggressive pruning based on the worst result in each subgroup size's result set</li>
     *   <li>Returns a map of results organized by subgroup size</li>
     * </ul>
     *
     * @param invertedList Document index used for textual relevance calculation
     * @param sgnnkQuery Query object containing multiple sub-queries, aggregation method, and subgroup parameters
     * @param topk Number of results to return for each subgroup size
     * @return Map where:
     *         - Key: Subgroup size (from sgnnkQuery.subGroupSize to sgnnkQuery.groupSize)
     *         - Value: List of results for that subgroup size, sorted by ascending aggregate cost (best first)
     */
    public Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
        return sgnnkExtended(invertedList, sgnnkQuery, topk, RankingSumMode.defaultMode());
    }

    public Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedList,
                                                                       AggregateSKNNQuery sgnnkQuery,
                                                                       int topk,
                                                                       RankingSumMode scoringMode) {
        AggregateQueryProcessor processor = new AggregateQueryProcessor(this);
        Map<Integer, List<AggregateSKNNQuery.Result>> results = processor.sgnnkExtended(invertedList, sgnnkQuery, topk, scoringMode);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }


    //==========================================================================================
    //================================== Boolean Range and KNN Queries =========================
    //==========================================================================================

    /**
     * Performs a Boolean Range Query (BRQ) using a best-first traversal approach.
     * This algorithm finds all objects that match a set of keywords within a specified spatial radius.
     *
     * <p>The implementation uses a priority queue to traverse the R-tree in best-first order,
     * ensuring efficient access to relevant nodes while applying keyword filters at each step.
     *
     * @param invertedList Document index used for keyword filtering
     * @param query SKNNQuery object containing keywords and spatial location
     * @param radius Search radius for spatial proximity
     * @return List of results sorted by ascending distance, where each result contains an object ID and its distance
     */
    public abstract List<SKNNQuery.Result> booleanRangeQuery(IDocumentIndex invertedList, SKNNQuery query, float radius);


    /**
     * Performs a Boolean KNN Query (BKQ) to find the top-k spatial objects that contain all query keywords.
     * This algorithm uses a best-first traversal approach to efficiently retrieve objects based on spatial
     * proximity while ensuring they satisfy the keyword constraints.
     *
     * @param invertedList Document index used for keyword filtering
     * @param query SKNNQuery object containing query location and keywords
     * @param topk Number of results to return
     * @return List of results sorted by ascending distance
     */
    public abstract List<SKNNQuery.Result> booleanKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk);


    /**
     * Performs a Top-k Keyword Nearest Neighbor (Top-k KNN) query to find spatially closest objects
     * that are also textually relevant to the query keywords.
     *
     * <p>This implementation uses a best-first traversal approach where:
     * <ul>
     *   <li>Nodes are processed in order of increasing combined cost (spatial + textual)</li>
     *   <li>Pruning occurs when enough results are found or when minimum distance exceeds current threshold</li>
     *   <li>Both spatial proximity and textual relevance are considered in the ranking</li>
     * </ul>
     *
     * @param invertedList Document index used for textual relevance calculation
     * @param query Query object containing spatial location and keywords
     * @param topk Number of results to return
     * @return List of results sorted by ascending combined cost (best matches first)
     */
    public List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk) {
        return topkKnnQuery(invertedList, query, topk, RankingSumMode.defaultMode());
    }

    public List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk,
                                               RankingSumMode scoringMode) {
        RankingQueryProcessor processor = new RankingQueryProcessor(this);
        List<SKNNQuery.Result> results = processor.topkKnnQuery(invertedList, query, topk, scoringMode);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }


    //==========================================================================================
    //====================================== JOIN SK Queries ===================================
    //==========================================================================================

    /**
     * Performs a Self-Join SK Query using a best-first traversal approach.
     * This algorithm finds pairs of objects that are spatially close and textually similar,
     * based on configurable thresholds for both dimensions.
     *
     * <p>Key features of this implementation:
     * <ul>
     *   <li>Uses a priority queue for best-first traversal of the R-tree</li>
     *   <li>Maintains a set to track processed pairs to avoid duplicates</li>
     *   <li>Applies both spatial and textual thresholds during pruning</li>
     *   <li>Supports different threshold adjustment strategies</li>
     * </ul>
     *
     * @param invertedList Document index used for textual relevance calculation
     * @param query SKJoinQuery object containing keywords and query parameters
     * @param spatialThreshold Maximum spatial distance threshold for joining objects
     * @param textualThreshold Minimum textual similarity threshold for joining objects
     * @param joinConfiguration Join policy bundle (threshold, join, similarity, query strategy)
     * @return List of result pairs sorted by ascending combined cost (best matches first)
     */
    public abstract List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex invertedList, SKJoinQuery query,
                                                              float spatialThreshold, float textualThreshold,
                                                               JoinConfiguration joinConfiguration);



    //==========================================================================================
    //=================================== Recursive JOIN =======================================
    //==========================================================================================

    /**
     * Performs a recursive self-join SK query using a depth-first traversal strategy.
     * This method explores the IR-tree recursively, processing pairs of nodes and applying
     * spatial and textual filtering to find matching object pairs.
     *
     * <p>Key features:
     * <ul>
     *   <li>Uses depth-first traversal for memory efficiency</li>
     *   <li>Applies early filtering based on both spatial and textual thresholds</li>
     *   <li>Leverages inverted files for optimized textual similarity checks</li>
     *   <li>Handles both spatial proximity and keyword matching requirements</li>
     * </ul>
     *
     * @param invertedList Document index for calculating textual relevance scores
     * @param query Query parameters including keywords and their weights
     * @param spatialThreshold Maximum allowed spatial distance between object pairs
     * @param textualThreshold Minimum required textual similarity between pairs
     * @param joinConfiguration Join policy bundle (threshold, join, similarity, query strategy)
     * @return List of matching object pairs sorted by combined similarity score
     */
    public abstract List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex invertedList, SKJoinQuery query,
                                                              float spatialThreshold, float textualThreshold,
                                                               JoinConfiguration joinConfiguration);


    //==========================================================================================
    //====================== (Legacy) Location-based Keyword Top-k Queries =====================
    //==========================================================================================

    /**
     * Performs a Location-based Keyword Top-k (LKT) query to find the k nearest objects that match both spatial
     * and keyword criteria. This algorithm uses a best-first traversal strategy with a priority queue to efficiently
     * process both spatial proximity and keyword relevance simultaneously.
     *
     * <p>The algorithm combines spatial distance and keyword relevance using a weighted scoring function:
     * - Spatial score is normalized by the maximum distance in the dataset
     * - Keyword relevance is calculated using the inverted file index
     * - Final score is computed using alphaDistribution parameter to balance spatial and textual relevance
     *
     * @param invertedFile The inverted file index that stores keyword information for each node
     * @param query The query object containing both the spatial location and keywords
     * @param topk The number of nearest neighbors to return
     * @return List of results sorted by ascending combined cost (best matches first)
     */
    public List<SKNNQuery.Result> lkt(IDocumentIndex invertedFile, SKNNQuery query, int topk) {
        return lkt(invertedFile, query, topk, RankingSumMode.defaultMode());
    }

    public List<SKNNQuery.Result> lkt(IDocumentIndex invertedFile, SKNNQuery query, int topk,
                                      RankingSumMode scoringMode) {
        RankingQueryProcessor processor = new RankingQueryProcessor(this);
        List<SKNNQuery.Result> results = processor.lkt(invertedFile, query, topk, scoringMode);
        this.numOfVisitedNodes = processor.getVisitedNodes();
        return results;
    }

    //==========================================================================================
    //============================= Auxiliary Methods ==========================================
    //==========================================================================================

    /**
     * Calculates a combined score that balances spatial proximity and keyword relevance.
     * Lower scores indicate better matches (closer to the query point and more textually relevant).
     *
     * @param spatial Spatial distance between query location and indexed object
     * @param ir Information retrieval relevance score, where higher values (0-1) indicate better keyword matches
     * @return Combined score between 0 and 1, with lower values indicating better overall relevance
     */
    public double combinedScore(double spatial, double ir) {
        double maxDist = datasetParameters.maxEuclideanDistance;
        if (maxDist <= 0.0 || Double.isNaN(maxDist)) {
            return 1.0 - ir;
        }

        double a = alphaDistribution;
        double spatialCost = spatial / maxDist;
        double keywordMismatchCost = 1.0 - ir;

        return a * spatialCost + (1.0 - a) * keywordMismatchCost;
    }

    public Map<Integer, Double> calculatePairTextualRelevancy(int objectId1, int objectId2, IDocumentIndex invertedFile, List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType) {
        if (numberOfClusters > 0) {
            return invertedFile.nodesConstraintTextualSimForClusters(objectId1, objectId2, keywords, keywordWeights, similarityType);
        } else {
            return invertedFile.nodesConstraintTextualSim(objectId1, objectId2, keywords, keywordWeights, similarityType);
        }
    }

    /**
     * Returns the number of nodes visited during the last query operation.
     * This is useful for performance analysis and comparing different query algorithms.
     *
     * @return The count of nodes that were accessed during query processing
     */
    @Override
    public int getVisitedNodes() {
        return numOfVisitedNodes;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Append basic tree parameters
        sb.append("Dimension: ").append(dimension).append('\n')
                .append("Fill factor: ").append(fillFactor).append('\n')
                .append("Index capacity: ").append(indexCapacity).append('\n')
                .append("Leaf capacity: ").append(leafCapacity).append('\n');

        // Append R*-tree specific parameters if applicable
        if (treeVariant == SpatialIndex.RtreeVariantRstar) {
            sb.append("Near minimum overlap factor: ").append(nearMinimumOverlapFactor).append('\n')
                    .append("Reinsert factor: ").append(reinsertFactor).append('\n')
                    .append("Split distribution factor: ").append(splitDistributionFactor).append('\n')
                    .append("Alpha distribution: ").append(alphaDistribution).append('\n');
        }

        // Append IRTree specific parameters
        sb.append("Number of clusters: ").append(numberOfClusters).append('\n')
                .append("Tree variant: ").append(SpatialIndex.getTreeVariantString(treeVariant)).append('\n');

        // Calculate and append utilization percentage, avoiding division by zero
        long leafNodes = stats.getNumberOfNodesInLevel(0);
        String utilization = (leafNodes > 0)
                ? String.format("%.1f%%", 100.0 * stats.getNumberOfData() / ((double) leafNodes * leafCapacity))
                : "N/A";
        sb.append("Utilization: ").append(utilization).append('\n')
                .append(stats);

        return sb.toString();
    }

    public String printTreeStructure() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tree Structure:\n");
        sb.append("==============\n");

        if (rootID == -1) {
            sb.append("Empty tree\n");
            return sb.toString();
        }

        // Use BFS to traverse tree level by level
        Queue<Integer> nodeQueue = new LinkedList<>();
        nodeQueue.offer(rootID);

        int currentLevel = -1;

        while (!nodeQueue.isEmpty()) {
            int nodeId = nodeQueue.poll();
            Node node = readNode(nodeId);
            int level = node.getLevel();

            // Print level header when we enter a new level
            if (level != currentLevel) {
                currentLevel = level;
                sb.append("\nLevel ").append(level).append(":\n");
                sb.append("--------\n");
            }

            sb.append("Node ID: ").append(nodeId);
            sb.append(" (").append(node.isLeaf() ? "Leaf" : "Internal").append(")");
            sb.append(" Children: ").append(node.getNodeEntriesSize());
            sb.append("\n");

            // Print children information using new TreeMap structure
            int childIndex = 0;
            for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                Integer entryId = entry.getKey();
                NodeEntry nodeEntry = entry.getValue();

                sb.append("  Child[").append(childIndex).append("]: ID=").append(entryId);

                if (node.isLeaf()) {
                    // For leaf nodes, show data object MBR
                    Region dataMBR = nodeEntry.getMBR();
                    sb.append(" DataMBR=").append(dataMBR);

                    // Show document information if available
                    if (nodeEntry.getDocument() != null && !nodeEntry.getDocument().isEmpty()) {
                        sb.append(" Documents=").append(nodeEntry.getDocument().size());
                    }
                } else {
                    // For internal nodes, show child MBR and add to queue for next level
                    Region childMBR = nodeEntry.getMBR();
                    sb.append(" MBR=").append(childMBR);

                    // Show document information if available
                    if (nodeEntry.getDocument() != null && !nodeEntry.getDocument().isEmpty()) {
                        sb.append(" Documents=").append(nodeEntry.getDocument().size());
                    }

                    nodeQueue.offer(entryId);
                }
                sb.append("\n");
                childIndex++;
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}

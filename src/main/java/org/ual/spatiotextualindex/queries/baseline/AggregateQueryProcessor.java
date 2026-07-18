package org.ual.spatiotextualindex.queries.baseline;

import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.RankingSumMode;
import org.ual.query.Query;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.Cost;
import org.ual.spatialindex.spatialindex.NNEntry;
import org.ual.spatialindex.spatialindex.NodeEntry;
import org.ual.spatialindex.spatialindex.RtreeEntry;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.AbstractQueryProcessor;
import org.ual.spatiotextualindex.queries.IAggregateQueryProcessor;

import java.util.*;

public class AggregateQueryProcessor extends AbstractQueryProcessor implements IAggregateQueryProcessor {

    public AggregateQueryProcessor(AbstractIRTree tree) {
        super(tree);
    }

//    public List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk) {
//        return gnnkBaseline(invertedFile, gnnkQuery, topk, RankingSumMode.defaultMode());
//    }
//
//    public List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedList, AggregateSKNNQuery gnnkQuery, int topk) {
//        return gnnk(invertedList, gnnkQuery, topk, RankingSumMode.defaultMode());
//    }
//
//    public List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
//        return sgnnkBaseline(invertedList, sgnnkQuery, topk, RankingSumMode.defaultMode());
//    }
//
//    public List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk) {
//        return sgnnk(invertedList, sgnnkQuery, topk, RankingSumMode.defaultMode());
//    }
//
//    public Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedList,
//                                                                       AggregateSKNNQuery sgnnkQuery,
//                                                                       int topk) {
//        return sgnnkExtended(invertedList, sgnnkQuery, topk, RankingSumMode.defaultMode());
//    }

    private static double safeQueueThreshold(PriorityQueue<AggregateSKNNQuery.Result> queue) {
        if (queue == null || queue.isEmpty()) {
            return Double.MAX_VALUE;
        }
        AggregateSKNNQuery.Result peek = queue.peek();
        if (peek == null || peek.getAggregateCost() == null) {
            return Double.MAX_VALUE;
        }
        return peek.getAggregateCost().getCombinedCost();
    }

    private static double safeExtendedThreshold(Map<Integer, PriorityQueue<AggregateSKNNQuery.Result>> subgroupResults) {
        if (subgroupResults == null || subgroupResults.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double liveThreshold = Double.NEGATIVE_INFINITY;
        for (PriorityQueue<AggregateSKNNQuery.Result> queue : subgroupResults.values()) {
            liveThreshold = Math.max(liveThreshold, safeQueueThreshold(queue));
        }

        return Double.isFinite(liveThreshold) ? liveThreshold : Double.MAX_VALUE;
    }


    private int[] selectLowestCostQueryIndices(List<Cost> queryCosts, int selectionSize) {
        final int targetSize = Math.min(selectionSize, queryCosts.size());
        if (targetSize <= 0) {
            return new int[0];
        }

        int[] selectedIndices = new int[targetSize];
        double[] selectedCosts = new double[targetSize];
        int selectedCount = 0;

        for (int queryIndex = 0; queryIndex < queryCosts.size(); queryIndex++) {
            final double combinedCost = queryCosts.get(queryIndex).getCombinedCost();

            if (selectedCount == 0) {
                selectedIndices[0] = queryIndex;
                selectedCosts[0] = combinedCost;
                selectedCount = 1;
                continue;
            }

            if (selectedCount < targetSize || combinedCost < selectedCosts[selectedCount - 1]) {
                int insertAt = Math.min(selectedCount, targetSize - 1);
                while (insertAt > 0 && combinedCost < selectedCosts[insertAt - 1]) {
                    if (insertAt < targetSize) {
                        selectedIndices[insertAt] = selectedIndices[insertAt - 1];
                        selectedCosts[insertAt] = selectedCosts[insertAt - 1];
                    }
                    insertAt--;
                }

                selectedIndices[insertAt] = queryIndex;
                selectedCosts[insertAt] = combinedCost;

                if (selectedCount < targetSize) {
                    selectedCount++;
                }
            }
        }

        return selectedIndices;
    }


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
    @Override
    public List<AggregateSKNNQuery.Result> gnnkBaseline(IDocumentIndex invertedFile, AggregateSKNNQuery gnnkQuery, int topk,
                                                        RankingSumMode scoringMode) {
        if (topk <= 0) {
            return Collections.emptyList();
        }

        numOfVisitedNodes = 0;
        final List<Double> queryWeights = gnnkQuery.getWeights();
        // Processing queue for the tree traversal (priority queue for best-first)
        PriorityQueue<NNEntry> searchQueue = new PriorityQueue<>(Comparator.comparingDouble(e -> e.cost.getCombinedCost()));
        NNEntry rootEntry = new NNEntry(new RtreeEntry(tree.getRootIdentifier(), false), new Cost(0, 0, 0));
        searchQueue.add(rootEntry);

        // Priority queue to maintain the k best objects found so far (highest cost at top)
        PriorityQueue<AggregateSKNNQuery.Result> candidateResults =
                new PriorityQueue<>(topk, new WorstFirstNNEntryComparator());

        // Initialize with dummy objects having maximum cost
        for (int i = 0; i < topk; i++) {
            candidateResults.add(new AggregateSKNNQuery.Result(-1, new Cost(0, 0, Double.MAX_VALUE)));
        }

        // Threshold for pruning - initially set to maximum
        double pruningThreshold = Double.MAX_VALUE;

        while (!searchQueue.isEmpty()) {
            NNEntry currentEntry = searchQueue.poll();
            RtreeEntry rTreeEntry = (RtreeEntry) currentEntry.entry;

            // Early pruning: skip if cost exceeds current kth best result
            if (currentEntry.cost.getCombinedCost() > pruningThreshold) {
                continue;
            }

            Node currentNode = readNode(rTreeEntry.getIdentifier());
            numOfVisitedNodes++;

            HashMap<Integer, List<Cost>> childCosts = calculateQueryCosts(
                    invertedFile,
                    gnnkQuery.queries,
                    currentNode,
                    scoringMode,
                    pruningThreshold
            );

            // Process each child node entry using the new TreeMap structure
            int childIndex = 0;
            for (Map.Entry<Integer, NodeEntry> nodeEntryPair : currentNode.getNodeEntries().entrySet()) {
                Integer entryId = nodeEntryPair.getKey();

                List<Cost> queryCostsForChild = childCosts.get(childIndex);
                if (queryCostsForChild == null || queryCostsForChild.isEmpty()) {
                    childIndex++;
                    continue;
                }

                Cost aggregateCost = gnnkQuery.aggregator.getAggregateValue(queryCostsForChild, queryWeights);

                if (currentNode.getLevel() == 0) {  // Leaf node - data entry
                    // Add to candidate results and remove worst entry
                    candidateResults.add(new AggregateSKNNQuery.Result(entryId, aggregateCost));
                    candidateResults.poll();
                    assert candidateResults.peek() != null;
                    pruningThreshold = candidateResults.peek().getAggregateCost().getCombinedCost();
                } else {  // Non-leaf node - add to search queue for further processing
                    // Only add if cost doesn't exceed current threshold
                    if (aggregateCost.getCombinedCost() <= pruningThreshold) {
                        RtreeEntry childEntry = new RtreeEntry(entryId, false);
                        NNEntry queueEntry = new NNEntry(childEntry, aggregateCost);
                        searchQueue.add(queueEntry);  // Use add() for proper priority queue behavior
                    }
                }
                childIndex++;
            }
        }

        // Convert results to list and sort by ascending cost (best first)
        List<AggregateSKNNQuery.Result> finalResults = new ArrayList<>(candidateResults);
        Collections.sort(finalResults);

        return finalResults;
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
    @Override
    public List<AggregateSKNNQuery.Result> gnnk(IDocumentIndex invertedList, AggregateSKNNQuery gnnkQuery, int topk,
                                                RankingSumMode scoringMode) {
        if (topk <= 0) {
            return Collections.emptyList();
        }

        numOfVisitedNodes = 0;
        int filtered = 0;
        final List<Double> queryWeights = gnnkQuery.getWeights();
        // Use a min-heap priority queue for best-first traversal
        PriorityQueue<NNEntry> queue = new PriorityQueue<>();
        List<AggregateSKNNQuery.Result> results = new ArrayList<>();

        // Pre-calculate query IDs once for reuse throughout the tree traversal
        List<Integer> allQueryIds = null;
        if (gnnkQuery.queries != null && !gnnkQuery.queries.isEmpty()) {
            allQueryIds = new ArrayList<>(gnnkQuery.queries.size());
            for (Query query : gnnkQuery.queries) {
                allQueryIds.add(query.getId());
            }
        }

        // Initialize search with root node
        queue.add(new NNEntry(new RtreeEntry(tree.getRootIdentifier(), false), new Cost(0, 0, 0)));

        // Process nodes in best-first order until we have enough results or queue is empty
        while (!queue.isEmpty() && results.size() < topk) {
            NNEntry currentEntry = queue.poll();
            RtreeEntry rTreeEntry = (RtreeEntry) currentEntry.entry;

            if (rTreeEntry.isLeafEntry) {
                // We've reached a data object - add it to results
                results.add(new AggregateSKNNQuery.Result(
                        currentEntry.entry.getIdentifier(),
                        currentEntry.cost,
                        currentEntry.queryIndices
                ));
            } else {
                // We're at an internal node - process its children
                Node node = readNode(rTreeEntry.getIdentifier());
                numOfVisitedNodes++;

                HashMap<Integer, List<Cost>> childCosts = calculateQueryCosts(invertedList, gnnkQuery.queries, node, scoringMode);

                // Enqueue each child with its aggregate cost using TreeMap entries
                int childIndex = 0;
                for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                    Integer entryId = entry.getKey();

                    List<Cost> queryCosts = childCosts.get(childIndex);
                    if (queryCosts == null || queryCosts.isEmpty()) {
                        childIndex++;
//                        filtered++;
                        continue;
                    }
                    Cost aggregateCost = gnnkQuery.aggregator.getAggregateValue(queryCosts, queryWeights);

                    // Determine if this child is a leaf entry
                    boolean isLeafEntry = node.getLevel() == 0;

                    // Create entry and add to queue
                    RtreeEntry childEntry = new RtreeEntry(entryId, isLeafEntry);
                    queue.add(new NNEntry(childEntry, allQueryIds, aggregateCost));

                    childIndex++;
                }
            }
        }

        //TODO Remove
//        System.out.println("GNNK");
//        invertedList.printStatistics();
//        System.out.println("Filtered Nodes (real): " + filtered);

        // Return results sorted by cost (best matches first)
        Collections.sort(results);
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
    @Override
    public List<AggregateSKNNQuery.Result> sgnnkBaseline(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk,
                                                         RankingSumMode scoringMode) {
        if (topk <= 0) {
            return Collections.emptyList();
        }

        numOfVisitedNodes = 0;
        // Processing queue for tree traversal
        LinkedList<NNEntry> searchQueue = new LinkedList<>();
        searchQueue.add(new NNEntry(new RtreeEntry(tree.getRootIdentifier(), false), new Cost(0, 0, 0)));

        // Priority queue to maintain the k best objects (highest cost at top for easy removal)
        PriorityQueue<AggregateSKNNQuery.Result> candidateResults =
                new PriorityQueue<>(topk, new WorstFirstNNEntryComparator());

        // Initialize with dummy objects having maximum cost
        for (int i = 0; i < topk; i++) {
            candidateResults.add(new AggregateSKNNQuery.Result(-1, new Cost(0, 0, Double.MAX_VALUE), null));
        }

        // Threshold for pruning - initially set to maximum
        double pruningThreshold = Double.MAX_VALUE;

        while (!searchQueue.isEmpty()) {
            NNEntry currentEntry = searchQueue.poll();

            // Early pruning: skip if cost exceeds current kth best result
            if (currentEntry.cost.getCombinedCost() > pruningThreshold) {
                continue;
            }

            RtreeEntry rTreeEntry = (RtreeEntry) currentEntry.entry;
            Node currentNode = readNode(rTreeEntry.getIdentifier());
            numOfVisitedNodes++;

            // Calculate costs for all queries against all children of current node
            HashMap<Integer, List<Cost>> childCosts = calculateQueryCosts(
                    invertedList,
                    sgnnkQuery.queries,
                    currentNode,
                    scoringMode,
                    pruningThreshold
            );

            // Process each child entry using TreeMap iteration
            int childIndex = 0;
            for (Map.Entry<Integer, NodeEntry> nodeEntryPair : currentNode.getNodeEntries().entrySet()) {
                Integer childId = nodeEntryPair.getKey();
                List<Cost> queryCostsForChild = childCosts.get(childIndex);
                if (queryCostsForChild == null || queryCostsForChild.isEmpty()) {
                    childIndex++;
                    continue;
                }

                int[] sortedQueryIndices = selectLowestCostQueryIndices(queryCostsForChild, sgnnkQuery.subGroupSize);

                // Select the subgroup of queries with lowest costs
                List<Cost> selectedQueryCosts = new ArrayList<>(sgnnkQuery.subGroupSize);
                List<Integer> selectedQueryIds = new ArrayList<>(sgnnkQuery.subGroupSize);
                List<Double> selectedQueryWeights = new ArrayList<>(sgnnkQuery.subGroupSize);

                for (int i = 0; i < sgnnkQuery.subGroupSize; i++) {
                    int queryIndex = sortedQueryIndices[i];
                    selectedQueryCosts.add(queryCostsForChild.get(queryIndex));
                    selectedQueryIds.add(sgnnkQuery.queries.get(queryIndex).getId());
                    selectedQueryWeights.add(sgnnkQuery.queries.get(queryIndex).getWeight());
                }

                // Calculate aggregate cost for the selected subgroup
                Cost aggregateCost = sgnnkQuery.aggregator.getAggregateValue(selectedQueryCosts, selectedQueryWeights);

                if (currentNode.getLevel() == 0) {  // Leaf node - data entry
                    // Add to candidate results and remove worst entry
                    candidateResults.add(new AggregateSKNNQuery.Result(childId, aggregateCost, selectedQueryIds));
                    candidateResults.poll();
                    assert candidateResults.peek() != null;
                    pruningThreshold = candidateResults.peek().getAggregateCost().getCombinedCost();
                } else {  // Non-leaf node - add to search queue for further processing
                    searchQueue.addFirst(new NNEntry(new RtreeEntry(childId, false), selectedQueryIds, aggregateCost));
                }

                childIndex++;
            }
        }

        // Convert results to list and sort by ascending cost (best first)
        List<AggregateSKNNQuery.Result> finalResults = new ArrayList<>(candidateResults);
        Collections.sort(finalResults);

        return finalResults;
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
    @Override
    public List<AggregateSKNNQuery.Result> sgnnk(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery, int topk,
                                                 RankingSumMode scoringMode) {
        if (topk <= 0) {
            return Collections.emptyList();
        }

        numOfVisitedNodes = 0;
        int filtered = 0;
        // Min-heap priority queue for best-first traversal
        PriorityQueue<NNEntry> queue = new PriorityQueue<>();
        queue.add(new NNEntry(new RtreeEntry(tree.getRootIdentifier(), false), new Cost(0, 0, 0)));

        List<AggregateSKNNQuery.Result> results = new ArrayList<>();

        while (!queue.isEmpty() && results.size() < topk) {
            NNEntry currentEntry = queue.poll();
            RtreeEntry rTreeEntry = (RtreeEntry) currentEntry.entry;

            if (rTreeEntry.isLeafEntry) {
                // We've found a leaf entry - add it to results
                results.add(new AggregateSKNNQuery.Result(
                        currentEntry.entry.getIdentifier(),
                        currentEntry.cost,
                        currentEntry.queryIndices
                ));
            } else {
                // Process internal node
                Node node = readNode(rTreeEntry.getIdentifier());
                numOfVisitedNodes++;

                // Get costs for all queries against all children
                HashMap<Integer, List<Cost>> childCosts = calculateQueryCosts(invertedList, sgnnkQuery.queries, node, scoringMode);

                // Process each child using proper index-based access
                int childIndex = 0;
                for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                    int childId = entry.getKey();
                    List<Cost> queryCosts = childCosts.get(childIndex);
                    if (queryCosts == null || queryCosts.isEmpty()) {
                        childIndex++;
//                        filtered++;
                        continue;
                    }

                    int[] queryIndices = selectLowestCostQueryIndices(queryCosts, sgnnkQuery.subGroupSize);

                    // Select subgroup of m queries with lowest costs
                    int subGroupSize = sgnnkQuery.subGroupSize;
                    List<Cost> selectedCosts = new ArrayList<>(subGroupSize);
                    List<Integer> selectedQueryIds = new ArrayList<>(subGroupSize);
                    List<Double> selectedWeights = new ArrayList<>(subGroupSize);

                    for (int i = 0; i < subGroupSize; i++) {
                        int queryIndex = queryIndices[i];
                        selectedCosts.add(queryCosts.get(queryIndex));
                        selectedQueryIds.add(sgnnkQuery.queries.get(queryIndex).getId());
                        selectedWeights.add(sgnnkQuery.queries.get(queryIndex).getWeight());
                    }

                    // Calculate aggregate cost for this subgroup
                    Cost aggregateCost = sgnnkQuery.aggregator.getAggregateValue(selectedCosts, selectedWeights);

                    // Create entry for child node
                    boolean isLeafEntry = node.getLevel() == 0;
                    RtreeEntry childEntry = new RtreeEntry(childId, isLeafEntry);

                    // Add to queue for further processing
                    queue.add(new NNEntry(childEntry, selectedQueryIds, aggregateCost));

                    childIndex++;
                }
            }
        }

        //TODO Remove
//        System.out.println("SGNNK");
//        invertedList.printStatistics();
//        System.out.println("Filtered Nodes (real): " + filtered);

        // Sort results by increasing cost (best first)
        Collections.sort(results);
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
    @Override
    public Map<Integer, List<AggregateSKNNQuery.Result>> sgnnkExtended(IDocumentIndex invertedList, AggregateSKNNQuery sgnnkQuery,
                                                                       int topk, RankingSumMode scoringMode) {
        if (topk <= 0) {
            return Collections.emptyMap();
        }

        numOfVisitedNodes = 0;
        // Initialize priority queue for tree traversal
        PriorityQueue<NNEntry> searchQueue = new PriorityQueue<>();
        NNEntry rootEntry = new NNEntry(new RtreeEntry(tree.getRootIdentifier(), false), new Cost(0, 0, 0), null);
        searchQueue.add(rootEntry);

        // Create result containers for each subgroup size
        Map<Integer, PriorityQueue<AggregateSKNNQuery.Result>> subgroupResults = new HashMap<>();
        for (int subgroupSize = sgnnkQuery.subGroupSize; subgroupSize <= sgnnkQuery.groupSize; subgroupSize++) {
            // Use worst-first comparator to easily discard worst result when queue is full
            PriorityQueue<AggregateSKNNQuery.Result> candidateResults =
                    new PriorityQueue<>(topk, new WorstFirstNNEntryComparator());

            // Initialize with placeholder results having maximum cost
            for (int i = 0; i < topk; i++) {
                candidateResults.add(new AggregateSKNNQuery.Result(-1, new Cost(0, 0, Double.MAX_VALUE), null));
            }
            subgroupResults.put(subgroupSize, candidateResults);
        }

        while (!searchQueue.isEmpty()) {
            NNEntry currentEntry = searchQueue.poll();
            RtreeEntry rTreeEntry = (RtreeEntry) currentEntry.entry;

            // Apply early pruning (except for root node)
            if (currentEntry.queryComponentCosts != null) {
                boolean shouldPruneNode = true;
                // Check if this node could improve any subgroup's results
                for (int i = 0; i < currentEntry.queryComponentCosts.size(); i++) {
                    int subgroupSize = i + sgnnkQuery.subGroupSize;
                    double pruningThreshold = safeQueueThreshold(subgroupResults.get(subgroupSize));

                    if (currentEntry.queryComponentCosts.get(i).getCombinedCost() < pruningThreshold) {
                        shouldPruneNode = false;
                        break;
                    }
                }
                if (shouldPruneNode) continue;
            }

            // Read node data
            Node currentNode = readNode(rTreeEntry.getIdentifier());
            numOfVisitedNodes++;

            // Calculate costs for all queries against all children using TreeMap
            HashMap<Integer, List<Cost>> childCosts = calculateQueryCosts(
                    invertedList,
                    sgnnkQuery.queries,
                    currentNode,
                    scoringMode,
                    safeExtendedThreshold(subgroupResults)
            );

            // Get all child entries from the TreeMap
            List<Integer> childIds = new ArrayList<>(currentNode.getNodeEntries().keySet());

            // Process each child of the current node
            for (int childIndex = 0; childIndex < childIds.size(); childIndex++) {
                Integer childId = childIds.get(childIndex);
                List<Cost> queryCosts = childCosts.get(childIndex);
                if (queryCosts == null || queryCosts.isEmpty()) {
                    continue;
                }

                int[] sortedQueryIndices = selectLowestCostQueryIndices(queryCosts, sgnnkQuery.groupSize);

                // Select the group of queries with lowest costs
                List<Cost> selectedQueryCosts = new ArrayList<>(sgnnkQuery.groupSize);
                List<Integer> selectedQueryIds = new ArrayList<>(sgnnkQuery.groupSize);

                for (int i = 0; i < sgnnkQuery.groupSize; i++) {
                    int queryIndex = sortedQueryIndices[i];
                    selectedQueryCosts.add(queryCosts.get(queryIndex));
                    selectedQueryIds.add(sgnnkQuery.queries.get(queryIndex).getId());
                }

                // Initialize aggregator and accumulate first (subGroupSize-1) queries
                sgnnkQuery.aggregator.initializeAccumulator();
                for (int i = 0; i < sgnnkQuery.subGroupSize - 1; i++) {
                    int queryIndex = sortedQueryIndices[i];
                    sgnnkQuery.aggregator.accumulate(selectedQueryCosts.get(i),
                            sgnnkQuery.queries.get(queryIndex).getWeight());
                }

                // Track variables for node traversal decisions
                Cost highestSubgroupCost = null;
                boolean shouldPrune = true;
                List<Cost> allSubgroupCosts = new ArrayList<>();

                // Process each possible subgroup size
                for (int i = sgnnkQuery.subGroupSize - 1; i < sgnnkQuery.groupSize; i++) {
                    // Add one more query to the aggregation
                    int queryIndex = sortedQueryIndices[i];
                    sgnnkQuery.aggregator.accumulate(selectedQueryCosts.get(i),
                            sgnnkQuery.queries.get(queryIndex).getWeight());

                    // Get the current subgroup's aggregate cost
                    Cost aggregateCost = sgnnkQuery.aggregator.getAccumulatedValue();
                    allSubgroupCosts.add(aggregateCost);

                    // Get the current subgroup size and its results queue
                    int subgroupSize = i + 1;
                    PriorityQueue<AggregateSKNNQuery.Result> subgroupQueue = subgroupResults.get(subgroupSize);
                    List<Integer> subgroupQueryIds = selectedQueryIds.subList(0, subgroupSize);

                    // Check if this result improves the current top-k
                    if (aggregateCost.getCombinedCost() < safeQueueThreshold(subgroupQueue)) {
                        shouldPrune = false;

                        // If at leaf level, add the POI to results
                        if (currentNode.getLevel() == 0) {
                            subgroupQueue.add(new AggregateSKNNQuery.Result(childId, aggregateCost, subgroupQueryIds));
                            subgroupQueue.poll(); // Remove worst result
                        }

                        // Track the highest cost among all subgroups for this child
                        if (highestSubgroupCost == null ||
                                highestSubgroupCost.getCombinedCost() < aggregateCost.getCombinedCost()) {
                            highestSubgroupCost = aggregateCost;
                        }
                    }
                }

                // For non-leaf nodes that could improve results, add to search queue
                if (currentNode.getLevel() > 0 && !shouldPrune) {
                    RtreeEntry childEntry = new RtreeEntry(childId, false);
                    searchQueue.add(new NNEntry(childEntry, highestSubgroupCost, allSubgroupCosts));
                }
            }
        }

        // Convert priority queues to sorted result lists
        Map<Integer, List<AggregateSKNNQuery.Result>> finalResults = new HashMap<>();
        for (Integer subgroupSize : subgroupResults.keySet()) {
            List<AggregateSKNNQuery.Result> sortedResults = new ArrayList<>(subgroupResults.get(subgroupSize));
            Collections.sort(sortedResults); // Sort by ascending cost (best first)
            finalResults.put(subgroupSize, sortedResults);
        }

        return finalResults;
    }

}

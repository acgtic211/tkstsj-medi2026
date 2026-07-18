package org.ual.spatiotextualindex.queries.baseline;

import org.ual.documentindex.IDocumentIndex;
//import org.ual.documentindex.query.IQueryTextualIndex;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.NNEntry;
import org.ual.spatialindex.spatialindex.NNEntryComparatorMinDistance;
import org.ual.spatialindex.spatialindex.NodeEntry;
import org.ual.spatialindex.spatialindex.RtreeEntry;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.AbstractQueryProcessor;
import org.ual.spatiotextualindex.queries.IBooleanQueryProcessor;

import java.util.*;

public class BooleanQueryProcessor extends AbstractQueryProcessor implements IBooleanQueryProcessor {

    private static final Comparator<SKNNQuery.Result> SPATIAL_RESULT_COMPARATOR =
            Comparator.comparingDouble(SKNNQuery.Result::getSpatialCost)
                    .thenComparingInt(SKNNQuery.Result::getId);

    public BooleanQueryProcessor(AbstractIRTree tree) {
        super(tree);
    }

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
    @Override
    public List<SKNNQuery.Result> booleanRangeQuery(IDocumentIndex invertedList, SKNNQuery query, float radius) {
        if (radius < 0) {
            return Collections.emptyList();
        }

        numOfVisitedNodes = 0;
//        final IQueryTextualIndex textualIndex = getTextualIndexAdapter(invertedList);
        final int requiredKeywordMatches = query.getKeywords().size();
        // Initialize priority queue for best-first traversal based on minimum distance
        PriorityQueue<NNEntry> searchQueue = new PriorityQueue<>(new NNEntryComparatorMinDistance());
        // Start search from root node
        searchQueue.add(new NNEntry(new RtreeEntry(tree.getRootIdentifier(), false), 0.0));

        List<SKNNQuery.Result> results = new ArrayList<>();

        while (!searchQueue.isEmpty()) {
            NNEntry currentEntry = searchQueue.poll();
            RtreeEntry currentRtreeEntry = (RtreeEntry) currentEntry.entry;

            if (currentRtreeEntry.isLeafEntry) {
                // This is a data entry (POI)
                // Skip if distance exceeds search radius
                if (currentEntry.getSpatialCost() > radius) {
                    break; // All remaining entries will be farther away
                }

                // Add to results with spatial distance
                results.add(new SKNNQuery.Result(currentEntry.entry.getIdentifier(), currentEntry.getSpatialCost()));
            } else {
                // This is an internal node
                Node node = readNode(currentRtreeEntry.getIdentifier());
                numOfVisitedNodes++;

                // Get keyword filter for all children of this node
                Map<Integer, Integer> keywordMatchCounts = invertedList.booleanFilter(node.getIdentifier(), query.getKeywords());

                // Process each child entry in the node using TreeMap
                for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                    int childId = entry.getKey();
                    NodeEntry nodeEntry = entry.getValue();

                    // Check if child matches all keywords
                    Integer matchCount = keywordMatchCounts.get(childId);
                    if (matchCount == null || matchCount < requiredKeywordMatches) {
                        continue; // Skip nodes that don't contain all keywords
                    }

                    // Create appropriate entry based on node level
                    boolean isLeafEntry = node.getLevel() == 0;
                    RtreeEntry childEntry = new RtreeEntry(childId, isLeafEntry);

                    // Calculate minimum distance to child node and add to queue
                    double minDistance = nodeEntry.getMBR().getMinimumDistance(query.getLocation());
                    // Only add to queue if within search radius
                    if (minDistance <= radius) {
                        searchQueue.add(new NNEntry(childEntry, minDistance));
                    }
                }
            }
        }

        //TODO Remove
//        System.out.println("booleanRangeQuery");
//        invertedList.printStatistics();

        // Sort results by increasing distance
        Collections.sort(results);
        //logger.debug("Number of BRQ results: Radius = {} - Number: {}", radius, results.size());
        return results;
    }

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
    @Override
    public List<SKNNQuery.Result> booleanKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk) {
        if (topk <= 0) {
            return Collections.emptyList();
        }

        numOfVisitedNodes = 0;
//        final IQueryTextualIndex textualIndex = getTextualIndexAdapter(invertedList);
        final int requiredKeywordMatches = query.getKeywords().size();
        // Priority queue for best-first traversal based on minimum distance
        PriorityQueue<NNEntry> queue = new PriorityQueue<>(new NNEntryComparatorMinDistance());

        // Start search from root node
        queue.add(new NNEntry(new RtreeEntry(tree.getRootIdentifier(), false), 0.0));
        PriorityQueue<SKNNQuery.Result> topKResults = new PriorityQueue<>(topk, SPATIAL_RESULT_COMPARATOR.reversed());
        double kthDistance = Double.MAX_VALUE;

        while (!queue.isEmpty()) {
            NNEntry current = queue.poll();
            RtreeEntry rtreeEntry = (RtreeEntry) current.entry;

            // Early termination: if current entry distance is >= kth distance and we have k results
            if (topKResults.size() >= topk && current.getSpatialCost() >= kthDistance) {
                break;
            }

            if (rtreeEntry.isLeafEntry) {
                SKNNQuery.Result candidate = new SKNNQuery.Result(current.entry.getIdentifier(), current.getSpatialCost());

                if (topKResults.size() < topk) {
                    topKResults.add(candidate);
                } else {
                    SKNNQuery.Result currentWorst = topKResults.peek();
                    if (currentWorst != null && SPATIAL_RESULT_COMPARATOR.compare(candidate, currentWorst) < 0) {
                        topKResults.poll();
                        topKResults.add(candidate);
                    }
                }

                if (topKResults.size() == topk && topKResults.peek() != null) {
                    kthDistance = topKResults.peek().getSpatialCost();
                }
            } else {
                // Process internal node
                Node node = readNode(rtreeEntry.getIdentifier());
                numOfVisitedNodes++;

                // Get keyword filter for all children of this node
                Map<Integer, Integer> filter = invertedList.booleanFilter(
                        node.getIdentifier(),
                        query.getKeywords()
                );

                // Process each child of the current node
                for (Map.Entry<Integer, NodeEntry> entry : node.getNodeEntries().entrySet()) {
                    int childId = entry.getKey();
                    NodeEntry nodeEntry = entry.getValue();

                    // Check if child contains all keywords
                    Integer matchCount = filter.get(childId);
                    if (matchCount == null || matchCount < requiredKeywordMatches) {
                        continue; // Skip nodes that don't contain all keywords
                    }

                    // Calculate minimum distance to child node
                    double minDistance = nodeEntry.getMBR().getMinimumDistance(query.getLocation());

                    // Only add to queue if it could potentially improve results
                    if (topKResults.size() < topk || minDistance < kthDistance) {
                        // Create appropriate entry based on node level
                        boolean isLeafEntry = node.getLevel() == 0;
                        RtreeEntry childEntry = new RtreeEntry(childId, isLeafEntry);
                        queue.add(new NNEntry(childEntry, minDistance));
                    }
                }
            }
        }

        //TODO Remove
//        System.out.println("booleanKnnQuery");
//        invertedList.printStatistics();

        List<SKNNQuery.Result> sortedResults = new ArrayList<>(topKResults);
        sortedResults.sort(SPATIAL_RESULT_COMPARATOR);
        return sortedResults;
    }
}

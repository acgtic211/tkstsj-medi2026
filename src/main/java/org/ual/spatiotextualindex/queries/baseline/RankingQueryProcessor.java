package org.ual.spatiotextualindex.queries.baseline;

import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.RankingSumMode;
//import org.ual.documentindex.query.IQueryTextualIndex;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.AbstractQueryProcessor;
import org.ual.spatiotextualindex.queries.IRankingQueryProcessor;

import java.util.*;

public class RankingQueryProcessor extends AbstractQueryProcessor implements IRankingQueryProcessor {

    private static final Comparator<SKNNQuery.Result> RESULT_COMPARATOR =
            Comparator.comparingDouble(SKNNQuery.Result::getCombinedCost)
                    .thenComparingInt(SKNNQuery.Result::getId);


    public RankingQueryProcessor(AbstractIRTree tree) {
        super(tree);
    }

//    public List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk) {
//        return topkKnnQuery(invertedList, query, topk, RankingSumMode.defaultMode());
//    }
//
//    public List<SKNNQuery.Result> lkt(IDocumentIndex invertedList, SKNNQuery query, int topk) {
//        return lkt(invertedList, query, topk, RankingSumMode.defaultMode());
//    }

    /**
     * Performs a Top-k Keyword Nearest Neighbor (Top-k KNN) query.
     *
     * <p>This implementation uses a best-first traversal approach where:
     * <ul>
     *   <li>Nodes are processed in order of increasing combined cost (spatial + textual)</li>
     *   <li>Pruning occurs when enough results are found or when minimum distance exceeds current threshold</li>
     *   <li>Both spatial proximity and textual relevance are considered in the ranking</li>
     * </ul>
     *
     * @param invertedList Document index used for textual relevance calculation
     * @param query        Query object containing spatial location and keywords
     * @param topk         Number of results to return
     * @return List of results sorted by ascending combined cost (best matches first)
     */
    @Override
    public List<SKNNQuery.Result> topkKnnQuery(IDocumentIndex invertedList, SKNNQuery query, int topk, RankingSumMode scoringMode) {
        if (topk <= 0) {
            return Collections.emptyList();
        }

        numOfVisitedNodes = 0;
        int filtered = 0;

        long maxTimeElapsed = 0;
        long minTimeElapsed = 0;

        PriorityQueue<NNEntry> queue = new PriorityQueue<>();
        int rootId = tree.getRootIdentifier();
        queue.add(new NNEntry(new RtreeEntry(rootId, false), new Cost(0.0, 0.0, 0.0)));

        PriorityQueue<SKNNQuery.Result> topKResults = new PriorityQueue<>(topk, RESULT_COMPARATOR.reversed());
        double combinedThreshold = Double.MAX_VALUE;
        //final IQueryTextualIndex textualIndex = getTextualIndexAdapter(invertedList);
        final IShape queryLocation = query.getLocation();

        while (!queue.isEmpty()) {
            NNEntry currentEntry = queue.poll();
            RtreeEntry rtreeEntry = (RtreeEntry) currentEntry.entry;

            numOfVisitedNodes++;

            if (topKResults.size() >= topk && currentEntry.cost.getCombinedCost() >= combinedThreshold) {
                break;
            }

            if (rtreeEntry.isLeafEntry) {
                SKNNQuery.Result candidate = new SKNNQuery.Result(
                        rtreeEntry.getIdentifier(),
                        currentEntry.cost.getCombinedCost(),
                        currentEntry.cost.getSpatialCost()
                );

                if (topKResults.size() < topk) {
                    topKResults.add(candidate);
                } else {
                    SKNNQuery.Result currentWorst = topKResults.peek();
                    if (currentWorst != null && RESULT_COMPARATOR.compare(candidate, currentWorst) < 0) {
                        topKResults.poll();
                        topKResults.add(candidate);
                    }
                }

                if (topKResults.size() >= topk && topKResults.peek() != null) {
                    combinedThreshold = topKResults.peek().getCombinedCost();
                }
            } else {
                Node node = readNode(rtreeEntry.getIdentifier());
//                final double liveSimilarityThreshold = (topKResults.size() >= topk)
//                        ? toSimilarityLowerBoundFromCost(combinedThreshold)
//                        : 0.0;

//                long startTime = System.nanoTime();

                // TODO Why the cluster enhanced version is not call...
                Map<Integer, Double> relevanceScores;
                if (tree.getNumberOfClusters() > 0) {
                    // Cluster-enhanced ranking
                    relevanceScores = invertedList.rankingSumClusterEnhance(node.getIdentifier(), query.getKeywords(), query.getKeywordWeights(), scoringMode);
                } else {
                    // Regular ranking
                     relevanceScores = invertedList.rankingSum(node.getIdentifier(), query.getKeywords(), query.getKeywordWeights(), scoringMode);
                }
//                Map<Integer, Double> relevanceScores = invertedList.rankingSum(node.getIdentifier(), query.getKeywords(), query.getKeywordWeights(), scoringMode);
//                long endTime = System.nanoTime();
//                long timeElapsed = endTime - startTime;
//                maxTimeElapsed = Math.max(maxTimeElapsed, timeElapsed);
//                minTimeElapsed = (minTimeElapsed == 0) ? timeElapsed : Math.min(minTimeElapsed, timeElapsed);

                //                NodeScoreResult nodeScores = scoreNodeForQuery(
//                        invertedList,
//                        textualIndex,
//                        node.getIdentifier(),
//                        query,
//                        liveSimilarityThreshold,
//                        scoringMode
//                );

//                Map<Integer, Double> relevanceScores = nodeScores.getScores();
                if (relevanceScores == null || relevanceScores.isEmpty()) {
                    filtered++;
                    continue;
                }

//                final boolean usingSpatialAdapterPath = nodeScores.isSpatialAdapterPath();
                final Map<Integer, NodeEntry> nodeEntries = node.getNodeEntries();

                // Iterate only textual hits to avoid scanning children with zero relevance.
                for (Map.Entry<Integer, Double> relevanceEntry : relevanceScores.entrySet()) {
                    int childId = relevanceEntry.getKey();
                    NodeEntry nodeEntry = nodeEntries.get(childId);
                    if (nodeEntry == null) {
                        continue;
                    }

                    double irScore = relevanceEntry.getValue();
                    if (irScore <= 0.0) {
                        continue;
                    }

//                    double spatialDistance = usingSpatialAdapterPath
//                            ? 0.0
//                            : nodeEntry.getMBR().getMinimumDistance(queryLocation);
//                    double combinedScore = toCombinedCost(spatialDistance, irScore, usingSpatialAdapterPath);

                    double spatialDistance  = nodeEntry.getMBR().getMinimumDistance(queryLocation);
                    double combinedScore = combinedScore(spatialDistance, irScore);

                    if (topKResults.size() < topk || combinedScore < combinedThreshold) {
                        boolean isLeafEntry = node.getLevel() == 0;
                        RtreeEntry childEntry = new RtreeEntry(childId, isLeafEntry);
                        queue.add(new NNEntry(childEntry, new Cost(irScore, spatialDistance, combinedScore)));
                    }
                }
            }
        }

        //TODO Remove
//        System.out.println("TkSK - Time Elapsed (ms): min = " + minTimeElapsed + ", max = " + maxTimeElapsed);
//        System.out.println("topkKnnQuery");
//        invertedList.printStatistics();
//        System.out.println("Visited Nodes: " + numOfVisitedNodes);
//        System.out.println("Filtered Nodes (real): " + filtered);

        List<SKNNQuery.Result> sortedResults = new ArrayList<>(topKResults);
        sortedResults.sort(RESULT_COMPARATOR);
        return sortedResults;
    }


    //==========================================================================================
    //====================== (Legacy) Location-based Keyword Top-k Queries =====================
    //==========================================================================================

    /**
     * Performs a Location-based Keyword Top-k (LKT) query.
     */
    @Override
    public List<SKNNQuery.Result> lkt(IDocumentIndex invertedList, SKNNQuery query, int topk, RankingSumMode scoringMode) {
        if (topk <= 0) {
            return Collections.emptyList();
        }

        numOfVisitedNodes = 0;
        PriorityQueue<NNEntry> queue = new PriorityQueue<>();
        int rootId = tree.getRootIdentifier();
        queue.add(new NNEntry(new RtreeEntry(rootId, false), new Cost(0.0, 0.0, 0.0)));

        List<SKNNQuery.Result> results = new ArrayList<>();
        //final IQueryTextualIndex textualIndex = getTextualIndexAdapter(invertedList);
        final IShape queryLocation = query.getLocation();

        while (!queue.isEmpty() && results.size() < topk) {
            NNEntry currentEntry = queue.poll();
            RtreeEntry rtreeEntry = (RtreeEntry) currentEntry.entry;

            numOfVisitedNodes++;

            if (rtreeEntry.isLeafEntry) {
                results.add(new SKNNQuery.Result(
                        rtreeEntry.getIdentifier(),
                        currentEntry.getIrCost(),
                        currentEntry.getSpatialCost()
                ));
            } else {
                Node node = readNode(rtreeEntry.getIdentifier());
//                NodeScoreResult nodeScores = scoreNodeForQuery(
//                        invertedList,
//                        textualIndex,
//                        node.getIdentifier(),
//                        query,
//                        0.0,
//                        scoringMode
//                );

//                Map<Integer, Double> relevanceScores = nodeScores.getScores();

                Map<Integer, Double> relevanceScores = invertedList.rankingSum(node.getIdentifier(), query.getKeywords(), query.getKeywordWeights(), scoringMode);
                if (relevanceScores == null || relevanceScores.isEmpty()) {
                    continue;
                }
//                final boolean usingSpatialAdapterPath = nodeScores.isSpatialAdapterPath();

                for (Map.Entry<Integer, NodeEntry> nodeEntryPair : node.getNodeEntries().entrySet()) {
                    int childId = nodeEntryPair.getKey();
                    NodeEntry nodeEntry = nodeEntryPair.getValue();

                    Double irScore = relevanceScores.get(childId);
                    if (irScore == null || irScore <= 0.0) {
                        continue;
                    }

                    boolean isLeafEntry = node.getLevel() == 0;
                    RtreeEntry childEntry = new RtreeEntry(childId, isLeafEntry);

//                    double spatialDistance = usingSpatialAdapterPath
//                            ? 0.0
//                            : nodeEntry.getMBR().getMinimumDistance(queryLocation);
//                    double combinedScore = toCombinedCost(spatialDistance, irScore, usingSpatialAdapterPath);

                    double spatialDistance  = nodeEntry.getMBR().getMinimumDistance(queryLocation);
                    double combinedScore = combinedScore(spatialDistance, irScore);
                    queue.add(new NNEntry(childEntry, new Cost(irScore, spatialDistance, combinedScore)));
                }
            }
        }

        Collections.sort(results);
        return results;
    }
}

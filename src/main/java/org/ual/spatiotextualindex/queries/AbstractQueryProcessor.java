package org.ual.spatiotextualindex.queries;

import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.IAggregateDocumentIndex;
import org.ual.documentindex.RankingSumMode;
import org.ual.documentindex.SimilarityType;
//import org.ual.documentindex.signedblocknew.SpatioTextualQueryContextNEW;
//import org.ual.documentindex.query.IQueryTextualIndex;
//import org.ual.documentindex.query.QueryTextualIndexFactory;
import org.ual.query.Query;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;

public abstract class AbstractQueryProcessor implements ISpatioTextualQueryProcessor {
    protected final AbstractIRTree tree;
    protected int numOfVisitedNodes;

    protected AbstractQueryProcessor(AbstractIRTree tree) {
        this.tree = tree;
        this.numOfVisitedNodes = 0;
    }

    // Shared utility methods
    protected Node readNode(int nodeId) {
        return tree.readNode(nodeId);
    }

    protected Node readNode(ISpatioTextualIndex targetTree, int nodeId) {
        return targetTree.readNode(nodeId);
    }

    protected double combinedScore(double spatial, double ir) {
        return tree.combinedScore(spatial, ir);
    }


    protected double maxScore(Map<Integer, Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0.0;
        }

        double max = 0.0;
        for (double value : scores.values()) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }



    protected HashMap<Integer, List<Cost>> calculateQueryCosts(IDocumentIndex invertedList,
                                                               List<Query> queries,
                                                               Node node,
                                                               RankingSumMode rankingSumMode) {
        return calculateQueryCosts(invertedList, queries, node, rankingSumMode, Double.POSITIVE_INFINITY);
    }

    protected HashMap<Integer, List<Cost>> calculateQueryCosts(IDocumentIndex invertedList,
                                                               List<Query> queries,
                                                               Node node,
                                                               RankingSumMode rankingSumMode,
                                                               double liveCombinedThreshold) {
        int numChildren = node.getNodeEntriesSize();
        if (numChildren == 0 || queries.isEmpty()) {
            return new HashMap<>(); // Early return for edge cases
        }

        // Result container: child index -> list of costs (one per query)
        HashMap<Integer, List<Cost>> costs = new HashMap<>(numChildren);

        // Pre-fetch all child data to avoid repeated method calls
        List<Integer> childIds = new ArrayList<>(node.getNodeEntries().keySet());
        List<IShape> childMBRs = new ArrayList<>(numChildren);

        // Initialize data structures
        for (int i = 0; i < numChildren; i++) {
            int childId = childIds.get(i);
            childMBRs.add(node.getNodeEntries().get(childId).getMBR());
            costs.put(i, new ArrayList<>(queries.size()));
        }

        // Process each query
        for (Query query : queries) {
//            NodeScoreResult nodeScores = scoreNodeForQuery(
//                    invertedList, textualIndex, node.getIdentifier(), query, 0.0, rankingSumMode);
//            final Map<Integer, Double> similarities = nodeScores.getScores();
//            final boolean useSpatialAdapterPath = nodeScores.isSpatialAdapterPath();

            final Map<Integer, Double> similarities;
            if (tree.getNumberOfClusters() != 0) {
                similarities = invertedList.rankingSumClusterEnhance(
                        node.getIdentifier(),
                        query.getKeywords(),
                        query.getKeywordWeights(),
                        rankingSumMode
                );
            } else if (invertedList instanceof IAggregateDocumentIndex) {
                IAggregateDocumentIndex aggregateIndex = (IAggregateDocumentIndex) invertedList;
                double[] queryPoint = resolveQueryPointCoordinates(query.getLocation());
                double irLowerBound = toIrLowerBound(liveCombinedThreshold);
//                SpatioTextualQueryContextNEW ctx = SpatioTextualQueryContextNEW.spatioTextual(
//                        query.getKeywords(),
//                        query.getKeywordWeights(),
//                        queryPoint[0],
//                        queryPoint[1],
//                        aggregateIndex.getGlobalExtent(),
//                        irLowerBound,
//                        SimilarityType.WEIGHTED_SUM
//                );
//                similarities = aggregateIndex.rankingSum(node.getIdentifier(), ctx, rankingSumMode);
                throw new UnsupportedOperationException("Experimental aggregate index support is not implemented yet.");
            } else {
                similarities = invertedList.rankingSum(
                        node.getIdentifier(),
                        query.getKeywords(),
                        query.getKeywordWeights(),
                        rankingSumMode
                );
            }



            // Calculate costs for each child
            for (int childIdx = 0; childIdx < numChildren; childIdx++) {
                int childId = childIds.get(childIdx);
                IShape childMBR = childMBRs.get(childIdx);

                // Get textual relevance score (default 0 if no match)
                double irScore = similarities.getOrDefault(childId, 0.0);

                // Calculate spatial distance and combined score
                double spatialCost = childMBR.getMinimumDistance(query.getLocation());
                double combinedCost = combinedScore(spatialCost, irScore);

                // Store results
                costs.get(childIdx).add(new Cost(irScore, spatialCost, combinedCost));
            }
        }

        return costs;
    }

    private double[] resolveQueryPointCoordinates(IShape shape) {
        if (shape instanceof Point) {
            Point point = (Point) shape;
            return new double[]{point.getCoord(0), point.getCoord(1)};
        }
        if (shape instanceof Region) {
            Region region = (Region) shape;
            return new double[]{region.getCenterX(), region.getCenterY()};
        }
        return new double[]{0.0, 0.0};
    }

    protected double toIrLowerBound(double combinedThreshold) {
        if (!Double.isFinite(combinedThreshold))
            return 0.0;
        double a = tree.getAlphaDistribution();
        double denom = 1.0 - a;
        if (denom <= 1e-9) // TODO Change this to SpatialIndex.EPSILON
            return 0.0; // alpha ~1 => no safe textual-only pruning
        double lb = 1.0 - (combinedThreshold / denom);
        return Math.max(0.0, Math.min(1.0, lb));
    }



    @Override
    public int getVisitedNodes() {
        return numOfVisitedNodes;
    }

    /**
     * Comparator that orders AggregateSKNNQuery.Result objects by decreasing combined cost.
     * This creates a max-heap where the object with the highest cost is at the top,
     * making it efficient to remove the worst result when the queue is full.
     */
    public static class WorstFirstNNEntryComparator implements Comparator<AggregateSKNNQuery.Result> {
        @Override
        public int compare(AggregateSKNNQuery.Result n1, AggregateSKNNQuery.Result n2) {
            // Compare in reverse order (highest cost first)
            return Double.compare(n2.getAggregateCost().getCombinedCost(), n1.getAggregateCost().getCombinedCost());
        }
    }
}

package org.ual.spatiotextualindex.queries.baseline.join;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.BoundLimit;
import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.documentindex.invertedlist.InvertedListIndex;
import org.ual.querytype.SKJoinQuery;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.*;

import java.util.*;

public class JoinMultiSetQueryProcessor extends JoinQueryProcessor implements IJoinMultiSetQueryProcessor {
    private static final Logger logger = LogManager.getLogger(JoinMultiSetQueryProcessor.class);

    public JoinMultiSetQueryProcessor(AbstractIRTree tree) {
        super(tree);
    }

//    private long buildDirectionalPairKey(int leftId, int rightId) {
//        // Cross-dataset joins are directional: (A,B) and (B,A) are distinct pairs.
//        return (((long) leftId) << 32) | (rightId & 0xffffffffL);
//    }

    private boolean shouldPruneByThresholds(double spatialDistance, double textualSimilarity,
                                            float spatialThreshold, float textualThreshold) {
        // Pairs with zero textual overlap must be pruned.
        if (textualThreshold > 0 && textualSimilarity <= 0.0) {
            return true;
        }
        // Prune by spatial distance
        if (spatialDistance > spatialThreshold) {
            return true;
        }

        return textualThreshold > 0 && textualSimilarity < textualThreshold;

    }

//    private static class NodePairRelevance {
//        private final Map<Integer, Double> leftScores;
//        private final Map<Integer, Double> rightScores;
//        private final double fullJoinUpperBound;
//
//        private NodePairRelevance(Map<Integer, Double> leftScores,
//                                  Map<Integer, Double> rightScores,
//                                  double fullJoinUpperBound) {
//            this.leftScores = (leftScores == null) ? Collections.emptyMap() : leftScores;
//            this.rightScores = (rightScores == null) ? Collections.emptyMap() : rightScores;
//            this.fullJoinUpperBound = fullJoinUpperBound;
//        }
//
//        private boolean isEmpty(QueryStrategy queryStrategy) {
//            if (QueryStrategy.orDefault(queryStrategy) == QueryStrategy.FULL_JOIN) {
//                return fullJoinUpperBound <= 0.0;
//            }
//            return leftScores.isEmpty() || rightScores.isEmpty();
//        }
//    }

    private NodePairRelevance calculateNodePairRelevanceScores(IDocumentIndex primaryInvertedList,
                                                               IDocumentIndex secondaryInvertedList,
                                                               int nodeIdX,
                                                               int nodeIdY,
                                                               List<Integer> keywords,
                                                               List<Double> keywordWeights,
                                                               SimilarityType similarityType,
                                                               QueryStrategy queryStrategy) {
        QueryStrategy effectiveStrategy = QueryStrategy.orDefault(queryStrategy);

        if (effectiveStrategy == QueryStrategy.FULL_JOIN) {
            double score = primaryInvertedList.crossDatasetTextualSim(nodeIdX, nodeIdY, secondaryInvertedList, similarityType, BoundLimit.LOWER_BOUND);
            return new NodePairRelevance(Collections.emptyMap(), Collections.emptyMap(), Math.max(0.0, score));
        }

//        if (primaryInvertedList instanceof InvertedListIndex && secondaryInvertedList instanceof InvertedListIndex) {
            InvertedListIndex primaryIndex = (InvertedListIndex) primaryInvertedList;
            InvertedListIndex secondaryIndex = (InvertedListIndex) secondaryInvertedList;

            Map<Integer, Double> leftScores = primaryIndex.crossDatasetConstraintTextualSim(
                    nodeIdX, keywords, keywordWeights, similarityType);
            Map<Integer, Double> rightScores = secondaryIndex.crossDatasetConstraintTextualSim(
                    nodeIdY, keywords, keywordWeights, similarityType);

            return new NodePairRelevance(leftScores, rightScores, 0.0);
//        }

        // Fallback for non-InvertedListIndex implementations.
//        Map<Integer, Double> mergedScores = primaryInvertedList.crossDatasetConstraintTextualSim(
//                nodeIdX, nodeIdY, secondaryInvertedList, keywords, keywordWeights, similarityType);
//
//        if (mergedScores == null || mergedScores.isEmpty()) {
//            return new NodePairRelevance(Collections.emptyMap(), Collections.emptyMap(), 0.0);
//        }
//
//        return new NodePairRelevance(mergedScores, mergedScores, 0.0);
    }

    private NodePairRelevance calculateNodePairRelevanceScoresUpperBound(IDocumentIndex primaryInvertedList,
                                                               IDocumentIndex secondaryInvertedList,
                                                               int nodeIdX,
                                                               int nodeIdY,
                                                               List<Integer> keywords,
                                                               List<Double> keywordWeights,
                                                               SimilarityType similarityType,
                                                               QueryStrategy queryStrategy) {
        QueryStrategy effectiveStrategy = QueryStrategy.orDefault(queryStrategy);

        if (effectiveStrategy == QueryStrategy.FULL_JOIN) {
            double score = primaryInvertedList.crossDatasetTextualSim(nodeIdX, nodeIdY, secondaryInvertedList, similarityType, BoundLimit.UPPER_BOUND);
            return new NodePairRelevance(Collections.emptyMap(), Collections.emptyMap(), Math.max(0.0, score));
        }

//        if (primaryInvertedList instanceof InvertedListIndex && secondaryInvertedList instanceof InvertedListIndex) {
            InvertedListIndex primaryIndex = (InvertedListIndex) primaryInvertedList;
            InvertedListIndex secondaryIndex = (InvertedListIndex) secondaryInvertedList;

            Map<Integer, Double> leftScores = primaryIndex.crossDatasetConstraintTextualSim(
                    nodeIdX, keywords, keywordWeights, similarityType);
            Map<Integer, Double> rightScores = secondaryIndex.crossDatasetConstraintTextualSim(
                    nodeIdY, keywords, keywordWeights, similarityType);

            return new NodePairRelevance(leftScores, rightScores, 0.0);
//        }

        // Fallback for non-InvertedListIndex implementations.
//        Map<Integer, Double> mergedScores = primaryInvertedList.crossDatasetTextualSim(
//                nodeIdX, nodeIdY, secondaryInvertedList, keywords, keywordWeights, similarityType);
//
//        if (mergedScores == null || mergedScores.isEmpty()) {
//            return new NodePairRelevance(Collections.emptyMap(), Collections.emptyMap(), 0.0);
//        }
//
//        return new NodePairRelevance(mergedScores, mergedScores, 0.0);
    }

    private double resolveMultiSetTextualUpperBound(NodePairRelevance relevance,
                                                    int childIdX,
                                                    int childIdY,
                                                    QueryStrategy queryStrategy) {
        if (relevance == null) {
            return 0.0;
        }

        if (QueryStrategy.orDefault(queryStrategy) == QueryStrategy.FULL_JOIN) {
            return relevance.fullJoinUpperBound;
        }

        double left = relevance.leftScores.getOrDefault(childIdX, 0.0);
        double right = relevance.rightScores.getOrDefault(childIdY, 0.0);
        return Math.min(left, right);
    }

    private double resolveLeafTextualSimilarity(IDocumentIndex primaryInvertedList,
                                                IDocumentIndex secondaryInvertedList,
                                                int leafNodeIdX,
                                                int objectIdX,
                                                int leafNodeIdY,
                                                int objectIdY,
                                                SimilarityType similarityType,
                                                QueryStrategy queryStrategy,
                                                NodePairRelevance relevance) {
        if (QueryStrategy.orDefault(queryStrategy) == QueryStrategy.FULL_JOIN
                && primaryInvertedList instanceof InvertedListIndex
                && secondaryInvertedList instanceof InvertedListIndex) {
            InvertedListIndex primaryIndex = (InvertedListIndex) primaryInvertedList;
            return primaryIndex.crossDatasetDocumentSim(leafNodeIdX, leafNodeIdY, objectIdX, objectIdY, secondaryInvertedList, similarityType);
        }

        return resolveMultiSetTextualUpperBound(relevance, objectIdX, objectIdY, queryStrategy);
    }

//    private Node readNode(ISpatioTextualIndex tree, int nodeId) {
//        return tree.readNode(nodeId);
//    }

//    private double maxScore(Map<Integer, Double> scores) {
//        if (scores == null || scores.isEmpty()) {
//            return 0.0;
//        }
//
//        double max = 0.0;
//        for (double value : scores.values()) {
//            if (value > max) {
//                max = value;
//            }
//        }
//        return max;
//    }

    private boolean shouldPruneUpperBoundTraversal(double spatialDistance, double textualUpperBound, float spatialThreshold,
                                                   float textualThreshold, boolean bothLeafEntries) {
        if (textualThreshold > 0 && textualUpperBound <= 0.0) {
            return true;
        }
        if (textualThreshold > 0 && textualUpperBound < textualThreshold) {
            return true;
        }
        return bothLeafEntries && spatialDistance > spatialThreshold;
    }

    private void expandAsymmetricBestFirst(Node leafNode, Node innerNode, boolean leafOnX,
                                           IDocumentIndex primaryInvertedList, IDocumentIndex secondaryInvertedList,
                                           SKJoinQuery query, float spatialThreshold, float textualThreshold,
                                           SimilarityType similarityType, QueryStrategy queryStrategy,
                                           PriorityQueue<NNEntryPair> queue) {
        NodePairRelevance relevance = null;
        double leafSideMaxScore = 0.0;
        if (textualThreshold > 0) {
            relevance = calculateNodePairRelevanceScores(
                    primaryInvertedList, secondaryInvertedList,
                    leafNode.getIdentifier(), innerNode.getIdentifier(),
                    query.getKeywords(), query.getKeywordWeights(),
                    similarityType, queryStrategy);

            if (QueryStrategy.orDefault(queryStrategy) != QueryStrategy.FULL_JOIN) {
                leafSideMaxScore = leafOnX ? maxScore(relevance.leftScores) : maxScore(relevance.rightScores);
            }

            if (relevance.isEmpty(queryStrategy)) {
                return;
            }
        }

        final TreeMap<Integer, NodeEntry> innerEntries = innerNode.getNodeEntries();
        final Region leafMbr = leafNode.getMBR();
        final int leafNodeId = leafNode.getIdentifier();

        for (Map.Entry<Integer, NodeEntry> entry : innerEntries.entrySet()) {
            final int childId = entry.getKey();
            final Region childMbr = entry.getValue().getMBR();
            final double spatialDistance = leafMbr.getMinimumDistance(childMbr);

            if (spatialDistance > spatialThreshold) {
                continue;
            }

            double textualUpperBound = 1.0;
            if (textualThreshold > 0) {
                if (QueryStrategy.orDefault(queryStrategy) == QueryStrategy.FULL_JOIN) {
                    textualUpperBound = relevance.fullJoinUpperBound;
                } else {
                    double childSideScore = leafOnX
                            ? relevance.rightScores.getOrDefault(childId, 0.0)
                            : relevance.leftScores.getOrDefault(childId, 0.0);
                    textualUpperBound = Math.min(leafSideMaxScore, childSideScore);
                }

                if (textualUpperBound < textualThreshold) {
                    continue;
                }
            }

            // Keep both entries as node identifiers so the next iteration can always readNode safely.
            if (!leafOnX) {
                queue.add(new NNEntryPair(new RtreeEntry(childId, false),
                        new RtreeEntry(leafNodeId, false),
                        spatialDistance, textualUpperBound, combinedScore(spatialDistance, textualUpperBound)));
            } else {
                queue.add(new NNEntryPair(new RtreeEntry(leafNodeId, false),
                        new RtreeEntry(childId, false),
                        spatialDistance, textualUpperBound, combinedScore(spatialDistance, textualUpperBound)));
            }
        }
    }

    private void expandAsymmetricRecursive(Node leafNode, Node innerNode, boolean leafOnX,
                                           IDocumentIndex primaryInvertedList, IDocumentIndex secondaryInvertedList,
                                           SKJoinQuery query, float spatialThreshold, float textualThreshold,
                                           JoinStrategy joinStrategy, SimilarityType similarityType,
                                           QueryStrategy queryStrategy, ISpatioTextualIndex secondaryTree,
                                           List<SKJoinQuery.Result> results) {
        NodePairRelevance relevance = null;
        if (textualThreshold > 0) {
            relevance = calculateNodePairRelevanceScores(
                    primaryInvertedList, secondaryInvertedList,
                    leafNode.getIdentifier(), innerNode.getIdentifier(),
                    query.getKeywords(), query.getKeywordWeights(),
                    similarityType, queryStrategy);
            if (relevance.isEmpty(queryStrategy)) {
                return;
            }
        }

        final TreeMap<Integer, NodeEntry> innerEntries = innerNode.getNodeEntries();
        final Region leafMbr = leafNode.getMBR();

        for (Map.Entry<Integer, NodeEntry> entry : innerEntries.entrySet()) {
            final int childId = entry.getKey();
            final Region childMbr = entry.getValue().getMBR();
            final double spatialDistance = leafMbr.getMinimumDistance(childMbr);

            if (spatialDistance > spatialThreshold) {
                continue;
            }

            double textualUpperBound = 1.0;
            if (textualThreshold > 0) {
                textualUpperBound = leafOnX
                        ? resolveMultiSetTextualUpperBound(relevance, leafNode.getIdentifier(), childId, queryStrategy)
                        : resolveMultiSetTextualUpperBound(relevance, childId, leafNode.getIdentifier(), queryStrategy);
                if (textualUpperBound < textualThreshold) {
                    continue;
                }
            }

            // Keep recursive state in node-id space for asymmetric levels.
            if (leafOnX) {
                selfJoinSKQueryRecursiveTraversal(
                        new NNEntryPair(new RtreeEntry(leafNode.getIdentifier(), false),
                                new RtreeEntry(childId, false),
                                spatialDistance, textualUpperBound, combinedScore(spatialDistance, textualUpperBound)),
                        secondaryTree,
                        primaryInvertedList, secondaryInvertedList, query, spatialThreshold, textualThreshold,
                        joinStrategy, similarityType, queryStrategy, results);
            } else {
                selfJoinSKQueryRecursiveTraversal(
                        new NNEntryPair(new RtreeEntry(childId, false),
                                new RtreeEntry(leafNode.getIdentifier(), false),
                                spatialDistance, textualUpperBound, combinedScore(spatialDistance, textualUpperBound)),
                        secondaryTree,
                        primaryInvertedList, secondaryInvertedList, query, spatialThreshold, textualThreshold,
                        joinStrategy, similarityType, queryStrategy, results);
            }
        }
    }


    //******************************************************************************************

    @Override
    public List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex primaryInvertedList, IDocumentIndex secondaryInvertedList,
                                                             ISpatioTextualIndex secondaryTree, SKJoinQuery query, float spatialThreshold,
                                                             float textualThreshold, JoinConfiguration joinConfiguration) {
        numOfVisitedNodes = 0;

        JoinConfiguration config = Objects.requireNonNull(joinConfiguration, "joinConfiguration must not be null");

        //ThresholdPolicy thresholdPolicy = config.getThresholdPolicy();
        SimilarityType similarityType = config.getSimilarityType(); // Weighted Jaccard, Cosine or Weighted SUM
        JoinStrategy joinStrategy = config.getJoinStrategy();   // Plain Sweep or Default (smart)
        QueryStrategy queryStrategy = config.getQueryStrategy(); // Partial Join or Full Join

        logger.info("[BestFirst] Starting self-join SKQuery with similarity: {}, join strategy: {}, query strategy: {}",
                similarityType.getDescription(), joinStrategy.getDescription(), queryStrategy.getDescription());

        // Initialize priority queue for best-first traversal
        PriorityQueue<NNEntryPair> queue = new PriorityQueue<>(new NNEntryPairComparatorSpatialFirst());
        queue.add(new NNEntryPair(new RtreeEntry(tree.getRootIdentifier(), false),
                new RtreeEntry(secondaryTree.getRootIdentifier(), false),
                Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_VALUE));

        // Track results
        List<SKJoinQuery.Result> results = new ArrayList<>();
        int priorityQueueMaxSize = queue.size();    // Maximum size of the priority queue to control memory usage

        // Set for tracking processed pairs to avoid duplicates
        // Set<Integer> processedPairs = new HashSet<>();
        Set<Long> processedPairs = new HashSet<>();

        double minSpatialCost = Double.MAX_VALUE;

        while (!queue.isEmpty()) {
            NNEntryPair currentEntry = queue.poll();
            RtreeEntry rtreeEntry1 = currentEntry.entry1;
            RtreeEntry rtreeEntry2 = currentEntry.entry2;

            // Check leaf entries (actual data objects)
            if (rtreeEntry1.isLeafEntry && rtreeEntry2.isLeafEntry) {
                int id1 = rtreeEntry1.getIdentifier();
                int id2 = rtreeEntry2.getIdentifier();

                // Create a unique key for this pair to avoid duplicates
                // int pairKey = Objects.hash(Math.min(id1, id2), Math.max(id1, id2));
                long pairKey = buildDirectionalPairKey(id1, id2);

                if (processedPairs.contains(pairKey)) {
                    continue;
                }
                processedPairs.add(pairKey);

                double textualSimilarity = currentEntry.textualCost;
//
//                if (shouldPruneByThresholdPolicy(
//                        currentEntry.spatialCost,
//                        textualSimilarity,
//                        spatialThreshold,
//                        textualThreshold,
//                        thresholdPolicy)) {
//                    continue;
//                }
                if (shouldPruneByThresholds(currentEntry.spatialCost, textualSimilarity, spatialThreshold, textualThreshold)) {
                    logger.debug("Ejected pair ({}, {}) spatial={} textual={}", id1, id2, currentEntry.spatialCost, textualSimilarity);
                    continue;
                }

                double combinedCost = combinedScore(currentEntry.spatialCost, textualSimilarity);
                results.add(new SKJoinQuery.Result(id1, id2, currentEntry.spatialCost, textualSimilarity, combinedCost));
                numOfVisitedNodes++;

            } else {
                // Internal node pair — expand children.
                Node nodeX = readNode(this.tree, rtreeEntry1.getIdentifier());
                Node nodeY = readNode(secondaryTree, rtreeEntry2.getIdentifier());
                if (nodeX == null || nodeY == null) {
                    logger.warn("Skipping pair with missing node(s): x={}, y={}", rtreeEntry1.getIdentifier(), rtreeEntry2.getIdentifier());
                    continue;
                }

                numOfVisitedNodes += 2;

                // XOR condition: exactly one of the nodes is a leaf node
                if (nodeX.isLeaf() ^ nodeY.isLeaf()) {
                    if (nodeX.isLeaf()) {
                        expandAsymmetricBestFirst(nodeX, nodeY, true,
                                primaryInvertedList, secondaryInvertedList, query,
                                spatialThreshold, textualThreshold, similarityType, queryStrategy,
                                queue);
                    } else {
                        expandAsymmetricBestFirst(nodeY, nodeX, false,
                                primaryInvertedList, secondaryInvertedList, query,
                                spatialThreshold, textualThreshold, similarityType, queryStrategy,
                                queue);
                    }
                    continue;
                }

                NodePairRelevance relevance = null;
                if (textualThreshold > 0) {
//                    relevance = calculateNodePairRelevanceScores(
//                            primaryInvertedList, secondaryInvertedList,
//                            nodeX.getIdentifier(), nodeY.getIdentifier(),
//                            query.getKeywords(), query.getKeywordWeights(),
//                            similarityType, queryStrategy);

                    relevance = calculateNodePairRelevanceScoresUpperBound(
                            primaryInvertedList,
                            secondaryInvertedList,
                            nodeX.getIdentifier(),
                            nodeY.getIdentifier(),
                            query.getKeywords(),
                            query.getKeywordWeights(),
                            similarityType,
                            queryStrategy
                    );

                    // Early exit if either node has no relevant keywords
                    if (relevance.isEmpty(queryStrategy)) {
                        continue;
                    }
                }

                //final boolean isSelfJoin = (nodeX.getIdentifier() == nodeY.getIdentifier());
                final boolean isSelfJoin = false; // Here we never do a self join
                final boolean isLeafEntryX = nodeX.isLeaf();
                final boolean isLeafEntryY = nodeY.isLeaf();

                // Choose join strategy: plane-sweep or nested loop
                if (joinStrategy == JoinStrategy.PLANE_SWEEP) {
                    // Apply plane-sweep algorithm for efficient spatial join processing
                    List<JoinQueryProcessor.CandidatePair> candidatePairs = generatePlaneSweepJoin(nodeX, nodeY, spatialThreshold,
                            textualThreshold, null, isSelfJoin, queryStrategy);

                    // Process candidate pairs from plane-sweep
                    for (JoinQueryProcessor.CandidatePair candidate : candidatePairs) {
                        final int childIdX = candidate.childIdX;
                        final int childIdY = candidate.childIdY;

                        double textualUpperBound = 1.0;
                        if (textualThreshold > 0) {
                            textualUpperBound = isLeafEntryX && isLeafEntryY
                                    ? resolveLeafTextualSimilarity(
                                    primaryInvertedList, secondaryInvertedList,
                                    nodeX.getIdentifier(), childIdX,
                                    nodeY.getIdentifier(), childIdY,
                                    similarityType, queryStrategy, relevance)
                                    : resolveMultiSetTextualUpperBound(relevance, childIdX, childIdY, queryStrategy);
                        }

                        if (shouldPruneUpperBoundTraversal(candidate.spatialDistance, textualUpperBound,
                                spatialThreshold, textualThreshold, isLeafEntryX && isLeafEntryY)) {
                            continue;
                        }

                        double combinedScore = combinedScore(candidate.spatialDistance, textualUpperBound);
                        queue.add(new NNEntryPair(
                                    new RtreeEntry(childIdX, isLeafEntryX),
                                    new RtreeEntry(childIdY, isLeafEntryY),
                                candidate.spatialDistance, textualUpperBound, combinedScore));
                    }
                } else {
                    final TreeMap<Integer, NodeEntry> entriesX = nodeX.getNodeEntries();
                    final TreeMap<Integer, NodeEntry> entriesY = nodeY.getNodeEntries();

                    // Generate all potential pairs between children of nodeX and nodeY
                    for (Map.Entry<Integer, NodeEntry> entryX : entriesX.entrySet()) {
                        final int childIdX = entryX.getKey();
                        final Region mbrX = entryX.getValue().getMBR();

                        for (Map.Entry<Integer, NodeEntry> entryY : entriesY.entrySet()) {
                            final int childIdY = entryY.getKey();
                            final Region mbrY = entryY.getValue().getMBR();

                            // Calculate spatial distance
                            final double spatialDistance = mbrX.getMinimumDistance(mbrY);
                            minSpatialCost = Math.min(minSpatialCost, spatialDistance);

                            // Spatial pruning only when both sides are already leaf-level entries.
                            if (isLeafEntryX && isLeafEntryY && spatialDistance > spatialThreshold) {
                                continue;
                            }

                            // Calculate textual similarity
                            double textualUpperBound = 1.0;
                            if (textualThreshold > 0) {
                                textualUpperBound = isLeafEntryX && isLeafEntryY
                                        ? resolveLeafTextualSimilarity(
                                        primaryInvertedList, secondaryInvertedList,
                                        nodeX.getIdentifier(), childIdX,
                                        nodeY.getIdentifier(), childIdY,
                                        similarityType, queryStrategy, relevance)
                                        : resolveMultiSetTextualUpperBound(relevance, childIdX, childIdY, queryStrategy);
                                if (textualUpperBound < textualThreshold) {
                                    continue;
                                }
                            }

                            final double combinedScore = combinedScore(spatialDistance, textualUpperBound);
                            queue.add(new NNEntryPair(
                                    new RtreeEntry(childIdX, isLeafEntryX),
                                    new RtreeEntry(childIdY, isLeafEntryY),
                                    spatialDistance, textualUpperBound, combinedScore));
                        }
                    }
                }
            }
            priorityQueueMaxSize = Math.max(priorityQueueMaxSize, queue.size());
        }

        Collections.sort(results);
        logger.debug("[BestFirst] Number of results: {}", results.size());
        logger.info("[BestFirst] Maximum priority queue size: {}", priorityQueueMaxSize);
        logger.info("[BestFirst] Using similarity: {} query strategy: {}", similarityType.getDescription(), queryStrategy.getDescription());
        logger.info("[BestFirst] MinDist = {}", minSpatialCost);

        //TODO Remove
        System.out.println("[BestFirst] Using similarity: " + similarityType.getDescription() + " and query strategy: " + queryStrategy.getDescription());
//        invertedList.printStatistics();

        System.out.println("MinDist = " + minSpatialCost);

        return results;
    }


    //==========================================================================================
    //=================================== Recursive JOIN =======================================
    //==========================================================================================


    @Override
    public List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex primaryInvertedList, IDocumentIndex secondaryInvertedList,
                                                             ISpatioTextualIndex secondaryTree, SKJoinQuery query, float spatialThreshold,
                                                             float textualThreshold, JoinConfiguration joinConfiguration) {
        JoinConfiguration config = Objects.requireNonNull(joinConfiguration, "joinConfiguration must not be null");
        JoinStrategy joinStrategy = config.getJoinStrategy();
        SimilarityType similarityType = config.getSimilarityType();
        QueryStrategy queryStrategy = config.getQueryStrategy();

        List<SKJoinQuery.Result> results = new ArrayList<>();

        // Start recursive traversal from root
        NNEntryPair rootEntry = new NNEntryPair(
                new RtreeEntry(tree.getRootIdentifier(), false),
                new RtreeEntry(secondaryTree.getRootIdentifier(), false),
                Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_VALUE);

        selfJoinSKQueryRecursiveTraversal(rootEntry, secondaryTree,
                primaryInvertedList, secondaryInvertedList, query, spatialThreshold,
                textualThreshold, joinStrategy, similarityType, queryStrategy, results);

        // Sort results by combined score (best first)(lower is better)
        Collections.sort(results);
        logger.debug("[Recursive] Number of results: {}", results.size());
        System.out.println("[Recursive] Using similarity: " + similarityType.getDescription() + " and query strategy: " + queryStrategy.getDescription());
//        invertedList.printStatistics();


        return results;
    }


    private void selfJoinSKQueryRecursiveTraversal(NNEntryPair pairEntry, ISpatioTextualIndex secondaryTree,
                                                   IDocumentIndex primaryInvertedList,
                                                   IDocumentIndex secondaryInvertedList,
                                                   SKJoinQuery query, float spatialThreshold, float textualThreshold,
                                                   JoinStrategy joinStrategy, SimilarityType similarityType,
                                                   QueryStrategy queryStrategy, List<SKJoinQuery.Result> results) {
        final int nodeIdX = pairEntry.entry1.getIdentifier();
        final int nodeIdY = pairEntry.entry2.getIdentifier();

        numOfVisitedNodes += 2;

        final Node nodeX = readNode(this.tree, nodeIdX);
        final Node nodeY = readNode(secondaryTree, nodeIdY);
        if (nodeX == null || nodeY == null) {
            logger.warn("Skipping traversal pair with missing node(s): x={}, y={}", nodeIdX, nodeIdY);
            return;
        }

        // Base case: both nodes are leaf nodes (level 0)
        if (nodeX.isLeaf() && nodeY.isLeaf()) {
            processLeafNodes(nodeX, nodeY, primaryInvertedList, secondaryInvertedList, query,
                    spatialThreshold, textualThreshold, similarityType, queryStrategy, results);
            return;
        }

        // XOR condition exactly one node is leaf
        if (nodeX.isLeaf() ^ nodeY.isLeaf()) {
            if (nodeX.isLeaf()) {
                expandAsymmetricRecursive(nodeX, nodeY, true,
                        primaryInvertedList, secondaryInvertedList, query, spatialThreshold, textualThreshold,
                        joinStrategy, similarityType, queryStrategy, secondaryTree, results);
            } else {
                expandAsymmetricRecursive(nodeY, nodeX, false,
                        primaryInvertedList, secondaryInvertedList, query, spatialThreshold, textualThreshold,
                        joinStrategy, similarityType, queryStrategy, secondaryTree, results);
            }
            return;
        }

        // Process internal nodes based on join strategy
        switch (joinStrategy) {
            case PLANE_SWEEP:
                processInternalNodesWithPlainSweep(nodeX, nodeY, primaryInvertedList, secondaryInvertedList, query,
                        spatialThreshold, textualThreshold, joinStrategy, similarityType, queryStrategy, secondaryTree, results);
                break;
            case DEFAULT:
                processInternalNodes(nodeX, nodeY, primaryInvertedList, secondaryInvertedList, query,
                        spatialThreshold, textualThreshold, joinStrategy, similarityType, queryStrategy, secondaryTree, results);
                break;
            default:
                throw new IllegalArgumentException("Unsupported join strategy: " + joinStrategy);
        }
    }


    private void processLeafNodes(Node nodeX, Node nodeY, IDocumentIndex primaryInvertedList,
                                  IDocumentIndex secondaryInvertedList, SKJoinQuery query,
                                  float spatialThreshold, float textualThreshold,
                                  SimilarityType similarityType,
                                  QueryStrategy queryStrategy,
                                  List<SKJoinQuery.Result> results) {

        // Pre-calculate textual relevance scores once
        NodePairRelevance relevance = null;
        if (textualThreshold > 0) {
            relevance = calculateNodePairRelevanceScores(
                    primaryInvertedList, secondaryInvertedList,
                    nodeX.getIdentifier(), nodeY.getIdentifier(),
                    query.getKeywords(), query.getKeywordWeights(),
                    similarityType, queryStrategy);

            if (relevance.isEmpty(queryStrategy)) {
                return;
            }
        }

        TreeMap<Integer, NodeEntry> entriesX = nodeX.getNodeEntries();
        TreeMap<Integer, NodeEntry> entriesY = nodeY.getNodeEntries();

        // Process all pairs between the two nodes
        for (Map.Entry<Integer, NodeEntry> entryX : entriesX.entrySet()) {
            int objectIdX = entryX.getKey();
            NodeEntry nodeEntryX = entryX.getValue();

            for (Map.Entry<Integer, NodeEntry> entryY : entriesY.entrySet()) {
                int objectIdY = entryY.getKey();
                NodeEntry nodeEntryY = entryY.getValue();

                // Calculate spatial distance between MBRs of the two entries
                double spatialDistance = nodeEntryX.getMBR().getMinimumDistance(nodeEntryY.getMBR());

                // Calculate textual similarity
                double textualSimilarity = 1.0;
                if (textualThreshold > 0) {
                    textualSimilarity = resolveLeafTextualSimilarity(
                            primaryInvertedList, secondaryInvertedList,
                            nodeX.getIdentifier(), objectIdX,
                            nodeY.getIdentifier(), objectIdY,
                            similarityType, queryStrategy, relevance);
                }

                if (shouldPruneByThresholds(spatialDistance, textualSimilarity, spatialThreshold, textualThreshold)) {
                    continue;
                }

                results.add(new SKJoinQuery.Result(objectIdX, objectIdY, spatialDistance, textualSimilarity,
                        combinedScore(spatialDistance, textualSimilarity)));
            }
        }
    }



    private void processInternalNodes(Node nodeX, Node nodeY, IDocumentIndex primaryInvertedList,
                                      IDocumentIndex secondaryInvertedList, SKJoinQuery query,
                                      float spatialThreshold, float textualThreshold,
                                      JoinStrategy joinStrategy, SimilarityType similarityType,
                                      QueryStrategy queryStrategy, ISpatioTextualIndex secondaryTree,
                                      List<SKJoinQuery.Result> results) {

        // Pre-calculate textual relevance scores once if textual threshold is active
        NodePairRelevance relevance = null;
        if (textualThreshold > 0) {

            relevance = calculateNodePairRelevanceScoresUpperBound(
                    primaryInvertedList, secondaryInvertedList,
                    nodeX.getIdentifier(), nodeY.getIdentifier(),
                    query.getKeywords(), query.getKeywordWeights(),
                    similarityType, queryStrategy);

            // Early exit if no textual relevance found
            if (relevance.isEmpty(queryStrategy)) {
                return;
            }
        }

        final boolean isLeafEntryX = nodeX.isLeaf();
        final boolean isLeafEntryY = nodeY.isLeaf();
        final TreeMap<Integer, NodeEntry> entriesX = nodeX.getNodeEntries();
        final TreeMap<Integer, NodeEntry> entriesY = nodeY.getNodeEntries();

        // Process all pairs between the two nodes
        for (Map.Entry<Integer, NodeEntry> entryX : entriesX.entrySet()) {
            final int childIdX = entryX.getKey();
            final Region mbrX = entryX.getValue().getMBR();

            for (Map.Entry<Integer, NodeEntry> entryY : entriesY.entrySet()) {
                final int childIdY = entryY.getKey();
                final Region mbrY = entryY.getValue().getMBR();

                // Early spatial pruning - calculate distance only once
                final double spatialDistance = mbrX.getMinimumDistance(mbrY);
                if (isLeafEntryX && isLeafEntryY && spatialDistance > spatialThreshold) {
                    continue;
                }

                // Calculate textual upper bound only if textual threshold is active
                double textualUpperBound = 1.0;
                if (textualThreshold > 0) {
                    textualUpperBound = resolveMultiSetTextualUpperBound(relevance, childIdX, childIdY, queryStrategy);
                }

                if (shouldPruneUpperBoundTraversal(spatialDistance, textualUpperBound,
                        spatialThreshold, textualThreshold, isLeafEntryX && isLeafEntryY)) {
                    continue;
                }

                // Create entries for recursive call
//                final RtreeEntry childEntryX = new RtreeEntry(childIdX, isLeafEntry);
//                final RtreeEntry childEntryY = new RtreeEntry(childIdY, isLeafEntry);
                final double combinedScore = combinedScore(spatialDistance, textualUpperBound);

                // Recursive traversal
                selfJoinSKQueryRecursiveTraversal(
                        new NNEntryPair(
                                new RtreeEntry(childIdX, isLeafEntryX),
                                new RtreeEntry(childIdY, isLeafEntryY),
                                spatialDistance, textualUpperBound, combinedScore),
                        secondaryTree,
                        primaryInvertedList, secondaryInvertedList, query, spatialThreshold,
                        textualThreshold, joinStrategy, similarityType, queryStrategy, results);
            }
        }
    }


    private void processInternalNodesWithPlainSweep(Node nodeX, Node nodeY, IDocumentIndex primaryInvertedList,
                                                    IDocumentIndex secondaryInvertedList, SKJoinQuery query,
                                                    float spatialThreshold, float textualThreshold,
                                                    JoinStrategy joinStrategy, SimilarityType similarityType,
                                                    QueryStrategy queryStrategy, ISpatioTextualIndex secondaryTree,
                                                    List<SKJoinQuery.Result> results) {

        // Pre-calculate textual relevance scores once if textual filtering is needed
        NodePairRelevance relevance = null;
        if (textualThreshold > 0) {
            relevance = calculateNodePairRelevanceScores(
                    primaryInvertedList, secondaryInvertedList,
                    nodeX.getIdentifier(), nodeY.getIdentifier(),
                    query.getKeywords(), query.getKeywordWeights(),
                    similarityType, queryStrategy);


            // Early exit if no textual relevance found
            if (relevance.isEmpty(queryStrategy)) {
                return;
            }
        }

        // Apply plane-sweep algorithm to find spatial candidates efficiently
//        float sweepTextualThreshold = thresholdPolicy == ThresholdPolicy.STRICT
//                ? textualThreshold
//                : 0.0f;
        final List<CandidatePair> candidates = generatePlaneSweepJoin(
                nodeX, nodeY, spatialThreshold, textualThreshold, null, false, queryStrategy);

        final boolean isLeafEntryX = nodeX.isLeaf();
        final boolean isLeafEntryY = nodeY.isLeaf();

        // Process each candidate pair recursively
        for (final CandidatePair candidate : candidates) {
            double textualUpperBound = 1.0;
            if (textualThreshold > 0) {
                textualUpperBound = isLeafEntryX && isLeafEntryY
                        ? resolveLeafTextualSimilarity(
                        primaryInvertedList, secondaryInvertedList,
                        nodeX.getIdentifier(), candidate.childIdX,
                        nodeY.getIdentifier(), candidate.childIdY,
                        similarityType, queryStrategy, relevance)
                        : resolveMultiSetTextualUpperBound(relevance, candidate.childIdX, candidate.childIdY, queryStrategy);
            }

            if (shouldPruneUpperBoundTraversal(candidate.spatialDistance, textualUpperBound,
                    spatialThreshold, textualThreshold, isLeafEntryX && isLeafEntryY)) {
                continue;
            }

            final double combinedScore = combinedScore(candidate.spatialDistance, textualUpperBound);
            selfJoinSKQueryRecursiveTraversal(
                    new NNEntryPair(
                            new RtreeEntry(candidate.childIdX, isLeafEntryX),
                            new RtreeEntry(candidate.childIdY, isLeafEntryY),
                            candidate.spatialDistance, textualUpperBound, combinedScore),
                    secondaryTree, primaryInvertedList, secondaryInvertedList, query,
                    spatialThreshold, textualThreshold, joinStrategy, similarityType, queryStrategy, results);
        }
    }


    @Override
    public int getVisitedNodes() {
        return this.numOfVisitedNodes;
    }
}

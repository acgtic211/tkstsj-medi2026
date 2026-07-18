package org.ual.spatiotextualindex.queries.baseline.join;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.BoundLimit;
import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.querytype.SKJoinQuery;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.*;

import java.util.*;

public class JoinTopKMultiSetQueryProcessor extends AbstractJoinQueryProcessor implements IJoinTopKQueryProcessor {
    private static final Logger logger = LogManager.getLogger(JoinTopKMultiSetQueryProcessor.class);

    public JoinTopKMultiSetQueryProcessor(AbstractIRTree tree) {
        super(tree);
    }

//    private long buildDirectionalPairKey(int leftId, int rightId) {
//        // Cross-dataset joins are directional: (A,B) and (B,A) are distinct pairs.
//        return (((long) leftId) << 32) | (rightId & 0xffffffffL);
//    }

//    private Node readNode(ISpatioTextualIndex targetTree, int nodeId) {
//        return targetTree.readNode(nodeId);
//    }

    public List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex primaryInvertedList,
                                                              IDocumentIndex secondaryInvertedList,
                                                              ISpatioTextualIndex secondaryTree,
                                                              SKJoinQuery query,
                                                              int topK,
                                                              JoinConfiguration joinConfiguration) {
        numOfVisitedNodes = 0;
        int maxPriorityQ = 0;
        JoinConfiguration config = Objects.requireNonNull(joinConfiguration, "joinConfiguration must not be null");
        if (topK <= 0) {
            return Collections.emptyList();
        }

        SimilarityType similarityType = config.getSimilarityType(); // Weighted Jaccard, Cosine or Weighted SUM
        JoinStrategy joinStrategy = config.getJoinStrategy();   // Plain Sweep or Default (smart)
        QueryStrategy queryStrategy = QueryStrategy.orDefault(config.getQueryStrategy()); // Constraint Join Variant or Full Join
        Region spatialWindow = resolveSpatialWindow(query, queryStrategy);
        if (queryStrategy.usesSpatialWindowConstraint() && spatialWindow == null) {
            logger.warn("[BestFirst][MultiSet] CONSTRAINT_SPATIAL_JOIN requires query.spatialWindow. Returning empty result set.");
            return Collections.emptyList();
        }

        PriorityQueue<NNEntryPair> queue = new PriorityQueue<>(new NNEntryPairComparatorCombinedCost());
        queue.add(new NNEntryPair(new RtreeEntry(tree.getRootIdentifier(), false),
                new RtreeEntry(secondaryTree.getRootIdentifier(), false),
                0.0, 1.0, 0.0));

        TopKBuffer topKBuffer = new TopKBuffer(topK);
        Map<Long, NodePairRelevance> relevanceCache = new HashMap<>();

        while (!queue.isEmpty()) {
            maxPriorityQ = Math.max(maxPriorityQ, queue.size());
            NNEntryPair current = queue.poll();
            if (topKBuffer.shouldPrune(current.combinedCost)) break;

            RtreeEntry entryX = current.entry1;
            RtreeEntry entryY = current.entry2;

            if (entryX.isLeafEntry && entryY.isLeafEntry) {
                int objectIdX = entryX.getIdentifier();
                int objectIdY = entryY.getIdentifier();
                if (!topKBuffer.markProcessedPair(objectIdX, objectIdY)) continue;
                if (entryX.treeId < 0 || entryY.treeId < 0) continue;

                // Spatial Constraint Filtering
                if (queryStrategy.usesSpatialWindowConstraint()) {
                    Region objectMbrX = resolveObjectMBR(this.tree, entryX.treeId, objectIdX);
                    Region objectMbrY = resolveObjectMBR(secondaryTree, entryY.treeId, objectIdY);
                    if (!pairInsideSpatialWindow(objectMbrX, objectMbrY, spatialWindow)) {
                        continue;
                    }
                }

                NodePairRelevance leafRelevance = getOrComputeRelevance(relevanceCache,
                        primaryInvertedList, secondaryInvertedList,
                        entryX.treeId, entryY.treeId,
                        query, similarityType, queryStrategy);
                if (leafRelevance.isEmpty(queryStrategy)) continue;

                double textualSimilarity = resolveLeafTextualSimilarity(primaryInvertedList, secondaryInvertedList,
                        entryX.treeId, objectIdX, entryY.treeId, objectIdY,
                        similarityType, queryStrategy, leafRelevance);
                if (textualSimilarity <= 0.0) continue;

                double combinedCost = combinedScore(current.spatialCost, textualSimilarity);
                if (topKBuffer.shouldPrune(combinedCost)) continue;

                topKBuffer.offer(new SKJoinQuery.Result(objectIdX, objectIdY, current.spatialCost, textualSimilarity, combinedCost));
                numOfVisitedNodes++;
                continue;
            }

            Node nodeX = readNode(this.tree, entryX.getIdentifier());
            Node nodeY = readNode(secondaryTree, entryY.getIdentifier());
            if (nodeX == null || nodeY == null) continue;
            numOfVisitedNodes += 2;

            if (!pairIntersectsSpatialWindow(nodeX.getMBR(), nodeY.getMBR(), spatialWindow, queryStrategy)) {
                continue;
            }

            if (nodeX.isLeaf() ^ nodeY.isLeaf()) {
                if (nodeX.isLeaf()) {
                    expandAsymmetricBestFirst(nodeX, nodeY, true, primaryInvertedList, secondaryInvertedList,
                            query, similarityType, queryStrategy, spatialWindow, queue, topKBuffer, relevanceCache);
                } else {
                    expandAsymmetricBestFirst(nodeY, nodeX, false, primaryInvertedList, secondaryInvertedList,
                            query, similarityType, queryStrategy, spatialWindow, queue, topKBuffer, relevanceCache);
                }
                continue;
            }

            NodePairRelevance relevance = getOrComputeRelevance(relevanceCache,
                    primaryInvertedList, secondaryInvertedList,
                    nodeX.getIdentifier(), nodeY.getIdentifier(),
                    query, similarityType, queryStrategy);
            if (relevance.isEmpty(queryStrategy)) continue;

            List<CandidatePair> candidates = (joinStrategy == JoinStrategy.PLANE_SWEEP)
                    ? generatePlaneSweepJoinTopK(nodeX, nodeY, relevance, queryStrategy, spatialWindow)
                    : generateNestedCandidates(nodeX, nodeY, relevance, queryStrategy, spatialWindow);

            boolean isLeafEntryX = nodeX.isLeaf();
            boolean isLeafEntryY = nodeY.isLeaf();

            for (CandidatePair candidate : candidates) {
                double textualUpperBound = candidate.textualUpperBound;
                if (isLeafEntryX && isLeafEntryY) {
                    textualUpperBound = resolveLeafTextualSimilarity(primaryInvertedList, secondaryInvertedList,
                            nodeX.getIdentifier(), candidate.childIdX,
                            nodeY.getIdentifier(), candidate.childIdY,
                            similarityType, queryStrategy, relevance);
                }
                if (textualUpperBound <= 0.0) continue;

                double lowerBound = combinedScore(candidate.spatialDistance, textualUpperBound);
                if (topKBuffer.shouldPrune(lowerBound)) continue;

                RtreeEntry childX = new RtreeEntry(candidate.childIdX, isLeafEntryX);
                RtreeEntry childY = new RtreeEntry(candidate.childIdY, isLeafEntryY);
                if (isLeafEntryX) childX.treeId = nodeX.getIdentifier();
                if (isLeafEntryY) childY.treeId = nodeY.getIdentifier();

                queue.add(new NNEntryPair(childX, childY, candidate.spatialDistance, textualUpperBound, lowerBound));
            }
        }

        List<SKJoinQuery.Result> results = topKBuffer.asSortedResults();
        logger.info("[BestFirst][MultiSet] results={} visitedNodes={} maxPriorityQ={}", results.size(), numOfVisitedNodes, maxPriorityQ);
        return results;
    }

    public List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex primaryInvertedList,
                                                             IDocumentIndex secondaryInvertedList,
                                                             ISpatioTextualIndex secondaryTree,
                                                             SKJoinQuery query,
                                                             int topK,
                                                             JoinConfiguration joinConfiguration) {
        numOfVisitedNodes = 0;
        JoinConfiguration config = Objects.requireNonNull(joinConfiguration, "joinConfiguration must not be null");
        if (topK <= 0) return Collections.emptyList();

        TopKBuffer topKBuffer = new TopKBuffer(topK);
        Map<Long, NodePairRelevance> relevanceCache = new HashMap<>();
        QueryStrategy queryStrategy = QueryStrategy.orDefault(config.getQueryStrategy());
        Region spatialWindow = resolveSpatialWindow(query, queryStrategy);
        if (queryStrategy.usesSpatialWindowConstraint() && spatialWindow == null) {
            logger.warn("[Recursive][MultiSet] CONSTRAINT_SPATIAL_JOIN requires query.spatialWindow. Returning empty result set.");
            return Collections.emptyList();
        }

        recursiveTraversal(new NNEntryPair(
                        new RtreeEntry(tree.getRootIdentifier(), false),
                        new RtreeEntry(secondaryTree.getRootIdentifier(), false),
                        0.0, 1.0, 0.0),
                secondaryTree, primaryInvertedList, secondaryInvertedList, query,
                config.getJoinStrategy(), config.getSimilarityType(), queryStrategy,
                spatialWindow,
                topKBuffer, relevanceCache);

        List<SKJoinQuery.Result> results = topKBuffer.asSortedResults();
        logger.info("[Recursive][MultiSet] results={} visitedNodes={}", results.size(), numOfVisitedNodes);
        return results;
    }

    private void recursiveTraversal(NNEntryPair pair,
                                    ISpatioTextualIndex secondaryTree,
                                    IDocumentIndex primaryInvertedList,
                                    IDocumentIndex secondaryInvertedList,
                                    SKJoinQuery query,
                                    JoinStrategy joinStrategy,
                                    SimilarityType similarityType,
                                    QueryStrategy queryStrategy,
                                    Region spatialWindow,
                                    TopKBuffer topKBuffer,
                                    Map<Long, NodePairRelevance> relevanceCache) {
        Node nodeX = readNode(this.tree, pair.entry1.getIdentifier());
        Node nodeY = readNode(secondaryTree, pair.entry2.getIdentifier());
        if (nodeX == null || nodeY == null) return;
        numOfVisitedNodes += 2;

        if (!pairIntersectsSpatialWindow(nodeX.getMBR(), nodeY.getMBR(), spatialWindow, queryStrategy)) return;

        NodePairRelevance relevance = getOrComputeRelevance(relevanceCache,
                primaryInvertedList, secondaryInvertedList,
                nodeX.getIdentifier(), nodeY.getIdentifier(),
                query, similarityType, queryStrategy);
        if (relevance.isEmpty(queryStrategy)) return;

        double nodeLowerBound = combinedScore(nodeX.getMBR().getMinimumDistance(nodeY.getMBR()),
                resolveNodeTextualUpperBound(relevance, queryStrategy));
        if (topKBuffer.shouldPrune(nodeLowerBound)) return;

        if (nodeX.isLeaf() && nodeY.isLeaf()) {
            for (Map.Entry<Integer, NodeEntry> entryX : nodeX.getNodeEntries().entrySet()) {
                for (Map.Entry<Integer, NodeEntry> entryY : nodeY.getNodeEntries().entrySet()) {
                    int objectIdX = entryX.getKey();
                    int objectIdY = entryY.getKey();
                    if (!topKBuffer.markProcessedPair(objectIdX, objectIdY)) continue;

                    if (queryStrategy.usesSpatialWindowConstraint()
                            && !pairInsideSpatialWindow(entryX.getValue().getMBR(), entryY.getValue().getMBR(), spatialWindow)) {
                        continue;
                    }

                    double textualSimilarity = resolveLeafTextualSimilarity(primaryInvertedList, secondaryInvertedList,
                            nodeX.getIdentifier(), objectIdX, nodeY.getIdentifier(), objectIdY,
                            similarityType, queryStrategy, relevance);
                    if (textualSimilarity <= 0.0) continue;

                    double spatialDistance = entryX.getValue().getMBR().getMinimumDistance(entryY.getValue().getMBR());
                    double combined = combinedScore(spatialDistance, textualSimilarity);
                    if (topKBuffer.shouldPrune(combined)) continue;

                    topKBuffer.offer(new SKJoinQuery.Result(objectIdX, objectIdY, spatialDistance, textualSimilarity, combined));
                }
            }
            return;
        }

        if (nodeX.isLeaf() ^ nodeY.isLeaf()) {
            if (nodeX.isLeaf()) {
                expandAsymmetricRecursive(nodeX, nodeY, true, secondaryTree,
                        primaryInvertedList, secondaryInvertedList,
                        query, joinStrategy, similarityType, queryStrategy,
                        spatialWindow, topKBuffer, relevanceCache);
            } else {
                expandAsymmetricRecursive(nodeY, nodeX, false, secondaryTree,
                        primaryInvertedList, secondaryInvertedList,
                        query, joinStrategy, similarityType, queryStrategy,
                        spatialWindow, topKBuffer, relevanceCache);
            }
            return;
        }

        List<CandidatePair> candidates = (joinStrategy == JoinStrategy.PLANE_SWEEP)
                ? generatePlaneSweepJoinTopK(nodeX, nodeY, relevance, queryStrategy, spatialWindow)
                : generateNestedCandidates(nodeX, nodeY, relevance, queryStrategy, spatialWindow);

        for (CandidatePair candidate : candidates) {
            double lowerBound = combinedScore(candidate.spatialDistance, candidate.textualUpperBound);
            if (topKBuffer.shouldPrune(lowerBound)) continue;

            recursiveTraversal(new NNEntryPair(
                            new RtreeEntry(candidate.childIdX, nodeX.isLeaf()),
                            new RtreeEntry(candidate.childIdY, nodeY.isLeaf()),
                            candidate.spatialDistance,
                            candidate.textualUpperBound,
                            lowerBound),
                    secondaryTree, primaryInvertedList, secondaryInvertedList, query,
                    joinStrategy, similarityType, queryStrategy, spatialWindow, topKBuffer, relevanceCache);
        }
    }

    private void expandAsymmetricBestFirst(Node leafNode,
                                           Node innerNode,
                                           boolean leafOnX,
                                           IDocumentIndex primaryInvertedList,
                                           IDocumentIndex secondaryInvertedList,
                                           SKJoinQuery query,
                                           SimilarityType similarityType,
                                           QueryStrategy queryStrategy,
                                           Region spatialWindow,
                                           PriorityQueue<NNEntryPair> queue,
                                           TopKBuffer topKBuffer,
                                           Map<Long, NodePairRelevance> relevanceCache) {
        NodePairRelevance relevance = getOrComputeRelevance(relevanceCache,
                primaryInvertedList, secondaryInvertedList,
                leafNode.getIdentifier(), innerNode.getIdentifier(),
                query, similarityType, queryStrategy);
        if (relevance.isEmpty(queryStrategy)) return;

        double leafMax = QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity()
                ? relevance.fullJoinUpperBound
                : (leafOnX ? maxScore(relevance.leftScores) : maxScore(relevance.rightScores));
        if (leafMax <= 0.0) return;

        for (Map.Entry<Integer, NodeEntry> child : innerNode.getNodeEntries().entrySet()) {
            int childId = child.getKey();
            Region childMbr = child.getValue().getMBR();
            if (!pairIntersectsSpatialWindow(leafNode.getMBR(), childMbr, spatialWindow, queryStrategy)) {
                continue;
            }

            double childScore = QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity()
                    ? relevance.fullJoinUpperBound
                    : (leafOnX ? relevance.rightScores.getOrDefault(childId, 0.0)
                    : relevance.leftScores.getOrDefault(childId, 0.0));
            double textualUpperBound = Math.min(leafMax, childScore);
            if (textualUpperBound <= 0.0) continue;

            double spatialDistance = leafNode.getMBR().getMinimumDistance(childMbr);
            double lowerBound = combinedScore(spatialDistance, textualUpperBound);
            if (topKBuffer.shouldPrune(lowerBound)) continue;

            if (leafOnX) {
                queue.add(new NNEntryPair(new RtreeEntry(leafNode.getIdentifier(), false),
                        new RtreeEntry(childId, false),
                        spatialDistance, textualUpperBound, lowerBound));
            } else {
                queue.add(new NNEntryPair(new RtreeEntry(childId, false),
                        new RtreeEntry(leafNode.getIdentifier(), false),
                        spatialDistance, textualUpperBound, lowerBound));
            }
        }
    }

    private void expandAsymmetricRecursive(Node leafNode,
                                           Node innerNode,
                                           boolean leafOnX,
                                           ISpatioTextualIndex secondaryTree,
                                           IDocumentIndex primaryInvertedList,
                                           IDocumentIndex secondaryInvertedList,
                                           SKJoinQuery query,
                                           JoinStrategy joinStrategy,
                                           SimilarityType similarityType,
                                           QueryStrategy queryStrategy,
                                           Region spatialWindow,
                                           TopKBuffer topKBuffer,
                                           Map<Long, NodePairRelevance> relevanceCache) {
        NodePairRelevance relevance = getOrComputeRelevance(relevanceCache,
                primaryInvertedList, secondaryInvertedList,
                leafNode.getIdentifier(), innerNode.getIdentifier(),
                query, similarityType, queryStrategy);
        if (relevance.isEmpty(queryStrategy)) return;

        double leafMax = QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity()
                ? relevance.fullJoinUpperBound
                : (leafOnX ? maxScore(relevance.leftScores) : maxScore(relevance.rightScores));
        if (leafMax <= 0.0) return;

        for (Map.Entry<Integer, NodeEntry> child : innerNode.getNodeEntries().entrySet()) {
            int childId = child.getKey();
            Region childMbr = child.getValue().getMBR();
            if (!pairIntersectsSpatialWindow(leafNode.getMBR(), childMbr, spatialWindow, queryStrategy)) {
                continue;
            }

            double childScore = QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity()
                    ? relevance.fullJoinUpperBound
                    : (leafOnX ? relevance.rightScores.getOrDefault(childId, 0.0)
                    : relevance.leftScores.getOrDefault(childId, 0.0));
            double textualUpperBound = Math.min(leafMax, childScore);
            if (textualUpperBound <= 0.0) continue;

            double spatialDistance = leafNode.getMBR().getMinimumDistance(childMbr);
            double lowerBound = combinedScore(spatialDistance, textualUpperBound);
            if (topKBuffer.shouldPrune(lowerBound)) continue;

            NNEntryPair next = leafOnX
                    ? new NNEntryPair(new RtreeEntry(leafNode.getIdentifier(), false), new RtreeEntry(childId, false), spatialDistance, textualUpperBound, lowerBound)
                    : new NNEntryPair(new RtreeEntry(childId, false), new RtreeEntry(leafNode.getIdentifier(), false), spatialDistance, textualUpperBound, lowerBound);

            recursiveTraversal(next, secondaryTree, primaryInvertedList, secondaryInvertedList, query,
                    joinStrategy, similarityType, queryStrategy, spatialWindow, topKBuffer, relevanceCache);
        }
    }

    private List<CandidatePair> generateNestedCandidates(Node nodeX,
                                                         Node nodeY,
                                                         NodePairRelevance relevance,
                                                         QueryStrategy queryStrategy,
                                                         Region spatialWindow) {
        List<CandidatePair> candidates = new ArrayList<>();
        for (Map.Entry<Integer, NodeEntry> childX : nodeX.getNodeEntries().entrySet()) {
            int childIdX = childX.getKey();
            Region mbrX = childX.getValue().getMBR();
            for (Map.Entry<Integer, NodeEntry> childY : nodeY.getNodeEntries().entrySet()) {
                int childIdY = childY.getKey();
                Region mbrY = childY.getValue().getMBR();
                if (!pairIntersectsSpatialWindow(mbrX, mbrY, spatialWindow, queryStrategy)) continue;
                double textualUpperBound = resolveMultiSetTextualUpperBound(relevance, childIdX, childIdY, queryStrategy);
                if (textualUpperBound <= 0.0) continue;
                candidates.add(new CandidatePair(childIdX, childIdY, mbrX.getMinimumDistance(mbrY), textualUpperBound));
            }
        }
        return candidates;
    }

//    private List<CandidatePair> generatePlaneSweepJoinTopK(Node nodeX,
//                                                            Node nodeY,
//                                                            NodePairRelevance relevance,
//                                                            QueryStrategy queryStrategy) {
//        List<CandidatePair> candidates = new ArrayList<>();
//        List<SweepEvent> events = new ArrayList<>();
//
//        // Add events for nodeX children
//        for (Map.Entry<Integer, NodeEntry> entry : nodeX.getNodeEntries().entrySet()) {
//            events.add(new SweepEvent(entry.getKey(), entry.getValue().getMBR(), 1));
//        }
//
//        // Add events for nodeY children
//        for (Map.Entry<Integer, NodeEntry> entry : nodeY.getNodeEntries().entrySet()) {
//            events.add(new SweepEvent(entry.getKey(), entry.getValue().getMBR(), 2));
//        }
//
//        // Sort by x-coordinate for sweep line algorithm
//        events.sort(Comparator.comparingDouble(e -> e.mbr.getMBR().getMinX()));
//
//        List<SweepEvent> activeX = new ArrayList<>();
//        List<SweepEvent> activeY = new ArrayList<>();
//
//        for (SweepEvent event : events) {
//            if (event.source == 1) {
//                for (SweepEvent right : activeY) {
//                    double textualUpperBound = resolveMultiSetTextualUpperBound(relevance, event.childId, right.childId, queryStrategy);
//                    if (textualUpperBound > 0.0) {
//                        candidates.add(new CandidatePair(event.childId, right.childId, event.mbr.getMinimumDistance(right.mbr), textualUpperBound));
//                    }
//                }
//                activeX.add(event);
//            } else {
//                for (SweepEvent left : activeX) {
//                    double textualUpperBound = resolveMultiSetTextualUpperBound(relevance, left.childId, event.childId, queryStrategy);
//                    if (textualUpperBound > 0.0) {
//                        candidates.add(new CandidatePair(left.childId, event.childId, left.mbr.getMinimumDistance(event.mbr), textualUpperBound));
//                    }
//                }
//                activeY.add(event);
//            }
//        }
//
//        return candidates;
//    }

    private List<CandidatePair> generatePlaneSweepJoinTopK(Node nodeX,
                                                        Node nodeY,
                                                        NodePairRelevance relevance,
                                                        QueryStrategy queryStrategy,
                                                        Region spatialWindow) {
        List<CandidatePair> candidates = new ArrayList<>();
        List<SweepEvent> events = new ArrayList<>();

        // Add events for nodeX children
        for (Map.Entry<Integer, NodeEntry> entry : nodeX.getNodeEntries().entrySet()) {
            events.add(new SweepEvent(entry.getKey(), entry.getValue().getMBR(), 1));
        }

        // Add events for nodeY children
        for (Map.Entry<Integer, NodeEntry> entry : nodeY.getNodeEntries().entrySet()) {
            events.add(new SweepEvent(entry.getKey(), entry.getValue().getMBR(), 2));
        }

        // Sort by x-coordinate for sweep line algorithm
        events.sort(Comparator.comparingDouble(e -> e.mbr.getMBR().getMinX()));

        List<SweepEvent> activeX = new ArrayList<>();
        List<SweepEvent> activeY = new ArrayList<>();

        for (SweepEvent event : events) {
            if (event.source == 1) {
                for (SweepEvent right : activeY) {
                    if (!pairIntersectsSpatialWindow(event.mbr.getMBR(), right.mbr.getMBR(), spatialWindow, queryStrategy)) {
                        continue;
                    }
                    double textualUpperBound = resolveMultiSetTextualUpperBound(relevance, event.childId, right.childId, queryStrategy);
                    if (textualUpperBound > 0.0) {
                        candidates.add(new CandidatePair(event.childId, right.childId, event.mbr.getMinimumDistance(right.mbr), textualUpperBound));
                    }
                }
                activeX.add(event);
            } else {
                for (SweepEvent left : activeX) {
                    if (!pairIntersectsSpatialWindow(left.mbr.getMBR(), event.mbr.getMBR(), spatialWindow, queryStrategy)) {
                        continue;
                    }
                    double textualUpperBound = resolveMultiSetTextualUpperBound(relevance, left.childId, event.childId, queryStrategy);
                    if (textualUpperBound > 0.0) {
                        candidates.add(new CandidatePair(left.childId, event.childId, left.mbr.getMinimumDistance(event.mbr), textualUpperBound));
                    }
                }
                activeY.add(event);
            }
        }

        return candidates;
    }

    private NodePairRelevance getOrComputeRelevance(Map<Long, NodePairRelevance> cache,
                                                    IDocumentIndex primaryInvertedList,
                                                    IDocumentIndex secondaryInvertedList,
                                                    int nodeIdX,
                                                    int nodeIdY,
                                                    SKJoinQuery query,
                                                    SimilarityType similarityType,
                                                    QueryStrategy queryStrategy) {
        long key = buildDirectionalPairKey(nodeIdX, nodeIdY);
        NodePairRelevance relevance = cache.get(key);
        if (relevance != null) return relevance;

        relevance = calculateNodePairRelevanceScores(primaryInvertedList, secondaryInvertedList,
                nodeIdX, nodeIdY,
                query.getKeywords(), query.getKeywordWeights(),
                similarityType, queryStrategy);
        cache.put(key, relevance);
        return relevance;
    }

    private NodePairRelevance calculateNodePairRelevanceScores(IDocumentIndex primaryInvertedList,
                                                               IDocumentIndex secondaryInvertedList,
                                                               int nodeIdX,
                                                               int nodeIdY,
                                                               List<Integer> keywords,
                                                               List<Double> keywordWeights,
                                                               SimilarityType similarityType,
                                                               QueryStrategy queryStrategy) {
        QueryStrategy effectiveStrategy = QueryStrategy.orDefault(queryStrategy);
        if (effectiveStrategy.usesExactTextualSimilarity()) {
            double ub = primaryInvertedList.crossDatasetTextualSim(
                    nodeIdX, nodeIdY, secondaryInvertedList, similarityType, BoundLimit.UPPER_BOUND);
            return new NodePairRelevance(Collections.emptyMap(), Collections.emptyMap(), Math.max(0.0, ub));
        }

        Map<Integer, Double> leftScores = primaryInvertedList.crossDatasetConstraintTextualSim(
                nodeIdX, keywords, keywordWeights, similarityType);
        Map<Integer, Double> rightScores = secondaryInvertedList.crossDatasetConstraintTextualSim(
                nodeIdY, keywords, keywordWeights, similarityType);

        return new NodePairRelevance(leftScores, rightScores, 0.0);
    }

    private double resolveNodeTextualUpperBound(NodePairRelevance relevance, QueryStrategy queryStrategy) {
        if (QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity()) {
            return relevance.fullJoinUpperBound;
        }
        return Math.min(maxScore(relevance.leftScores), maxScore(relevance.rightScores));
    }

    private double resolveMultiSetTextualUpperBound(NodePairRelevance relevance,
                                                    int childIdX,
                                                    int childIdY,
                                                    QueryStrategy queryStrategy) {
        if (QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity()) {
            return relevance.fullJoinUpperBound;
        }
        return Math.min(relevance.leftScores.getOrDefault(childIdX, 0.0), relevance.rightScores.getOrDefault(childIdY, 0.0));
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
        if (QueryStrategy.orDefault(queryStrategy).usesExactTextualSimilarity()) {
            return primaryInvertedList.crossDatasetDocumentSim(
                    leafNodeIdX, leafNodeIdY, objectIdX, objectIdY, secondaryInvertedList, similarityType);
        }
        return resolveMultiSetTextualUpperBound(relevance, objectIdX, objectIdY, queryStrategy);
    }

    private Region resolveSpatialWindow(SKJoinQuery query, QueryStrategy queryStrategy) {
        if (!QueryStrategy.orDefault(queryStrategy).usesSpatialWindowConstraint()) {
            return null;
        }
        return query == null ? null : query.getSpatialWindow();
    }

    private boolean pairIntersectsSpatialWindow(Region mbrX, Region mbrY, Region spatialWindow, QueryStrategy queryStrategy) {
        if (!QueryStrategy.orDefault(queryStrategy).usesSpatialWindowConstraint()) {
            return true;
        }
        if (spatialWindow == null || mbrX == null || mbrY == null) {
            return false;
        }
        // Keep traversal permissive to avoid false negatives.
        return spatialWindow.intersects(mbrX) || spatialWindow.intersects(mbrY);
    }

    private boolean pairInsideSpatialWindow(Region mbrX, Region mbrY, Region spatialWindow) {
        if (spatialWindow == null || mbrX == null || mbrY == null) {
            return false;
        }
        //return mbrX != null && mbrY != null && spatialWindow.contains(mbrX) && spatialWindow.contains(mbrY);
        // Accept partial overlap at object level.
        //return spatialWindow.intersects(mbrX) || spatialWindow.intersects(mbrY);
        //return spatialWindow.intersects(mbrX) && spatialWindow.intersects(mbrY);
        return spatialWindow.contains(mbrX) && spatialWindow.contains(mbrY);
    }

    private Region resolveObjectMBR(ISpatioTextualIndex targetTree, int leafNodeId, int objectId) {
        Node leafNode = readNode(targetTree, leafNodeId);
        if (leafNode == null) {
            return null;
        }
        NodeEntry entry = leafNode.getNodeEntries().get(objectId);
        return entry == null ? null : entry.getMBR();
    }

//    private double maxScore(Map<Integer, Double> scores) {
//        double max = 0.0;
//        for (double score : scores.values()) {
//            max = Math.max(max, score);
//        }
//        return max;
//    }

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

    private static class TopKBuffer {
        private final int topK;
        private final PriorityQueue<SKJoinQuery.Result> results;
        private final Set<Long> processedPairs;
        private double tau;

        private TopKBuffer(int topK) {
            this.topK = topK;
            this.results = new PriorityQueue<>(Comparator.comparingDouble(SKJoinQuery.Result::getCombineCost).reversed());
            this.processedPairs = new HashSet<>();
            this.tau = Double.POSITIVE_INFINITY;
        }

        private boolean isFull() {
            return results.size() >= topK;
        }

        private boolean shouldPrune(double lowerBoundCost) {
            return isFull() && lowerBoundCost >= tau;
        }

        private boolean markProcessedPair(int id1, int id2) {
            long key = (((long) id1) << 32) | (id2 & 0xffffffffL);
            return processedPairs.add(key);
        }

        private void offer(SKJoinQuery.Result candidate) {
            if (!isFull()) {
                results.add(candidate);
                if (isFull() && results.peek() != null) {
                    tau = results.peek().getCombineCost();
                }
                return;
            }

            if (candidate.getCombineCost() < tau) {
                results.poll();
                results.add(candidate);
                SKJoinQuery.Result worst = results.peek();
                tau = (worst == null) ? Double.POSITIVE_INFINITY : worst.getCombineCost();
            }
        }

        private List<SKJoinQuery.Result> asSortedResults() {
            List<SKJoinQuery.Result> sorted = new ArrayList<>(results);
            Collections.sort(sorted);
            return sorted;
        }
    }

//    private static class CandidatePair {
//        final int childIdX;
//        final int childIdY;
//        final double spatialDistance;
//        final double textualUpperBound;
//
//        CandidatePair(int childIdX, int childIdY, double spatialDistance, double textualUpperBound) {
//            this.childIdX = childIdX;
//            this.childIdY = childIdY;
//            this.spatialDistance = spatialDistance;
//            this.textualUpperBound = textualUpperBound;
//        }
//    }

    private static class SweepEvent {
        final int childId;
        final IShape mbr;
        final int source; // 1 for nodeX, 2 for nodeY

        SweepEvent(int childId, IShape mbr, int source) {
            this.childId = childId;
            this.mbr = mbr;
            this.source = source;
        }

    }
}

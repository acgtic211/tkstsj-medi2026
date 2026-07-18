package org.ual.spatiotextualindex.queries.baseline.join;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.IDocumentIndex;
import org.ual.documentindex.SimilarityType;
import org.ual.querytype.SKJoinQuery;
import org.ual.spatialindex.rtreebase.Node;
import org.ual.spatialindex.spatialindex.*;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.*;

import java.util.*;

public class JoinTopKQueryProcessor extends AbstractJoinQueryProcessor implements IJoinTopKQueryProcessor {
    private static final Logger logger = LogManager.getLogger(JoinTopKQueryProcessor.class);

    public JoinTopKQueryProcessor(AbstractIRTree tree) {
        super(tree);
    }

//    private long buildOrderedPairKey(int id1, int id2) {
//        int minId = Math.min(id1, id2);
//        int maxId = Math.max(id1, id2);
//        return (((long) minId) << 32) | (maxId & 0xffffffffL);
//    }


//    private boolean shouldPruneByThresholds(double spatialDistance, double textualSimilarity,
//                                            float spatialThreshold, float textualThreshold) {
//        // Pairs with zero textual overlap must be pruned.
//        if (textualThreshold > 0 && textualSimilarity <= 0.0) {
//            return true;
//        }
//
//        // Prune by spatial distance
//        if (spatialDistance > spatialThreshold) {
//            return true;
//        }
//
//        return textualThreshold > 0 && textualSimilarity < textualThreshold;
//
//    }

//    protected double resolveTextualUpperBound(Map<Integer, Double> relevanceScores, int childIdX, int childIdY, QueryStrategy queryStrategy) {
//        if (relevanceScores == null || relevanceScores.isEmpty()) {
//            return 0.0;
//        }
//
//        if (QueryStrategy.orDefault(queryStrategy) == QueryStrategy.FULL_JOIN) {
//            double max = 0.0;
//            for (double v : relevanceScores.values()) {
//                max = Math.max(max, v);
//            }
//            return max;
//        }
//
//        return pairTextualUpperBound(relevanceScores, childIdX, childIdY);
//    }

    private double resolveNodeTextualUpperBound(Map<Integer, Double> relevanceScores) {
        if (relevanceScores == null || relevanceScores.isEmpty()) {
            return 0.0;
        }
        double max = 0.0;
        for (double score : relevanceScores.values()) {
            max = Math.max(max, score);
        }
        return max;
    }

//    private double resolveLeafTextualSimilarity(IDocumentIndex invertedList,
//                                                int leafNodeIdX,
//                                                int objectIdX,
//                                                int leafNodeIdY,
//                                                int objectIdY,
//                                                SimilarityType similarityType,
//                                                QueryStrategy queryStrategy,
//                                                Map<Integer, Double> relevanceScores) {
//        if (QueryStrategy.orDefault(queryStrategy) == QueryStrategy.FULL_JOIN) {
//            return invertedList.nodesDocumentSim(
//                    leafNodeIdX, leafNodeIdY,
//                    objectIdX, objectIdY,
//                    similarityType);
//        }
//
//        return resolveTextualUpperBound(relevanceScores, objectIdX, objectIdY, queryStrategy);
//    }

//    private Map<Integer, Double> calculateNodePairRelevanceScores(IDocumentIndex invertedList, int nodeIdX, int nodeIdY,
//                                                                  List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType,
//                                                                  QueryStrategy queryStrategy) {
//        if (QueryStrategy.orDefault(queryStrategy) == QueryStrategy.FULL_JOIN) {
//            double score = invertedList.nodesTextualSim(nodeIdX, nodeIdY, similarityType);//, spatialThreshold);
//            if (score <= 0.0) {
//                return Collections.emptyMap();
//            }
//            return Collections.singletonMap(-1, score);
//        }
//
//        Map<Integer, Double> scores = invertedList.nodesConstraintTextualSim(
//                nodeIdX, nodeIdY, keywords, keywordWeights, similarityType);
//
//        return (scores == null) ? Collections.emptyMap() : scores;
//    }


    ///////////////////////////////////////////


//    private double calculateUpperBound(IDocumentIndex documentIndex, Node nodeA, Node nodeB, double alpha, SimilarityType similarityType) {
//        // Spatial Sim
//        // Calculate minDistance between the two nodes, normalized to [0, 1]
//        double maxSpatialSim = calculateMaxSpatialSimilarity(nodeA, nodeB);
//
//        // Textual Sim
//        double maxTextualSim = documentIndex.nodesTextualSim(nodeA.getIdentifier(), nodeB.getIdentifier(), similarityType);
//
//        // Combined Spatio-Textual Sim
//        return (alpha * maxSpatialSim) + (1.0 - alpha) * maxTextualSim;
//    }

//    private double calculateMaxSpatialSimilarity(Node nodeA, Node nodeB) {
//        final double minDistance = nodeA.getMBR().getMinimumDistance(nodeB.getMBR());
//        final double maxDistance = nodeA.getMBR().getMaximumDistance(nodeB.getMBR());
//
//        if (maxDistance <= 0.0) {
//            return (minDistance <= 0.0) ? 1.0 : 0.0;
//        }
//
//        final double similarity = 1.0 - (minDistance / maxDistance);
//        return Math.max(0.0, Math.min(1.0, similarity));
//    }
//
//    private double calculateSTSimilarity(Node nodeA, Node nodeB) {
//
//    }



    //==========================================================================================
    //=============================== Best-First JOIN SK Queries ===============================
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
     *   <li>Supports different threshold adjustment strategies and spatial constraints</li>
     * </ul>
     *
     * @param invertedList Document index used for textual relevance calculation
     * @param query SKJoinQuery object containing keywords and query parameters
     * @param joinConfiguration Join policy bundle (threshold, join, similarity, query strategy)
     * @return List of result pairs sorted by ascending combined cost (best matches first)
     */
//    @Override
    public List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex invertedList, SKJoinQuery query,
                                                             int topK, JoinConfiguration joinConfiguration) {
        numOfVisitedNodes = 0;

        JoinConfiguration config = Objects.requireNonNull(joinConfiguration, "joinConfiguration must not be null");
        if (topK <= 0) {
            return Collections.emptyList();
        }

        SimilarityType similarityType = config.getSimilarityType(); // Weighted Jaccard, Cosine or Weighted SUM
        JoinStrategy joinStrategy = config.getJoinStrategy();   // Plain Sweep or Default (smart)
        QueryStrategy queryStrategy = QueryStrategy.orDefault(config.getQueryStrategy()); // Constraint Join Variant or Full Join
        Region spatialWindow = resolveSpatialWindow(query, queryStrategy);
        if (queryStrategy.usesSpatialWindowConstraint() && spatialWindow == null) {
            logger.warn("[BestFirst] CONSTRAINT_SPATIAL_JOIN requires query.spatialWindow. Returning empty result set.");
            return Collections.emptyList();
        }

        logger.info("[BestFirst] Starting Top-k STSJ Query with similarity: {}, join strategy: {}, query strategy: {}, K: {}",
                similarityType.getDescription(), joinStrategy.getDescription(), queryStrategy.getDescription(), topK);

        // Min-heap on lower-bound combined cost: lower cost is better.
        PriorityQueue<NNEntryPair> queue = new PriorityQueue<>(new NNEntryPairComparatorCombinedCost());
        queue.add(new NNEntryPair(new RtreeEntry(tree.getRootIdentifier(), false),
                new RtreeEntry(tree.getRootIdentifier(), false), 0.0, 1.0, 0.0));

        TopKBuffer topKBuffer = new TopKBuffer(topK);
        int priorityQueueMaxSize = queue.size();    // Maximum size of the priority queue to control memory usage
        Map<Long, Map<Integer, Double>> leafPairRelevanceCache = new HashMap<>();
        Map<Long, Map<Integer, Double>> nodePairRelevanceCache = new HashMap<>();

        while (!queue.isEmpty()) {
            // Extract best candidate according to current lower bound.
            NNEntryPair currentEntry = queue.poll();
            RtreeEntry rtreeEntry1 = currentEntry.entry1;
            RtreeEntry rtreeEntry2 = currentEntry.entry2;

            // Global lower-bound pruning. Since queue is sorted, we can terminate here.
            if (topKBuffer.shouldPrune(currentEntry.combinedCost)) {
                logger.debug("[BestFirst] Early termination: lower-bound {} >= tau {}",
                        currentEntry.combinedCost,
                        topKBuffer.currentTau());
                break;
            }

            // Check leaf entries (actual data objects)
            if (rtreeEntry1.isLeafEntry && rtreeEntry2.isLeafEntry) {
                int id1 = rtreeEntry1.getIdentifier();
                int id2 = rtreeEntry2.getIdentifier();

                if (id1 == id2) {
                    continue; // Skip self-joins
                }

                if (!topKBuffer.markProcessedPair(id1, id2)) {
                    continue;
                }

                // Leaf entries carry their parent leaf node IDs in treeId.
                int leafNodeIdX = rtreeEntry1.treeId;
                int leafNodeIdY = rtreeEntry2.treeId;
                if (leafNodeIdX < 0 || leafNodeIdY < 0) {
                    logger.warn("[BestFirst] Missing parent leaf IDs for pair ({}, {})", id1, id2);
                    continue;
                }

                // Apply spatial window constraint if needed
                if (queryStrategy.usesSpatialWindowConstraint()) {
                    Region objectMbrX = resolveObjectMBR(leafNodeIdX, id1);
                    Region objectMbrY = resolveObjectMBR(leafNodeIdY, id2);
                    if (!pairInsideSpatialWindow(objectMbrX, objectMbrY, spatialWindow)) {
                        continue;
                    }
                }

                double exactTextualSimilarity;
                if (QueryStrategy.orDefault(queryStrategy) == QueryStrategy.FULL_JOIN) {
                    exactTextualSimilarity = invertedList.nodesDocumentSim(
                            leafNodeIdX,
                            leafNodeIdY,
                            id1,
                            id2,
                            similarityType);
                } else {
                    long leafPairKey = buildOrderedPairKey(leafNodeIdX, leafNodeIdY);
                    Map<Integer, Double> leafRelevanceScores = leafPairRelevanceCache.computeIfAbsent(
                            leafPairKey,
                            ignored -> calculateNodePairRelevanceScores(
                                    invertedList,
                                    leafNodeIdX,
                                    leafNodeIdY,
                                    query.getKeywords(),
                                    query.getKeywordWeights(),
                                    similarityType,
                                    queryStrategy));

                    if (leafRelevanceScores.isEmpty()) {
                        continue;
                    }

                    exactTextualSimilarity = resolveTextualUpperBound(leafRelevanceScores, id1, id2, queryStrategy);
                }
                if (exactTextualSimilarity <= 0.0) {
                    continue;
                }

                // Spatial cost in leaf-leaf entries is already exact.
                double exactSpatialDistance = currentEntry.spatialCost;
                double combinedCost = combinedScore(exactSpatialDistance, exactTextualSimilarity);
                if (topKBuffer.shouldPrune(combinedCost)) {
                    continue;
                }

                topKBuffer.offer(new SKJoinQuery.Result(
                        id1,
                        id2,
                        exactSpatialDistance,
                        exactTextualSimilarity,
                        combinedCost));

                numOfVisitedNodes++;
            } else {
                // ===== INTERNAL NODE PROCESSING =====
                Node nodeX = readNode(rtreeEntry1.getIdentifier());
                Node nodeY = readNode(rtreeEntry2.getIdentifier());
                if (nodeX == null || nodeY == null) {
                    logger.warn("Skipping pair with missing node(s): x={}, y={}", rtreeEntry1.getIdentifier(), rtreeEntry2.getIdentifier());
                    continue;
                }
                numOfVisitedNodes += 2;

                // Check spatial window intersection for internal nodes
                if (queryStrategy.usesSpatialWindowConstraint()) {
                    if (!pairIntersectsSpatialWindow(nodeX.getMBR(), nodeY.getMBR(), spatialWindow)) {
                        continue;
                    }
                }

                final boolean isSelfJoin = (nodeX.getIdentifier() == nodeY.getIdentifier());
                final boolean isLeafEntryX = nodeX.isLeaf();
                final boolean isLeafEntryY = nodeY.isLeaf();

                // Choose join strategy: plane-sweep or nested loop
                if (joinStrategy == JoinStrategy.PLANE_SWEEP) {
                    long nodePairKey = buildOrderedPairKey(nodeX.getIdentifier(), nodeY.getIdentifier());
                    Map<Integer, Double> relevanceScores = nodePairRelevanceCache.computeIfAbsent(
                            nodePairKey,
                            ignored -> calculateNodePairRelevanceScores(
                                    invertedList,
                                    nodeX.getIdentifier(),
                                    nodeY.getIdentifier(),
                                    query.getKeywords(),
                                    query.getKeywordWeights(),
                                    similarityType,
                                    queryStrategy
                            ));
                    if (relevanceScores.isEmpty()) {
                        continue;
                    }

                    List<CandidatePair> candidatePairs = generatePlaneSweepJoinTopK(
                            nodeX, nodeY, isSelfJoin, queryStrategy, relevanceScores, spatialWindow);

                    // Process candidate pairs from plane-sweep
                    for (CandidatePair candidate : candidatePairs) {
                        final int childIdX = candidate.childIdX;
                        final int childIdY = candidate.childIdY;

                        // Calculate combined score for upper bound
                        double spatialUpperBound = candidate.spatialDistance;
                        double textualUpperBound = candidate.textualUpperBound;

                        if (isLeafEntryX && isLeafEntryY) {
                            textualUpperBound = resolveLeafTextualSimilarity(
                                    invertedList,
                                    nodeX.getIdentifier(), childIdX,
                                    nodeY.getIdentifier(), childIdY,
                                    similarityType,
                                    queryStrategy,
                                    relevanceScores);
                        }

                        if (textualUpperBound <= 0.0) {
                            continue;
                        }

                        double combinedScoreUB = combinedScore(spatialUpperBound, textualUpperBound);
                        if (topKBuffer.shouldPrune(combinedScoreUB)) {
                            continue;
                        }

                        RtreeEntry childEntryX = new RtreeEntry(childIdX, isLeafEntryX);
                        RtreeEntry childEntryY = new RtreeEntry(childIdY, isLeafEntryY);
                        if (isLeafEntryX) {
                            childEntryX.treeId = nodeX.getIdentifier();
                        }
                        if (isLeafEntryY) {
                            childEntryY.treeId = nodeY.getIdentifier();
                        }

                        queue.add(new NNEntryPair(childEntryX, childEntryY, spatialUpperBound,
                                textualUpperBound, combinedScoreUB));
                    }
                } else {
                    final TreeMap<Integer, NodeEntry> entriesX = nodeX.getNodeEntries();
                    final TreeMap<Integer, NodeEntry> entriesY = nodeY.getNodeEntries();

                    long nodePairKey = buildOrderedPairKey(nodeX.getIdentifier(), nodeY.getIdentifier());
                    Map<Integer, Double> relevanceScores = nodePairRelevanceCache.computeIfAbsent(
                            nodePairKey,
                            ignored -> calculateNodePairRelevanceScores(
                                    invertedList,
                                    nodeX.getIdentifier(),
                                    nodeY.getIdentifier(),
                                    query.getKeywords(),
                                    query.getKeywordWeights(),
                                    similarityType,
                                    queryStrategy
                            ));
                    if (relevanceScores.isEmpty()) {
                        continue;
                    }

                    for (Map.Entry<Integer, NodeEntry> entryX : entriesX.entrySet()) {
                        final int childIdX = entryX.getKey();
                        final NodeEntry nodeEntryX = entryX.getValue();
                        final Region mbrX = nodeEntryX.getMBR();

                        for (Map.Entry<Integer, NodeEntry> entryY : entriesY.entrySet()) {
                            final int childIdY = entryY.getKey();
                            final NodeEntry nodeEntryY = entryY.getValue();
                            final Region mbrY = nodeEntryY.getMBR();

                            // Skip redundant pairs (when processing same node, avoid duplicate pairs)
                            if (isSelfJoin && childIdX > childIdY) {
                                continue;
                            }

                            // Check spatial window intersection for children
                            if (queryStrategy.usesSpatialWindowConstraint()) {
                                if (!pairIntersectsSpatialWindow(mbrX, mbrY, spatialWindow)) {
                                    continue;
                                }
                            }

                            double spatialUpperBound = mbrX.getMinimumDistance(mbrY);

                            double textualUpperBound = isLeafEntryX && isLeafEntryY
                                    ? resolveLeafTextualSimilarity(
                                    invertedList,
                                    nodeX.getIdentifier(), childIdX,
                                    nodeY.getIdentifier(), childIdY,
                                    similarityType,
                                    queryStrategy,
                                    relevanceScores)
                                    : resolveTextualUpperBound(relevanceScores, childIdX, childIdY, queryStrategy);

                            if (textualUpperBound <= 0.0) {
                                continue;
                            }

                            double combinedScoreUB = combinedScore(spatialUpperBound, textualUpperBound);
                            if (topKBuffer.shouldPrune(combinedScoreUB)) {
                                continue;
                            }

                            final RtreeEntry childEntryX = new RtreeEntry(childIdX, isLeafEntryX);
                            final RtreeEntry childEntryY = new RtreeEntry(childIdY, isLeafEntryY);
                            if (isLeafEntryX) {
                                childEntryX.treeId = nodeX.getIdentifier();
                            }
                            if (isLeafEntryY) {
                                childEntryY.treeId = nodeY.getIdentifier();
                            }

                            queue.add(new NNEntryPair(childEntryX, childEntryY, spatialUpperBound, textualUpperBound, combinedScoreUB));
                        }
                    }
                }
            }
            priorityQueueMaxSize = Math.max(priorityQueueMaxSize, queue.size());
        }

        List<SKJoinQuery.Result> results = topKBuffer.asSortedResults();

        logger.debug("[BestFirst] Number of results: {}", results.size());
        logger.info("[BestFirst] Maximum priority queue size: {}", priorityQueueMaxSize);

        //TODO Remove debug output
        System.out.println("[BestFirst] Using similarity: " + similarityType.getDescription() + " and query strategy: " + queryStrategy.getDescription());
        invertedList.printStatistics();


        return results;
    }



//    /**
//     * Calculates the exact spatial distance between two leaf objects.
//     * This method retrieves the actual spatial representations (MBRs) from leaf node entries
//     * and calculates the minimum distance between them.
//     *
//     * @param leafNodeX First leaf node containing the object
//     * @param leafNodeY Second leaf node containing the object
//     * @param objectIdX ID of the first object
//     * @param objectIdY ID of the second object
//     * @return Minimum spatial distance between the two objects
//     */
//    private double calculateExactLeafSpatialDistance(Node leafNodeX, Node leafNodeY, int objectIdX, int objectIdY) {
//        // If same node, get both entries from the same node
//        if (leafNodeX.getIdentifier() == leafNodeY.getIdentifier()) {
//            NodeEntry entryX = leafNodeX.getNodeEntry(objectIdX);
//            NodeEntry entryY = leafNodeX.getNodeEntry(objectIdY);
//            if (entryX != null && entryY != null) {
//                return entryX.getMBR().getMinimumDistance(entryY.getMBR());
//            }
//        } else {
//            // Different nodes - get entries from respective leaf nodes
//            NodeEntry entryX = leafNodeX.getNodeEntry(objectIdX);
//            NodeEntry entryY = leafNodeY.getNodeEntry(objectIdY);
//            if (entryX != null && entryY != null) {
//                return entryX.getMBR().getMinimumDistance(entryY.getMBR());
//            }
//        }
//        // Fallback: If entries not found, return max distance
//        logger.warn("Could not find leaf entries for objects {} and {}", objectIdX, objectIdY);
//        return Double.MAX_VALUE;
//    }

    /**
     * Generates candidate pairs for plane-sweep join in the top-k algorithm.
     * Unlike the threshold-based version, this doesn't apply hard spatial/textual thresholds,
     * relying instead on tau-based pruning at a higher level.
     * Optionally filters pairs that don't intersect with a spatial window constraint.
     *
     * @param nodeX First internal node to join
     * @param nodeY Second internal node to join
     * @param isSelfJoin Flag indicating if this is a self-join (both nodes are the same)
     * @param queryStrategy Strategy for computing textual similarities
     * @param relevanceScores Pre-computed relevance scores for textual upper bounds (null if not applicable)
     * @param spatialWindow Optional spatial window constraint (null if no spatial constraint)
     * @return List of candidate pairs with spatial and textual upper bounds
     */
    private List<CandidatePair> generatePlaneSweepJoinTopK(
            Node nodeX, Node nodeY,
            boolean isSelfJoin,
            QueryStrategy queryStrategy,
            Map<Integer, Double> relevanceScores,
            Region spatialWindow) {

        List<CandidatePair> candidates = new ArrayList<>();

        // Pre-size collections based on node entries
        final int nodeXSize = nodeX.getNodeEntries().size();
        final int nodeYSize = nodeY.getNodeEntries().size();
        List<SweepEvent> events = new ArrayList<>(nodeXSize + nodeYSize);

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

        // Use ArrayLists with initial capacity for better performance
        List<SweepEvent> activeX = new ArrayList<>(nodeXSize);
        List<SweepEvent> activeY = new ArrayList<>(nodeYSize);

        // Sweep through all events without hard thresholds.
        // We intentionally keep active events to preserve completeness in top-k mode.
        for (SweepEvent event : events) {
            if (event.source == 1) {
                // Check nodeX event against all active nodeY events
                for (SweepEvent activeEventY : activeY) {
                    CandidatePair candidate = createCandidateIfValidTopK(
                            event, activeEventY, isSelfJoin, relevanceScores, queryStrategy, spatialWindow);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
                activeX.add(event);
            } else {
                // Check nodeY event against all active nodeX events
                for (SweepEvent activeEventX : activeX) {
                    CandidatePair candidate = createCandidateIfValidTopK(
                            activeEventX, event, isSelfJoin, relevanceScores, queryStrategy, spatialWindow);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
                activeY.add(event);
            }

            // No geometric window pruning here: tau-based pruning happens at parent levels.
        }

        return candidates;
    }

    /**
     * Creates a candidate pair for top-k best-first (no hard thresholds - just upper bounds).
     * This version doesn't prune based on hard thresholds, allowing tau-based pruning at higher levels.
     * Optionally checks spatial window constraint.
     *
     * @param eventX First sweep event
     * @param eventY Second sweep event
     * @param isSelfJoin Flag indicating if this is a self-join
     * @param relevanceScores Pre-computed relevance scores (null if not applicable)
     * @param queryStrategy Strategy for computing textual upper bounds
     * @param spatialWindow Optional spatial window constraint (null if no constraint)
     * @return CandidatePair if spatially overlapping and passes spatial window check, null otherwise
     */
    private CandidatePair createCandidateIfValidTopK(
            SweepEvent eventX, SweepEvent eventY, boolean isSelfJoin,
            Map<Integer, Double> relevanceScores,
            QueryStrategy queryStrategy,
            Region spatialWindow) {

        // Skip duplicate pairs in self-join
        if (isSelfJoin && eventX.childId > eventY.childId) {
            return null;
        }

        // Calculate spatial distance
        final double spatialDistance = eventX.mbr.getMinimumDistance(eventY.mbr);

        // Check spatial window intersection if constraint is enabled
        if (spatialWindow != null) {
            Region mbrXAsRegion = (eventX.mbr instanceof Region) ? (Region) eventX.mbr : null;
            Region mbrYAsRegion = (eventY.mbr instanceof Region) ? (Region) eventY.mbr : null;
            if (mbrXAsRegion != null && mbrYAsRegion != null) {
                if (!pairIntersectsSpatialWindow(mbrXAsRegion, mbrYAsRegion, spatialWindow)) {
                    return null;
                }
            }
        }

        // For top-k, we accept all spatially overlapping pairs (distance >= 0)
        // Pruning happens at higher level based on tau

        // Calculate textual upper bound (or use 1.0 if no relevance scores)
        double textualUpperBound = 1.0;
        if (relevanceScores != null && !relevanceScores.isEmpty()) {
            textualUpperBound = resolveTextualUpperBound(relevanceScores, eventX.childId, eventY.childId, queryStrategy);
        }

        return new CandidatePair(eventX.childId, eventY.childId, spatialDistance, textualUpperBound);
    }


    //==========================================================================================
    //================================= Recursive Top-K  =======================================
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
     *   <li>Supports optional spatial window constraints</li>
     * </ul>
     *
     * @param invertedList Document index for calculating textual relevance scores
     * @param query Query parameters including keywords and their weights
     * @param joinConfiguration Join policy bundle (threshold, join, similarity, query strategy)
     * @return List of matching object pairs sorted by combined similarity score
     */
//    @Override
    public List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex invertedList, SKJoinQuery query,
                                                             int topK, JoinConfiguration joinConfiguration) {
        this.numOfVisitedNodes = 0;

        JoinConfiguration config = Objects.requireNonNull(joinConfiguration, "joinConfiguration must not be null");
        if (topK <= 0) {
            return Collections.emptyList();
        }

        JoinStrategy joinStrategy = config.getJoinStrategy();
        SimilarityType similarityType = config.getSimilarityType();
        QueryStrategy queryStrategy = QueryStrategy.orDefault(config.getQueryStrategy());
        Region spatialWindow = resolveSpatialWindow(query, queryStrategy);
        if (queryStrategy.usesSpatialWindowConstraint() && spatialWindow == null) {
            logger.warn("[Recursive] CONSTRAINT_SPATIAL_JOIN requires query.spatialWindow. Returning empty result set.");
            return Collections.emptyList();
        }

        logger.info("[Recursive] Starting Top-k STSJ Query with similarity: {}, join strategy: {}, query strategy: {}, K: {}",
                similarityType.getDescription(), joinStrategy.getDescription(), queryStrategy.getDescription(), topK);

        TopKBuffer topKBuffer = new TopKBuffer(topK);
        List<SKJoinQuery.Result> results = new ArrayList<>(topK);

        // Start recursive traversal from root
        NNEntryPair rootEntry = new NNEntryPair(
                new RtreeEntry(tree.getRootIdentifier(), false),
                new RtreeEntry(tree.getRootIdentifier(), false),
                0.0, 1.0, 0.0
        );

        selfJoinSKQueryRecursiveTraversal(rootEntry, invertedList, query,
                joinStrategy, similarityType, queryStrategy, topKBuffer, spatialWindow);

        results.addAll(topKBuffer.asSortedResults());
        logger.debug("[Recursive] Number of results: {}",  results.size());

        //TODO Remove
        System.out.println("[Recursive] Using similarity: " + similarityType.getDescription() + " and query strategy: " + queryStrategy.getDescription());
        invertedList.printStatistics();


        return results;
    }


    /**
     * Recursive traversal method for self-join SK query using depth-first search.
     * This method processes pairs of nodes, applying spatial and textual filtering,
     * to find matching object pairs that satisfy both distance and similarity thresholds.
     *
     * @param pairEntry Current pair of R-tree entries to process
     * @param invertedFile Document index for textual relevance calculation
     * @param query Query parameters including keywords and weights
     * @param joinStrategy Algorithm strategy for joining nodes (PLANE_SWEEP or NONE)
     * @param spatialWindow Optional spatial window constraint (null if no constraint)
     */
    private void selfJoinSKQueryRecursiveTraversal(NNEntryPair pairEntry, IDocumentIndex invertedFile,
                                                   SKJoinQuery query, JoinStrategy joinStrategy, SimilarityType similarityType,
                                                   QueryStrategy queryStrategy, TopKBuffer topKBuffer, Region spatialWindow) {
        final RtreeEntry rtentryX = pairEntry.entry1;
        final RtreeEntry rtentryY = pairEntry.entry2;
        final int nodeIdX = rtentryX.getIdentifier();
        final int nodeIdY = rtentryY.getIdentifier();

        numOfVisitedNodes += 2;

        final Node nodeX = readNode(nodeIdX);
        final Node nodeY = readNode(nodeIdY);
        if (nodeX == null || nodeY == null) {
            logger.warn("Skipping traversal pair with missing node(s): x={}, y={}", nodeIdX, nodeIdY);
            return;
        }

        // Check spatial window intersection for this node pair
        if (queryStrategy.usesSpatialWindowConstraint()) {
            if (!pairIntersectsSpatialWindow(nodeX.getMBR(), nodeY.getMBR(), spatialWindow)) {
                return;
            }
        }

        final boolean isSelfJoin = (nodeIdX == nodeIdY);

        Map<Integer, Double> relevanceScores = calculateNodePairRelevanceScores(
                invertedFile,
                nodeX.getIdentifier(),
                nodeY.getIdentifier(),
                query.getKeywords(),
                query.getKeywordWeights(),
                similarityType,
                queryStrategy
        );
        if (relevanceScores.isEmpty()) {
            return;
        }

        // Use node-level optimistic score to derive a lower bound in cost space.
        final double nodeSpatialLowerBound = nodeX.getMBR().getMinimumDistance(nodeY.getMBR());
        final double nodeTextualUpperBound = resolveNodeTextualUpperBound(relevanceScores);
        final double nodeCombinedLowerBound = combinedScore(nodeSpatialLowerBound, nodeTextualUpperBound);
        if (topKBuffer.shouldPrune(nodeCombinedLowerBound)) {
            return;
        }

        // Base case: both nodes are leaf nodes (level 0)
        if (nodeX.isLeaf() && nodeY.isLeaf()) {
            processLeafNodes(nodeX, nodeY, isSelfJoin, invertedFile, similarityType,
                    queryStrategy, relevanceScores, topKBuffer, spatialWindow);
            return;
        }

        // Process internal nodes based on join strategy
        switch (joinStrategy) {
            case PLANE_SWEEP:
                processInternalNodesWithPlainSweep(nodeX, nodeY, invertedFile, query,
                        joinStrategy, similarityType, queryStrategy, relevanceScores, topKBuffer, spatialWindow);
                break;
            case DEFAULT:
                processInternalNodes(nodeX, nodeY, invertedFile, query,
                        joinStrategy, similarityType, queryStrategy, relevanceScores, topKBuffer, spatialWindow);
                break;
            default:
                throw new IllegalArgumentException("Unsupported join strategy: " + joinStrategy);
        }
    }

    /**
     * Processes pairs of leaf nodes and accumulates results based on spatial and textual thresholds.
     * This method handles the actual data objects (POIs) at the leaf level of the IR-tree and applies
     * the filtering criteria to find matching pairs.
     *
     * <p>Key features:
     * <ul>
     *   <li>Processes actual data objects stored in leaf nodes</li>
     *   <li>Applies spatial distance filtering using MBR calculations</li>
     *   <li>Calculates textual similarity using inverted file index</li>
     *   <li>Handles duplicate avoidance for self-join cases</li>
     *   <li>Optionally applies spatial window constraints</li>
     * </ul>
     *
     * @param nodeX First leaf node containing POI data objects
     * @param nodeY Second leaf node containing POI data objects
     * @param isSelfJoin Flag indicating if nodeX and nodeY are the same node (self-join case)
     * @param invertedList Index structure for efficient textual relevance calculation
     * @param spatialWindow Optional spatial window constraint (null if no constraint)
     */
    private void processLeafNodes(Node nodeX, Node nodeY, boolean isSelfJoin,
                                  IDocumentIndex invertedList,
                                  SimilarityType similarityType,
                                  QueryStrategy queryStrategy,
                                  Map<Integer, Double> relevanceScores,
                                  TopKBuffer topKBuffer,
                                  Region spatialWindow) {

        // Work directly with TreeMap entries for better performance
        TreeMap<Integer, NodeEntry> entriesX = nodeX.getNodeEntries();
        TreeMap<Integer, NodeEntry> entriesY = nodeY.getNodeEntries();

        // Process all pairs between the two nodes
        for (Map.Entry<Integer, NodeEntry> entryX : entriesX.entrySet()) {
            int objectIdX = entryX.getKey();
            NodeEntry nodeEntryX = entryX.getValue();

            for (Map.Entry<Integer, NodeEntry> entryY : entriesY.entrySet()) {
                int objectIdY = entryY.getKey();
                NodeEntry nodeEntryY = entryY.getValue();

                // Skip duplicate pairs in self-join scenarios
                if (isSelfJoin && objectIdX >= objectIdY) {
                    continue;
                }
                if (!topKBuffer.markProcessedPair(objectIdX, objectIdY)) {
                    continue;
                }

                // Check spatial window constraint if enabled
                if (queryStrategy.usesSpatialWindowConstraint()) {
                    if (!pairInsideSpatialWindow(nodeEntryX.getMBR(), nodeEntryY.getMBR(), spatialWindow)) {
                        continue;
                    }
                }

                // Calculate spatial distance between MBRs of the two entries
                double spatialDistance = nodeEntryX.getMBR().getMinimumDistance(nodeEntryY.getMBR());

                // Exact textual similarity at leaf level.
                double textualSimilarity = resolveLeafTextualSimilarity(
                        invertedList,
                        nodeX.getIdentifier(), objectIdX,
                        nodeY.getIdentifier(), objectIdY,
                        similarityType,
                        queryStrategy,
                        relevanceScores);

                if (textualSimilarity <= 0.0) {
                    continue;
                }

                double combinedCost = combinedScore(spatialDistance, textualSimilarity);
                if (topKBuffer.shouldPrune(combinedCost)) {
                    continue;
                }

                topKBuffer.offer(new SKJoinQuery.Result(
                        objectIdX,
                        objectIdY,
                        spatialDistance,
                        textualSimilarity,
                        combinedCost));
            }
        }
    }

    /**
     * Processes pairs of internal nodes using nested loop join and recursively traverses the IR-tree structure.
     * This method evaluates pairs of internal nodes, applying spatial and textual filtering criteria to prune
     * the search space before recursively processing their child nodes.
     *
     * <p>Key features:
     * <ul>
     *   <li>Applies nested loop join strategy for processing node pairs</li>
     *   <li>Uses early pruning based on spatial and textual thresholds</li>
     *   <li>Handles self-join scenarios by avoiding duplicate pairs</li>
     *   <li>Supports optional spatial window constraints</li>
     * </ul>
     *
     * @param nodeX First internal node to process
     * @param nodeY Second internal node to process
     * @param invertedList Document index for calculating textual relevance scores
     * @param query Contains search keywords and their weights
     * @param joinStrategy Algorithm strategy for joining nodes (PLANE_SWEEP or NONE)
     * @param spatialWindow Optional spatial window constraint (null if no constraint)
     */
    private void processInternalNodes(Node nodeX, Node nodeY,
                                      IDocumentIndex invertedList, SKJoinQuery query,
                                      JoinStrategy joinStrategy, SimilarityType similarityType,
                                      QueryStrategy queryStrategy,
                                      Map<Integer, Double> relevanceScores,
                                      TopKBuffer topKBuffer,
                                      Region spatialWindow) {

        final boolean isSelfJoin = (nodeX.getIdentifier() == nodeY.getIdentifier());
        final boolean isLeafEntryX = nodeX.isLeaf();
        final boolean isLeafEntryY = nodeY.isLeaf();

        // Get node entries once for better performance
        final TreeMap<Integer, NodeEntry> entriesX = nodeX.getNodeEntries();
        final TreeMap<Integer, NodeEntry> entriesY = nodeY.getNodeEntries();

        List<CandidatePair> candidates = new ArrayList<>(entriesX.size() * Math.max(1, entriesY.size()));

        // Generate child pairs with optimistic bounds.
        for (Map.Entry<Integer, NodeEntry> entryX : entriesX.entrySet()) {
            final int childIdX = entryX.getKey();
            final NodeEntry nodeEntryX = entryX.getValue();
            final Region mbrX = nodeEntryX.getMBR();

            for (Map.Entry<Integer, NodeEntry> entryY : entriesY.entrySet()) {
                final int childIdY = entryY.getKey();
                final NodeEntry nodeEntryY = entryY.getValue();
                final Region mbrY = nodeEntryY.getMBR();

                // Skip if childIdX is greater than childIdY to avoid duplicates
                if (isSelfJoin && childIdX > childIdY) {
                    continue;
                }

                // Check spatial window intersection for children
                if (queryStrategy.usesSpatialWindowConstraint()) {
                    if (!pairIntersectsSpatialWindow(mbrX, mbrY, spatialWindow)) {
                        continue;
                    }
                }

                final double spatialDistance = mbrX.getMinimumDistance(mbrY);

                final double textualUpperBound = isLeafEntryX && isLeafEntryY
                        ? resolveLeafTextualSimilarity(
                        invertedList,
                        nodeX.getIdentifier(), childIdX,
                        nodeY.getIdentifier(), childIdY,
                        similarityType,
                        queryStrategy,
                        relevanceScores)
                        : resolveTextualUpperBound(relevanceScores, childIdX, childIdY, queryStrategy);

                final double lowerBound = combinedScore(spatialDistance, textualUpperBound);
                if (topKBuffer.shouldPrune(lowerBound)) {
                    continue;
                }

                candidates.add(new CandidatePair(childIdX, childIdY, spatialDistance, textualUpperBound));
            }
        }

        // Explore better lower bounds first so tau tightens earlier.
        candidates.sort(Comparator.comparingDouble(c -> combinedScore(c.spatialDistance, c.textualUpperBound)));

        for (CandidatePair candidate : candidates) {
            final double lowerBound = combinedScore(candidate.spatialDistance, candidate.textualUpperBound);
            if (topKBuffer.shouldPrune(lowerBound)) {
                continue;
            }

            final RtreeEntry childEntryX = new RtreeEntry(candidate.childIdX, isLeafEntryX);
            final RtreeEntry childEntryY = new RtreeEntry(candidate.childIdY, isLeafEntryY);

            selfJoinSKQueryRecursiveTraversal(
                    new NNEntryPair(childEntryX, childEntryY,
                            candidate.spatialDistance,
                            candidate.textualUpperBound,
                            lowerBound),
                    invertedList,
                    query,
                    joinStrategy,
                    similarityType,
                    queryStrategy,
                    topKBuffer,
                    spatialWindow);
        }
    }


    //==========================================================================================
    //============================ Plane Sweep for Recursive Traversal ==========================
    //==========================================================================================

    /**
     * Processes pairs of internal nodes using a plane-sweep algorithm for efficient spatial join.
     * This method implements a plane-sweep strategy to reduce the number of comparisons needed
     * when joining internal nodes of the IR-tree. It sorts nodes by their x-coordinates and
     * maintains an active set of nodes while sweeping through the space.
     *
     * <p>Key features:
     * <ul>
     *   <li>Uses plane-sweep algorithm to reduce comparison complexity</li>
     *   <li>Applies early pruning based on spatial and textual thresholds</li>
     *   <li>Handles self-join scenarios efficiently</li>
     *   <li>Maintains active node sets for both input nodes</li>
     *   <li>Supports optional spatial window constraints</li>
     * </ul>
     *
     * @param nodeX First internal node to process in the join operation
     * @param nodeY Second internal node to process in the join operation
     * @param invertedList Document index for calculating textual relevance scores
     * @param query Query parameters containing keywords and their weights
     * @param joinStrategy Algorithm strategy for joining nodes (PLANE_SWEEP or NONE)
     * @param spatialWindow Optional spatial window constraint (null if no constraint)
     */
    private void processInternalNodesWithPlainSweep(Node nodeX, Node nodeY, IDocumentIndex invertedList, SKJoinQuery query,
                                                    JoinStrategy joinStrategy, SimilarityType similarityType,
                                                    QueryStrategy queryStrategy,
                                                    Map<Integer, Double> relevanceScores,
                                                    TopKBuffer topKBuffer,
                                                    Region spatialWindow) {

        // Apply plane-sweep algorithm to find spatial candidates efficiently
        final boolean isSelfJoin = (nodeX.getIdentifier() == nodeY.getIdentifier());

        final List<CandidatePair> candidates = generatePlaneSweepJoinTopK(
                nodeX,
                nodeY,
                isSelfJoin,
                queryStrategy,
                relevanceScores,
                spatialWindow
        );

        final boolean isLeafEntry = nodeX.isLeaf();

        candidates.sort(Comparator.comparingDouble(c -> combinedScore(c.spatialDistance, c.textualUpperBound)));

        // Process each candidate pair recursively
        for (final CandidatePair candidate : candidates) {
            double textualUpperBound = candidate.textualUpperBound;
            if (isLeafEntry) {
                textualUpperBound = resolveLeafTextualSimilarity(
                        invertedList,
                        nodeX.getIdentifier(), candidate.childIdX,
                        nodeY.getIdentifier(), candidate.childIdY,
                        similarityType,
                        queryStrategy,
                        relevanceScores);
            }

            final double lowerBound = combinedScore(candidate.spatialDistance, textualUpperBound);
            if (topKBuffer.shouldPrune(lowerBound)) {
                continue;
            }

            final NNEntryPair entryPair = new NNEntryPair(
                    new RtreeEntry(candidate.childIdX, isLeafEntry),
                    new RtreeEntry(candidate.childIdY, isLeafEntry),
                    candidate.spatialDistance,
                    textualUpperBound,
                    lowerBound);

            selfJoinSKQueryRecursiveTraversal(entryPair, invertedList, query,
                    joinStrategy, similarityType, queryStrategy, topKBuffer, spatialWindow);
        }
    }

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

        private double currentTau() {
            return tau;
        }

        private List<SKJoinQuery.Result> asSortedResults() {
            List<SKJoinQuery.Result> sorted = new ArrayList<>(results);
            Collections.sort(sorted);
            return sorted;
        }

        private boolean markProcessedPair(int id1, int id2) {
            int minId = Math.min(id1, id2);
            int maxId = Math.max(id1, id2);
            long key = (((long) minId) << 32) | (maxId & 0xffffffffL);
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
    }

    //==========================================================================================
    //====================================== Common JOIN Methods ===================================
    //==========================================================================================

    private Region resolveSpatialWindow(SKJoinQuery query, QueryStrategy queryStrategy) {
        if (!QueryStrategy.orDefault(queryStrategy).usesSpatialWindowConstraint()) {
            return null;
        }
        return query == null ? null : query.getSpatialWindow();
    }

    private boolean pairIntersectsSpatialWindow(Region mbrX, Region mbrY, Region spatialWindow) {
        if (spatialWindow == null) {
            return true;
        }
        if (mbrX == null || mbrY == null) {
            return false;
        }
        return spatialWindow.intersects(mbrX) && spatialWindow.intersects(mbrY);
    }

    private boolean pairInsideSpatialWindow(Region mbrX, Region mbrY, Region spatialWindow) {
        if (spatialWindow == null || mbrX == null || mbrY == null) {
            return false;
        }
        //return mbrX != null && mbrY != null && spatialWindow.contains(mbrX) && spatialWindow.contains(mbrY);
        //return spatialWindow.intersects(mbrX) && spatialWindow.intersects(mbrY);
        return spatialWindow.contains(mbrX) && spatialWindow.contains(mbrY);
    }

    private Region resolveObjectMBR(int leafNodeId, int objectId) {
        Node leafNode = readNode(leafNodeId);
        if (leafNode == null) {
            return null;
        }
        NodeEntry entry = leafNode.getNodeEntries().get(objectId);
        return entry == null ? null : entry.getMBR();
    }

//    /**
//     * Applies the plane-sweep algorithm to efficiently find candidate pairs of nodes for recursive traversal.
//     * The algorithm sorts nodes by x-coordinate and maintains an active set while sweeping through space,
//     * reducing the number of comparisons needed compared to nested loops.
//     *
//     * @param nodeX First node to process in the plane-sweep algorithm
//     * @param nodeY Second node to process in the plane-sweep algorithm
//     * @param spatialThreshold Maximum allowed spatial distance between node pairs
//     * @param textualThreshold Minimum required textual similarity between node pairs
//     * @param relevanceScores Pre-calculated textual relevance scores for node entries
//     * @return List of candidate pairs that satisfy the spatial and textual thresholds
//     */
//    protected List<CandidatePair> generatePlaneSweepJoin(
//            Node nodeX, Node nodeY,
//            float spatialThreshold, float textualThreshold,
//            Map<Integer, Double> relevanceScores,
//            boolean isSelfJoin,
//            QueryStrategy queryStrategy) {
//
//        List<CandidatePair> candidates = new ArrayList<>();
//
//        // Pre-size collections based on node entries
//        final int nodeXSize = nodeX.getNodeEntries().size();
//        final int nodeYSize = nodeY.getNodeEntries().size();
//        List<SweepEvent> events = new ArrayList<>(nodeXSize + nodeYSize);
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
//        // Use ArrayLists with initial capacity for better performance
//        List<SweepEvent> activeX = new ArrayList<>(nodeXSize);
//        List<SweepEvent> activeY = new ArrayList<>(nodeYSize);
//
//        for (SweepEvent event : events) {
//            final double currentMinX = event.mbr.getMBR().getMinX();
//            final double pruneThreshold = currentMinX - spatialThreshold;
//
//            // Process event based on source node
//            if (event.source == 1) {
//                // Check nodeX event against all active nodeY events
//                for (SweepEvent activeEventY : activeY) {
//                    CandidatePair candidate = createCandidateIfValid(
//                            event, activeEventY, isSelfJoin,
//                            spatialThreshold, textualThreshold, relevanceScores, queryStrategy);
//                    if (candidate != null) {
//                        candidates.add(candidate);
//                    }
//                }
//                activeX.add(event);
//            } else {
//                // Check nodeY event against all active nodeX events
//                for (SweepEvent activeEventX : activeX) {
//                    CandidatePair candidate = createCandidateIfValid(
//                            activeEventX, event, isSelfJoin,
//                            spatialThreshold, textualThreshold, relevanceScores, queryStrategy);
//                    if (candidate != null) {
//                        candidates.add(candidate);
//                    }
//                }
//                activeY.add(event);
//            }
//
//            // Efficiently prune events outside the sweep window
//            // Remove events whose maxX is less than the prune threshold
//            activeX.removeIf(e -> e.mbr.getMBR().getMaxX() < pruneThreshold);
//            activeY.removeIf(e -> e.mbr.getMBR().getMaxX() < pruneThreshold);
//        }
//
//        return candidates;
//    }


//    /**
//     * Creates a candidate pair if the spatial and textual conditions are met.
//     * This method checks if the two events are within the spatial threshold
//     * and if their textual relevance scores meet the required threshold.
//     *
//     * @param eventX First sweep event
//     * @param eventY Second sweep event
//     * @param isSelfJoin Flag indicating if this is a self-join operation
//     * @param spatialThreshold Maximum allowed spatial distance
//     * @param textualThreshold Minimum required textual similarity
//     * @param relevanceScores Pre-calculated relevance scores for textual filtering
//     * @return CandidatePair if valid, null otherwise
//     */
//    private CandidatePair createCandidateIfValid(
//            SweepEvent eventX, SweepEvent eventY, boolean isSelfJoin,
//            float spatialThreshold, float textualThreshold,
//            Map<Integer, Double> relevanceScores,
//            QueryStrategy queryStrategy) {
//
//        // Skip duplicate pairs in self-join
//        if (isSelfJoin && eventX.childId > eventY.childId) {
//            return null;
//        }
//
//        // Calculate spatial distance
//        final double spatialDistance = eventX.mbr.getMinimumDistance(eventY.mbr);
//        if (spatialDistance > spatialThreshold) {
//            return null;
//        }
//
//        // Calculate textual upper bound
//        double textualUpperBound = 1.0;
//        if (relevanceScores != null) {
////            textualUpperBound = resolvePairTextualUpperBound(
////                    relevanceScores,
////                    eventX.childId,
////                    eventY.childId,
////                    queryStrategy
////            );
//            textualUpperBound = resolveTextualUpperBound(relevanceScores, eventX.childId, eventY.childId, queryStrategy);
////            textualUpperBound = pairTextualUpperBound(relevanceScores, eventX.childId, eventY.childId);
//
//            if (textualUpperBound < textualThreshold) {
//                return null; // Early exit if textual threshold is not met
//            }
//        }
//
//        return new CandidatePair(eventX.childId, eventY.childId, spatialDistance, textualUpperBound);
//    }

    /**
     * Helper class for candidate pairs from plane-sweep.
     */
    protected static class CandidatePair {
        final int childIdX;
        final int childIdY;
        double spatialDistance;
        double textualUpperBound;

        CandidatePair(int childIdX, int childIdY, double spatialDistance, double textualUpperBound) {
            this.childIdX = childIdX;
            this.childIdY = childIdY;
            this.spatialDistance = spatialDistance;
            this.textualUpperBound = textualUpperBound;
        }
    }

    /**
     * Helper class for sweep events used in the plane-sweep algorithm.
     * This class encapsulates the child ID, MBR, and source node information
     * to facilitate efficient processing of spatial joins.
     */
    private static class SweepEvent implements Comparable<SweepEvent> {
        final int childId;
        final IShape mbr;
        final int source; // 1 for nodeX, 2 for nodeY

        SweepEvent(int childId, IShape mbr, int source) {
            this.childId = childId;
            this.mbr = mbr;
            this.source = source;
        }

        @Override
        public int compareTo(SweepEvent other) {
            int cmp = Double.compare(this.mbr.getMBR().getMinX(), other.mbr.getMBR().getMinX());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(this.childId, other.childId);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(this.source, other.source);
        }

    }
}

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

public class JoinQueryProcessor extends AbstractJoinQueryProcessor implements IJoinQueryProcessor {
    private static final Logger logger = LogManager.getLogger(JoinQueryProcessor.class);

    public JoinQueryProcessor(AbstractIRTree tree) {
        super(tree);
    }

//    private long buildOrderedPairKey(int id1, int id2) {
//        int minId = Math.min(id1, id2);
//        int maxId = Math.max(id1, id2);
//        return (((long) minId) << 32) | (maxId & 0xffffffffL);
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

//    private boolean shouldPruneByThresholdPolicy(double spatialDistance,
//                                                 double textualSimilarity,
//                                                 float spatialThreshold,
//                                                 float textualThreshold,
//                                                 ThresholdPolicy thresholdPolicy) {
//        // When textual filtering is enabled, pairs with zero textual overlap must be pruned.
//        if (textualThreshold > 0 && textualSimilarity <= 0.0) {
//            return true;
//        }
//
//        if (thresholdPolicy == ThresholdPolicy.STRICT) {
//            if (spatialDistance > spatialThreshold) {
//                return true;
//            }
//            return textualThreshold > 0 && textualSimilarity < textualThreshold;
//        }
//
//        if (thresholdPolicy == ThresholdPolicy.COMBINED_COST) {
//            if (textualThreshold <= 0) {
//                return spatialDistance > spatialThreshold;
//            }
//            double combinedThreshold = combinedScore(spatialThreshold, textualThreshold);
//            return combinedScore(spatialDistance, textualSimilarity) > combinedThreshold;
//        }
//
//        throw new IllegalArgumentException("Unsupported threshold adjustment strategy: " + thresholdPolicy);
//    }

//    private double resolvePairTextualUpperBound(Map<Integer, Double> relevanceScores,
//                                                int entryIdA,
//                                                int entryIdB,
//                                                QueryStrategy queryStrategy) {
//        if (relevanceScores == null || relevanceScores.isEmpty()) {
//            return 0.0;
//        }
//
//        double directUpperBound = pairTextualUpperBound(relevanceScores, entryIdA, entryIdB);
//        QueryStrategy effectiveStrategy = QueryStrategy.orDefault(queryStrategy);
//        if (effectiveStrategy != QueryStrategy.FULL_JOIN || directUpperBound > 0.0) {
//            return directUpperBound;
//        }
//
//        // FULL_JOIN may return node-level term scores (non child-id keyed map).
//        // Use a conservative node-pair upper bound instead of forcing zero and pruning all pairs.
//        double fallbackUpperBound = 0.0;
//        for (double score : relevanceScores.values()) {
//            fallbackUpperBound = Math.max(fallbackUpperBound, score);
//        }
//        return fallbackUpperBound;
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
//                                                                   QueryStrategy queryStrategy) {
////                                                                   float spatialThreshold) {
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
    @Override
    public List<SKJoinQuery.Result> selfJoinSKQueryBestFirst(IDocumentIndex invertedList, SKJoinQuery query,
                                                             float spatialThreshold, float textualThreshold,
                                                             JoinConfiguration joinConfiguration) {
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
                new RtreeEntry(tree.getRootIdentifier(), false), Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_VALUE));

        // Track results
        List<SKJoinQuery.Result> results = new ArrayList<>();
        int priorityQueueMaxSize = queue.size();    // Maximum size of the priority queue to control memory usage

        // Set for tracking processed pairs to avoid duplicates
        // Set<Integer> processedPairs = new HashSet<>();
        Set<Long> processedPairs = new HashSet<>();

        while (!queue.isEmpty()) {
            NNEntryPair currentEntry = queue.poll();
            RtreeEntry rtreeEntry1 = currentEntry.entry1;
            RtreeEntry rtreeEntry2 = currentEntry.entry2;

            // Check leaf entries (actual data objects)
            if (rtreeEntry1.isLeafEntry && rtreeEntry2.isLeafEntry) {
                int id1 = rtreeEntry1.getIdentifier();
                int id2 = rtreeEntry2.getIdentifier();

                if (id1 == id2) {
                    continue; // Skip self-joins
                }

                // Create a unique key for this pair to avoid duplicates
                // int pairKey = Objects.hash(Math.min(id1, id2), Math.max(id1, id2));
                long pairKey = buildOrderedPairKey(id1, id2);

                if (!processedPairs.add(pairKey)) {
                    continue;
                }

//                double textualSimilarity = currentEntry.textualCost;
//
//                if (shouldPruneByThresholdPolicy(
//                        currentEntry.spatialCost,
//                        textualSimilarity,
//                        spatialThreshold,
//                        textualThreshold,
//                        thresholdPolicy)) {
//                    continue;
//                }
                if (shouldPruneByThresholds(currentEntry.spatialCost, currentEntry.textualCost, spatialThreshold, textualThreshold)) {
                    logger.debug("Ejected pair ({}, {}) spatial={} textual={}", id1, id2, currentEntry.spatialCost, currentEntry.textualCost);
                    continue; // Skip pairs that don't meet the spatial threshold
                }

//                double combinedCost = combinedScore(currentEntry.spatialCost, textualSimilarity);
                double combinedCost = combinedScore(currentEntry.spatialCost, currentEntry.textualCost); // TODO FOR STATISTICS

                // Add valid pair to results
                results.add(new SKJoinQuery.Result(id1, id2, currentEntry.spatialCost,  currentEntry.textualCost,  combinedCost));
//                        textualSimilarity, combinedCost));

                numOfVisitedNodes++;
            } else {
                // Process internal nodes
                Node nodeX = readNode(rtreeEntry1.getIdentifier());
                Node nodeY = readNode(rtreeEntry2.getIdentifier());
                if (nodeX == null || nodeY == null) {
                    logger.warn("Skipping pair with missing node(s): x={}, y={}", rtreeEntry1.getIdentifier(), rtreeEntry2.getIdentifier());
                    continue;
                }
                numOfVisitedNodes += 2;

                Map<Integer, Double> relevanceScores = null;

                if (textualThreshold > 0) {
//                    relevanceScores = scoreNodePairForQuery(
//                            invertedList,
//                            nodeX.getIdentifier(),
//                            nodeY.getIdentifier(),
//                            query.getKeywords(),
//                            query.getKeywordWeights(),
//                            similarityType,
//                            queryStrategy
//                    );
//                    relevanceScores = invertedList.calculateTextualRelevancy(
//                            nodeX.getIdentifier(),
//                            nodeY.getIdentifier(),
//                            query.getKeywords(),
//                            query.getKeywordWeights(),
//                            similarityType,
//                            queryStrategy
//                    );
                    relevanceScores = calculateNodePairRelevanceScores(
                            invertedList,
                            nodeX.getIdentifier(),
                            nodeY.getIdentifier(),
                            query.getKeywords(),
                            query.getKeywordWeights(),
                            similarityType,
                            queryStrategy
                    );

                    // Early exit if either node has no relevant keywords
                    if (relevanceScores.isEmpty()) {
                        continue;
                    }
                }

                final boolean isSelfJoin = (nodeX.getIdentifier() == nodeY.getIdentifier());
                final boolean isLeafEntryX = nodeX.isLeaf();
                final boolean isLeafEntryY = nodeY.isLeaf();

                // Choose join strategy: plane-sweep or nested loop
                if (joinStrategy == JoinStrategy.PLANE_SWEEP) {
                    // Apply plane-sweep algorithm for efficient spatial join processing
//                    float sweepTextualThreshold = thresholdPolicy == ThresholdPolicy.STRICT
//                            ? textualThreshold
//                            : 0.0f;
//                    List<CandidatePair> candidatePairs = generatePlaneSweepJoin(
//                            nodeX,
//                            nodeY,
//                            spatialThreshold,
//                            sweepTextualThreshold,
//                            relevanceScores,
//                            isSelfJoin,
//                            queryStrategy
//                    );

                    List<CandidatePair> candidatePairs = generatePlaneSweepJoin(nodeX, nodeY, spatialThreshold,
                            textualThreshold, relevanceScores, isSelfJoin, queryStrategy);

                    // Process candidate pairs from plane-sweep
                    for (CandidatePair candidate : candidatePairs) {
                        final int childIdX = candidate.childIdX;
                        final int childIdY = candidate.childIdY;

                        // Skip if childIdX is greater than childIdY to avoid duplicates
                        if (isSelfJoin && childIdX > childIdY) {
                            continue;
                        }

                        // Calculate combined score
                        double textualUpperBound = candidate.textualUpperBound;
                        if (textualThreshold > 0 && isLeafEntryX && isLeafEntryY) {
                            textualUpperBound = resolveLeafTextualSimilarity(
                                    invertedList,
                                    nodeX.getIdentifier(), childIdX,
                                    nodeY.getIdentifier(), childIdY,
                                    similarityType,
                                    queryStrategy,
                                    relevanceScores);
                        }

                        if (shouldPruneByThresholds(candidate.spatialDistance, textualUpperBound,
                                spatialThreshold, textualThreshold)) {
                            continue;
                        }

                        double combinedScore = combinedScore(candidate.spatialDistance, textualUpperBound);

//                        if (shouldPruneByThresholdPolicy(
//                                candidate.spatialDistance,
//                                candidate.textualUpperBound,
//                                spatialThreshold,
//                                textualThreshold,
//                                thresholdPolicy)) {
//                            continue;
//                        }

//                        if (shouldPruneByThresholds(candidate.spatialDistance, candidate.textualUpperBound,
//                                spatialThreshold, textualThreshold)) {
//                            continue;
//                        }

                        // Create entries based on node level
                        RtreeEntry childEntryX = new RtreeEntry(candidate.childIdX, isLeafEntryX);
                        RtreeEntry childEntryY = new RtreeEntry(candidate.childIdY, isLeafEntryY);

                        // Add to queue for further processing
                        queue.add(new NNEntryPair(childEntryX, childEntryY, candidate.spatialDistance,
                                textualUpperBound, combinedScore));
                    }
                } else {
                    // Use nested loop approach (original implementation)
                    // Get node entries once for better performance
                    final TreeMap<Integer, NodeEntry> entriesX = nodeX.getNodeEntries();
                    final TreeMap<Integer, NodeEntry> entriesY = nodeY.getNodeEntries();

                    // Generate all potential pairs between children of nodeX and nodeY
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
                                continue; // Skip if childIdX is greater than childIdY to avoid duplicates
                            }

                            // Calculate spatial distance first (cheaper operation)
                            final double spatialDistance = mbrX.getMinimumDistance(mbrY);
                            // Early spatial pruning - skip if distance exceeds threshold
                            if (spatialDistance > spatialThreshold) {
                                continue;
                            }

                            // Calculate textual similarity when textual threshold is active
                            double textualUpperBound = 1.0;
                            if (textualThreshold > 0) {
                                // Get minimum textual upper bound score between the two children for conservative estimation
//                                textualUpperBound = resolvePairTextualUpperBound(
//                                        relevanceScores,
//                                        childIdX,
//                                        childIdY,
//                                        queryStrategy
//                                );
                                textualUpperBound = isLeafEntryX && isLeafEntryY
                                        ? resolveLeafTextualSimilarity(
                                        invertedList,
                                        nodeX.getIdentifier(), childIdX,
                                        nodeY.getIdentifier(), childIdY,
                                        similarityType,
                                        queryStrategy,
                                        relevanceScores)
                                        : resolveTextualUpperBound(relevanceScores, childIdX, childIdY, queryStrategy);
//                                textualUpperBound = pairTextualUpperBound(relevanceScores, childIdX, childIdY);
                            }

//                            if (shouldPruneByThresholdPolicy(
//                                    spatialDistance,
//                                    textualUpperBound,
//                                    spatialThreshold,
//                                    textualThreshold,
//                                    thresholdPolicy)) {
//                                continue;
//                            }

                            if (shouldPruneByThresholds(spatialDistance, textualUpperBound, spatialThreshold, textualThreshold)) {
                                continue;
                            }

                            // Calculate combined score only after both spatial and textual criteria pass
                            final double combinedScore = combinedScore(spatialDistance, textualUpperBound);

                            // Final combined threshold check
//                            if (combinedScore > combinedThreshold) {
//                                continue;
//                            }

                            // Create R-tree entries for the child nodes
                            final RtreeEntry childEntryX = new RtreeEntry(childIdX, isLeafEntryX);
                            final RtreeEntry childEntryY = new RtreeEntry(childIdY, isLeafEntryY);

                            // Add promising pair to queue for further processing
                            queue.add(new NNEntryPair(childEntryX, childEntryY, spatialDistance,
                                    textualUpperBound, combinedScore));
                        }
                    }
                }
            }
            priorityQueueMaxSize = Math.max(priorityQueueMaxSize, queue.size());
        }

        // Sort results by combined score (best first)
        Collections.sort(results);
        logger.debug("[BestFirst] Number of results: {}", results.size());
        logger.info("[BestFirst] Maximum priority queue size: {}", priorityQueueMaxSize);

        //TODO Remove
        System.out.println("[BestFirst] Using similarity: " + similarityType.getDescription() + " and query strategy: " + queryStrategy.getDescription());
        invertedList.printStatistics();


        return results;
    }



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
    @Override
    public List<SKJoinQuery.Result> selfJoinSKQueryRecursive(IDocumentIndex invertedList, SKJoinQuery query,
                                                             float spatialThreshold, float textualThreshold,
                                                             JoinConfiguration joinConfiguration) {
        JoinConfiguration config = Objects.requireNonNull(joinConfiguration, "joinConfiguration must not be null");
//        ThresholdPolicy thresholdPolicy = config.getThresholdPolicy();
        JoinStrategy joinStrategy = config.getJoinStrategy();
        SimilarityType similarityType = config.getSimilarityType();
        QueryStrategy queryStrategy = config.getQueryStrategy();

        List<SKJoinQuery.Result> results = new ArrayList<>();

        // Start recursive traversal from root
        NNEntryPair rootEntry = new NNEntryPair(
                new RtreeEntry(tree.getRootIdentifier(), false),
                new RtreeEntry(tree.getRootIdentifier(), false),
                Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_VALUE
        );

        selfJoinSKQueryRecursiveTraversal(rootEntry, invertedList, query, spatialThreshold, textualThreshold,
                joinStrategy, similarityType, queryStrategy, results);

        // Sort results by combined score (best first)(lower is better)
        Collections.sort(results);
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
     * @param spatialThreshold Maximum allowed spatial distance between pairs
     * @param textualThreshold Minimum required textual similarity between pairs
     * @param joinStrategy Algorithm strategy for joining nodes (PLANE_SWEEP or NONE)
     * @param results List to collect matching object pairs that meet all criteria
     */
    private void selfJoinSKQueryRecursiveTraversal(NNEntryPair pairEntry, IDocumentIndex invertedFile,
                                                   SKJoinQuery query, float spatialThreshold, float textualThreshold,
                                                   JoinStrategy joinStrategy, SimilarityType similarityType,
                                                   QueryStrategy queryStrategy, List<SKJoinQuery.Result> results) {
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
        final boolean isSelfJoin = (nodeIdX == nodeIdY);

        // Base case: both nodes are leaf nodes (level 0)
        if (nodeX.isLeaf() && nodeY.isLeaf()) {
            processLeafNodes(nodeX, nodeY, isSelfJoin, invertedFile, query,
                    spatialThreshold, textualThreshold, similarityType, queryStrategy, results);
            return;
        }

        // Process internal nodes based on join strategy
        switch (joinStrategy) {
            case PLANE_SWEEP:
                processInternalNodesWithPlainSweep(nodeX, nodeY, invertedFile, query,
                        spatialThreshold, textualThreshold, joinStrategy, similarityType, queryStrategy, results);
                break;
            case DEFAULT:
                processInternalNodes(nodeX, nodeY, invertedFile, query,
                        spatialThreshold, textualThreshold, joinStrategy, similarityType, queryStrategy, results);
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
     * </ul>
     *
     * @param nodeX First leaf node containing POI data objects
     * @param nodeY Second leaf node containing POI data objects
     * @param isSelfJoin Flag indicating if nodeX and nodeY are the same node (self-join case)
     * @param invertedList Index structure for efficient textual relevance calculation
     * @param query Contains search keywords and their associated weights
     * @param spatialThreshold Maximum allowed spatial distance between POI pairs
     * @param textualThreshold Minimum required textual similarity between POI pairs
     * @param results Collection to store matching POI pairs that satisfy all criteria
     */
    private void processLeafNodes(Node nodeX, Node nodeY, boolean isSelfJoin,
                                  IDocumentIndex invertedList, SKJoinQuery query,
                                  float spatialThreshold, float textualThreshold,
                                  SimilarityType similarityType,
                                  QueryStrategy queryStrategy,
                                  List<SKJoinQuery.Result> results) {

        // Pre-calculate textual relevance scores once
        Map<Integer, Double> relevanceScores = null;
        if (textualThreshold > 0) {
//            relevanceScores = scoreNodePairForQuery(
//                    invertedList,
//                    nodeX.getIdentifier(),
//                    nodeY.getIdentifier(),
//                    query.getKeywords(),
//                    query.getKeywordWeights(),
//                    similarityType,
//                    queryStrategy
//            );

//            relevanceScores = invertedList.calculateTextualRelevancy( nodeX.getIdentifier(), nodeY.getIdentifier(),
//                    query.getKeywords(), query.getKeywordWeights(), similarityType, queryStrategy);
            relevanceScores = calculateNodePairRelevanceScores(
                    invertedList,
                    nodeX.getIdentifier(),
                    nodeY.getIdentifier(),
                    query.getKeywords(),
                    query.getKeywordWeights(),
                    similarityType,
                    queryStrategy
//                    spatialThreshold
            );

            if (relevanceScores.isEmpty()) {
                return;
            }
        }

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

                // Calculate spatial distance between MBRs of the two entries
                double spatialDistance = nodeEntryX.getMBR().getMinimumDistance(nodeEntryY.getMBR());

                // Calculate textual similarity
                double textualSimilarity = 1.0;
                if (textualThreshold > 0) {
//                    textualSimilarity = resolvePairTextualUpperBound(
//                            relevanceScores,
//                            objectIdX,
//                            objectIdY,
//                            queryStrategy
//                    );
                    textualSimilarity = resolveLeafTextualSimilarity(
                            invertedList,
                            nodeX.getIdentifier(), objectIdX,
                            nodeY.getIdentifier(), objectIdY,
                            similarityType,
                            queryStrategy,
                            relevanceScores);
//                    textualSimilarity = pairTextualUpperBound(relevanceScores, objectIdX, objectIdY);
                }

//                if (shouldPruneByThresholdPolicy(
//                        spatialDistance,
//                        textualSimilarity,
//                        spatialThreshold,
//                        textualThreshold,
//                        thresholdPolicy)) {
//                    continue;
//                }
                if (shouldPruneByThresholds(spatialDistance, textualSimilarity, spatialThreshold, textualThreshold)) {
                    continue;
                }

                results.add(new SKJoinQuery.Result(objectIdX, objectIdY, spatialDistance, textualSimilarity,
                        combinedScore(spatialDistance, textualSimilarity)));
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
     *   <li>Supports different threshold adjustment strategies</li>
     * </ul>
     *
     * @param nodeX First internal node to process
     * @param nodeY Second internal node to process
     * @param invertedList Document index for calculating textual relevance scores
     * @param query Contains search keywords and their weights
     * @param spatialThreshold Maximum allowed spatial distance between node pairs
     * @param textualThreshold Minimum required textual similarity between node pairs
     * @param joinStrategy Algorithm strategy for joining nodes (PLANE_SWEEP or NONE)
     * @param results Collection to store matching pairs that satisfy all criteria
     */
    private void processInternalNodes(Node nodeX, Node nodeY,
                                      IDocumentIndex invertedList, SKJoinQuery query,
                                      float spatialThreshold, float textualThreshold,
                                      JoinStrategy joinStrategy, SimilarityType similarityType,
                                      QueryStrategy queryStrategy, List<SKJoinQuery.Result> results) {

        // Pre-calculate textual relevance scores once if textual threshold is active
        Map<Integer, Double> relevanceScores = null;
        if (textualThreshold > 0) {
//            relevanceScores = scoreNodePairForQuery(
//                    invertedList,
//                    nodeX.getIdentifier(),
//                    nodeY.getIdentifier(),
//                    query.getKeywords(),
//                    query.getKeywordWeights(),
//                    similarityType,
//                    queryStrategy
//            );
//            relevanceScores = invertedList.calculateTextualRelevancy(nodeX.getIdentifier(), nodeY.getIdentifier(),
//                    query.getKeywords(), query.getKeywordWeights(), similarityType, queryStrategy);

            relevanceScores = calculateNodePairRelevanceScores(
                    invertedList,
                    nodeX.getIdentifier(),
                    nodeY.getIdentifier(),
                    query.getKeywords(),
                    query.getKeywordWeights(),
                    similarityType,
                    queryStrategy
//                    spatialThreshold
            );

            // Early exit if no textual relevance found
            if (relevanceScores.isEmpty()) {
                return;
            }
        }

        final boolean isSelfJoin = (nodeX.getIdentifier() == nodeY.getIdentifier());
        final boolean isLeafEntryX = nodeX.isLeaf();
        final boolean isLeafEntryY = nodeY.isLeaf();

        // Get node entries once for better performance
        final TreeMap<Integer, NodeEntry> entriesX = nodeX.getNodeEntries();
        final TreeMap<Integer, NodeEntry> entriesY = nodeY.getNodeEntries();

        // Process all pairs between the two nodes
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

                // Early spatial pruning - calculate distance only once
                final double spatialDistance = mbrX.getMinimumDistance(mbrY);
                if (spatialDistance > spatialThreshold) {
                    continue; // Skip pairs that don't meet the spatial threshold
                }

                // Calculate textual upper bound only if textual threshold is active
                double textualUpperBound = 1.0;
                if (textualThreshold > 0) {
//                    textualUpperBound = resolvePairTextualUpperBound(
//                            relevanceScores,
//                            childIdX,
//                            childIdY,
//                            queryStrategy
//                    );
                    textualUpperBound = isLeafEntryX && isLeafEntryY
                            ? resolveLeafTextualSimilarity(
                            invertedList,
                            nodeX.getIdentifier(), childIdX,
                            nodeY.getIdentifier(), childIdY,
                            similarityType,
                            queryStrategy,
                            relevanceScores)
                            : resolveTextualUpperBound(relevanceScores, childIdX, childIdY, queryStrategy);
//                    textualUpperBound = pairTextualUpperBound(relevanceScores, childIdX, childIdY);
                }

//                if (shouldPruneByThresholdPolicy(
//                        spatialDistance,
//                        textualUpperBound,
//                        spatialThreshold,
//                        textualThreshold,
//                        thresholdPolicy)) {
//                    continue;
//                }
                if (shouldPruneByThresholds(spatialDistance, textualUpperBound, spatialThreshold, textualThreshold)) {
                    continue;
                }

                // Create entries for recursive call
                final RtreeEntry childEntryX = new RtreeEntry(childIdX, isLeafEntryX);
                final RtreeEntry childEntryY = new RtreeEntry(childIdY, isLeafEntryY);
                final double combinedScore = combinedScore(spatialDistance, textualUpperBound);

                // Recursive traversal
                selfJoinSKQueryRecursiveTraversal(
                        new NNEntryPair(childEntryX, childEntryY, spatialDistance, textualUpperBound, combinedScore),
                        invertedList, query, spatialThreshold, textualThreshold, joinStrategy, similarityType, queryStrategy, results);
            }
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
     * </ul>
     *
     * @param nodeX First internal node to process in the join operation
     * @param nodeY Second internal node to process in the join operation
     * @param invertedList Document index for calculating textual relevance scores
     * @param query Query parameters containing keywords and their weights
     * @param spatialThreshold Maximum allowed spatial distance between node pairs
     * @param textualThreshold Minimum required textual similarity between node pairs
     * @param joinStrategy Algorithm strategy for joining nodes (PLANE_SWEEP or NONE)
     * @param results Collection to store matching pairs that satisfy all criteria
     */
    private void processInternalNodesWithPlainSweep(Node nodeX, Node nodeY, IDocumentIndex invertedList, SKJoinQuery query,
                                                    float spatialThreshold, float textualThreshold,
                                                    JoinStrategy joinStrategy, SimilarityType similarityType,
                                                    QueryStrategy queryStrategy, List<SKJoinQuery.Result> results) {

        // Pre-calculate textual relevance scores once if textual filtering is needed
        Map<Integer, Double> relevanceScores = null;
        if (textualThreshold > 0) {
//            relevanceScores = scoreNodePairForQuery(
//                    invertedList,
//                    nodeX.getIdentifier(),
//                    nodeY.getIdentifier(),
//                    query.getKeywords(),
//                    query.getKeywordWeights(),
//                    similarityType,
//                    queryStrategy
//            );

//            relevanceScores = invertedList.calculateTextualRelevancy(nodeX.getIdentifier(), nodeY.getIdentifier(),
//                    query.getKeywords(), query.getKeywordWeights(), similarityType, queryStrategy);

            relevanceScores  = calculateNodePairRelevanceScores(
                    invertedList,
                    nodeX.getIdentifier(),
                    nodeY.getIdentifier(),
                    query.getKeywords(),
                    query.getKeywordWeights(),
                    similarityType,
                    queryStrategy
//                    spatialThreshold
            );


            // Early exit if no textual relevance found
            if (relevanceScores.isEmpty()) {
                return;
            }
        }

        // Apply plane-sweep algorithm to find spatial candidates efficiently
//        float sweepTextualThreshold = thresholdPolicy == ThresholdPolicy.STRICT
//                ? textualThreshold
//                : 0.0f;
        final boolean isSelfJoin = (nodeX.getIdentifier() == nodeY.getIdentifier());

        final List<CandidatePair> candidates = generatePlaneSweepJoin(
                nodeX,
                nodeY,
                spatialThreshold,
                textualThreshold,
                relevanceScores,
                isSelfJoin,
                queryStrategy
        );

        final boolean isLeafEntry = nodeX.isLeaf();

        // Process each candidate pair recursively
        for (final CandidatePair candidate : candidates) {
//            if (shouldPruneByThresholdPolicy(
//                    candidate.spatialDistance,
//                    candidate.textualUpperBound,
//                    spatialThreshold,
//                    textualThreshold,
//                    thresholdPolicy)) {
//                continue;
//            }

            double textualUpperBound = candidate.textualUpperBound;
            if (textualThreshold > 0 && isLeafEntry) {
                textualUpperBound = resolveLeafTextualSimilarity(
                        invertedList,
                        nodeX.getIdentifier(), candidate.childIdX,
                        nodeY.getIdentifier(), candidate.childIdY,
                        similarityType,
                        queryStrategy,
                        relevanceScores);
            }

            if (shouldPruneByThresholds(candidate.spatialDistance, textualUpperBound, spatialThreshold, textualThreshold)) {
                continue;
            }

            final double combinedScore = combinedScore(candidate.spatialDistance, textualUpperBound);

            final NNEntryPair entryPair = new NNEntryPair(
                    new RtreeEntry(candidate.childIdX, isLeafEntry),
                    new RtreeEntry(candidate.childIdY, isLeafEntry),
                    candidate.spatialDistance,
                    textualUpperBound,
                    combinedScore);

            selfJoinSKQueryRecursiveTraversal(entryPair, invertedList, query,
                    spatialThreshold, textualThreshold, joinStrategy, similarityType, queryStrategy, results);
        }
    }

    //==========================================================================================
    //====================================== Common JOIN Methods ===================================
    //==========================================================================================

    /**
     * Applies the plane-sweep algorithm to efficiently find candidate pairs of nodes for recursive traversal.
     * The algorithm sorts nodes by x-coordinate and maintains an active set while sweeping through space,
     * reducing the number of comparisons needed compared to nested loops.
     *
     * @param nodeX First node to process in the plane-sweep algorithm
     * @param nodeY Second node to process in the plane-sweep algorithm
     * @param spatialThreshold Maximum allowed spatial distance between node pairs
     * @param textualThreshold Minimum required textual similarity between node pairs
     * @param relevanceScores Pre-calculated textual relevance scores for node entries
     * @return List of candidate pairs that satisfy the spatial and textual thresholds
     */
    protected List<CandidatePair> generatePlaneSweepJoin(
            Node nodeX, Node nodeY,
            float spatialThreshold, float textualThreshold,
            Map<Integer, Double> relevanceScores,
            boolean isSelfJoin,
            QueryStrategy queryStrategy) {

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

        for (SweepEvent event : events) {
            final double currentMinX = event.mbr.getMBR().getMinX();
            final double pruneThreshold = currentMinX - spatialThreshold;

            // Process event based on source node
            if (event.source == 1) {
                // Check nodeX event against all active nodeY events
                for (SweepEvent activeEventY : activeY) {
                    CandidatePair candidate = createCandidateIfValid(
                            event, activeEventY, isSelfJoin,
                            spatialThreshold, textualThreshold, relevanceScores, queryStrategy);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
                activeX.add(event);
            } else {
                // Check nodeY event against all active nodeX events
                for (SweepEvent activeEventX : activeX) {
                    CandidatePair candidate = createCandidateIfValid(
                            activeEventX, event, isSelfJoin,
                            spatialThreshold, textualThreshold, relevanceScores, queryStrategy);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
                activeY.add(event);
            }

            // Efficiently prune events outside the sweep window
            // Remove events whose maxX is less than the prune threshold
            activeX.removeIf(e -> e.mbr.getMBR().getMaxX() < pruneThreshold);
            activeY.removeIf(e -> e.mbr.getMBR().getMaxX() < pruneThreshold);
        }

        return candidates;
    }


    /**
     * Creates a candidate pair if the spatial and textual conditions are met.
     * This method checks if the two events are within the spatial threshold
     * and if their textual relevance scores meet the required threshold.
     *
     * @param eventX First sweep event
     * @param eventY Second sweep event
     * @param isSelfJoin Flag indicating if this is a self-join operation
     * @param spatialThreshold Maximum allowed spatial distance
     * @param textualThreshold Minimum required textual similarity
     * @param relevanceScores Pre-calculated relevance scores for textual filtering
     * @return CandidatePair if valid, null otherwise
     */
    private CandidatePair createCandidateIfValid(
            SweepEvent eventX, SweepEvent eventY, boolean isSelfJoin,
            float spatialThreshold, float textualThreshold,
            Map<Integer, Double> relevanceScores,
            QueryStrategy queryStrategy) {

        // Skip duplicate pairs in self-join
        if (isSelfJoin && eventX.childId > eventY.childId) {
            return null;
        }

        // Calculate spatial distance
        final double spatialDistance = eventX.mbr.getMinimumDistance(eventY.mbr);
        if (spatialDistance > spatialThreshold) {
            return null;
        }

        // Calculate textual upper bound
        double textualUpperBound = 1.0;
        if (relevanceScores != null) {
//            textualUpperBound = resolvePairTextualUpperBound(
//                    relevanceScores,
//                    eventX.childId,
//                    eventY.childId,
//                    queryStrategy
//            );
            textualUpperBound = resolveTextualUpperBound(relevanceScores, eventX.childId, eventY.childId, queryStrategy);
//            textualUpperBound = pairTextualUpperBound(relevanceScores, eventX.childId, eventY.childId);

            if (textualUpperBound < textualThreshold) {
                return null; // Early exit if textual threshold is not met
            }
        }

        return new CandidatePair(eventX.childId, eventY.childId, spatialDistance, textualUpperBound);
    }

    /**
     * Helper class for candidate pairs from plane-sweep.
     */
//    protected static class CandidatePair {
//        final int childIdX;
//        final int childIdY;
//        double spatialDistance;
//        double textualUpperBound;
//
//        CandidatePair(int childIdX, int childIdY, double spatialDistance, double textualUpperBound) {
//            this.childIdX = childIdX;
//            this.childIdY = childIdY;
//            this.spatialDistance = spatialDistance;
//            this.textualUpperBound = textualUpperBound;
//        }
//    }

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

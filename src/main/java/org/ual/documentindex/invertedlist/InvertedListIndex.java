package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.*;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InvertedListIndex implements ITextOnlyDocumentIndex {
    private static final Logger logger = LogManager.getLogger(InvertedListIndex.class);

    /**
     * Thread-safe storage for inverted lists, organized by node ID.
     * - Key (Integer): Node ID in the tree structure.
     * - Value (HashMap): Maps terms (Integer) to their posting lists (ArrayList<PlEntry>).
     * Uses ConcurrentHashMap for thread-safety.
     */
    protected Map<Integer, InvertedListEntry> indexMap;

    protected int numberOfClusters = 0;

    /**
     * Constructor for HashMapInvertedFileIndex.
     * Initializes the storage for inverted lists and sets the number of clusters.
     *
     * @param numberOfClusters The number of clusters to be used in the index.
     */
    public InvertedListIndex(int numberOfClusters) {
        this.indexMap = new ConcurrentHashMap<>();
        this.numberOfClusters = numberOfClusters;
    }

    /**
      * Initializes a new, empty inverted list for the specified node.
      * This method creates a mapping for the given `nodeId` in the internal storage,
      * allowing documents to be added to the node's inverted index.
      *
      * @param nodeId the unique identifier of the node for which to create the inverted list
      */
    public void create(int nodeId) {
        indexMap.put(nodeId, new InvertedListEntry(nodeId));
    }


    /**
     * Adds a document to the inverted index for a specific node.
     * Each term in the document is added to the node's inverted list with its corresponding weight.
     *
     * @param nodeId   the ID of the node where the document should be indexed
     * @param docId    the unique identifier of the document being added
     * @param document the list of weighted terms representing the document content
     * @throws IllegalArgumentException if the specified node doesn't exist in the index
     */
    @Override
    public void addDocument(int nodeId, int docId, List<WeightEntry> document) {
        InvertedListEntry invertedList = indexMap.get(nodeId);
        if (invertedList == null) {
            logger.error("Attempted to add document [docId={}] to non-existent nodeId {}.", docId, nodeId);
            throw new IllegalArgumentException("Node " + nodeId + " not found in the index");
        }

        invertedList.addDocument(docId, document);
    }


    /**
     * Adds a document to the inverted index for a specific node with cluster information.
     * Each term in the document is added to the node's inverted list with its corresponding weight
     * and cluster assignment.
     *
     * @param nodeId   the ID of the node where the document should be indexed
     * @param docId    the unique identifier of the document being added
     * @param document the list of weighted terms representing the document content
     * @param cluster  the cluster ID to which this document belongs
     * @throws IllegalArgumentException if the specified node doesn't exist in the index
     */
    public void addDocument(int nodeId, int docId, List<WeightEntry> document, int cluster) {
        InvertedListEntry invertedList = indexMap.get(nodeId);
        if (invertedList == null) {
            logger.error("Attempted to add document to non-existent nodeId {}", nodeId);
            throw new IllegalArgumentException("Node " + nodeId + " not found in the index");
        }

        if (cluster < 0 || cluster >= this.numberOfClusters) {
            logger.error("Cluster {} out of bounds for node [numClusters={}]", cluster, this.numberOfClusters);
            throw new IllegalArgumentException("Cluster " + cluster + " is out of bounds");
        }

        invertedList.addDocument(docId, document, cluster);
    }


    /**
     * Stores the inverted list for a node as a collection of pseudo-documents.
     * Each pseudo-document contains the maximum weight for each term in that node's inverted list.
     *
     * @param nodeId The ID of the node whose inverted list should be stored
     * @return A list of pseudo-documents, where each document is represented by its term and maximum weight
     */
    @Override
    public List<WeightEntry> store(int nodeId) {
        InvertedListEntry invertedList = indexMap.get(nodeId);

        if (invertedList == null) {
            logger.warn("Inverted list for nodeId {} not found during store operation.", nodeId);
            return Collections.emptyList();
        }

        return invertedList.extractPseudoDocument();
    }


    /**
     * Stores the inverted list for a node as a collection of cluster-specific pseudo-documents.
     * Each pseudo-document contains the maximum weight for each term in that cluster.
     *
     * @param nodeId The ID of the node whose inverted list should be stored
     * @return A list of cluster-specific pseudo-documents
     */
    public List<List<WeightEntry>> storeClusterEnhance(int nodeId) {
        InvertedListEntry invertedList = indexMap.get(nodeId);

        if (invertedList == null) {
            logger.warn("Inverted list for nodeId {} not found during storeClusterEnhance operation.", nodeId);
            return Collections.emptyList();
        }

        return invertedList.extractClusterSpecificPseudoDocuments(numberOfClusters);
    }

    /**
     * Retrieves the inverted list entry for a specific node.
     *
     * @param nodeId the unique identifier of the node
     * @return the InvertedListEntry associated with the node, or null if not found
     */
    protected InvertedListEntry getInvertedList(int nodeId) {
        return indexMap.get(nodeId);
    }


    /**
      * Calculates document scores based on a simple weighted sum of matching terms.
      *
      * @param nodeId   ID of the node containing the inverted list to search
      * @param keywords List of term IDs to search for in the inverted list
      * @return A HashMap mapping document IDs to their accumulated scores
      * @deprecated Use {@link #rankingSum(int, List, List, RankingSumMode)} instead, which supports query term weights and scoring mode.
      */
    @Override
    @Deprecated
    public Map<Integer, Double> rankingSum(int nodeId, List<Integer> keywords) {
        InvertedListEntry invertedList = indexMap.get(nodeId);
        if (invertedList == null) {
            logger.warn("No inverted list found for node: {}", nodeId);
            return Collections.emptyMap();
        }

        if (keywords == null ) {
            logger.error("Keywords must not be null.");
            throw new IllegalArgumentException("Keywords must not be null.");
        }

        RankingOps rankingOps = new RankingOps();
        return rankingOps.rankingSum(invertedList, keywords);
    }


    /**
      * rankingSum implementation that accumulates per-keyword query weights for matching documents.
      *
      * @param nodeId         ID of the node containing the inverted list to search in
      * @param keywords       List of term IDs (integers) to search for in the inverted list
      * @param keywordWeights List of weights for each keyword in the query, must match keywords list length
      * @return A HashMap mapping document IDs to their accumulated scores
      */
    @Override
    public Map<Integer, Double> rankingSum(int nodeId, List<Integer> keywords, List<Double> keywordWeights, RankingSumMode scoringMode) {
        InvertedListEntry invertedList = indexMap.get(nodeId);

        if (invertedList == null) {
            logger.warn("Inverted list for nodeId {} not found in rankingSum.", nodeId);
            return Collections.emptyMap();
        }

        if (keywords == null || keywordWeights == null) {
            logger.error("Keywords and keywordWeights must not be null.");
            throw new IllegalArgumentException("Keywords and keywordWeights must not be null.");
        }

        if (keywords.size() != keywordWeights.size()) {
            logger.error("Keywords and keywordWeights must have the same size. Keywords: {}, Weights: {}",
                    keywords.size(), keywordWeights.size());
            throw new IllegalArgumentException("Keywords size (" + keywords.size() +
                    ") does not match keywordWeights size (" + keywordWeights.size() + ")");
        }

        RankingOps rankingOps = new RankingOps();
        return rankingOps.rankingSum(invertedList, keywords, keywordWeights, scoringMode);
    }


    /**
       * Enhanced `rankingSum` that considers document clusters for scoring.
       *
       * @param nodeId         the ID of the node containing the inverted list to search in
       * @param keywords       the list of term IDs (integers) to search for in the inverted list
       * @param keywordWeights the list of weights for the query terms, must match keywords list length
       * @return a {@link HashMap} mapping document IDs to their maximum scores across all clusters
       * @see #rankingSum(int, List, List, RankingSumMode) for the non-cluster-aware version
       */
    @Override
    public Map<Integer, Double> rankingSumClusterEnhance(int nodeId, List<Integer> keywords, List<Double> keywordWeights, RankingSumMode scoringMode) {
        InvertedListEntry invertedList = indexMap.get(nodeId);

        if (invertedList == null) {
            logger.warn("Inverted list for nodeId {} not found in rankingSumClusterEnhance.", nodeId);
            return Collections.emptyMap();
        }

        if (keywords == null || keywordWeights == null) {
            logger.error("Keywords and keywordWeights must not be null.");
            throw new IllegalArgumentException("Keywords and keywordWeights must not be null.");
        }

        if (keywords.size() != keywordWeights.size()) {
            logger.error("Keywords and keywordWeights must have the same size. Keywords: {}, Weights: {}",
                    keywords.size(), keywordWeights.size());
            throw new IllegalArgumentException("Keywords size (" + keywords.size() +
                    ") does not match keywordWeights size (" + keywordWeights.size() + ")");
        }

        if (this.numberOfClusters <= 0) {
            logger.warn("rankingSumClusterEnhance called with numberOfClusters <= 0. Falling back to standard rankingSum.");
            return rankingSum(nodeId, keywords, keywordWeights, scoringMode);
        }

        RankingOps rankingOps = new RankingOps();
        return rankingOps.rankingSumByCluster(invertedList, keywords, keywordWeights, scoringMode, this.numberOfClusters);
    }


    @Override
    public double rankingSum(int nodeIdA, int nodeIdB) {
        InvertedListEntry listA = indexMap.get(nodeIdA);
        InvertedListEntry listB = indexMap.get(nodeIdB);

        if (listA == null || listB == null) {
            logger.warn("rankingSum called with missing node(s): {} or {}.", nodeIdA, nodeIdB);
            return 0.0;
        }

        NodeSimilarityOps nodeSimilarityOps = new NodeSimilarityOps();
        return nodeSimilarityOps.rankingSum(listA, listB);
    }


    /**
     * Performs boolean filtering on documents in a specified node using a list of keywords.
     *
     * @param nodeId   ID of the node containing the inverted list
     * @param keywords List of term IDs to search for
     * @return A map where keys are document IDs and values are the count of matched keywords
     */
    @Override
    public Map<Integer, Integer> booleanFilter(int nodeId, List<Integer> keywords) {
        InvertedListEntry invertedList = indexMap.get(nodeId);

        if (invertedList == null) {
            logger.warn("Inverted list for nodeId {} not found in booleanFilter.", nodeId);
            return Collections.emptyMap();
        }

        if (keywords == null) {
            logger.error("Keywords list must not be null for booleanFilter on nodeId {}.", nodeId);
            throw new IllegalArgumentException("keywords must not be null (nodeId=" + nodeId + ")");
        }

        RankingOps rankingOps = new RankingOps();
        return rankingOps.booleanFilter(invertedList, keywords);
    }


    //======================================================
    // Textual Similarity Calculations for JOINS
    //======================================================
    //======================================================
    // Single Dataset - Full JOIN
    //======================================================

    /**
     * Computes textual similarity between two nodes using the selected similarity metric.
     *
     * @param nodeIdA the first node identifier
     * @param nodeIdB the second node identifier
     * @param similarityType the similarity metric to apply
     * @return the computed similarity score; returns `0.0` if either node is missing
     */
    @Override
    public double nodesTextualSim(int nodeIdA, int nodeIdB, SimilarityType similarityType) {
        InvertedListEntry listA = indexMap.get(nodeIdA);
        InvertedListEntry listB = indexMap.get(nodeIdB);

        if (listA == null || listB == null) {
            logger.error("nodesTextualSim called with missing node(s): {} or {}. Returning similarity of 0.0.", nodeIdA, nodeIdB);
            return 0.0;
        }

        NodeSimilarityOps nodeSimilarityOps = new NodeSimilarityOps();
        return nodeSimilarityOps.textSimilarity(listA, listB, similarityType);
    }


    /**
     * Computes textual similarity between two nodes with optional cluster-aware processing.
     * Falls back to {@link #nodesTextualSim(int, int, SimilarityType)} when clustering is disabled.
     *
     * @param nodeIdA the first node identifier
     * @param nodeIdB the second node identifier
     * @param similarityType the similarity metric to apply
     * @return the computed similarity score; returns `0.0` if either node is missing
     */
    @Override
    public double nodesTextualSimForClusters(int nodeIdA, int nodeIdB, SimilarityType similarityType) {
        if (numberOfClusters <= 0) {
            return nodesTextualSim(nodeIdA, nodeIdB, similarityType);
        }

        InvertedListEntry listA = indexMap.get(nodeIdA);
        InvertedListEntry listB = indexMap.get(nodeIdB);

        if (listA == null || listB == null) {
            return 0.0;
        }

        NodeSimilarityOps nodeSimilarityOps = new NodeSimilarityOps();
        return nodeSimilarityOps.textSimilarityForClusters(listA, listB, similarityType, numberOfClusters);
    }


    /**
     * Computes textual similarity between two specific documents located in two nodes.
     *
     * @param nodeIdA the identifier of the first node
     * @param nodeIdB the identifier of the second node
     * @param docIdA the identifier of the document in the first node
     * @param docIdB the identifier of the document in the second node
     * @param similarityType the similarity metric to use
     * @return the computed document-level similarity score; returns `0.0` if either node is missing
     */
    @Override
    public double nodesDocumentSim(int nodeIdA, int nodeIdB, int docIdA, int docIdB, SimilarityType similarityType) {
        InvertedListEntry listA = indexMap.get(nodeIdA);
        InvertedListEntry listB = indexMap.get(nodeIdB);

        if (listA == null || listB == null) {
            logger.warn("nodesDocumentSim called with missing node(s): nodeA={} or nodeB={}. Returning similarity of 0.0.", nodeIdA, nodeIdB);
            return 0.0;
        }

        NodeSimilarityOps nodeSimilarityOps = new NodeSimilarityOps();
        return nodeSimilarityOps.textualSimExact(listA, listB, docIdA, docIdB, similarityType);
    }

    //======================================================
    // Single Dataset - Constraint JOIN
    //======================================================

    /**
     * Calculates the textual relevancy scores between two nodes based on their inverted lists.
     *
     * @param nodeId1        ID of the first node
     * @param nodeId2        ID of the second node
     * @param keywords       List of term IDs to search for in both nodes
     * @param keywordWeights List of weights corresponding to each keyword in the query
     * @return A map where keys are document IDs and values are their combined relevancy scores.
     */
    @Override
    public Map<Integer, Double> nodesConstraintTextualSim(int nodeId1, int nodeId2, List<Integer> keywords,
                                                          List<Double> keywordWeights, SimilarityType similarityType) {
        if (keywords == null || keywordWeights == null) {
            logger.error("keywords and keywordWeights must not be null.");
            throw new IllegalArgumentException("keywords and keywordWeights must not be null.");
        }

        if (keywords.size() != keywordWeights.size()) {
            logger.error("keywords and keywordWeights size mismatch. keywords: {}, keywordWeights: {}",
                    keywords.size(), keywordWeights.size());
            throw new IllegalArgumentException("keywords size (" + keywords.size()
                    + ") does not match keywordWeights size (" + keywordWeights.size() + ").");
        }

        InvertedListEntry list1 = indexMap.get(nodeId1);
        InvertedListEntry list2 = indexMap.get(nodeId2);

        if (list1 == null && list2 == null) {
            logger.warn("No inverted lists found for nodeId1={} and nodeId2={}.", nodeId1, nodeId2);
            return Collections.emptyMap();
        }

        if (keywords.isEmpty()) {
            logger.debug("Empty keywords list provided. Returning empty result.");
            return Collections.emptyMap();
        }

        Map<Integer, Double> relevancyScores = new HashMap<>();
        NodeConstraintSimilarityOps similarityOps = new NodeConstraintSimilarityOps();

        if (list1 != null) {
            similarityOps.textualSimilarity(list1, keywords, keywordWeights, relevancyScores, similarityType);
        }

        if (list2 != null && nodeId1 != nodeId2) {
            similarityOps.textualSimilarity(list2, keywords, keywordWeights, relevancyScores, similarityType);
        }

        return sanitizeSimilarityScores(relevancyScores);
    }

    /**
     * Calculates the textual relevancy scores between two nodes with cluster enhancement.
     *
     * @param nodeId1        ID of the first node
     * @param nodeId2        ID of the second node
     * @param keywords       List of term IDs to search for in both nodes
     * @param keywordWeights List of weights corresponding to each keyword in the query
     * @return A map where keys are document IDs and values are their combined relevancy scores.
     */
    @Override
    public Map<Integer, Double> nodesConstraintTextualSimForClusters(int nodeId1, int nodeId2, List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType) {
        InvertedListEntry list1 = indexMap.get(nodeId1);
        InvertedListEntry list2 = indexMap.get(nodeId2);

        if (list1 == null && list2 == null) {
            return new HashMap<>();
        }

        if (this.numberOfClusters <= 0) {
            logger.warn("nodesConstraintTextualSimForClusters called with numberOfClusters <= 0. Falling back to standard calculateTextualRelevancy.");
            return nodesConstraintTextualSim(nodeId1, nodeId2, keywords, keywordWeights, similarityType);
        }

        Map<Integer, Double> relevancyScores = new HashMap<>();
        NodeConstraintSimilarityOps similarityOps = new NodeConstraintSimilarityOps();

        if (list1 != null) {
            similarityOps.textualSimilarityForClusters(list1, keywords, keywordWeights, relevancyScores, similarityType, this.numberOfClusters);
        }

        if (list2 != null && nodeId1 != nodeId2) {
            similarityOps.textualSimilarityForClusters(list2, keywords, keywordWeights, relevancyScores, similarityType, this.numberOfClusters);
        }

        return sanitizeSimilarityScores(relevancyScores);
    }

    //======================================================
    // Cross-Dataset - Full JOIN
    //======================================================

    @Override
    public double crossDatasetTextualSim(int internalNodeId, int externalNodeId, IDocumentIndex externalInvList,
                                         SimilarityType similarityType, BoundLimit boundLimit) {
        if (externalInvList == null) {
            logger.error("externalInvList must not be null.");
            throw new IllegalArgumentException("externalInvList must not be null.");
        }

        InvertedListEntry listA = indexMap.get(internalNodeId);
        InvertedListEntry listB = ((InvertedListIndex) externalInvList).getInvertedList(externalNodeId);  // TODO For now only InvList

        if (listA == null || listB == null) {
            logger.debug("crossDatasetTextualSim called with missing node(s): internalNode={} or externalNode={}. Returning similarity of 0.0.", internalNodeId, externalNodeId);
            return 0.0;
        }

        CrossDatasetNodeSimilarityOps crossDatasetNodeSimilarityOps = new CrossDatasetNodeSimilarityOps();

        switch(boundLimit) {
            case UPPER_BOUND:
                return crossDatasetNodeSimilarityOps.textualSimUpperBound(listA, listB, similarityType);
            case LOWER_BOUND:
                return crossDatasetNodeSimilarityOps.textualSimLowerBound(listA, listB, similarityType);
            default:
                throw new IllegalArgumentException("boundLimit not yet implemented");
        }
    }

    @Override
    public double crossDatasetDocumentSim(int internalNodeId, int externalNodeId, int internalDocId, int externalDocId,
                                          IDocumentIndex externalInvList, SimilarityType similarityType) {
        if (externalInvList == null) {
            logger.error("externalInvList must not be null.");
            throw new IllegalArgumentException("externalInvList must not be null.");
        }

        InvertedListEntry listA = indexMap.get(internalNodeId);
        InvertedListEntry listB = ((InvertedListIndex) externalInvList).getInvertedList(externalNodeId);  // TODO For now only InvList

        if (listA == null || listB == null) {
            logger.warn("crossDatasetTextualSim called with missing node(s): internalNode={} or externalNode={}. Returning similarity of 0.0.", internalNodeId, externalNodeId);
            return 0.0;
        }

        CrossDatasetNodeSimilarityOps crossDatasetNodeSimilarityOps = new CrossDatasetNodeSimilarityOps();
        return crossDatasetNodeSimilarityOps.textualSimExact(listA, listB, internalDocId, externalDocId, similarityType);
    }

    //======================================================
    // Cross-Dataset - Constraint JOIN
    //======================================================

    // EXPERIMENTAL (NOT IMPLEMENTED)
    @Override
    public double crossDatasetConstraintPerNodeTextualSim(int internalNodeId, int externalNodeId, IDocumentIndex externalInvList,
                                                   Map<Integer, Double> internalKeywordWeights, Map<Integer, Double> externalKeywordWeights,
                                                   SimilarityType similarityType) {
        if (internalKeywordWeights == null || externalKeywordWeights == null) {
            logger.error("internalKeywordWeights and externalKeywordWeights must not be null.");
            throw new IllegalArgumentException("internalKeywordWeights and externalKeywordWeights must not be null.");
        }

        InvertedListEntry internalList = indexMap.get(internalNodeId);
        InvertedListEntry externalList = ((InvertedListIndex) externalInvList).getInvertedList(externalNodeId);

        if (internalList == null && externalList == null) {
            logger.warn("No inverted lists found for nodeId1={} and nodeId2={}.", internalNodeId, externalNodeId);
            return 0.0;
        }

        // TODO WIP
        CrossDatasetNodeConstraintSimilarityOps crossDatasetNodeSimilarityOps = new CrossDatasetNodeConstraintSimilarityOps();
        return crossDatasetNodeSimilarityOps.textualSimilarity(internalList, externalList, internalKeywordWeights, externalKeywordWeights, similarityType);
    }

    // TODO TEMPORARY METHOD
    @Override
    public Map<Integer, Double> crossDatasetConstraintTextualSim(int nodeId, List<Integer> keywords, List<Double> keywordWeights, SimilarityType similarityType) {
        if (keywords == null || keywordWeights == null) {
            logger.error("keywords and keywordWeights must not be null.");
            throw new IllegalArgumentException("keywords and keywordWeights must not be null.");
        }

        if (keywords.size() != keywordWeights.size()) {
            logger.error("keywords and keywordWeights size mismatch. keywords: {}, keywordWeights: {}",
                    keywords.size(), keywordWeights.size());
            throw new IllegalArgumentException("keywords size (" + keywords.size()
                    + ") does not match keywordWeights size (" + keywordWeights.size() + ").");
        }

        if (keywords.isEmpty()) {
            return Collections.emptyMap();
        }

        InvertedListEntry list = indexMap.get(nodeId);
        if (list == null) {
            return Collections.emptyMap();
        }

        Map<Integer, Double> relevanceScores = new HashMap<>();
        NodeConstraintSimilarityOps similarityOps = new NodeConstraintSimilarityOps();
        similarityOps.textualSimilarity(list, keywords, keywordWeights, relevanceScores, similarityType);
        return sanitizeSimilarityScores(relevanceScores);
    }



    //======================================================
    // Test-Only Methods - Remove
    //======================================================

    public Map<Integer, Double> calculateTextualRelevancyJaccard(int nodeId1, int nodeId2) {
        InvertedListEntry list1 = indexMap.get(nodeId1);
        InvertedListEntry list2 = indexMap.get(nodeId2);

        if (list1 == null || list2 == null) {
            return new HashMap<>();
        }

        Map<Integer, Double> relevancyScores = new HashMap<>();

        Set<Integer> terms1 = extractAllTerms(list1);
        Set<Integer> terms2 = extractAllTerms(list2);

        for (Integer docId1 : list1.getAllDocumentIds()) {
            Set<Integer> docTerms1 = list1.getTermsForDocument(docId1);
            if (docTerms1 == null) {
                docTerms1 = Collections.emptySet();
            }

            double jaccardSim = calculateJaccardSimilarity(docTerms1, terms2);
            //relevancyScores.put(docId1, jaccardSim);
            relevancyScores.merge(docId1, jaccardSim, Math::max);
        }

        if (nodeId1 != nodeId2) {
            for (Integer docId2 : list2.getAllDocumentIds()) {
                Set<Integer> docTerms2 = list2.getTermsForDocument(docId2);
                if (docTerms2 == null) {
                    docTerms2 = Collections.emptySet();
                }

                double jaccardSim = calculateJaccardSimilarity(docTerms2, terms1);

                // Explicit collision policy for overlapping doc IDs across nodes
                relevancyScores.merge(docId2, jaccardSim, Math::max);
            }
        }

        return relevancyScores;
    }

    public double calculateJaccardSimilarity(Set<Integer> set1, Set<Integer> set2) {
        if (set1 == null || set2 == null || set1.isEmpty() || set2.isEmpty()) {
            logger.info("One or both sets are null or empty. Returning similarity of 0.0.");
            return 0.0;
        }

        Set<Integer> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<Integer> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }


    private Set<Integer> extractAllTerms(InvertedListEntry list) {
        if (list == null || list.postingLists == null || list.postingLists.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(list.postingLists.keySet());

        //return list.postingLists.keySet();
    }


    public int getTotalDocuments() {
        int totalDocuments = 0;
        for (InvertedListEntry invList : indexMap.values()) {
            totalDocuments += invList.getTotalDocumentCount();
        }
        return totalDocuments;
    }

    public int getDocumentFrequency(int term) {
        int frequency = 0;
        for (InvertedListEntry nodeInvList : indexMap.values()) {
            List<PostingListEntry> postingList = nodeInvList.getPostingList(term);
            if (postingList != null) {
                frequency += postingList.size();
            }
        }
        return frequency;
    }

    // Filters invalid numeric values before scores are consumed by join pipelines. (Debugging, remove later)
    private Map<Integer, Double> sanitizeSimilarityScores(Map<Integer, Double> similarityScores) {
        if (similarityScores == null || similarityScores.isEmpty()) {
            return Collections.emptyMap();
        }

        similarityScores.entrySet().removeIf(e -> {
            Double value = e.getValue();
            return value == null || Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0;
        });

        return similarityScores;
    }
}

package org.ual.documentindex.invertedlist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.*;

public class InvertedListEntry {
    private static final Logger logger = LogManager.getLogger(InvertedListEntry.class);

    protected int nodeId;
    protected HashMap<Integer, List<PostingListEntry>> postingLists; // term -> Posting List


    public InvertedListEntry(int nodeId) {
        this.nodeId = nodeId;
        this.postingLists = new HashMap<>();
    }

    @Deprecated
    public void add(int term, PostingListEntry plEntry) {
        postingLists.computeIfAbsent(term, k -> new ArrayList<>()).add(plEntry);
    }

    public void addEntry(int term, double weight) {
        PostingListEntry entry = new PostingListEntry(term, weight);
        postingLists.computeIfAbsent(term, k -> new ArrayList<>()).add(entry);
    }

    public void addDocument(int docId, List<WeightEntry> documents) {
        if (documents == null || documents.isEmpty()) return;

        for (WeightEntry doc : documents) {
            // add the entry to the term's posting list
            PostingListEntry entry = new PostingListEntry(docId, doc.weight);
            postingLists.computeIfAbsent(doc.word, k -> new ArrayList<>()).add(entry);
        }
    }

    public void addDocument(int dockId, List<WeightEntry> documents, int clusterId) {
        if (documents == null || documents.isEmpty()) return;

        for (WeightEntry doc : documents) {
            // add the entry to the term's posting list
            PostingListEntry entry = new PostingListEntry(dockId, doc.weight, clusterId);
            postingLists.computeIfAbsent(doc.word, k -> new ArrayList<>()).add(entry);
        }
    }

    public List<WeightEntry> extractPseudoDocument() {
        List<WeightEntry> pseudoDoc = new ArrayList<>();

        for (Map.Entry<Integer, List<PostingListEntry>> entry : this.postingLists.entrySet()) {
            int term = entry.getKey();
            List<PostingListEntry> postingList = entry.getValue();

            double maxWeight = postingList.stream()
                    .mapToDouble(plEntry -> plEntry.weight)
                    .max()
                    .orElse(0.0);

            pseudoDoc.add(new WeightEntry(term, maxWeight));
        }

        return pseudoDoc;
    }

    public List<List<WeightEntry>> extractClusterSpecificPseudoDocuments(int numberOfClusters) {
        List<List<WeightEntry>> pseudoDoc = new ArrayList<>(numberOfClusters);
        for (int i = 0; i < numberOfClusters; i++) {
            pseudoDoc.add(new ArrayList<>());
        }

        for (Map.Entry<Integer, List<PostingListEntry>> entry : postingLists.entrySet()) {
            int term = entry.getKey();
            List<PostingListEntry> postingList = entry.getValue();

            double[] maxWeight = new double[numberOfClusters];
            Arrays.fill(maxWeight, Double.NEGATIVE_INFINITY);

            for (PostingListEntry plEntry : postingList) {
                int cluster = plEntry.clusterId;
                if (cluster >= 0 && cluster < numberOfClusters) {
                    if (plEntry.weight > maxWeight[cluster]) {
                        maxWeight[cluster] = plEntry.weight;
                    }
                } else {
                    logger.warn("Invalid cluster index {} for documentId {} and term {} during storeClusterEnhance.",
                            cluster, plEntry.documentId, term);
                }
            }

            for (int j = 0; j < numberOfClusters; j++) {
                if (maxWeight[j] > Double.NEGATIVE_INFINITY) {
                    pseudoDoc.get(j).add(new WeightEntry(term, maxWeight[j]));
                }
            }
        }

        return pseudoDoc;
    }

    public List<PostingListEntry> getPostingList(Integer keyword) {
        return postingLists.get(keyword);
    }

    public int getTotalDocumentCount() {
        return postingLists.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public int getPostingListSize() {
        return postingLists.size();
    }

    public Set<Integer> getAllTerms() {
        return postingLists.keySet();
    }

    public int getNodeId() {
        return nodeId;
    }

    /**
     * Retrieves all document IDs that appear in this inverted list entry.
     * For leaf nodes, this returns all documents stored in the posting lists.
     * For internal nodes, this aggregates documents from all terms.
     *
     * @return Set of all document IDs present in this inverted list entry
     */
    public Set<Integer> getAllDocumentIds() {
        Set<Integer> documentIds = new HashSet<>();

        for (List<PostingListEntry> postingList : postingLists.values()) {
            for (PostingListEntry entry : postingList) {
                documentIds.add(entry.documentId);
            }
        }

        return documentIds;
    }

    /**
     * Retrieves all terms (keywords) associated with a specific document in this inverted list entry.
     * Returns the set of term IDs that have postings for the given document.
     *
     * @param documentId The ID of the document to query
     * @return Set of term IDs (keywords) that appear in the specified document,
     *         or empty set if document not found
     */
    public Set<Integer> getTermsForDocument(int documentId) {
        Set<Integer> terms = new HashSet<>();

        for (Map.Entry<Integer, List<PostingListEntry>> entry : postingLists.entrySet()) {
            int term = entry.getKey();
            List<PostingListEntry> postingList = entry.getValue();

            for (PostingListEntry plEntry : postingList) {
                if (plEntry.documentId == documentId) {
                    terms.add(term);
                    break; // Found the document for this term, move to next term
                }
            }
        }

        return terms;
    }

    protected double getTermMaxWeight(int term) {
        List<PostingListEntry> lists = postingLists.get(term);
        if (lists == null || lists.isEmpty()) {
            return 0.0;
        }

        return lists.stream()
                .mapToDouble(plEntry -> plEntry.weight)
                .max()
                .orElse(0.0);

    }
}

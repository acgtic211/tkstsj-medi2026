package org.ual.documentindex.invertedlist;

import org.junit.jupiter.api.Test;
import org.ual.spatialindex.storage.WeightEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InvertedListEntryTest {

    private static List<WeightEntry> doc(WeightEntry... entries) {
        return Arrays.asList(entries);
    }

    private static Set<Integer> setOf(Integer... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    void addDocumentShouldIgnoreNullOrEmptyInput() {
        InvertedListEntry entry = new InvertedListEntry(10);

        entry.addDocument(1, null);
        entry.addDocument(1, new ArrayList<>());

        assertEquals(0, entry.getPostingListSize());
        assertEquals(0, entry.getTotalDocumentCount());
    }

    @Test
    void addDocumentShouldIndexTermsPerDocument() {
        InvertedListEntry entry = new InvertedListEntry(10);

        entry.addDocument(7, doc(new WeightEntry(100, 0.4), new WeightEntry(200, 0.9)));

        assertEquals(2, entry.getPostingListSize());
        assertEquals(2, entry.getTotalDocumentCount());
        assertEquals(1, entry.getPostingList(100).size());
        assertEquals(7, entry.getPostingList(100).get(0).documentId);
    }

    @Test
    void addDocumentWithClusterShouldStoreClusterId() {
        InvertedListEntry entry = new InvertedListEntry(10);

        entry.addDocument(3, doc(new WeightEntry(100, 0.8)), 2);

        PostingListEntry posting = entry.getPostingList(100).get(0);
        assertEquals(3, posting.documentId);
        assertEquals(0.8, posting.weight, 1e-9);
        assertEquals(2, posting.clusterId);
    }

    @Test
    void extractPseudoDocumentShouldKeepMaxWeightPerTerm() {
        InvertedListEntry entry = new InvertedListEntry(10);
        entry.addDocument(1, doc(new WeightEntry(100, 0.3), new WeightEntry(200, 0.4)));
        entry.addDocument(2, doc(new WeightEntry(100, 0.9)));

        List<WeightEntry> pseudoDoc = entry.extractPseudoDocument();

        assertEquals(2, pseudoDoc.size());
        assertTrue(pseudoDoc.stream().anyMatch(w -> w.word == 100 && Math.abs(w.weight - 0.9) < 1e-9));
        assertTrue(pseudoDoc.stream().anyMatch(w -> w.word == 200 && Math.abs(w.weight - 0.4) < 1e-9));
    }

    @Test
    void extractClusterSpecificPseudoDocumentsShouldGroupByClusterAndTerm() {
        InvertedListEntry entry = new InvertedListEntry(10);
        entry.addDocument(1, doc(new WeightEntry(100, 0.5), new WeightEntry(200, 0.2)), 0);
        entry.addDocument(2, doc(new WeightEntry(100, 0.7), new WeightEntry(200, 0.8)), 1);

        List<List<WeightEntry>> clusterDocs = entry.extractClusterSpecificPseudoDocuments(3);

        assertEquals(3, clusterDocs.size());
        assertTrue(clusterDocs.get(0).stream().anyMatch(w -> w.word == 100 && Math.abs(w.weight - 0.5) < 1e-9));
        assertTrue(clusterDocs.get(1).stream().anyMatch(w -> w.word == 100 && Math.abs(w.weight - 0.7) < 1e-9));
        assertTrue(clusterDocs.get(2).isEmpty());
    }

    @Test
    void extractClusterSpecificPseudoDocumentsShouldIgnoreInvalidClusterIds() {
        InvertedListEntry entry = new InvertedListEntry(10);
        entry.addDocument(1, doc(new WeightEntry(100, 0.5)), -1);

        List<List<WeightEntry>> clusterDocs = entry.extractClusterSpecificPseudoDocuments(2);

        assertEquals(2, clusterDocs.size());
        assertTrue(clusterDocs.get(0).isEmpty());
        assertTrue(clusterDocs.get(1).isEmpty());
    }

    @Test
    void getAllTermsAndDocumentsShouldReturnDistinctValues() {
        InvertedListEntry entry = new InvertedListEntry(10);
        entry.addDocument(1, doc(new WeightEntry(100, 0.5), new WeightEntry(200, 0.3)));
        entry.addDocument(1, doc(new WeightEntry(100, 0.9)));
        entry.addDocument(2, doc(new WeightEntry(100, 0.7)));

        Set<Integer> terms = new HashSet<>(entry.getAllTerms());
        Set<Integer> docs = entry.getAllDocumentIds();

        assertEquals(setOf(100, 200), terms);
        assertEquals(setOf(1, 2), docs);
    }

    @Test
    void getTermsForDocumentShouldReturnEmptySetWhenDocumentNotFound() {
        InvertedListEntry entry = new InvertedListEntry(10);
        entry.addDocument(1, doc(new WeightEntry(100, 0.5), new WeightEntry(200, 0.6)));

        assertEquals(setOf(100, 200), entry.getTermsForDocument(1));
        assertTrue(entry.getTermsForDocument(99).isEmpty());
    }
}



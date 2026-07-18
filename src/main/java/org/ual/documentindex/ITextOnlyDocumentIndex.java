package org.ual.documentindex;

import org.ual.spatialindex.storage.WeightEntry;

import java.util.List;

/**
 * Capability contract for indexes that ingest text-only documents.
 */
public interface ITextOnlyDocumentIndex extends IDocumentIndex {
    void addDocument(int nodeId, int docId, List<WeightEntry> document);

    void addDocument(int nodeId, int docId, List<WeightEntry> document, int cluster);
}


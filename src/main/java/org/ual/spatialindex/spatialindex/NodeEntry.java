package org.ual.spatialindex.spatialindex;

import java.util.Comparator;
import java.util.HashSet;

/**
  * NodeEntry represents a node in a spatial index structure, containing an identifier and a minimum bounding rectangle (MBR).
  * It is used to store spatial data in hierarchical index structures like R-trees.
  * For document-aware R-trees, it can also hold document identifiers associated with this spatial entry.
  */
public class NodeEntry implements Comparable<NodeEntry> {
    int identifier;
    Region mbr;
    HashSet<Integer> document; // Optional, used for document-aware R-trees
    long childSignature; // Bloom filter signature for the child node
    float childMaxScore; // Max score for the child node subtree (used in score-based pruning)

    public NodeEntry(int identifier, Region mbr, long childSignature, float childMaxScore) {
        this.identifier = identifier;
        this.mbr = (mbr != null) ? mbr.clone() : null;
        this.childSignature = childSignature;
        this.childMaxScore = childMaxScore;
    }

    public NodeEntry(int identifier, Region mbr, HashSet<Integer> document, long childSignature, float childMaxScore) {
        this.identifier = identifier;
        this.mbr = (mbr != null) ? mbr.clone() : null;
        this.document = (document != null) ? new HashSet<>(document) : null;
        this.childSignature = childSignature;
        this.childMaxScore = childMaxScore;
    }

    public int getIdentifier() {
        return identifier;
    }

    public Region getMBR() {
        return mbr;
    }

    public void setMBR(Region nodeMBR) {
        this.mbr = (nodeMBR != null) ? nodeMBR.clone() : null;
    }

    public HashSet<Integer> getDocument() {
        return document;
    }

    public void setDocument(HashSet<Integer> document) {
        this.document = (document != null) ? new HashSet<>(document) : null;
    }

    public long getChildSignature() {
        return childSignature;
    }

    public void setChildSignature(long childSignature) {
        this.childSignature = childSignature;
    }

    public float getChildMaxScore() {
        return childMaxScore;
    }

    public void setChildMaxScore(float childMaxScore) {
        this.childMaxScore = childMaxScore;
    }

    @Override
    public int compareTo(NodeEntry other) {
        if (other == null) return 1;

        // Primary comparison by identifier
        int result = Integer.compare(this.identifier, other.identifier);
        if (result != 0) return result;

        // Secondary comparison by MBR area if identifiers are equal
        if (this.mbr != null && other.mbr != null) {
            return Double.compare(this.mbr.getArea(), other.mbr.getArea());
        } else if (this.mbr != null) {
            return 1;
        } else if (other.mbr != null) {
            return -1;
        }

        return 0;
    }

    @Override
    public String toString() {
        return "NodeEntry{" +
                "identifier=" + identifier +
                ", mbr=" + mbr +
                '}';
    }

    /**
     * Comparator for NodeEntry objects that can be used for alternative sorting strategies.
     * Testing for TreeSet sorting.
     */
    public static class NodeEntryComparatorMinX implements Comparator<NodeEntry> {
        @Override
        public int compare(NodeEntry e1, NodeEntry e2) {
            if (e1 == null && e2 == null) return 0;
            if (e1 == null) return -1;
            if (e2 == null) return 1;

            // Compare by MBR minx first
            if (e1.mbr != null && e2.mbr != null) {
                int result = Double.compare(e1.mbr.getLow(0), e2.mbr.getLow(0));
                if (result != 0) return result;
            } else if (e1.mbr != null) {
                return 1;
            } else if (e2.mbr != null) {
                return -1;
            }

            // Then by identifier as tie-breaker
            return Integer.compare(e1.identifier, e2.identifier);
        }
    }
}

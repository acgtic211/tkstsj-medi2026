package org.ual.documentindex.invertedlist;

import java.io.Serializable;

public class PostingListEntry implements Serializable {
    public int documentId;
    public double weight;
    public int clusterId;

    public PostingListEntry(int id, double weight) {
        this.documentId = id;
        this.weight = weight;
    }

    public PostingListEntry(int id, double weight, int clusterId) {
        this.documentId = id;
        this.weight = weight;
        this.clusterId = clusterId;
    }
}

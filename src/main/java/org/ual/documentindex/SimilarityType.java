package org.ual.documentindex;

public enum SimilarityType {
    //TF_IDF("TF-IDF"),
    COSINE("Cosine Similarity"),
    WEIGHTED_JACCARD("Weighted Jaccard Similarity"),
    WEIGHTED_SUM("Weighted Sum");

    private final String description;

    SimilarityType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

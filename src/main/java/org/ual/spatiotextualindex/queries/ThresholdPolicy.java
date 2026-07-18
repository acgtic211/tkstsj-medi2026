package org.ual.spatiotextualindex.queries;

/**
 * Enumeration for different threshold adjustment strategies used in spatio-textual queries.
 * This defines how the thresholds for pruning results are adjusted during query processing.
 *
 * STRICT: Uses strict thresholds for pruning based on individual spatial and textual thresholds.
 * COMBINED: Uses a combined threshold that considers both spatial and textual relevance together, adjusted by alpha.
 *
 */
public enum ThresholdPolicy {
    STRICT("Strict Threshold"),
    COMBINED_COST("Combined Cost Threshold");

    private final String description;

    ThresholdPolicy(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

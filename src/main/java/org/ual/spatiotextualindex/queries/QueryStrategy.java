package org.ual.spatiotextualindex.queries;

public enum QueryStrategy {
    FULL_JOIN("No query keyword presence requirement for the documents in the pair"),
    CONSTRAINT_TEXTUAL_JOIN("Query keywords need to be present in at least one of the documents in the pair"),
    CONSTRAINT_SPATIAL_JOIN("Pair need to be inside a spatial window"),
    CONSTRAINT_ALL_JOIN("Query keywords need to be present in at least one of the documents in the pair and the pair need to be inside a spatial window"),
    @Deprecated
    PARTIAL_JOIN("Deprecated alias of CONSTRAINT_TEXTUAL_JOIN");


    private final String description;
    QueryStrategy(String description) {
        this.description = description;
    }
    public String getDescription() {
        return description;
    }

    public static QueryStrategy orDefault(QueryStrategy strategy) {
        return normalize(strategy == null ? FULL_JOIN : strategy);
    }

    public boolean usesConstraintTextualFilter() {
        QueryStrategy effective = normalize(this);
        return effective == CONSTRAINT_TEXTUAL_JOIN || usesCombinedConstraint();
    }

    public boolean usesSpatialWindowConstraint() {
        QueryStrategy effective = normalize(this);
        return effective == CONSTRAINT_SPATIAL_JOIN || usesCombinedConstraint();
    }

    public boolean usesExactTextualSimilarity() {
        QueryStrategy effective = normalize(this);
        return effective == FULL_JOIN || effective == CONSTRAINT_SPATIAL_JOIN;
    }

    public boolean usesCombinedConstraint() {
        return normalize(this) == CONSTRAINT_ALL_JOIN;
    }

    public static QueryStrategy parse(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return FULL_JOIN;
        }

        String normalized = rawValue.trim().toUpperCase();
        if ("PARTIAL_JOIN".equals(normalized)) {
            return CONSTRAINT_TEXTUAL_JOIN;
        }

        return normalize(QueryStrategy.valueOf(normalized));
    }

    private static QueryStrategy normalize(QueryStrategy strategy) {
        return strategy == PARTIAL_JOIN ? CONSTRAINT_TEXTUAL_JOIN : strategy;
    }
}

package org.ual.spatiotextualindex.queries;

/**
 * Enumeration for different join strategies used in spatio-textual queries.
 * This defines how spatial and textual components are combined during query processing.
 */
public enum JoinStrategy {
    PLANE_SWEEP("Plane Sweep"),
    DEFAULT("Methods Default Strategy"),;

    private final String description;

    JoinStrategy(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

package org.ual.spatialindex.rtree;

public enum BulkLoadMethod {
    // Bulk load methods for R-Tree
    STR("Sort-Tile-Recursive");

    private final String description;

    BulkLoadMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

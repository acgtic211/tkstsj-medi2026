package org.ual.documentindex;

public enum BoundLimit {
    UPPER_BOUND("Upper Bound"),
    LOWER_BOUND("Lower Bound"),
    EXACT("Exact");

    private final String description;

    BoundLimit(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

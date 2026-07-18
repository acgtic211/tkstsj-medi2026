package org.ual.utils.config;

public enum TextualIndexType {
    INVERTED_LIST("Inverted List (HashMap)"),
    SIGNED_INVERTED_LIST("Signed Inverted List (Bloom + Stats)"),
    SIGNED_BLOCK("Signed Block (Aggregate)");


    private final String description;

    TextualIndexType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}


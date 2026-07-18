package org.ual.utils.config;

public enum DataStructureType {
    HASHMAP("HashMap"),
    TREEMAP("TreeMap");

    private final String description;

    DataStructureType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}


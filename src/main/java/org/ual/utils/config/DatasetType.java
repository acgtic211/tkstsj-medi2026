package org.ual.utils.config;

public enum DatasetType {
    POSTAL_CODES("Postal Codes (171K)"),
    SPORTS("Sports (1.75M)"),
    PARKS("Parks (9.96M)"),
    HOTELS("Hotels (20K)"),
    TEST("Test (10)");

    private final String description;

    DatasetType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

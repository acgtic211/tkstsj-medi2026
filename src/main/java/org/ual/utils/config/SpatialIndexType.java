package org.ual.utils.config;

public enum SpatialIndexType {
    IR("IR-Tree"),
    IR_BULK("IR-Tree with Bulk Loading"),
    DIR("DIR-Tree"),
    CIR("CIR-Tree"),
    CDIR("CDIR-Tree");

    private final String description;

    SpatialIndexType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
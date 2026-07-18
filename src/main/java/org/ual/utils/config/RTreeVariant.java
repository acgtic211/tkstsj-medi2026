package org.ual.utils.config;

public enum RTreeVariant {
    LINEAR("Linear Split"),
    QUADRATIC("Quadratic Split"),
    RSTAR("R* Tree");

    private final String description;

    RTreeVariant(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}



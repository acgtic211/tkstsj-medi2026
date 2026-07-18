package org.ual.documentindex;

public enum RankingSumMode {
    FAST_APPROXIMATE("Fast Approximate Scoring"),
    PRECISE("Precise Scoring");

    private final String description;

    RankingSumMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static RankingSumMode defaultMode() {
        return FAST_APPROXIMATE;
    }

    public static RankingSumMode orDefault(RankingSumMode mode) {
        return mode != null ? mode : defaultMode();
    }
}

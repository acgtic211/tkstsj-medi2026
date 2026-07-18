package org.ual.utils.sampling;

/**
 * Selects a continuous window of lines based on a target size and start position.
 * The effective start is clamped so there are always enough remaining lines to read.
 */
public class ContiguousWindowSampler {

    private int currentLineIndex = 0;
    private final int startLineInclusive;
    private final int endLineExclusive;

    public ContiguousWindowSampler(int totalLines, int targetLineCount, int requestedStartLine) {
        int safeTarget = Math.max(0, targetLineCount);
        int maxValidStart = Math.max(0, totalLines - safeTarget);
        this.startLineInclusive = Math.min(Math.max(0, requestedStartLine), maxValidStart);
        this.endLineExclusive = Math.min(totalLines, this.startLineInclusive + safeTarget);
    }

    public boolean shouldSelect() {
        boolean selected = currentLineIndex >= startLineInclusive && currentLineIndex < endLineExclusive;
        currentLineIndex++;
        return selected;
    }

    public int getStartLineInclusive() {
        return startLineInclusive;
    }

    public int getEndLineExclusive() {
        return endLineExclusive;
    }
}

package org.ual.utils.sampling;

/**
 * Stateful systematic sampler that maintains selection state across calls.
 * Used for the legacy sequential/systematic sampling approach.
 */
public class SystematicSampler {

    private int currentLineIndex = 0;
    private int nextTargetSelection = 0;

    /**
     * Resets the sampler state for processing a new file.
     */
    public void reset() {
        currentLineIndex = 0;
        nextTargetSelection = 0;
    }

    /**
     * Determines if the current line should be selected using systematic sampling.
     * This distribution mirrors the original spatial distribution across the file.
     *
     * @param totalLines the total number of lines in the file
     * @param targetLineCount the target number of lines to sample
     * @return true if this line should be selected, false otherwise
     */
    public boolean shouldSelect(int totalLines, int targetLineCount) {
        int expectedAccumulated = (int) (((long) (currentLineIndex + 1) * targetLineCount) / totalLines);
        currentLineIndex++;

        if (expectedAccumulated > nextTargetSelection) {
            nextTargetSelection = expectedAccumulated;
            return true;
        }
        return false;
    }

    public int getCurrentLineIndex() {
        return currentLineIndex;
    }

    public int getNextTargetSelection() {
        return nextTargetSelection;
    }
}

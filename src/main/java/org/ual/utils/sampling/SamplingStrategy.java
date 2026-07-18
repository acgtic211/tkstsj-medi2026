package org.ual.utils.sampling;

import java.util.Random;

/**
 * Provides sampling strategies for dataset selection.
 * Supports both systematic (biased) and randomized sampling to reduce bias.
 */
public class SamplingStrategy {

    /**
     * Enum to define available sampling methods.
     */
    public enum SamplingMethod {
        SYSTEMATIC,  // Sequential sampling (deterministic, may introduce bias)
        RANDOMIZED,  // Randomized sampling (requires seed for reproducibility)
        CONTIGUOUS   // Continuous window sampling from a configurable start line
    }

    private final SamplingMethod method;
    private final long seed;
    private final Random random;

    /**
     * Initializes a sampling strategy with the given method and optional seed.
     * For RANDOMIZED method, seed is required for reproducibility.
     * For SYSTEMATIC method, seed is ignored.
     *
     * @param method the sampling method to use
     * @param seed the random seed (only used for RANDOMIZED method)
     */
    public SamplingStrategy(SamplingMethod method, long seed) {
        this.method = method;
        this.seed = seed;
        this.random = (method == SamplingMethod.RANDOMIZED) ? new Random(seed) : null;
    }

    /**
     * Creates a SYSTEMATIC sampling strategy (default behavior, legacy mode).
     *
     * @return a SamplingStrategy with SYSTEMATIC method
     */
    public static SamplingStrategy systematic() {
        return new SamplingStrategy(SamplingMethod.SYSTEMATIC, 0);
    }

    /**
     * Creates a RANDOMIZED sampling strategy with the given seed.
     * Using the same seed across different datasets ensures reproducibility.
     *
     * @param seed the random seed for reproducibility
     * @return a SamplingStrategy with RANDOMIZED method
     */
    public static SamplingStrategy randomized(long seed) {
        return new SamplingStrategy(SamplingMethod.RANDOMIZED, seed);
    }

    /**
     * Randomized sampling: randomly selects lines based on the target percentage.
     * Probability of selection = targetLineCount / totalLines.
     * 
     * This method should be called sequentially for each line.
     *
     * @param totalLines the total number of lines in the file
     * @param targetLineCount the target number of lines to sample
     * @return true if this line should be selected, false otherwise
     */
    public boolean shouldSelectRandomized(int totalLines, int targetLineCount) {
        if (method != SamplingMethod.RANDOMIZED) {
            throw new IllegalStateException("This method requires RANDOMIZED sampling strategy");
        }
        double selectionProbability = (double) targetLineCount / totalLines;
        return random.nextDouble() < selectionProbability;
    }

    public SamplingMethod getMethod() {
        return method;
    }

    public long getSeed() {
        return seed;
    }
}

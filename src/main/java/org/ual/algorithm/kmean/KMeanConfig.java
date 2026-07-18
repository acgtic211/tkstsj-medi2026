package org.ual.algorithm.kmean;

/**
 * Runtime configuration for KMeans clustering.
 */
public final class KMeanConfig {
    private final int numClusters;
    private final int maxMovesWithoutChange;
    private final InitializationStrategy initializationStrategy;
    private final long randomSeed;
    private final int maxIterations;

    private KMeanConfig(Builder builder) {
        this.numClusters = builder.numClusters;
        this.maxMovesWithoutChange = builder.maxMovesWithoutChange;
        this.initializationStrategy = builder.initializationStrategy;
        this.randomSeed = builder.randomSeed;
        this.maxIterations = builder.maxIterations;
    }

    public int getNumClusters() {
        return numClusters;
    }

    public int getMaxMovesWithoutChange() {
        return maxMovesWithoutChange;
    }

    public InitializationStrategy getInitializationStrategy() {
        return initializationStrategy;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public static Builder builder(int numClusters) {
        return new Builder(numClusters);
    }

    public static final class Builder {
        private final int numClusters;
        private int maxMovesWithoutChange;
        private InitializationStrategy initializationStrategy;
        private long randomSeed;
        private int maxIterations;

        private Builder(int numClusters) {
            if (numClusters <= 0) {
                throw new IllegalArgumentException("numClusters must be positive");
            }
            this.numClusters = numClusters;
            this.maxMovesWithoutChange = 0;
            this.initializationStrategy = InitializationStrategy.KMEANS_PLUS_PLUS;
            this.randomSeed = 1L;
            this.maxIterations = 300;
        }

        public Builder maxMovesWithoutChange(int maxMovesWithoutChange) {
            if (maxMovesWithoutChange < 0) {
                throw new IllegalArgumentException("maxMovesWithoutChange cannot be negative");
            }
            this.maxMovesWithoutChange = maxMovesWithoutChange;
            return this;
        }

        public Builder initializationStrategy(InitializationStrategy initializationStrategy) {
            if (initializationStrategy == null) {
                throw new IllegalArgumentException("initializationStrategy cannot be null");
            }
            this.initializationStrategy = initializationStrategy;
            return this;
        }

        public Builder randomSeed(long randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            if (maxIterations <= 0) {
                throw new IllegalArgumentException("maxIterations must be positive");
            }
            this.maxIterations = maxIterations;
            return this;
        }

        public KMeanConfig build() {
            return new KMeanConfig(this);
        }
    }
}

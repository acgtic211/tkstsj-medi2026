package org.ual.spatiotextualindex.queries;


import org.ual.documentindex.SimilarityType;

import java.util.Objects;

public class JoinConfiguration {
    private ThresholdPolicy thresholdPolicy;
    private JoinStrategy joinStrategy;
    private SimilarityType similarityType;
    private QueryStrategy queryStrategy;

    public JoinConfiguration(ThresholdPolicy thresholdPolicy, JoinStrategy joinStrategy, SimilarityType similarityType, QueryStrategy queryStrategy) {
        this.thresholdPolicy = Objects.requireNonNull(thresholdPolicy, "thresholdPolicy must not be null");
        this.joinStrategy = Objects.requireNonNull(joinStrategy, "joinStrategy must not be null");
        this.similarityType = Objects.requireNonNull(similarityType, "similarityType must not be null");
        this.queryStrategy = Objects.requireNonNull(queryStrategy, "queryStrategy must not be null");
    }

    public ThresholdPolicy getThresholdPolicy() {
        return thresholdPolicy;
    }

    public void setThresholdPolicy(ThresholdPolicy thresholdPolicy) {
        this.thresholdPolicy = Objects.requireNonNull(thresholdPolicy, "thresholdPolicy must not be null");
    }

    public JoinStrategy getJoinStrategy() {
        return joinStrategy;
    }

    public void setJoinStrategy(JoinStrategy joinStrategy) {
        this.joinStrategy = Objects.requireNonNull(joinStrategy, "joinStrategy must not be null");
    }

    public QueryStrategy getQueryStrategy() {
        return queryStrategy;
    }

    public void setQueryStrategy(QueryStrategy queryStrategy) {
        this.queryStrategy = Objects.requireNonNull(queryStrategy, "queryStrategy must not be null");
    }

    public SimilarityType getSimilarityType() {
        return similarityType;
    }

    public void setSimilarityType(SimilarityType similarityType) {
        this.similarityType = Objects.requireNonNull(similarityType, "similarityType must not be null");
    }
}

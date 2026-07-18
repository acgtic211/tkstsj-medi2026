package org.ual.utils.experiment;

import org.ual.documentindex.RankingSumMode;
import org.ual.utils.config.DatasetConfig;
import org.ual.utils.config.IndexConfig;

import java.util.List;
import java.util.Map;

public class JoinExperiment implements IExperimentConfiguration {
    private List<String> queryTypes;
    private String algorithm;
    private String joinStrategy;
    private String thresholdPolicy;
    private String similarityType;
    private String queryStrategy;
    private String varyParameter;
    private Double fixedTextualSimilarity;
    private Double fixedSpatialDistance;
    private Double fixedAlpha;
    private RankingSumMode fixedRankingSumMode;
    private int numberOfQueries;
    private DatasetConfig secondaryDataset;
    private IndexConfig secondaryIndex;
    private Integer overrideNumberOfKeywords;
    private Integer overrideNumberOfQueries;
    private Map<String, QueryParameterOverrides> queryTypeOverrides;

    // Getters and setters for all fields
    @Override
    public List<String> getQueryTypes() {
        return queryTypes;
    }
    @Override
    public void setQueryTypes(List<String> queryTypes) {
        this.queryTypes = queryTypes;
    }

    public String getAlgorithm() {
        return algorithm;
    }
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getJoinStrategy() {
        return joinStrategy;
    }
    public void setJoinStrategy(String joinStrategy) {
        this.joinStrategy = joinStrategy;
    }

    public String getThresholdPolicy() {
        return thresholdPolicy;
    }
    public void setThresholdPolicy(String thresholdPolicy) {
        this.thresholdPolicy = thresholdPolicy;
    }

    public String getSimilarityType() {
        return similarityType;
    }
    public void setSimilarityType(String similarityType) {
        this.similarityType = similarityType;
    }

    public String getQueryStrategy() {
        return queryStrategy;
    }

    public void setQueryStrategy(String queryStrategy) {
        this.queryStrategy = queryStrategy;
    }

    @Override
    public String getVaryParameter() {
        return varyParameter;
    }
    @Override
    public void setVaryParameter(String varyParameter) {
        this.varyParameter = varyParameter;
    }

    @Override
    public Double getFixedAlpha() {
        return fixedAlpha;
    }
    @Override
    public void setFixedAlpha(Double fixedAlpha) {
        this.fixedAlpha = fixedAlpha;
    }

    @Override
    public RankingSumMode getFixedRankingSumMode() {
        return fixedRankingSumMode;
    }

    public void setFixedRankingSumMode(RankingSumMode fixedRankingSumMode) {
        this.fixedRankingSumMode = fixedRankingSumMode;
    }

    public Double getFixedTextualSimilarity() {
        return fixedTextualSimilarity;
    }
    public void setFixedTextualSimilarity(Double fixedTextualSimilarity) {
        this.fixedTextualSimilarity = fixedTextualSimilarity;
    }

    public Double getFixedSpatialDistance() {
        return fixedSpatialDistance;
    }
    public void setFixedSpatialDistance(Double fixedSpatialDistance) {
        this.fixedSpatialDistance = fixedSpatialDistance;
    }

    public int getNumberOfQueries() {
        return numberOfQueries;
    }
    public void setNumberOfQueries(int numberOfQueries) {
        this.numberOfQueries = numberOfQueries;
    }

    public DatasetConfig getSecondaryDataset() {
        return secondaryDataset;
    }

    public void setSecondaryDataset(DatasetConfig secondaryDataset) {
        this.secondaryDataset = secondaryDataset;
    }

    public IndexConfig getSecondaryIndex() {
        return secondaryIndex;
    }

    public void setSecondaryIndex(IndexConfig secondaryIndex) {
        this.secondaryIndex = secondaryIndex;
    }

    @Override
    public Integer getOverrideNumberOfKeywords() {
        return overrideNumberOfKeywords;
    }

    public void setOverrideNumberOfKeywords(Integer overrideNumberOfKeywords) {
        this.overrideNumberOfKeywords = overrideNumberOfKeywords;
    }

    @Override
    public Integer getOverrideNumberOfQueries() {
        if (overrideNumberOfQueries != null) {
            return overrideNumberOfQueries;
        }
        return numberOfQueries > 0 ? numberOfQueries : null;
    }

    public void setOverrideNumberOfQueries(Integer overrideNumberOfQueries) {
        this.overrideNumberOfQueries = overrideNumberOfQueries;
    }

    @Override
    public Map<String, QueryParameterOverrides> getQueryTypeOverrides() {
        return queryTypeOverrides;
    }

    public void setQueryTypeOverrides(Map<String, QueryParameterOverrides> queryTypeOverrides) {
        this.queryTypeOverrides = queryTypeOverrides;
    }
}

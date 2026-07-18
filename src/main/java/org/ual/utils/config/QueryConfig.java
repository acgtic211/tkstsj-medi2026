package org.ual.utils.config;

import org.ual.documentindex.RankingSumMode;

public class QueryConfig {
    private int numberOfQueries = 20;
    private boolean writeResults = false;

    // Group sizes for aggregate queries
    private int[] groupSizes = {10, 20, 40, 60, 80};
    private int groupSizeDefault = 10;

    // M percentages for aggregate queries
    private int[] mPercentages = {40, 50, 60, 70, 80};
    private int mPercentageDefault = 60;

    // Number of keywords
    private int[] numberOfKeywords = {1, 2, 4, 8, 10};
    private int numberOfKeywordsDefault = 4;

    // Space area percentages
    private double[] spaceAreaPercentages = {0.001, 0.01, 0.02, 0.03, 0.04};
    private double spaceAreaPercentageDefault = 0.01;

    // Keyword space size percentages
    private int[] keywordSpaceSizePercentages = {1, 2, 3, 4, 5};
    private int keywordSpaceSizePercentageDefault = 3;

    // Top K values
    private int[] topKValues = {1, 10, 20, 30, 40, 50};
    private int topKDefault = 10;

    // Alpha values (for scoring functions)
    private double[] alphaValues = {0.1, 0.3, 0.5, 0.7, 0.9};
    private double alphaDefault = 0.5;
    private RankingSumMode rankingSumModeDefault = RankingSumMode.defaultMode();

    // Radius values for range queries
    private float[] radiusValues = {1f, 2f, 5f, 10f, 20f};
    private float radiusDefault = 10f;

    // Spatial distance and textual similarity for join queries
    float[] spatialDistance = {0.001f, 0.005f, 0.01f, 0.05f, 0.1f};
    float spatialDistanceDefault = 0.01f;
    float[] textualSimilarity = {0.1f, 0.3f, 0.5f, 0.7f, 0.9f};//{0.1f, 0.3f, 0.5f, 0.7f, 0.9f};
    float textualSimilarityDefault = 0.5f; //0.5f

    // Getters and setters
    public int getNumberOfQueries() {
        return numberOfQueries;
    }
    public void setNumberOfQueries(int numberOfQueries) {
        this.numberOfQueries = numberOfQueries;
    }

    public boolean isWriteResults() {
        return writeResults;
    }
    public void setWriteResults(boolean writeResults) {
        this.writeResults = writeResults;
    }

    public int[] getGroupSizes() {
        return groupSizes;
    }
    public void setGroupSizes(int[] groupSizes) {
        this.groupSizes = groupSizes;
    }
    public int getGroupSizeDefault() {
        return groupSizeDefault;
    }
    public void setGroupSizeDefault(int groupSizeDefault) {
        this.groupSizeDefault = groupSizeDefault;
    }

    public int[] getMPercentages() {
        return mPercentages;
    }
    public void setMPercentages(int[] mPercentages) {
        this.mPercentages = mPercentages;
    }
    public int getMPercentageDefault() {
        return mPercentageDefault;
    }
    public void setMPercentageDefault(int mPercentageDefault) {
        this.mPercentageDefault = mPercentageDefault;
    }

    public int[] getNumberOfKeywords() {
        return numberOfKeywords;
    }
    public void setNumberOfKeywords(int[] numberOfKeywords) {
        this.numberOfKeywords = numberOfKeywords;
    }
    public int getNumberOfKeywordsDefault() {
        return numberOfKeywordsDefault;
    }
    public void setNumberOfKeywordsDefault(int numberOfKeywordsDefault) {
        this.numberOfKeywordsDefault = numberOfKeywordsDefault;
    }

    public double[] getSpaceAreaPercentages() {
        return spaceAreaPercentages;
    }
    public void setSpaceAreaPercentages(double[] spaceAreaPercentages) {
        this.spaceAreaPercentages = spaceAreaPercentages;
    }
    public double getSpaceAreaPercentageDefault() {
        return spaceAreaPercentageDefault;
    }
    public void setSpaceAreaPercentageDefault(double spaceAreaPercentageDefault) {
        this.spaceAreaPercentageDefault = spaceAreaPercentageDefault;
    }

    public int[] getKeywordSpaceSizePercentages() {
        return keywordSpaceSizePercentages;
    }
    public void setKeywordSpaceSizePercentages(int[] keywordSpaceSizePercentages) {
        this.keywordSpaceSizePercentages = keywordSpaceSizePercentages;
    }
    public int getKeywordSpaceSizePercentageDefault() {
        return keywordSpaceSizePercentageDefault;
    }
    public void setKeywordSpaceSizePercentageDefault(int keywordSpaceSizePercentageDefault) {
        this.keywordSpaceSizePercentageDefault = keywordSpaceSizePercentageDefault;
    }

    public int[] getTopKValues() {
        return topKValues;
    }
    public void setTopKValues(int[] topKValues) {
        this.topKValues = topKValues;
    }
    public int getTopKDefault() {
        return topKDefault;
    }
    public void setTopKDefault(int topKDefault) {
        this.topKDefault = topKDefault;
    }

    public double[] getAlphaValues() {
        return alphaValues;
    }
    public void setAlphaValues(double[] alphaValues) {
        this.alphaValues = alphaValues;
    }
    public double getAlphaDefault() {
        return alphaDefault;
    }
    public void setAlphaDefault(double alphaDefault) {
        this.alphaDefault = alphaDefault;
    }

    public RankingSumMode getRankingSumModeDefault() {
        return rankingSumModeDefault;
    }

    public void setRankingSumModeDefault(RankingSumMode rankingSumModeDefault) {
        this.rankingSumModeDefault = RankingSumMode.orDefault(rankingSumModeDefault);
    }

    public float[] getRadiusValues() {
        return radiusValues;
    }
    public void setRadiusValues(float[] radiusValues) {
        this.radiusValues = radiusValues;
    }
    public float getRadiusDefault() {
        return radiusDefault;
    }
    public void setRadiusDefault(float radiusDefault) {
        this.radiusDefault = radiusDefault;
    }

    public float[] getSpatialDistance() { return spatialDistance; }
    public void setSpatialDistance(float[] spatialDistance) { this.spatialDistance = spatialDistance; }
    public float getSpatialDistanceDefault() { return spatialDistanceDefault; }
    public void setSpatialDistanceDefault(float spatialDistanceDefault) { this.spatialDistanceDefault = spatialDistanceDefault; }

    public float[] getTextualSimilarity() { return textualSimilarity; }
    public void setTextualSimilarity(float[] textualSimilarity) { this.textualSimilarity = textualSimilarity; }
    public float getTextualSimilarityDefault() { return textualSimilarityDefault; }
    public void setTextualSimilarityDefault(float textualSimilarityDefault) { this.textualSimilarityDefault = textualSimilarityDefault; }

}

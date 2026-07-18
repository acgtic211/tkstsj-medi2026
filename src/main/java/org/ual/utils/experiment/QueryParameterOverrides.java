package org.ual.utils.experiment;

import org.ual.documentindex.RankingSumMode;

/**
 * Optional per-query-type overrides. Any null field falls back to global defaults.
 */
public class QueryParameterOverrides {
    private int[] groupSizes;
    private Integer groupSizeDefault;
    private int[] mPercentages;
    private Integer mPercentageDefault;
    private int[] numberOfKeywords;
    private Integer numberOfKeywordsDefault;
    private double[] querySpaceAreaPercentages;
    private Double querySpaceAreaPercentageDefault;
    private int[] keywordSpaceSizePercentages;
    private Integer keywordSpaceSizePercentageDefault;
    private int[] topks;
    private Integer topkDefault;
    private double[] alphas;
    private Double alphaDefault;
    private float[] radius;
    private Float radiusDefault;
    private float[] spatialDistances;
    private Float spatialDistanceDefault;
    private float[] textualSimilarities;
    private Float textualSimilarityDefault;
    private Integer numberOfQueries;
    private RankingSumMode rankingSumMode;

    public int[] getGroupSizes() { return groupSizes; }
    public void setGroupSizes(int[] groupSizes) { this.groupSizes = groupSizes; }
    public Integer getGroupSizeDefault() { return groupSizeDefault; }
    public void setGroupSizeDefault(Integer groupSizeDefault) { this.groupSizeDefault = groupSizeDefault; }

    public int[] getMPercentages() { return mPercentages; }
    public void setMPercentages(int[] mPercentages) { this.mPercentages = mPercentages; }
    public Integer getMPercentageDefault() { return mPercentageDefault; }
    public void setMPercentageDefault(Integer mPercentageDefault) { this.mPercentageDefault = mPercentageDefault; }

    public int[] getNumberOfKeywords() { return numberOfKeywords; }
    public void setNumberOfKeywords(int[] numberOfKeywords) { this.numberOfKeywords = numberOfKeywords; }
    public Integer getNumberOfKeywordsDefault() { return numberOfKeywordsDefault; }
    public void setNumberOfKeywordsDefault(Integer numberOfKeywordsDefault) { this.numberOfKeywordsDefault = numberOfKeywordsDefault; }

    public double[] getQuerySpaceAreaPercentages() { return querySpaceAreaPercentages; }
    public void setQuerySpaceAreaPercentages(double[] querySpaceAreaPercentages) { this.querySpaceAreaPercentages = querySpaceAreaPercentages; }
    public Double getQuerySpaceAreaPercentageDefault() { return querySpaceAreaPercentageDefault; }
    public void setQuerySpaceAreaPercentageDefault(Double querySpaceAreaPercentageDefault) { this.querySpaceAreaPercentageDefault = querySpaceAreaPercentageDefault; }

    public int[] getKeywordSpaceSizePercentages() { return keywordSpaceSizePercentages; }
    public void setKeywordSpaceSizePercentages(int[] keywordSpaceSizePercentages) { this.keywordSpaceSizePercentages = keywordSpaceSizePercentages; }
    public Integer getKeywordSpaceSizePercentageDefault() { return keywordSpaceSizePercentageDefault; }
    public void setKeywordSpaceSizePercentageDefault(Integer keywordSpaceSizePercentageDefault) { this.keywordSpaceSizePercentageDefault = keywordSpaceSizePercentageDefault; }

    public int[] getTopks() { return topks; }
    public void setTopks(int[] topks) { this.topks = topks; }
    public Integer getTopkDefault() { return topkDefault; }
    public void setTopkDefault(Integer topkDefault) { this.topkDefault = topkDefault; }

    public double[] getAlphas() { return alphas; }
    public void setAlphas(double[] alphas) { this.alphas = alphas; }
    public Double getAlphaDefault() { return alphaDefault; }
    public void setAlphaDefault(Double alphaDefault) { this.alphaDefault = alphaDefault; }

    public float[] getRadius() { return radius; }
    public void setRadius(float[] radius) { this.radius = radius; }
    public Float getRadiusDefault() { return radiusDefault; }
    public void setRadiusDefault(Float radiusDefault) { this.radiusDefault = radiusDefault; }

    public float[] getSpatialDistances() { return spatialDistances; }
    public void setSpatialDistances(float[] spatialDistances) { this.spatialDistances = spatialDistances; }
    public Float getSpatialDistanceDefault() { return spatialDistanceDefault; }
    public void setSpatialDistanceDefault(Float spatialDistanceDefault) { this.spatialDistanceDefault = spatialDistanceDefault; }

    public float[] getTextualSimilarities() { return textualSimilarities; }
    public void setTextualSimilarities(float[] textualSimilarities) { this.textualSimilarities = textualSimilarities; }
    public Float getTextualSimilarityDefault() { return textualSimilarityDefault; }
    public void setTextualSimilarityDefault(Float textualSimilarityDefault) { this.textualSimilarityDefault = textualSimilarityDefault; }

    public Integer getNumberOfQueries() { return numberOfQueries; }
    public void setNumberOfQueries(Integer numberOfQueries) { this.numberOfQueries = numberOfQueries; }

    public RankingSumMode getRankingSumMode() { return rankingSumMode; }
    public void setRankingSumMode(RankingSumMode rankingSumMode) { this.rankingSumMode = rankingSumMode; }
}


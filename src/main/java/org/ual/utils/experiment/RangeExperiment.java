package org.ual.utils.experiment;

import org.ual.documentindex.RankingSumMode;

import java.util.List;
import java.util.Map;

public class RangeExperiment implements IExperimentConfiguration {
    private List<String> queryTypes;
    private String varyParameter;
    private Double fixedAlpha;
    private RankingSumMode fixedRankingSumMode;
    private Integer overrideNumberOfKeywords;
    private Integer overrideNumberOfQueries;
    private Map<String, QueryParameterOverrides> queryTypeOverrides;

    // Getters and setters
    @Override
    public List<String> getQueryTypes() { return queryTypes; }
    @Override
    public void setQueryTypes(List<String> queryTypes) { this.queryTypes = queryTypes; }

    @Override
    public String getVaryParameter() { return varyParameter; }
    @Override
    public void setVaryParameter(String varyParameter) { this.varyParameter = varyParameter; }

    @Override
    public Double getFixedAlpha() { return fixedAlpha; }
    @Override
    public void setFixedAlpha(Double fixedAlpha) { this.fixedAlpha = fixedAlpha; }

    @Override
    public RankingSumMode getFixedRankingSumMode() { return fixedRankingSumMode; }
    public void setFixedRankingSumMode(RankingSumMode fixedRankingSumMode) { this.fixedRankingSumMode = fixedRankingSumMode; }

    @Override
    public Integer getOverrideNumberOfKeywords() { return overrideNumberOfKeywords; }
    public void setOverrideNumberOfKeywords(Integer overrideNumberOfKeywords) { this.overrideNumberOfKeywords = overrideNumberOfKeywords; }

    @Override
    public Integer getOverrideNumberOfQueries() { return overrideNumberOfQueries; }
    public void setOverrideNumberOfQueries(Integer overrideNumberOfQueries) { this.overrideNumberOfQueries = overrideNumberOfQueries; }

    @Override
    public Map<String, QueryParameterOverrides> getQueryTypeOverrides() { return queryTypeOverrides; }
    public void setQueryTypeOverrides(Map<String, QueryParameterOverrides> queryTypeOverrides) { this.queryTypeOverrides = queryTypeOverrides; }
}

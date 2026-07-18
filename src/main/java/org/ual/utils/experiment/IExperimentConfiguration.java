package org.ual.utils.experiment;

import org.ual.documentindex.RankingSumMode;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface IExperimentConfiguration {
    List<String> getQueryTypes();
    void setQueryTypes(List<String> queryTypes);

    String getVaryParameter();
    void setVaryParameter(String varyParameter);

    Double getFixedAlpha();
    void setFixedAlpha(Double fixedAlpha);

    /**
     * Optional override for the number of keywords used in queries for this
     * specific experiment. Returns {@code null} to use the global default.
     */
    default Integer getOverrideNumberOfKeywords() { return null; }

    /**
     * Optional override for the number of queries executed per evaluation for
     * this specific experiment. Returns {@code null} to use the global default.
     */
    default Integer getOverrideNumberOfQueries() { return null; }

    /**
     * Optional override for the ranking-sum scoring mode used by ranking-based
     * query families. Returns {@code null} to use the global default.
     */
    default RankingSumMode getFixedRankingSumMode() { return null; }

    /**
     * Optional per-query-type overrides keyed by query enum name (e.g. TkSK, STSJ).
     */
    default Map<String, QueryParameterOverrides> getQueryTypeOverrides() { return null; }

    default QueryParameterOverrides getOverridesForQueryType(String queryType) {
        Map<String, QueryParameterOverrides> overrides = getQueryTypeOverrides();
        if (overrides == null || overrides.isEmpty() || queryType == null) {
            return null;
        }

        QueryParameterOverrides exact = overrides.get(queryType);
        if (exact != null) {
            return exact;
        }

        return overrides.get(queryType.toUpperCase(Locale.ROOT));
    }
}

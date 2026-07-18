package org.ual.utils.experiment;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryTypeOverridesResolutionTest {

    @Test
    void resolvesOverrideForSpecificQueryType() {
        KnnExperiment experiment = new KnnExperiment();

        QueryParameterOverrides tkskOverrides = new QueryParameterOverrides();
        tkskOverrides.setTopks(new int[]{1, 100, 200, 500});

        Map<String, QueryParameterOverrides> map = new HashMap<>();
        map.put("TkSK", tkskOverrides);
        experiment.setQueryTypeOverrides(map);

        QueryParameterOverrides resolved = experiment.getOverridesForQueryType("TkSK");
        assertNotNull(resolved);
        assertArrayEquals(new int[]{1, 100, 200, 500}, resolved.getTopks());
    }

    @Test
    void missingQueryTypeUsesDefaultsByReturningNullOverride() {
        KnnExperiment experiment = new KnnExperiment();

        QueryParameterOverrides tkskOverrides = new QueryParameterOverrides();
        tkskOverrides.setTopks(new int[]{1, 100, 200, 500});

        Map<String, QueryParameterOverrides> map = new HashMap<>();
        map.put("TkSK", tkskOverrides);
        experiment.setQueryTypeOverrides(map);

        assertNull(experiment.getOverridesForQueryType("BkSK"));
    }
}


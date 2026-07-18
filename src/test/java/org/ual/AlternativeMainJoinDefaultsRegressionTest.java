package org.ual;

import org.junit.jupiter.api.Test;
import org.ual.utils.experiment.JoinExperiment;
import org.ual.utils.query.QueryLogicNEW;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlternativeMainJoinDefaultsRegressionTest {

    @Test
    void resolveDefaultJoinVaryParameter_usesTopKForTopkJoinType() {
        AlternativeMain main = new AlternativeMain();
        JoinExperiment experiment = new JoinExperiment();
        experiment.setQueryTypes(Collections.singletonList("TOPK_STSJ"));

        QueryLogicNEW.QueryType resolved = main.resolveDefaultJoinVaryParameter(experiment);

        assertEquals(QueryLogicNEW.QueryType.TopK, resolved);
    }

    @Test
    void resolveDefaultJoinVaryParameter_usesTopKForTopkExtendedJoinType() {
        AlternativeMain main = new AlternativeMain();
        JoinExperiment experiment = new JoinExperiment();
        experiment.setQueryTypes(Collections.singletonList("TOPK_STSJ_EX"));

        QueryLogicNEW.QueryType resolved = main.resolveDefaultJoinVaryParameter(experiment);

        assertEquals(QueryLogicNEW.QueryType.TopK, resolved);
    }

    @Test
    void resolveDefaultJoinVaryParameter_defaultsToSpatialDistanceForClassicJoinTypes() {
        AlternativeMain main = new AlternativeMain();
        JoinExperiment experiment = new JoinExperiment();
        experiment.setQueryTypes(Arrays.asList("STSJ", "KNNJQ"));

        QueryLogicNEW.QueryType resolved = main.resolveDefaultJoinVaryParameter(experiment);

        assertEquals(QueryLogicNEW.QueryType.SpatialDistance, resolved);
    }
}


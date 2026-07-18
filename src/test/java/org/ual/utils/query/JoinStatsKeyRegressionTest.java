package org.ual.utils.query;

import org.junit.jupiter.api.Test;
import org.ual.utils.experiment.JoinExperiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JoinStatsKeyRegressionTest {

    @Test
    void buildJoinStatsKey_includesAlgorithmStrategySimilarityAndPolicy() {
        JoinExperiment join = new JoinExperiment();
        join.setAlgorithm("recursive");
        join.setJoinStrategy("default");
        join.setSimilarityType("cosine");
        join.setThresholdPolicy("strict");
        join.setQueryStrategy("constraint_textual_join");

        String key = AbstractQueryExecutor.buildJoinStatsKey("STSJ", join);

        assertEquals("STSJ|alg=RECURSIVE|strategy=DEFAULT|sim=COSINE|policy=STRICT|query=CONSTRAINT_TEXTUAL_JOIN", key);
    }

    @Test
    void buildJoinStatsKey_createsDistinctKeysForDifferentVariants() {
        JoinExperiment planeSweep = new JoinExperiment();
        planeSweep.setAlgorithm("best_first");
        planeSweep.setJoinStrategy("plane_sweep");
        planeSweep.setSimilarityType("weighted_jaccard");
        planeSweep.setThresholdPolicy("strict");
        planeSweep.setQueryStrategy("constraint_textual_join");

        JoinExperiment recursive = new JoinExperiment();
        recursive.setAlgorithm("recursive");
        recursive.setJoinStrategy("default");
        recursive.setSimilarityType("cosine");
        recursive.setThresholdPolicy("strict");
        recursive.setQueryStrategy("full_join");

        String keyA = AbstractQueryExecutor.buildJoinStatsKey("STSJ", planeSweep);
        String keyB = AbstractQueryExecutor.buildJoinStatsKey("STSJ", recursive);

        assertNotEquals(keyA, keyB);
    }
}


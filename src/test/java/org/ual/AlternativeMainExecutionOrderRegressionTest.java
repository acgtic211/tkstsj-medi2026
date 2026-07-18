package org.ual;

import org.junit.jupiter.api.Test;
import org.ual.utils.config.ApplicationConfig;
import org.ual.utils.config.ExperimentConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlternativeMainExecutionOrderRegressionTest {

    @Test
    void executeQueriesFromConfig_runsFamiliesInContiguousOrder_andAppliesIterationOnce() throws Exception {
        RecordingAlternativeMain main = new RecordingAlternativeMain();
        setConfig(main, createExperimentConfig(true, true, true, true, 1));

        main.executeQueriesFromConfig();

        assertEquals(1, main.iterationsConfigured);
        assertEquals(Arrays.asList("aggregate", "knn", "range", "join"), main.callOrder);
    }

    @Test
    void executeQueriesFromConfig_respectsEnabledFlags() throws Exception {
        RecordingAlternativeMain main = new RecordingAlternativeMain();
        setConfig(main, createExperimentConfig(false, true, false, true, 1));

        main.executeQueriesFromConfig();

        assertEquals(1, main.iterationsConfigured);
        assertEquals(Arrays.asList("knn", "join"), main.callOrder);
    }

    private static ApplicationConfig createExperimentConfig(boolean runAggregate, boolean runKnn,
                                                            boolean runRange, boolean runJoin,
                                                            int iterations) {
        ApplicationConfig appConfig = new ApplicationConfig();
        ExperimentConfig experimentConfig = new ExperimentConfig();
        experimentConfig.setRunAggregateQueries(runAggregate);
        experimentConfig.setRunKnnQueries(runKnn);
        experimentConfig.setRunRangeQueries(runRange);
        experimentConfig.setRunJoinQueries(runJoin);
        experimentConfig.setNumIterations(iterations);
        appConfig.setExperiment(experimentConfig);
        return appConfig;
    }

    private static void setConfig(AlternativeMain main, ApplicationConfig config) throws Exception {
        Field configField = AlternativeMain.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(main, config);
    }

    private static class RecordingAlternativeMain extends AlternativeMain {
        private final List<String> callOrder = new ArrayList<>();
        private int iterationsConfigured = -1;

        @Override
        protected void setExecutionIterations(int iterations) {
            this.iterationsConfigured = iterations;
        }

        @Override
        protected void executeAggregateQueries() {
            callOrder.add("aggregate");
        }

        @Override
        protected void executeKnnQueries() {
            callOrder.add("knn");
        }

        @Override
        protected void executeRangeQueries() {
            callOrder.add("range");
        }

        @Override
        protected void executeJoinQueries() {
            callOrder.add("join");
        }

        @Override
        protected void flushQueryStats() {
            callOrder.add("flush");
        }
    }
}


package org.ual.utils.main;

import org.junit.jupiter.api.Test;
import org.ual.utils.stats.QueryStatsData;
import org.ual.utils.stats.QueryStatisticsNEW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsLogicDetailedMetricsRegressionTest {

    @Test
    void writeResults_emitsDetailedMetricsWithPairsAndCostColumns() throws Exception {
        Path metricsDir = Files.createTempDirectory("metrics-regression-");
        StatisticsLogic stats = new StatisticsLogic(metricsDir.toString() + "/");

        QueryStatisticsNEW queryStatistics = new QueryStatisticsNEW("STSJ|alg=RECURSIVE|strategy=DEFAULT|sim=COSINE|policy=STRICT|query=CONSTRAINT_TEXTUAL_JOIN");
        QueryStatsData data = new QueryStatsData();
        data.queryType = "SpatialDistance";
        data.value = "0.01";
        data.numIterations = 3;
        data.totalTime = 100.0;
        data.medianTime = 90.0;
        data.minTime = 80.0;
        data.maxTime = 120.0;
        data.averageTime = 10.0;
        data.totalNodesVisited = 900;
        data.averageNodesVisited = 90.0;
        data.averageSpatialCost = 0.12;
        data.averageTextualCost = 0.21;
        data.averageIRCost = 0.33;
        data.totalResultsReturned = 300;
        data.averageResultsReturned = 100.0;
        data.memoryDeltaBytes = 0L;

        queryStatistics.addEntry(data);
        stats.queriesStatsNew.put(queryStatistics.getQueryName(), queryStatistics);

        stats.writeResults();

        Path detailed = metricsDir.resolve("[STSJ|alg=RECURSIVE|strategy=DEFAULT|sim=COSINE|policy=STRICT|query=CONSTRAINT_TEXTUAL_JOIN]DetailedMetrics.csv");
        String csv = new String(Files.readAllBytes(detailed), StandardCharsets.UTF_8);

        assertTrue(csv.contains("numIterations"));
        assertTrue(csv.contains("medianTimeMs"));
        assertTrue(csv.contains("avgSpatialCost"));
        assertTrue(csv.contains("avgTextualCost"));
        assertTrue(csv.contains("avgIRCost"));
        assertTrue(csv.contains("totalReturnedPairs"));
        assertTrue(csv.contains("avgReturnedPairs"));
        assertTrue(csv.contains("memoryDeltaBytes"));
        assertTrue(csv.contains("300"));
        assertTrue(csv.contains("90"));
    }
}


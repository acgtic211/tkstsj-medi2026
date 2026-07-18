package org.ual.utils.query;

import org.apache.logging.log4j.Level;
import org.ual.querygeneration.SKNNQueryGenerator;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.utils.index.IndexLogicNEW;
import org.ual.utils.io.QueryResultWriter;
import org.ual.utils.main.StatisticsLogic;
import org.ual.utils.stats.QueryStatsData;

import java.util.List;

public class RangeQueryExecutor extends AbstractQueryExecutor {

    public RangeQueryExecutor(IndexLogicNEW indexLogic, StatisticsLogic statisticsLogic,
                              String resultsDirectoryPath, DatasetParameters parameters,
                              int seed, boolean writeQueriesToDisk) {
        super(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, seed, writeQueriesToDisk);
    }

    @Override
    protected <T extends Enum<T>> QueryStatsData evaluateQuery(T queryType, QueryLogicNEW.QueryType paramType, Params params) {
        QueryLogicNEW.RangeQueryType rangeQueryType = (QueryLogicNEW.RangeQueryType) queryType;
        QueryStatsData statsData = new QueryStatsData();
        statsData.queryType = paramType.toString();

        QueryResultWriter resultWriter = new QueryResultWriter();
        tree.setAlphaDistribution((float) params.alphaDistribution);
        SKNNQueryGenerator queryGenerator = new SKNNQueryGenerator(ramdomSeed, parameters);

        long startTime = System.nanoTime();
        long totalResults = 0;
        long totalNodesVisited = 0;
        int queryCount = Math.max(1, params.numberOfQueries);

        if (rangeQueryType == QueryLogicNEW.RangeQueryType.BRSK) {
            List<SKNNQuery> brskQueries = queryGenerator.generateBooleanRangeQueries(queryCount,
                    params.numberOfKeywords, params.querySpaceAreaPercentage, params.keywordSpacePercentage);
            for (SKNNQuery q : brskQueries) {
                List<SKNNQuery.Result> results = tree.booleanRangeQuery(indexLogic.getTextualIndex(), q, params.radius);
                logger.debug("Range query: {} with {}", q, results.size());
                totalResults += results.size();
                totalNodesVisited += tree.getVisitedNodes();
                if (writeQueriesToDisk) {
                    resultWriter.writeSKNNResult(results);
                }
            }
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        int divisor = queryCount;
        double averageTime = totalTime / divisor;
        double averageNodesVisited = totalNodesVisited * 1.0 / divisor;
        double averageResults = totalResults * 1.0 / divisor;

        String prefix = "[" + rangeQueryType + "][" + paramType + "]";
        logger.printf(Level.INFO, "TotalTime=%.4fms avgT=%.4fms avgNodes=%.2f totalResults=%d",
                totalTime, averageTime, averageNodesVisited, totalResults);

        statsData.totalTime = totalTime;
        statsData.averageTime = averageTime;
        statsData.totalNodesVisited = totalNodesVisited;
        statsData.averageNodesVisited = averageNodesVisited;
        statsData.totalResultsReturned = totalResults;
        statsData.averageResultsReturned = averageResults;
        statsData.memoryDeltaBytes = 0L;

        logQueryStats(prefix, statsData, resultWriter);

        return statsData;
    }
}

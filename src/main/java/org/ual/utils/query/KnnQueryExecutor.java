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
import java.util.function.BiFunction;

public class KnnQueryExecutor extends AbstractQueryExecutor {

    public KnnQueryExecutor(IndexLogicNEW indexLogic, StatisticsLogic statisticsLogic,
                            String resultsDirectoryPath, DatasetParameters parameters,
                            int seed, boolean writeQueriesToDisk) {
        super(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, seed, writeQueriesToDisk);
    }

    @Override
    protected <T extends Enum<T>> QueryStatsData evaluateQuery(T queryType, QueryLogicNEW.QueryType paramType, Params params) {
        QueryLogicNEW.KnnQueryType knnQueryType = (QueryLogicNEW.KnnQueryType) queryType;
        QueryStatsData statsData = new QueryStatsData();
        statsData.queryType = paramType.toString();

        QueryResultWriter resultWriter = new QueryResultWriter();
        tree.setAlphaDistribution((float) params.alphaDistribution);
        SKNNQueryGenerator queryGenerator = new SKNNQueryGenerator(ramdomSeed, parameters);

        long startTime = System.nanoTime();

        BiFunction<SKNNQuery, Integer, List<SKNNQuery.Result>> queryExecutor;
        List<SKNNQuery> queries;
        int queryCount = Math.max(1, params.numberOfQueries);

        if (knnQueryType == QueryLogicNEW.KnnQueryType.BkSK) {
            queries = queryGenerator.generateBooleanKNNQueries(queryCount, params.numberOfKeywords,
                    params.querySpaceAreaPercentage, params.keywordSpacePercentage);
            queryExecutor = (q, k) -> tree.booleanKnnQuery(indexLogic.getTextualIndex(), q, k);
        } else { // TkSK
            queries = queryGenerator.generateTopKNNQueries(queryCount, params.numberOfKeywords,
                    params.querySpaceAreaPercentage, params.keywordSpacePercentage);
            queryExecutor = (q, k) -> tree.topkKnnQuery(indexLogic.getTextualIndex(), q, k, params.rankingSumMode);
        }

        long totalResults = 0;
        long totalNodesVisited = 0;
        for (SKNNQuery q : queries) {
            List<SKNNQuery.Result> results = queryExecutor.apply(q, params.topk);
            logger.debug("Knn query: {} with {}", q, results.size());
            totalResults += results.size();
            totalNodesVisited += tree.getVisitedNodes();
            if (writeQueriesToDisk) {
                resultWriter.writeSKNNResult(results);
            }
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        int divisor = queryCount;
        double averageTime = totalTime / divisor;
        double averageNodesVisited = totalNodesVisited * 1.0 / divisor;
        double averageResults = totalResults * 1.0 / divisor;

        String prefix = "[" + knnQueryType + "][" + paramType + "]";
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
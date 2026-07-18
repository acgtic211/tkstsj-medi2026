package org.ual.utils.query;

import org.apache.logging.log4j.Level;
import org.ual.querygeneration.AggregateSKNNQueryGenerator;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.spatialindex.Cost;
import org.ual.utils.index.IndexLogicNEW;
import org.ual.utils.io.QueryResultWriter;
import org.ual.utils.main.StatisticsLogic;
import org.ual.utils.stats.QueryStatsData;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class AggregateQueryExecutor extends AbstractQueryExecutor {

    public AggregateQueryExecutor(IndexLogicNEW indexLogic, StatisticsLogic statisticsLogic,
                                  String resultsDirectoryPath, DatasetParameters parameters,
                                  int seed, boolean writeQueriesToDisk) {
        super(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, seed, writeQueriesToDisk);
    }

    @Override
    protected <T extends Enum<T>> QueryStatsData evaluateQuery(T queryType, QueryLogicNEW.QueryType paramType, Params params) {
        QueryLogicNEW.AggregateQueryType aggregateQueryType = (QueryLogicNEW.AggregateQueryType) queryType;
        QueryStatsData statsData = new QueryStatsData();
        statsData.queryType = paramType.toString();

        QueryResultWriter resultWriter = new QueryResultWriter();
        tree.setAlphaDistribution((float) params.alphaDistribution);
        AggregateSKNNQueryGenerator queryGenerator = new AggregateSKNNQueryGenerator(ramdomSeed, parameters);
        CostAccumulator costAccumulator = new CostAccumulator();
        int queryCount = Math.max(1, params.numberOfQueries);

        long startTime = System.nanoTime();

        if (aggregateQueryType == QueryLogicNEW.AggregateQueryType.GNNK || aggregateQueryType == QueryLogicNEW.AggregateQueryType.GNNK_BL) {
            executeGNNKQueries(aggregateQueryType, queryGenerator, params, queryCount, costAccumulator, resultWriter);
        } else {
            executeSGNNKQueries(aggregateQueryType, queryGenerator, params, queryCount, costAccumulator, resultWriter);
        }

        long endTime = System.nanoTime();
        populateStatsData(statsData, startTime, endTime, queryCount, costAccumulator);

        String prefix = "[" + aggregateQueryType + "][" + paramType + "]";
        logQueryStats(prefix, statsData, resultWriter);

        return statsData;
    }

    private void executeGNNKQueries(QueryLogicNEW.AggregateQueryType queryType, AggregateSKNNQueryGenerator generator,
                                    Params params, int queryCount, CostAccumulator accumulator, QueryResultWriter writer) {
        List<AggregateSKNNQuery> queries = generator.generateGNNKQuery(queryCount, params.groupSize,
                params.numberOfKeywords, params.querySpaceAreaPercentage, params.keywordSpacePercentage, params.aggregator);

        Function<AggregateSKNNQuery, List<AggregateSKNNQuery.Result>> queryExecutor =
                (queryType == QueryLogicNEW.AggregateQueryType.GNNK)
                        ? q -> tree.gnnk(indexLogic.getTextualIndex(), q, params.topk, params.rankingSumMode)
                        : q -> tree.gnnkBaseline(indexLogic.getTextualIndex(), q, params.topk, params.rankingSumMode);

        for (AggregateSKNNQuery q : queries) {
            List<AggregateSKNNQuery.Result> results = queryExecutor.apply(q);
            logger.debug("GnnK query: {} with {}", q, results.size());
            accumulator.add(results);
            accumulator.totalNodesVisited += tree.getVisitedNodes();
            if (writeQueriesToDisk) {
                writer.writeAggregateSKNNResult(results);
            }
        }
        if (writeQueriesToDisk) writer.writeLineSeparator();
    }

    private void executeSGNNKQueries(QueryLogicNEW.AggregateQueryType queryType, AggregateSKNNQueryGenerator generator,
                                     Params params, int queryCount, CostAccumulator accumulator, QueryResultWriter writer) {
        List<AggregateSKNNQuery> queries = generator.generateSGNNKQuery(queryCount, params.groupSize,
                params.mPercentage, params.numberOfKeywords, params.querySpaceAreaPercentage, params.keywordSpacePercentage, params.aggregator);

        for (AggregateSKNNQuery q : queries) {
            if (queryType == QueryLogicNEW.AggregateQueryType.SGNNK || queryType == QueryLogicNEW.AggregateQueryType.SGNNK_BL) {
                Function<AggregateSKNNQuery, List<AggregateSKNNQuery.Result>> queryExecutor =
                        (queryType == QueryLogicNEW.AggregateQueryType.SGNNK)
                                ? qry -> tree.sgnnk(indexLogic.getTextualIndex(), qry, params.topk, params.rankingSumMode)
                                : qry -> tree.sgnnkBaseline(indexLogic.getTextualIndex(), qry, params.topk, params.rankingSumMode);
                List<AggregateSKNNQuery.Result> results = queryExecutor.apply(q);
                logger.debug("Sgnnk query: {} with {}", q, results.size());
                accumulator.add(results);
                accumulator.totalNodesVisited += tree.getVisitedNodes();
                if (writeQueriesToDisk) {
                    writer.writeAggregateSKNNResult(results);
                }
            } else if (queryType == QueryLogicNEW.AggregateQueryType.SGNNK_EX) {
                Map<Integer, List<AggregateSKNNQuery.Result>> resultsMap = tree.sgnnkExtended(indexLogic.getTextualIndex(), q, params.topk, params.rankingSumMode);
                if (writeQueriesToDisk) {
                    resultsMap.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> {
                                writer.write("Size " + entry.getKey(), true);
                                writer.writeAggregateSKNNResult(entry.getValue());
                            });
                }
            } else { // SGNNK_NM1
                int originalSubGroupSize = q.subGroupSize;
                while (q.subGroupSize <= q.groupSize) {
                    if (writeQueriesToDisk) writer.write("Size " + q.subGroupSize, true);
                    List<AggregateSKNNQuery.Result> results = tree.sgnnk(indexLogic.getTextualIndex(), q, params.topk, params.rankingSumMode);
                    logger.debug("Sgnnk query: {} with {}", q, results.size());
                    accumulator.add(results);
                    accumulator.totalNodesVisited += tree.getVisitedNodes();
                    if (writeQueriesToDisk) writer.writeAggregateSKNNResult(results);
                    q.subGroupSize++;
                }
                q.subGroupSize = originalSubGroupSize;
            }
            if (writeQueriesToDisk) writer.writeLineSeparator();
        }
    }

    private void populateStatsData(QueryStatsData statsData, long startTime, long endTime, int queryCount,
                                   CostAccumulator accumulator) {
        double totalTime = (endTime - startTime) / 1_000_000.0;
        int divisor = queryCount;
        double averageTime = totalTime / divisor;
        double averageNodesVisited = accumulator.totalNodesVisited * 1.0 / divisor;
        double averageSpatialCost = accumulator.spatialCost / divisor;
        double averageIRCost = accumulator.irCost / divisor;
        double averageResults = accumulator.totalResultsReturned * 1.0 / divisor;

        logger.printf(Level.INFO,
                        "AggregateQueryStats [type=%s, queries=%d] total=%.4fms avg=%.4fms avgNodes=%.2f avgSpatialCost=%.6f avgIRCost=%.6f totalResults=%d",
                        statsData.queryType, numberOfQueries, totalTime, averageTime, averageNodesVisited, averageSpatialCost, averageIRCost,
                        accumulator.totalResultsReturned);

        statsData.totalTime = totalTime;
        statsData.averageTime = averageTime;
        statsData.totalNodesVisited = accumulator.totalNodesVisited;
        statsData.averageNodesVisited = averageNodesVisited;
        statsData.averageSpatialCost = averageSpatialCost;
        statsData.averageIRCost = averageIRCost;
        statsData.totalResultsReturned = accumulator.totalResultsReturned;
        statsData.averageResultsReturned = averageResults;
        statsData.memoryDeltaBytes = 0L;
    }

    private static class CostAccumulator {
        double totalCost = 0;
        double spatialCost = 0;
        double irCost = 0;
        long totalResultsReturned = 0;
        long totalNodesVisited = 0;

        void add(List<AggregateSKNNQuery.Result> results) {
            if (results != null && !results.isEmpty()) {
                this.totalResultsReturned += results.size();
                Cost cost = results.get(0).getAggregateCost();
                if (cost != null) {
                    this.totalCost += cost.getCombinedCost();
                    this.spatialCost += cost.getSpatialCost();
                    this.irCost += cost.getIrCost();
                }
            }
        }
    }
}

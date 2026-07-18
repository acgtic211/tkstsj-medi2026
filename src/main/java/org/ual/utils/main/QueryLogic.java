package org.ual.utils.main;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.documentindex.SimilarityType;
import org.ual.querygeneration.AggregateSKNNQueryGenerator;
import org.ual.querygeneration.SKNNQueryGenerator;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKJoinQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.spatialindex.Cost;
import org.ual.spatialindex.spatialindex.ISpatioTextualIndex;
import org.ual.spatiotextualindex.queries.JoinStrategy;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.spatiotextualindex.queries.QueryStrategy;
import org.ual.spatiotextualindex.queries.ThresholdPolicy;
import org.ual.utils.ResultQueryTotal;
import org.ual.utils.io.QueryResultWriter;
import org.ual.utils.stats.QueryStats;
import org.ual.utils.stats.QueryStatsData;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class QueryLogic {

    private static final Logger logger = LogManager.getLogger(QueryLogic.class);
    private final IndexLogic indexLogic;
    private final StatisticsLogic statisticsLogic;
    private final String resultsDirectoryPath;
    private final DatasetParameters parameters;
    private final int ramdomSeed = 1; // Default seed
    // Query Parameters
    int[] groupSizes;
    int groupSizeDefault;
    int[] mPercentages;
    int mPercentageDefault;
    int[] numberOfKeywords;
    int numberOfKeywordsDefault;
    double[] querySpaceAreaPercentages;
    double querySpaceAreaPercentageDefault;
    int[] keywordSpaceSizePercentages;
    int keywordSpaceSizePercentageDefault;
    int[] topks;
    int topkDefault;
    double[] alphas;
    double alphaDefault;
    float[] radius;
    float radiusDefault;

    // JOIN parameters
    float[] spatialDistances;
    float spatialDistanceDefault;
    float[] textualSimilarities;
    float textualSimilarityDefault;

    int numberOfQueries;
    private boolean writeQueriesToDisk;

    public QueryLogic(IndexLogic indexLogic, StatisticsLogic statisticsLogic, String resultsDirectoryPath, DatasetParameters parameters, boolean writeQueriesToDisk) {
        this.indexLogic = indexLogic;
        this.writeQueriesToDisk = writeQueriesToDisk;
        this.resultsDirectoryPath = resultsDirectoryPath;
        this.statisticsLogic = statisticsLogic;
        this.parameters = parameters;
    }

    public QueryLogic(IndexLogic indexLogic, StatisticsLogic statisticsLogic, String resultsDirectoryPath, DatasetParameters parameters, boolean writeQueriesToDisk, int[] groupSizes,
                      int groupSizeDefault, int[] mPercentages, int mPercentageDefault, int[] numberOfKeywords,
                      int numberOfKeywordsDefault, double[] querySpaceAreaPercentages, double querySpaceAreaPercentageDefault,
                      int[] keywordSpaceSizePercentages, int keywordSpaceSizePercentageDefault, int[] topks, int topkDefault,
                      double[] alphas, double alphaDefault, float[] radius, float radiusDefault, int numberOfQueries) {

        this(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, writeQueriesToDisk);
        this.groupSizes = groupSizes;
        this.groupSizeDefault = groupSizeDefault;
        this.mPercentages = mPercentages;
        this.mPercentageDefault = mPercentageDefault;
        this.numberOfKeywords = numberOfKeywords;
        this.numberOfKeywordsDefault = numberOfKeywordsDefault;
        this.querySpaceAreaPercentages = querySpaceAreaPercentages;
        this.querySpaceAreaPercentageDefault = querySpaceAreaPercentageDefault;
        this.keywordSpaceSizePercentages = keywordSpaceSizePercentages;
        this.keywordSpaceSizePercentageDefault = keywordSpaceSizePercentageDefault;
        this.topks = topks;
        this.topkDefault = topkDefault;
        this.alphas = alphas;
        this.alphaDefault = alphaDefault;
        this.radius = radius;
        this.radiusDefault = radiusDefault;
        this.numberOfQueries = numberOfQueries;
    }

    public void printStats() {
        statisticsLogic.writeResults();
    }

    // Run queries
    public void initQueryVariables(int[] groupSizes, int groupSizeDefault, int[] mPercentages, int mPercentageDefault, int[] numberOfKeywords,
                                   int numberOfKeywordsDefault, double[] querySpaceAreaPercentages, double querySpaceAreaPercentageDefault,
                                   int[] keywordSpaceSizePercentages, int keywordSpaceSizePercentageDefault, int[] topks, int topkDefault,
                                   double[] alphas, double alphaDefault, float[] radius, float radiusDefault, int numberOfQueries) {
        this.groupSizes = groupSizes;
        this.groupSizeDefault = groupSizeDefault;
        this.mPercentages = mPercentages;
        this.mPercentageDefault = mPercentageDefault;
        this.numberOfKeywords = numberOfKeywords;
        this.numberOfKeywordsDefault = numberOfKeywordsDefault;
        this.querySpaceAreaPercentages = querySpaceAreaPercentages;
        this.querySpaceAreaPercentageDefault = querySpaceAreaPercentageDefault;
        this.keywordSpaceSizePercentages = keywordSpaceSizePercentages;
        this.keywordSpaceSizePercentageDefault = keywordSpaceSizePercentageDefault;
        this.topks = topks;
        this.topkDefault = topkDefault;
        this.alphas = alphas;
        this.alphaDefault = alphaDefault;
        this.radius = radius;
        this.radiusDefault = radiusDefault;
        this.numberOfQueries = numberOfQueries;
    }

    // TODO - Add methods to set individual parameters to test JOIN queries
    public void setSpatialDistances(float[] spatialDistances) {
        this.spatialDistances = spatialDistances;
    }

    public void setSpatialDistanceDefault(float spatialDistanceDefault) {
        this.spatialDistanceDefault = spatialDistanceDefault;
    }

    public void setTextualSimilarities(float[] textualSimilarities) {
        this.textualSimilarities = textualSimilarities;
    }

    public void setTextualSimilarityDefault(float textualSimilarityDefault) {
        this.textualSimilarityDefault = textualSimilarityDefault;
    }

    // TODO - Add methods to set number of queries no adpapt queries to big datasets
    public void setNumberOfQueries(int numberOfQueries) {
        this.numberOfQueries = numberOfQueries;
    }


    public void processAggregateQuery(AggregateQueryType[] aggregateQueryTypes, ArrayList<QueryType> queryTypes, IAggregator aggregator) {
        logger.info("Processing and Evaluating Aggregate Queries:");
        long startTime = System.nanoTime();

        for (AggregateQueryType aggregateQry : aggregateQueryTypes) {
            logger.info("Processing aggregate query: {}", aggregateQry);
            QueryStats queryStats = new QueryStats(aggregateQry.toString());

            for (QueryType qryType : queryTypes) {
                logger.info("\t ...based on {}", qryType);
                QueryStatsData qryData;
                switch (qryType) {
                    case GroupSize:
                        for (int gs : groupSizes) {
                            qryData = evaluateAggregateQuery(aggregateQry, qryType, gs, mPercentageDefault, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, topkDefault, alphaDefault, aggregator);
                            qryData.value = String.valueOf(gs);
                            queryStats.groupSizes.add(qryData);
                        }
                        break;
                    case Percentage:
                        for (int per : mPercentages) {
                            qryData = evaluateAggregateQuery(aggregateQry, qryType, groupSizeDefault, per, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, topkDefault, alphaDefault, aggregator);
                            qryData.value = String.valueOf(per);
                            queryStats.percentages.add(qryData);
                        }
                        break;
                    case NumberOfKeywords:
                        for (int nkey : numberOfKeywords) {
                            qryData = evaluateAggregateQuery(aggregateQry, qryType, groupSizeDefault, mPercentageDefault, nkey, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, topkDefault, alphaDefault, aggregator);
                            qryData.value = String.valueOf(nkey);
                            queryStats.numKeywords.add(qryData);
                        }
                        break;
                    case SpaceAreaPercentage:
                        for (double area : querySpaceAreaPercentages) {
                            qryData = evaluateAggregateQuery(aggregateQry, qryType, groupSizeDefault, mPercentageDefault, numberOfKeywordsDefault, area, keywordSpaceSizePercentageDefault, topkDefault, alphaDefault, aggregator);
                            qryData.value = String.valueOf(area);
                            queryStats.querySpaceAreas.add(qryData);
                        }
                        break;
                    case KeywordSpaceSizePercentage:
                        for (int space : keywordSpaceSizePercentages) {
                            qryData = evaluateAggregateQuery(aggregateQry, qryType, groupSizeDefault, mPercentageDefault, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, space, topkDefault, alphaDefault, aggregator);
                            qryData.value = String.valueOf(space);
                            queryStats.keyboardSpaceSizes.add(qryData);
                        }
                        break;
                    case TopK:
                        for (int k : topks) {
                            qryData = evaluateAggregateQuery(aggregateQry, qryType, groupSizeDefault, mPercentageDefault, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, k, alphaDefault, aggregator);
                            qryData.value = String.valueOf(k);
                            queryStats.topks.add(qryData);
                        }
                        break;
                    case Alpha:
                        for (double a : alphas) {
                            qryData = evaluateAggregateQuery(aggregateQry, qryType, groupSizeDefault, mPercentageDefault, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, topkDefault, a, aggregator);
                            qryData.value = String.valueOf(a);
                            queryStats.alphas.add(qryData);
                        }
                        break;
                    default:
                        logger.warn("Unsupported query type: {}", qryType);
                        break;
                }
            }
            statisticsLogic.queriesStats.put(aggregateQry.toString(), queryStats);
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        logger.info("All aggregate queries done in {} ms", totalTime);
    }

    private QueryStatsData evaluateAggregateQuery(AggregateQueryType aggregateQueryType, QueryType queryType, int groupSize, int mPercentage, int numberOfKeywords, double querySpaceAreaPercentage,
                                                  double keywordSpacePercentage, int topk, double alphaDistribution, IAggregator aggregator) {

        QueryStatsData statsData = new QueryStatsData();
        statsData.queryType = queryType.toString();

        QueryResultWriter resultWriter = new QueryResultWriter();
        ISpatioTextualIndex tree = (ISpatioTextualIndex) IndexLogic.spatialIndex;
        tree.setAlphaDistribution((float) alphaDistribution); // Set alpha distribution for the index
        AggregateSKNNQueryGenerator queryGenerator = new AggregateSKNNQueryGenerator(ramdomSeed, parameters);
        CostAccumulator costAccumulator = new CostAccumulator();

        long startTime = System.nanoTime();

        if (aggregateQueryType == AggregateQueryType.GNNK || aggregateQueryType == AggregateQueryType.GNNK_BL) {
            List<AggregateSKNNQuery> gnnkQueries = queryGenerator.generateGNNKQuery(numberOfQueries, groupSize, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage, aggregator);
            Function<AggregateSKNNQuery, List<AggregateSKNNQuery.Result>> queryExecutor = (aggregateQueryType == AggregateQueryType.GNNK)
                    ? q -> tree.gnnk(IndexLogic.textualIndex, q, topk)
                    : q -> tree.gnnkBaseline(IndexLogic.textualIndex, q, topk);

            for (AggregateSKNNQuery q : gnnkQueries) {
                List<AggregateSKNNQuery.Result> results = queryExecutor.apply(q);
                logger.info("GnnK query: {} with {}", q, results.size());
                System.out.println("GnnK results: " + results.toString());
                costAccumulator.add(results);
                if (writeQueriesToDisk) {
                    resultWriter.writeAggregateSKNNResult(results);
                    //resultWriter.writeLineSeparator();
                }
            }
            if (writeQueriesToDisk) resultWriter.writeLineSeparator();
        } else { // SGNNK variants
            List<AggregateSKNNQuery> sgnnkQueries = queryGenerator.generateSGNNKQuery(numberOfQueries, groupSize, mPercentage, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage, aggregator);
            for (AggregateSKNNQuery q : sgnnkQueries) {
                if (aggregateQueryType == AggregateQueryType.SGNNK || aggregateQueryType == AggregateQueryType.SGNNK_BL) {
                    Function<AggregateSKNNQuery, List<AggregateSKNNQuery.Result>> queryExecutor = (aggregateQueryType == AggregateQueryType.SGNNK)
                            ? qry -> tree.sgnnk(IndexLogic.textualIndex, qry, topk)
                            : qry -> tree.sgnnkBaseline(IndexLogic.textualIndex, qry, topk);
                    List<AggregateSKNNQuery.Result> results = queryExecutor.apply(q);
                    logger.info("Sgnnk query: {} with {}", q, results.size());
                    costAccumulator.add(results);
                    if (writeQueriesToDisk) {
                        resultWriter.writeAggregateSKNNResult(results);
                    }
                } else if (aggregateQueryType == AggregateQueryType.SGNNK_EX) {
                    Map<Integer, List<AggregateSKNNQuery.Result>> resultsMap = tree.sgnnkExtended(IndexLogic.textualIndex, q, topk);
                    if (writeQueriesToDisk) {
                        resultsMap.entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .forEach(entry -> {
                                    resultWriter.write("Size " + entry.getKey(), true);
                                    resultWriter.writeAggregateSKNNResult(entry.getValue());
                                });
                    }
                } else { // SGNNK_NM1
                    int originalSubGroupSize = q.subGroupSize;
                    while (q.subGroupSize <= q.groupSize) {
                        if (writeQueriesToDisk) resultWriter.write("Size " + q.subGroupSize, true);
                        List<AggregateSKNNQuery.Result> results = tree.sgnnk(IndexLogic.textualIndex, q, topk);
                        logger.info("Sgnnk query: {} with {}", q, results.size());
                        costAccumulator.add(results);
                        if (writeQueriesToDisk) resultWriter.writeAggregateSKNNResult(results);
                        q.subGroupSize++;
                    }
                    q.subGroupSize = originalSubGroupSize; // Restore original value
                }
                if (writeQueriesToDisk) resultWriter.writeLineSeparator();
            }
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        double averageTime = totalTime / numberOfQueries;
        double averageNodesVisited = tree.getVisitedNodes() * 1.0 / numberOfQueries;
        double averageSpatialCost = costAccumulator.spatialCost / numberOfQueries;
        double averageIRCost = costAccumulator.irCost / numberOfQueries;

        if (writeQueriesToDisk) {
            String prefix = "[" + aggregateQueryType + "][" + queryType + "]";
            resultWriter.write(prefix + " Average nodes visited: " + averageNodesVisited, true);
            resultWriter.write(prefix + " Total time millisecond: " + totalTime, true);
            resultWriter.writeLineSeparator();
            resultWriter.writeToDisk(resultsDirectoryPath, prefix);
        }

        logger.printf(Level.INFO, "TotalTime=%.4fms avgT=%.4fms avgNodes=%.2f avgSpatCost=%.6f avgIRCost=%.6f",
                totalTime, averageTime, averageNodesVisited, averageSpatialCost, averageIRCost);

        statsData.totalTime = totalTime;
        statsData.averageTime = averageTime;
        statsData.averageNodesVisited = averageNodesVisited;
        statsData.averageSpatialCost = averageSpatialCost;
        statsData.averageIRCost = averageIRCost;

        return statsData;
    }

    public void processRangeQuery(RangeQueryType[] rangeQueryTypes, ArrayList<QueryType> queryTypes) {
        logger.info("Processing and Evaluating Range Queries:");
        long startTime = System.nanoTime();

        for (RangeQueryType rangeQry : rangeQueryTypes) {
            logger.info("Processing range query: {}", rangeQry);
            QueryStats queryStats = new QueryStats(rangeQry.toString());
            for (QueryType qryType : queryTypes) {
                logger.info("\t ...based on {}", qryType);
                QueryStatsData qryData;
                switch (qryType) {
                    case Radius:
                        for (float r : radius) {
                            qryData = evaluateRangeQuery(rangeQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, alphaDefault, r);
                            qryData.value = String.valueOf(r);
                            queryStats.radii.add(qryData);
                        }
                        break;
                    case NumberOfKeywords:
                        for (int nkey : numberOfKeywords) {
                            qryData = evaluateRangeQuery(rangeQry, qryType, nkey, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, alphaDefault, radiusDefault);
                            qryData.value = String.valueOf(nkey);
                            queryStats.numKeywords.add(qryData);
                        }
                        break;
                    case SpaceAreaPercentage:
                        for (double area : querySpaceAreaPercentages) {
                            qryData = evaluateRangeQuery(rangeQry, qryType, numberOfKeywordsDefault, area, keywordSpaceSizePercentageDefault, alphaDefault, radiusDefault);
                            qryData.value = String.valueOf(area);
                            queryStats.querySpaceAreas.add(qryData);
                        }
                        break;
                    case KeywordSpaceSizePercentage:
                        for (int space : keywordSpaceSizePercentages) {
                            qryData = evaluateRangeQuery(rangeQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, space, alphaDefault, radiusDefault);
                            qryData.value = String.valueOf(space);
                            queryStats.keyboardSpaceSizes.add(qryData);
                        }
                        break;
                    case Alpha:
                        for (double a : alphas) {
                            qryData = evaluateRangeQuery(rangeQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, a, radiusDefault);
                            qryData.value = String.valueOf(a);
                            queryStats.alphas.add(qryData);
                        }
                        break;
                    default:
                        logger.warn("Unsupported query type: {}", qryType);
                        break;
                }
            }
            statisticsLogic.queriesStats.put(rangeQry.toString(), queryStats);
        }
        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        logger.info("All range queries done in {} ms", totalTime);
    }

    private QueryStatsData evaluateRangeQuery(RangeQueryType rangeQueryType, QueryType queryType, int numberOfKeywords, double querySpaceAreaPercentage,
                                              double keywordSpacePercentage, double alphaDistribution, float radius) {
        QueryStatsData statsData = new QueryStatsData();
        statsData.queryType = queryType.toString();

        QueryResultWriter resultWriter = new QueryResultWriter();
        ISpatioTextualIndex tree = (ISpatioTextualIndex) IndexLogic.spatialIndex;
        tree.setAlphaDistribution((float) alphaDistribution); // Set alpha distribution for the index
        SKNNQueryGenerator queryGenerator = new SKNNQueryGenerator(ramdomSeed, parameters);

        long startTime = System.nanoTime();

        if (rangeQueryType == RangeQueryType.BRSK) {
            List<SKNNQuery> brskQueries = queryGenerator.generateBooleanRangeQueries(numberOfQueries, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage);
            for (SKNNQuery q : brskQueries) {
                List<SKNNQuery.Result> results = tree.booleanRangeQuery(IndexLogic.textualIndex, q, radius);
                logger.info("Range query: {} with {}", q, results.size());
                if (writeQueriesToDisk) {
                    resultWriter.writeSKNNResult(results);
                }
            }
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        double averageTime = totalTime / numberOfQueries;
        double averageNodesVisited = tree.getVisitedNodes() * 1.0 / numberOfQueries;

        if (writeQueriesToDisk) {
            String prefix = "[" + rangeQueryType + "][" + queryType + "]";
            resultWriter.write(prefix + " Average nodes visited: " + averageNodesVisited, true);
            resultWriter.write(prefix + " Total time millisecond: " + totalTime, true);
            resultWriter.writeLineSeparator();
            resultWriter.writeToDisk(resultsDirectoryPath, prefix);
        }

        logger.printf(Level.INFO, "TotalTime=%.4fms avgT=%.4fms avgNodes=%.2f", totalTime, averageTime, averageNodesVisited);

        statsData.totalTime = totalTime;
        statsData.averageTime = averageTime;
        statsData.averageNodesVisited = averageNodesVisited;

        return statsData;
    }

    public void processKnnQuery(KnnQueryType[] knnQueryTypes, ArrayList<QueryType> queryTypes) {
        logger.info("Processing and Evaluating kNN Queries:");
        long startTime = System.nanoTime();

        for (KnnQueryType knnQry : knnQueryTypes) {
            logger.info("Processing kNN query: {}", knnQry);
            QueryStats queryStats = new QueryStats(knnQry.toString());
            for (QueryType qryType : queryTypes) {
                logger.info("\t ...based on {}", qryType);
                QueryStatsData qryData;
                switch (qryType) {
                    case TopK:
                        for (int k : topks) {
                            qryData = evaluateKnnQuery(knnQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, alphaDefault, k);
                            qryData.value = String.valueOf(k);
                            queryStats.topks.add(qryData);
                        }
                        break;
                    case NumberOfKeywords:
                        for (int nkey : numberOfKeywords) {
                            qryData = evaluateKnnQuery(knnQry, qryType, nkey, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, alphaDefault, topkDefault);
                            qryData.value = String.valueOf(nkey);
                            queryStats.numKeywords.add(qryData);
                        }
                        break;
                    case SpaceAreaPercentage:
                        for (double area : querySpaceAreaPercentages) {
                            qryData = evaluateKnnQuery(knnQry, qryType, numberOfKeywordsDefault, area, keywordSpaceSizePercentageDefault, alphaDefault, topkDefault);
                            qryData.value = String.valueOf(area);
                            queryStats.querySpaceAreas.add(qryData);
                        }
                        break;
                    case KeywordSpaceSizePercentage:
                        for (int space : keywordSpaceSizePercentages) {
                            qryData = evaluateKnnQuery(knnQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, space, alphaDefault, topkDefault);
                            qryData.value = String.valueOf(space);
                            queryStats.keyboardSpaceSizes.add(qryData);
                        }
                        break;
                    case Alpha:
                        for (double a : alphas) {
                            qryData = evaluateKnnQuery(knnQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, a, topkDefault);
                            qryData.value = String.valueOf(a);
                            queryStats.alphas.add(qryData);
                        }
                        break;
                    default:
                        logger.warn("Unsupported query type: {}", qryType);
                        break;
                }
            }
            statisticsLogic.queriesStats.put(knnQry.toString(), queryStats);
        }
        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        logger.info("kNN queries evaluation done in {:.3f} ms", totalTime);
    }

    private QueryStatsData evaluateKnnQuery(KnnQueryType knnQueryType, QueryType queryType, int numberOfKeywords, double querySpaceAreaPercentage,
                                            double keywordSpacePercentage, double alphaDistribution, int topk) {
        QueryStatsData statsData = new QueryStatsData();
        statsData.queryType = queryType.toString();

        QueryResultWriter resultWriter = new QueryResultWriter();
        ISpatioTextualIndex tree = (ISpatioTextualIndex) IndexLogic.spatialIndex;
        tree.setAlphaDistribution((float) alphaDistribution); // Set alpha distribution for the index
        SKNNQueryGenerator queryGenerator = new SKNNQueryGenerator(ramdomSeed, parameters);

        long startTime = System.nanoTime();

        BiFunction<SKNNQuery, Integer, List<SKNNQuery.Result>> queryExecutor;
        List<SKNNQuery> queries;

        if (knnQueryType == KnnQueryType.BkSK) {
            queries = queryGenerator.generateBooleanKNNQueries(numberOfQueries, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage);
            queryExecutor = (q, k) -> tree.booleanKnnQuery(IndexLogic.textualIndex, q, k);
        } else { // TkSK
            queries = queryGenerator.generateTopKNNQueries(numberOfQueries, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage);
            queryExecutor = (q, k) -> tree.topkKnnQuery(IndexLogic.textualIndex, q, k);
        }

        for (SKNNQuery q : queries) {
            List<SKNNQuery.Result> results = queryExecutor.apply(q, topk);
            logger.info("Knn query: {} with {}", q, results.size());
            if (writeQueriesToDisk) {
                resultWriter.writeSKNNResult(results);
            }
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        double averageTime = totalTime / numberOfQueries;
        double averageNodesVisited = tree.getVisitedNodes() * 1.0 / numberOfQueries;

        if (writeQueriesToDisk) {
            String prefix = "[" + knnQueryType + "][" + queryType + "]";
            resultWriter.write(prefix + " Average nodes visited: " + averageNodesVisited, true);
            resultWriter.write(prefix + " Total time millisecond: " + totalTime, true);
            resultWriter.writeLineSeparator();
            resultWriter.writeToDisk(resultsDirectoryPath, prefix);
        }

        logger.printf(Level.INFO, "TotalTime=%.4fms avgT=%.4fms avgNodes=%.2f", totalTime, averageTime, averageNodesVisited);

        statsData.totalTime = totalTime;
        statsData.averageTime = averageTime;
        statsData.averageNodesVisited = averageNodesVisited;

        return statsData;
    }

    public boolean isWriteQueriesToDisk() {
        return writeQueriesToDisk;
    }

    public void setWriteQueriesToDisk(boolean writeQueriesToDisk) {
        this.writeQueriesToDisk = writeQueriesToDisk;
    }

    public void setQueryResults(ResultQueryTotal globalQueryResults) {
        this.statisticsLogic.globalQueryResults = globalQueryResults;
    }

    public void processJoinQuery(JoinQueryType[] joinQueryTypes, ArrayList<QueryType> queryTypes) {
        logger.info("Processing and Evaluating JOIN Queries:");
        long startTime = System.nanoTime();

        for (JoinQueryType joinQry : joinQueryTypes) {
            logger.info("Processing join query: {}", joinQry);
            QueryStats queryStats = new QueryStats(joinQry.toString());
            for (QueryType qryType : queryTypes) {
                logger.info("\t ...based on {}", qryType);
                QueryStatsData qryData;
                switch (qryType) {
                    case SpatialDistance:
                        for (float d : spatialDistances) {
                            qryData = evaluateJoinQuery(joinQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, textualSimilarityDefault, d, alphaDefault);
                            qryData.value = String.valueOf(d);
                            queryStats.spatialDistance.add(qryData);
                        }
                        break;
                    case NumberOfKeywords:
                        for (int nkey : numberOfKeywords) {
                            qryData = evaluateJoinQuery(joinQry, qryType, nkey, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, textualSimilarityDefault, spatialDistanceDefault, alphaDefault);
                            qryData.value = String.valueOf(nkey);
                            queryStats.numKeywords.add(qryData);
                        }
                        break;
                    case SpaceAreaPercentage:
                        for (double area : querySpaceAreaPercentages) {
                            qryData = evaluateJoinQuery(joinQry, qryType, numberOfKeywordsDefault, area, keywordSpaceSizePercentageDefault, textualSimilarityDefault, spatialDistanceDefault, alphaDefault);
                            qryData.value = String.valueOf(area);
                            queryStats.querySpaceAreas.add(qryData);
                        }
                        break;
                    case KeywordSpaceSizePercentage:
                        for (int space : keywordSpaceSizePercentages) {
                            qryData = evaluateJoinQuery(joinQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, space, textualSimilarityDefault, spatialDistanceDefault, alphaDefault);
                            qryData.value = String.valueOf(space);
                            queryStats.keyboardSpaceSizes.add(qryData);
                        }
                        break;
                    case TextualSimilarity:
                        for (float ts : textualSimilarities) {
                            qryData = evaluateJoinQuery(joinQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, ts, spatialDistanceDefault, alphaDefault);
                            qryData.value = String.valueOf(ts);
                            queryStats.textualSimilarity.add(qryData);
                        }
                        break;
                    case Alpha:
                        for (double a : alphas) {
                            qryData = evaluateJoinQuery(joinQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, textualSimilarityDefault, spatialDistanceDefault, a);
                            qryData.value = String.valueOf(a);
                            queryStats.alphas.add(qryData);
                        }
                        break;
                    case Combined:
                        for (int combinedIndex = 0; combinedIndex < spatialDistances.length && combinedIndex < textualSimilarities.length; combinedIndex++) {
                            float spatialDistance = spatialDistances[combinedIndex];
                            float textualSimilarity = textualSimilarities[combinedIndex];
                            qryData = evaluateJoinQuery(joinQry, qryType, numberOfKeywordsDefault, querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, textualSimilarity, spatialDistance, alphaDefault);
                            //qryData.value = String.format("Spatial: %.2f, Textual: %.2f", spatialDistance, textualSimilarity);
                            qryData.value = String.format("%.3f / %.2f", spatialDistance, textualSimilarity);
                            queryStats.combinedST.add(qryData);
                        }
                        break;
                    default:
                        logger.warn("Unsupported query type: {}", qryType);
                        break;
                }
            }
            statisticsLogic.queriesStats.put(joinQry.toString(), queryStats);
        }
        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        logger.info("All join queries done in {} ms", totalTime);
    }

    public QueryStatsData evaluateJoinQuery(JoinQueryType joinQueryType, QueryType queryType, int numberOfKeywords, double querySpaceAreaPercentage,
                                                double keywordSpacePercentage, float textualSimilarity, float spatialDistance, double alphaDistribution) {
        QueryStatsData statsData = new QueryStatsData();
        statsData.queryType = queryType.toString();

        QueryResultWriter resultWriter = new QueryResultWriter();
        ISpatioTextualIndex tree = (ISpatioTextualIndex) IndexLogic.spatialIndex;
        tree.setAlphaDistribution((float) alphaDistribution);
        SKNNQueryGenerator queryGenerator = new SKNNQueryGenerator(ramdomSeed, parameters);

        logger.info("Evaluating join query: {} with {} keywords, query space area: {}, keyword space size: {}, textual similarity: {}, spatial distance: {}, alpha: {}",
                joinQueryType, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage, textualSimilarity, spatialDistance, alphaDistribution);

        long startTime = System.nanoTime();

        if (joinQueryType == JoinQueryType.STSJ) {
            //List<SKJoinQuery> queries = queryGenerator.generateJoinSKQueries(numberOfQueries, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage);
            List<SKJoinQuery> queries = queryGenerator.generateJoinSKQueries(numberOfQueries, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage);  // numberOfQueries = 1
            for (SKJoinQuery q : queries) {
                // TODO TEMP FIX
                // Best first and no threshold adjustment
                //List<SKJoinQuery.Result> resultsRPS = tree.selfJoinSKQueryBestFirst(IndexLogic.textualIndex, q, spatialDistance, textualSimilarity, AbstractIRTree.ThresholdAdjustment.STRICT);

                // Best first with plain sweep and no threshold adjustment
                List<SKJoinQuery.Result> resultsRPS = tree.selfJoinSKQueryBestFirst(
                        IndexLogic.textualIndex,
                        q,
                        spatialDistance,
                        textualSimilarity,
                        new JoinConfiguration(
                                ThresholdPolicy.STRICT,
                                JoinStrategy.PLANE_SWEEP,
                                SimilarityType.WEIGHTED_JACCARD,
                                QueryStrategy.CONSTRAINT_TEXTUAL_JOIN
                        )
                );

                // Recursive with no threshold adjustment
                //List<SKJoinQuery.Result> resultsRPS = tree.selfJoinSKQueryRecursive(IndexLogic.textualIndex, q, spatialDistance, textualSimilarity, ThresholdPolicy.STRICT, JoinStrategy.DEFAULT, SimilarityType.WEIGHTED_JACCARD);

                // Best first with plain sweep and Strict thresholds
                //List<SKJoinQuery.Result> resultsRPS = tree.selfJoinSKQueryBestFirst(IndexLogic.textualIndex, q, spatialDistance, textualSimilarity, ThresholdPolicy.STRICT, JoinStrategy.DEFAULT, SimilarityType.WEIGHTED_JACCARD);

                // Recursive with plain sweep and Strict thresholds
                //List<SKJoinQuery.Result> resultsRPS = tree.selfJoinSKQueryRecursive(IndexLogic.textualIndex, q, spatialDistance, textualSimilarity, ThresholdPolicy.STRICT, JoinStrategy.PLANE_SWEEP, SimilarityType.WEIGHTED_JACCARD);
                logger.info("Join query: {} results (RPS)", resultsRPS.size());
                //logger.info("Join query: {} with {} results (RPS)", q, resultsRPS.size());




                //logger.warn("Results size mismatch: BF={}, R={}", resultsBF.size(), resultsR.size());
//                if (resultsR.size() != resultsRPS.size()) {
//                    logger.warn("Results size mismatch: BF={}, R={}", resultsR.size(), resultsRPS.size());
//                } else {
//                    logger.info("Results size match: R={}", resultsR.size());
//                }
                // Compare results
//                if (resultsBF.size() != resultsR.size()) {
//                    logger.warn("Results size mismatch: BF={}, R={}", resultsBF.size(), resultsR.size());
//                } else {
//                    int mismatchCount = 0;
//                    for (int i = 0; i < resultsBF.size(); i++) {
//                        if (resultsBF.get(i).compareTo(resultsR.get(i)) != 0) {
//                            logger.debug("Results mismatch at index {}: BF={}, R={}", i, resultsBF.get(i), resultsR.get(i));
//                            mismatchCount++;
//                        }
//                    }
//                    if (mismatchCount > 0) {
//                        logger.warn("Total mismatches found: {}", mismatchCount);
//                    } else {
//                        logger.info("All results match for query: {}", q);
//                    }
//                }

                if (writeQueriesToDisk) {
                    resultWriter.writeSKJOINResult(resultsRPS);
                }
            }
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        double averageTime = totalTime / numberOfQueries;
        double averageNodesVisited = tree.getVisitedNodes() * 1.0 / numberOfQueries;

        if (writeQueriesToDisk) {
            String prefix = "[" + joinQueryType + "][" + queryType + "]";
            resultWriter.write(prefix + " Average nodes visited: " + averageNodesVisited, true);
            resultWriter.write(prefix + " Total time millisecond: " + totalTime, true);
            resultWriter.writeLineSeparator();
            resultWriter.writeToDisk(resultsDirectoryPath, prefix);
        }

        logger.printf(Level.INFO, "TotalTime=%.4fms avgT=%.4fms avgNodes=%.2f", totalTime, averageTime, averageNodesVisited);

        statsData.totalTime = totalTime;
        statsData.averageTime = averageTime;
        statsData.averageNodesVisited = averageNodesVisited;

        return statsData;
    }




    private static class CostAccumulator {
        double totalCost = 0;
        double spatialCost = 0;
        double irCost = 0;

        void add(List<AggregateSKNNQuery.Result> results) {
            if (results != null && !results.isEmpty()) {
                Cost cost = results.get(0).getAggregateCost();
                if (cost != null) {
                    this.totalCost += cost.getCombinedCost();
                    this.spatialCost += cost.getSpatialCost();
                    this.irCost += cost.getIrCost();
                }
            }
        }
    }

    public enum QueryType {
        GroupSize, Percentage, NumberOfKeywords, SpaceAreaPercentage, KeywordSpaceSizePercentage, TopK, Alpha, Radius, SpatialDistance, TextualSimilarity, Combined, Defaults
    }

    public enum AggregateQueryType {
        GNNK, GNNK_BL, SGNNK, SGNNK_BL, SGNNK_EX, SGNNK_NM1
    }

    public enum KnnQueryType {
        BkSK, TkSK
    }

    public enum RangeQueryType {
        BRSK
    }

    public enum JoinQueryType {
        STSJ
    }
}

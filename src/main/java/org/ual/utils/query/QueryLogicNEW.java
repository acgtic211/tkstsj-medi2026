package org.ual.utils.query;

import org.ual.algorithm.aggregator.IAggregator;
import org.ual.documentindex.RankingSumMode;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.utils.ResultQueryTotal;
import org.ual.utils.experiment.AggregateExperiment;
import org.ual.utils.experiment.JoinExperiment;
import org.ual.utils.experiment.KnnExperiment;
import org.ual.utils.experiment.RangeExperiment;
import org.ual.utils.index.IndexLogicNEW;
import org.ual.utils.main.StatisticsLogic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class QueryLogicNEW {
    private final IndexLogicNEW indexLogic;
    private final StatisticsLogic statisticsLogic;
    private final String resultsDirectoryPath;
    private final DatasetParameters parameters;
    private final boolean writeQueriesToDisk;
    private final int ramdomSeed = 1;

    private final Map<Class<? extends AbstractQueryExecutor>, AbstractQueryExecutor> executors;

    public QueryLogicNEW(IndexLogicNEW indexLogic, StatisticsLogic statisticsLogic, String resultsDirectoryPath, DatasetParameters parameters, boolean writeQueriesToDisk) {
        this.indexLogic = indexLogic;
        this.statisticsLogic = statisticsLogic;
        this.resultsDirectoryPath = resultsDirectoryPath;
        this.parameters = parameters;
        this.writeQueriesToDisk = writeQueriesToDisk;

        executors = new HashMap<>();
        executors.put(AggregateQueryExecutor.class, new AggregateQueryExecutor(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, ramdomSeed, writeQueriesToDisk));
        executors.put(KnnQueryExecutor.class, new KnnQueryExecutor(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, ramdomSeed, writeQueriesToDisk));
        executors.put(RangeQueryExecutor.class, new RangeQueryExecutor(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, ramdomSeed, writeQueriesToDisk));
        executors.put(JoinQueryExecutor.class, new JoinQueryExecutor(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, ramdomSeed, writeQueriesToDisk));
    }

    public void setAllParameters(int[] groupSizes, int groupSizeDefault, int[] mPercentages, int mPercentageDefault, int[] numberOfKeywords,
                                 int numberOfKeywordsDefault, double[] querySpaceAreaPercentages, double querySpaceAreaPercentageDefault,
                                 int[] keywordSpaceSizePercentages, int keywordSpaceSizePercentageDefault, int[] topks, int topkDefault,
                                 double[] alphas, double alphaDefault, float[] radius, float radiusDefault,
                                 float[] spatialDistances, float spatialDistanceDefault, float[] textualSimilarities,
                                 float textualSimilarityDefault, RankingSumMode rankingSumModeDefault, int numberOfQueries) {
        for (AbstractQueryExecutor executor : executors.values()) {
            executor.setParameters(groupSizes, groupSizeDefault, mPercentages, mPercentageDefault, numberOfKeywords,
                    numberOfKeywordsDefault, querySpaceAreaPercentages, querySpaceAreaPercentageDefault,
                    keywordSpaceSizePercentages, keywordSpaceSizePercentageDefault, topks, topkDefault,
                    alphas, alphaDefault, radius, radiusDefault, spatialDistances, spatialDistanceDefault,
                    textualSimilarities, textualSimilarityDefault, rankingSumModeDefault, numberOfQueries);
        }
    }

    public void processAggregateQuery(AggregateQueryType[] aggregateQueryTypes, ArrayList<QueryType> queryTypes,
                                      IAggregator aggregator, AggregateExperiment experiment) {
        executors.get(AggregateQueryExecutor.class).processQueries(aggregateQueryTypes, queryTypes, aggregator, experiment);
    }

    public void processKnnQuery(KnnQueryType[] knnQueryTypes, ArrayList<QueryType> queryTypes, KnnExperiment experiment) {
        executors.get(KnnQueryExecutor.class).processQueries(knnQueryTypes, queryTypes, null, experiment);
    }

    public void processRangeQuery(RangeQueryType[] rangeQueryTypes, ArrayList<QueryType> queryTypes, RangeExperiment experiment) {
        executors.get(RangeQueryExecutor.class).processQueries(rangeQueryTypes, queryTypes, null, experiment);
    }

    public void processJoinQuery(JoinQueryType[] joinQueryTypes, ArrayList<QueryType> queryTypes, JoinExperiment joinExperiment) {
        executors.get(JoinQueryExecutor.class).processQueries(joinQueryTypes, queryTypes, null, joinExperiment);
    }

    public void printStats() {
        statisticsLogic.writeResults();
    }

    public void setQueryResults(ResultQueryTotal globalQueryResults) {
        this.statisticsLogic.globalQueryResults = globalQueryResults;
    }

    public void setNumberOfQueries(int numberOfQueries) {
        // Call setParameters with updated numberOfQueries but keeping all other existing values
        for (AbstractQueryExecutor exec : executors.values()) {
            exec.setParameters(
                    exec.groupSizes, exec.groupSizeDefault,
                    exec.mPercentages, exec.mPercentageDefault,
                    exec.numberOfKeywords, exec.numberOfKeywordsDefault,
                    exec.querySpaceAreaPercentages, exec.querySpaceAreaPercentageDefault,
                    exec.keywordSpaceSizePercentages, exec.keywordSpaceSizePercentageDefault,
                    exec.topks, exec.topkDefault,
                    exec.alphas, exec.alphaDefault,
                    exec.radius, exec.radiusDefault,
                    exec.spatialDistances, exec.spatialDistanceDefault,
                    exec.textualSimilarities, exec.textualSimilarityDefault,
                    exec.rankingSumModeDefault,
                    numberOfQueries  // Only this parameter changes
            );
        }
    }

    public void setExecutionIterations(int executionIterations) {
        for (AbstractQueryExecutor exec : executors.values()) {
            exec.setExecutionIterations(executionIterations);
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
        STSJ,         // STSJ(O, e, d)
        STSJ_MULTISET, // STSJ over two datasets
        STSJ_EX,       // STSJ(O, P, e, d)
        TOPK_STSJ,      // Top-k-STSJ(O, k)
        TOPK_STSJ_EX,    // Top-k-STSJ(O, P, k)
        KNNJQ
    }
}

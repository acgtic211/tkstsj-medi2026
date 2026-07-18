package org.ual.utils.query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.documentindex.RankingSumMode;
import org.ual.documentindex.SimilarityType;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.JoinStrategy;
import org.ual.spatiotextualindex.queries.QueryStrategy;
import org.ual.spatiotextualindex.queries.ThresholdPolicy;
import org.ual.utils.experiment.IExperimentConfiguration;
import org.ual.utils.experiment.JoinExperiment;
import org.ual.utils.experiment.QueryParameterOverrides;
import org.ual.utils.index.IndexLogicNEW;
import org.ual.utils.io.QueryResultWriter;
import org.ual.utils.main.StatisticsLogic;
import org.ual.utils.stats.QueryStats;
import org.ual.utils.stats.QueryStatsData;
import org.ual.utils.stats.QueryStatisticsNEW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public abstract class AbstractQueryExecutor {
    protected final Logger logger = LogManager.getLogger(getClass());
    protected final IndexLogicNEW indexLogic;
    protected final StatisticsLogic statisticsLogic;
    protected final String resultsDirectoryPath;
    protected final DatasetParameters parameters;
    protected final int ramdomSeed;
    protected final boolean writeQueriesToDisk;
    protected final AbstractIRTree tree;

    // Query Parameters
    protected int[] groupSizes;
    protected int groupSizeDefault;
    protected int[] mPercentages;
    protected int mPercentageDefault;
    protected int[] numberOfKeywords;
    protected int numberOfKeywordsDefault;
    protected double[] querySpaceAreaPercentages;
    protected double querySpaceAreaPercentageDefault;
    protected int[] keywordSpaceSizePercentages;
    protected int keywordSpaceSizePercentageDefault;
    protected int[] topks;
    protected int topkDefault;
    protected double[] alphas;
    protected double alphaDefault;
    protected float[] radius;
    protected float radiusDefault;
    protected float[] spatialDistances;
    protected float spatialDistanceDefault;
    protected float[] textualSimilarities;
    protected float textualSimilarityDefault;
    protected RankingSumMode rankingSumModeDefault;
    protected int numberOfQueries;
    protected int executionIterations = 1;

    public AbstractQueryExecutor(IndexLogicNEW indexLogic, StatisticsLogic statisticsLogic,
                                 String resultsDirectoryPath, DatasetParameters parameters,
                                 int seed, boolean writeQueriesToDisk) {
        this.indexLogic = indexLogic;
        this.statisticsLogic = statisticsLogic;
        this.resultsDirectoryPath = resultsDirectoryPath;
        this.parameters = parameters;
        this.ramdomSeed = seed;
        this.writeQueriesToDisk = writeQueriesToDisk;
        this.tree = (AbstractIRTree) indexLogic.getSpatialIndex();
    }

    public void setParameters(int[] groupSizes, int groupSizeDefault, int[] mPercentages, int mPercentageDefault,
                              int[] numberOfKeywords, int numberOfKeywordsDefault, double[] querySpaceAreaPercentages,
                              double querySpaceAreaPercentageDefault, int[] keywordSpaceSizePercentages,
                              int keywordSpaceSizePercentageDefault, int[] topks, int topkDefault,
                              double[] alphas, double alphaDefault, float[] radius, float radiusDefault,
                              float[] spatialDistances, float spatialDistanceDefault, float[] textualSimilarities,
                               float textualSimilarityDefault, RankingSumMode rankingSumModeDefault, int numberOfQueries) {
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
        this.spatialDistances = spatialDistances;
        this.spatialDistanceDefault = spatialDistanceDefault;
        this.textualSimilarities = textualSimilarities;
        this.textualSimilarityDefault = textualSimilarityDefault;
        this.rankingSumModeDefault = RankingSumMode.orDefault(rankingSumModeDefault);
        this.numberOfQueries = numberOfQueries;
    }

    public void setExecutionIterations(int executionIterations) {
        this.executionIterations = Math.max(1, executionIterations);
    }


//    public void processQueries(Object[] queryTypes, ArrayList<QueryLogicNEW.QueryType> queryParams, IAggregator aggregator) {
//        logger.info("Processing and Evaluating {} Queries:", this.getClass().getSimpleName().replace("Executor", ""));
//        long startTime = System.nanoTime();
//
//        for (Object qType : queryTypes) {
//            logger.info("Processing query: {}", qType);
//            QueryStats queryStats = new QueryStats(qType.toString());
//
//            for (QueryLogicNEW.QueryType qryParam : queryParams) {
//                logger.info("\t ...based on {}", qryParam);
//                processQueryParameter(qType, qryParam, queryStats, aggregator);
//            }
//
//            statisticsLogic.queriesStats.put(qType.toString(), queryStats);
//        }
//
//        long endTime = System.nanoTime();
//        double totalTime = (endTime - startTime) / 1_000_000.0;
//        logger.info("All {} queries done in {} ms", this.getClass().getSimpleName().replace("Executor", ""), totalTime);
//    }

    public <T extends Enum<T>> void processQueries(T[] queryTypes, ArrayList<QueryLogicNEW.QueryType> queryParams, IAggregator aggregator, IExperimentConfiguration experimentConfig) {
        logger.info("Processing and Evaluating {} Queries:", this.getClass().getSimpleName().replace("Executor", ""));
        long startTime = System.nanoTime();

        for (T qType : queryTypes) {
            String queryKey = buildQueryStatsKey(qType, experimentConfig);
            logger.info("Processing query: {}", queryKey);
            QueryStats queryStats = new QueryStats(queryKey);
            QueryStatisticsNEW queryStatisticsNEW = new QueryStatisticsNEW(queryKey);
            QueryParameterOverrides queryTypeOverrides = experimentConfig == null
                    ? null
                    : experimentConfig.getOverridesForQueryType(qType.toString());

            for (QueryLogicNEW.QueryType qryParam : queryParams) {
                logger.info("\t ...based on {}", qryParam);
                processQueryParameter(qType, qryParam, queryStats, queryStatisticsNEW, aggregator, experimentConfig, queryTypeOverrides);
            }

            statisticsLogic.queriesStats.put(queryKey, queryStats);
            statisticsLogic.queriesStatsNew.put(queryKey, queryStatisticsNEW);
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        logger.info("All {} queries done in {} ms", this.getClass().getSimpleName().replace("Executor", ""), totalTime);
    }

//    private void processQueryParameter(Object queryType, QueryLogicNEW.QueryType qryParam, QueryStats queryStats, IAggregator aggregator) {
//        QueryStatsData qryData;
//        Params baseParams = new Params(groupSizeDefault, mPercentageDefault, numberOfKeywordsDefault,
//                querySpaceAreaPercentageDefault, keywordSpaceSizePercentageDefault, topkDefault, alphaDefault,
//                radiusDefault, spatialDistanceDefault, textualSimilarityDefault, aggregator, null, null, null, null);
//
//        switch (qryParam) {
//            case GroupSize:
//                for (int gs : groupSizes) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withGroupSize(gs));
//                    qryData.value = String.valueOf(gs);
//                    queryStats.groupSizes.add(qryData);
//                }
//                break;
//            case Percentage:
//                for (int per : mPercentages) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withMPercentage(per));
//                    qryData.value = String.valueOf(per);
//                    queryStats.percentages.add(qryData);
//                }
//                break;
//            case NumberOfKeywords:
//                for (int nkey : numberOfKeywords) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withNumberOfKeywords(nkey));
//                    qryData.value = String.valueOf(nkey);
//                    queryStats.numKeywords.add(qryData);
//                }
//                break;
//            case SpaceAreaPercentage:
//                for (double area : querySpaceAreaPercentages) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withQuerySpaceAreaPercentage(area));
//                    qryData.value = String.valueOf(area);
//                    queryStats.querySpaceAreas.add(qryData);
//                }
//                break;
//            case KeywordSpaceSizePercentage:
//                for (int space : keywordSpaceSizePercentages) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withKeywordSpacePercentage(space));
//                    qryData.value = String.valueOf(space);
//                    queryStats.keyboardSpaceSizes.add(qryData);
//                }
//                break;
//            case TopK:
//                for (int k : topks) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withTopK(k));
//                    qryData.value = String.valueOf(k);
//                    queryStats.topks.add(qryData);
//                }
//                break;
//            case Alpha:
//                for (double a : alphas) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withAlphaDistribution(a));
//                    qryData.value = String.valueOf(a);
//                    queryStats.alphas.add(qryData);
//                }
//                break;
//            case Radius:
//                for (float r : radius) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withRadius(r));
//                    qryData.value = String.valueOf(r);
//                    queryStats.radii.add(qryData);
//                }
//                break;
//            case SpatialDistance:
//                for (float d : spatialDistances) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withSpatialDistance(d));
//                    qryData.value = String.valueOf(d);
//                    queryStats.spatialDistance.add(qryData);
//                }
//                break;
//            case TextualSimilarity:
//                for (float ts : textualSimilarities) {
//                    qryData = evaluateQuery(queryType, qryParam, baseParams.withTextualSimilarity(ts));
//                    qryData.value = String.valueOf(ts);
//                    queryStats.textualSimilarity.add(qryData);
//                }
//                break;
//            case Combined:
//                for (int i = 0; i < spatialDistances.length && i < textualSimilarities.length; i++) {
//                    Params combinedParams = baseParams.withSpatialDistance(spatialDistances[i]).withTextualSimilarity(textualSimilarities[i]);
//                    qryData = evaluateQuery(queryType, qryParam, combinedParams);
//                    qryData.value = String.format("%.3f / %.2f", spatialDistances[i], textualSimilarities[i]);
//                    queryStats.combinedST.add(qryData);
//                }
//                break;
//            default:
//                logger.warn("Unsupported query parameter type: {}", qryParam);
//                break;
//        }
//    }

    private <T extends Enum<T>> void processQueryParameter(T queryType, QueryLogicNEW.QueryType qryParam,
                                                           QueryStats queryStats,
                                                           QueryStatisticsNEW queryStatisticsNEW,
                                                           IAggregator aggregator,
                                                            IExperimentConfiguration experimentConfig,
                                                            QueryParameterOverrides queryTypeOverrides) {
        QueryStatsData qryData;

        // Initialize join parameters from experiment configuration
        String joinAlgorithm = null;
        JoinStrategy joinStrategy = null;
        SimilarityType similarityType = null;
        ThresholdPolicy thresholdPolicy = null;
        QueryStrategy queryStrategy = null;

        // Extract join parameters if this is a join experiment
        if (experimentConfig instanceof JoinExperiment) {
            JoinExperiment joinExp = (JoinExperiment) experimentConfig;
            joinAlgorithm = requireNonBlank(joinExp.getAlgorithm(), "joinExperiments[].algorithm");
            joinStrategy = parseRequiredEnum(JoinStrategy.class, joinExp.getJoinStrategy(), "joinExperiments[].joinStrategy");
            similarityType = parseRequiredEnum(SimilarityType.class, joinExp.getSimilarityType(), "joinExperiments[].similarityType");
            thresholdPolicy = parseRequiredEnum(ThresholdPolicy.class, joinExp.getThresholdPolicy(), "joinExperiments[].thresholdPolicy");
            queryStrategy = parseRequiredEnum(QueryStrategy.class, joinExp.getQueryStrategy(), "joinExperiments[].queryStrategy");
        }

        int[] effectiveGroupSizes = choose(groupSizes, queryTypeOverrides != null ? queryTypeOverrides.getGroupSizes() : null);
        int[] effectiveMPercentages = choose(mPercentages, queryTypeOverrides != null ? queryTypeOverrides.getMPercentages() : null);
        int[] effectiveKeywords = choose(numberOfKeywords, queryTypeOverrides != null ? queryTypeOverrides.getNumberOfKeywords() : null);
        double[] effectiveSpaceAreas = choose(querySpaceAreaPercentages, queryTypeOverrides != null ? queryTypeOverrides.getQuerySpaceAreaPercentages() : null);
        int[] effectiveKeywordSpaceSizes = choose(keywordSpaceSizePercentages, queryTypeOverrides != null ? queryTypeOverrides.getKeywordSpaceSizePercentages() : null);
        int[] effectiveTopks = choose(topks, queryTypeOverrides != null ? queryTypeOverrides.getTopks() : null);
        double[] effectiveAlphas = choose(alphas, queryTypeOverrides != null ? queryTypeOverrides.getAlphas() : null);
        float[] effectiveRadii = choose(radius, queryTypeOverrides != null ? queryTypeOverrides.getRadius() : null);
        float[] effectiveSpatialDistances = choose(spatialDistances, queryTypeOverrides != null ? queryTypeOverrides.getSpatialDistances() : null);
        float[] effectiveTextualSimilarities = choose(textualSimilarities, queryTypeOverrides != null ? queryTypeOverrides.getTextualSimilarities() : null);

        Params baseParams = new Params(
                choose(groupSizeDefault, queryTypeOverrides != null ? queryTypeOverrides.getGroupSizeDefault() : null),
                choose(mPercentageDefault, queryTypeOverrides != null ? queryTypeOverrides.getMPercentageDefault() : null),
                choose(numberOfKeywordsDefault, queryTypeOverrides != null ? queryTypeOverrides.getNumberOfKeywordsDefault() : null),
                choose(querySpaceAreaPercentageDefault, queryTypeOverrides != null ? queryTypeOverrides.getQuerySpaceAreaPercentageDefault() : null),
                choose(keywordSpaceSizePercentageDefault, queryTypeOverrides != null ? queryTypeOverrides.getKeywordSpaceSizePercentageDefault() : null),
                choose(topkDefault, queryTypeOverrides != null ? queryTypeOverrides.getTopkDefault() : null),
                choose(alphaDefault, queryTypeOverrides != null ? queryTypeOverrides.getAlphaDefault() : null),
                choose(radiusDefault, queryTypeOverrides != null ? queryTypeOverrides.getRadiusDefault() : null),
                choose(spatialDistanceDefault, queryTypeOverrides != null ? queryTypeOverrides.getSpatialDistanceDefault() : null),
                choose(textualSimilarityDefault, queryTypeOverrides != null ? queryTypeOverrides.getTextualSimilarityDefault() : null),
                queryTypeOverrides != null && queryTypeOverrides.getRankingSumMode() != null
                        ? queryTypeOverrides.getRankingSumMode()
                        : rankingSumModeDefault,
                aggregator,
                joinAlgorithm, joinStrategy, similarityType, thresholdPolicy, queryStrategy, numberOfQueries);

        baseParams = applyExperimentOverrides(baseParams, experimentConfig, queryTypeOverrides);

        switch (qryParam) {
            case GroupSize:
                for (int gs : effectiveGroupSizes) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withGroupSize(gs));
                    addStatsEntry(qryData, String.valueOf(gs), queryStats.groupSizes, queryStatisticsNEW);
                }
                break;
            case Percentage:
                for (int per : effectiveMPercentages) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withMPercentage(per));
                    addStatsEntry(qryData, String.valueOf(per), queryStats.percentages, queryStatisticsNEW);
                }
                break;
            case NumberOfKeywords:
                for (int nkey : effectiveKeywords) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withNumberOfKeywords(nkey));
                    addStatsEntry(qryData, String.valueOf(nkey), queryStats.numKeywords, queryStatisticsNEW);
                }
                break;
            case SpaceAreaPercentage:
                for (double area : effectiveSpaceAreas) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withQuerySpaceAreaPercentage(area));
                    addStatsEntry(qryData, String.valueOf(area), queryStats.querySpaceAreas, queryStatisticsNEW);
                }
                break;
            case KeywordSpaceSizePercentage:
                for (int space : effectiveKeywordSpaceSizes) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withKeywordSpacePercentage(space));
                    addStatsEntry(qryData, String.valueOf(space), queryStats.keyboardSpaceSizes, queryStatisticsNEW);
                }
                break;
            case TopK:
                for (int k : effectiveTopks) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withTopK(k));
                    addStatsEntry(qryData, String.valueOf(k), queryStats.topks, queryStatisticsNEW);
                }
                break;
            case Alpha:
                for (double a : effectiveAlphas) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withAlphaDistribution(a));
                    addStatsEntry(qryData, String.valueOf(a), queryStats.alphas, queryStatisticsNEW);
                }
                break;
            case Radius:
                for (float r : effectiveRadii) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withRadius(r));
                    addStatsEntry(qryData, String.valueOf(r), queryStats.radii, queryStatisticsNEW);
                }
                break;
            case SpatialDistance:
                for (float d : effectiveSpatialDistances) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withSpatialDistance(d));
                    addStatsEntry(qryData, String.valueOf(d), queryStats.spatialDistance, queryStatisticsNEW);
                }
                break;
            case TextualSimilarity:
                for (float ts : effectiveTextualSimilarities) {
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams.withTextualSimilarity(ts));
                    addStatsEntry(qryData, String.valueOf(ts), queryStats.textualSimilarity, queryStatisticsNEW);
                }
                break;
            case Combined:
                for (int i = 0; i < effectiveSpatialDistances.length && i < effectiveTextualSimilarities.length; i++) {
                    Params combinedParams = baseParams.withSpatialDistance(effectiveSpatialDistances[i]).withTextualSimilarity(effectiveTextualSimilarities[i]);
                    qryData = evaluateQueryAcrossIterations(queryType, qryParam, combinedParams);
                    addStatsEntry(qryData,
                            String.format("%.3f / %.2f", effectiveSpatialDistances[i], effectiveTextualSimilarities[i]),
                            queryStats.combinedST,
                            queryStatisticsNEW);
                }
                break;
            case Defaults:
                qryData = evaluateQueryAcrossIterations(queryType, qryParam, baseParams);
                addStatsEntry(qryData, "default", queryStats.defaults, queryStatisticsNEW);
                break;
            default:
                logger.warn("Unsupported query parameter type: {}", qryParam);
                break;
        }
    }

    private Params applyExperimentOverrides(Params baseParams,
                                            IExperimentConfiguration experimentConfig,
                                            QueryParameterOverrides queryTypeOverrides) {
        if (experimentConfig == null) {
            return baseParams;
        }

        Params updated = baseParams;

        Double fixedAlphaValue = experimentConfig.getFixedAlpha();
        if (fixedAlphaValue != null) {
            updated = updated.withAlphaDistribution(fixedAlphaValue);
        }

        RankingSumMode fixedRankingSumMode = experimentConfig.getFixedRankingSumMode();
        if (fixedRankingSumMode != null) {
            updated = updated.withRankingSumMode(fixedRankingSumMode);
        }

        Integer overrideKeywords = experimentConfig.getOverrideNumberOfKeywords();
        if (overrideKeywords != null && overrideKeywords > 0) {
            updated = updated.withNumberOfKeywords(overrideKeywords);
        }

        Integer overrideQueries = experimentConfig.getOverrideNumberOfQueries();
        if (overrideQueries != null && overrideQueries > 0) {
            updated = updated.withNumberOfQueries(overrideQueries);
        }

        if (queryTypeOverrides != null && queryTypeOverrides.getNumberOfQueries() != null
                && queryTypeOverrides.getNumberOfQueries() > 0) {
            updated = updated.withNumberOfQueries(queryTypeOverrides.getNumberOfQueries());
        }

        if (queryTypeOverrides != null && queryTypeOverrides.getRankingSumMode() != null) {
            updated = updated.withRankingSumMode(queryTypeOverrides.getRankingSumMode());
        }

        if (experimentConfig instanceof JoinExperiment) {
            JoinExperiment joinExperiment = (JoinExperiment) experimentConfig;
            if (joinExperiment.getFixedTextualSimilarity() != null) {
                updated = updated.withTextualSimilarity(joinExperiment.getFixedTextualSimilarity().floatValue());
            }
            if (joinExperiment.getFixedSpatialDistance() != null) {
                updated = updated.withSpatialDistance(joinExperiment.getFixedSpatialDistance().floatValue());
            }
        }

        return updated;
    }

    private int[] choose(int[] base, int[] override) {
        return override != null && override.length > 0 ? override : base;
    }

    private double[] choose(double[] base, double[] override) {
        return override != null && override.length > 0 ? override : base;
    }

    private float[] choose(float[] base, float[] override) {
        return override != null && override.length > 0 ? override : base;
    }

    private int choose(int base, Integer override) {
        return override != null ? override : base;
    }

    private double choose(double base, Double override) {
        return override != null ? override : base;
    }

    private float choose(float base, Float override) {
        return override != null ? override : base;
    }

    private <T extends Enum<T>> String buildQueryStatsKey(T queryType, IExperimentConfiguration experimentConfig) {
        String base = queryType.toString();
        if (!(experimentConfig instanceof JoinExperiment)) {
            return base;
        }

        JoinExperiment join = (JoinExperiment) experimentConfig;
        return buildJoinStatsKey(base, join);
    }

    static String buildJoinStatsKey(String base, JoinExperiment join) {
        return String.format(Locale.ROOT, "%s|alg=%s|strategy=%s|sim=%s|policy=%s|query=%s",
                base,
                normalizeRequired(join.getAlgorithm(), "joinExperiments[].algorithm"),
                normalizeRequired(join.getJoinStrategy(), "joinExperiments[].joinStrategy"),
                normalizeRequired(join.getSimilarityType(), "joinExperiments[].similarityType"),
                normalizeRequired(join.getThresholdPolicy(), "joinExperiments[].thresholdPolicy"),
                normalizeRequired(join.getQueryStrategy(), "joinExperiments[].queryStrategy"));
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value.trim();
    }

    private static String normalizeRequired(String value, String fieldName) {
        return requireNonBlank(value, fieldName).toUpperCase(Locale.ROOT);
    }

    private static <T extends Enum<T>> T parseRequiredEnum(Class<T> enumType, String rawValue, String fieldName) {
        final String normalized = normalizeRequired(rawValue, fieldName);
        try {
            if (enumType == QueryStrategy.class) {
                @SuppressWarnings("unchecked")
                T parsed = (T) QueryStrategy.parse(normalized);
                return parsed;
            }
            return Enum.valueOf(enumType, normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid value '" + rawValue + "' for " + fieldName, ex);
        }
    }

    private void addStatsEntry(QueryStatsData data, String value, List<QueryStatsData> legacyList, QueryStatisticsNEW queryStatisticsNEW) {
        data.value = value;
        legacyList.add(data);
        queryStatisticsNEW.addEntry(data);
    }

    private <T extends Enum<T>> QueryStatsData evaluateQueryAcrossIterations(T queryType, QueryLogicNEW.QueryType paramType, Params params) {
        List<QueryStatsData> perIteration = new ArrayList<>();
        for (int i = 0; i < executionIterations; i++) {
            perIteration.add(evaluateQuery(queryType, paramType, params));
        }
        return aggregateIterations(perIteration);
    }

    private QueryStatsData aggregateIterations(List<QueryStatsData> perIteration) {
        QueryStatsData merged = new QueryStatsData();
        if (perIteration.isEmpty()) {
            return merged;
        }

        int n = perIteration.size();
        merged.queryType = perIteration.get(0).queryType;
        merged.numIterations = n;

        double totalTimeSum = 0;
        double avgTimeSum = 0;
        double avgNodesSum = 0;
        long totalNodesSum = 0;
        double avgSpatialSum = 0;
        double avgIRSum = 0;
        long totalResultsSum = 0;
        double avgResultsSum = 0;
        long memDeltaSum = 0;

        List<Double> times = new ArrayList<>();

        for (QueryStatsData item : perIteration) {
            totalTimeSum += item.totalTime;
            avgTimeSum += item.averageTime;
            avgNodesSum += item.averageNodesVisited;
            totalNodesSum += item.totalNodesVisited;
            avgSpatialSum += item.averageSpatialCost;
            avgIRSum += item.averageIRCost;
            totalResultsSum += item.totalResultsReturned;
            avgResultsSum += item.averageResultsReturned;
            memDeltaSum += item.memoryDeltaBytes;

            times.add(item.totalTime);
            merged.perIterationTimes.add(item.totalTime);
            merged.perIterationNodesVisited.add(item.totalNodesVisited);
        }

        Collections.sort(times);

        merged.totalTime = totalTimeSum / n;
        merged.averageTime = avgTimeSum / n;
        merged.minTime = times.get(0);
        merged.maxTime = times.get(times.size() - 1);
        merged.medianTime = median(times);
        merged.totalNodesVisited = Math.round(totalNodesSum * 1.0 / n);
        merged.averageNodesVisited = avgNodesSum / n;
        merged.averageSpatialCost = avgSpatialSum / n;
        merged.averageIRCost = avgIRSum / n;
        merged.totalResultsReturned = Math.round(totalResultsSum * 1.0 / n);
        merged.averageResultsReturned = avgResultsSum / n;
        merged.memoryDeltaBytes = Math.round(memDeltaSum * 1.0 / n);

        return merged;
    }

    private double median(List<Double> values) {
        int size = values.size();
        int mid = size / 2;
        if (size % 2 == 0) {
            return (values.get(mid - 1) + values.get(mid)) / 2.0;
        }
        return values.get(mid);
    }

    protected abstract <T extends Enum<T>> QueryStatsData evaluateQuery(T queryType, QueryLogicNEW.QueryType paramType, Params params);

    protected void logQueryStats(String prefix, QueryStatsData statsData, QueryResultWriter resultWriter) {
        if (writeQueriesToDisk) {
            resultWriter.write(prefix + " Average nodes visited: " + statsData.averageNodesVisited, true);
            resultWriter.write(prefix + " Total time millisecond: " + statsData.totalTime, true);
            resultWriter.writeLineSeparator();
            resultWriter.writeToDisk(resultsDirectoryPath, prefix);
        }
    }

    protected static class Params {
        int groupSize;
        int mPercentage;
        int numberOfKeywords;
        double querySpaceAreaPercentage;
        double keywordSpacePercentage;
        int topk;
        double alphaDistribution;
        float radius;
        float spatialDistance;
        float textualSimilarity;
        RankingSumMode rankingSumMode;
        IAggregator aggregator;
        // Join specific parameters
        String joinAlgorithm;
        JoinStrategy joinStrategy;
        SimilarityType similarityType;
        ThresholdPolicy thresholdPolicy;
        QueryStrategy queryStrategy;
        int numberOfQueries;

        public Params(int groupSize, int mPercentage, int numberOfKeywords, double querySpaceAreaPercentage,
                      double keywordSpacePercentage, int topk, double alphaDistribution, float radius,
                      float spatialDistance, float textualSimilarity, RankingSumMode rankingSumMode, IAggregator aggregator,
                      String joinAlgorithm, JoinStrategy joinStrategy, SimilarityType similarityType, ThresholdPolicy thresholdPolicy,
                      QueryStrategy queryStrategy,
                      int numberOfQueries) {
            this.groupSize = groupSize;
            this.mPercentage = mPercentage;
            this.numberOfKeywords = numberOfKeywords;
            this.querySpaceAreaPercentage = querySpaceAreaPercentage;
            this.keywordSpacePercentage = keywordSpacePercentage;
            this.topk = topk;
            this.alphaDistribution = alphaDistribution;
            this.radius = radius;
            this.spatialDistance = spatialDistance;
            this.textualSimilarity = textualSimilarity;
            this.rankingSumMode = RankingSumMode.orDefault(rankingSumMode);
            this.aggregator = aggregator;
            this.joinAlgorithm = joinAlgorithm;
            this.joinStrategy = joinStrategy;
            this.similarityType = similarityType;
            this.thresholdPolicy = thresholdPolicy;
            this.queryStrategy = queryStrategy;
            this.numberOfQueries = numberOfQueries;
        }

        private Params copy() {
            return new Params(groupSize, mPercentage, numberOfKeywords, querySpaceAreaPercentage, keywordSpacePercentage,
                    topk, alphaDistribution, radius, spatialDistance, textualSimilarity, rankingSumMode, aggregator,
                    joinAlgorithm, joinStrategy, similarityType, thresholdPolicy, queryStrategy, numberOfQueries);
        }

        public Params withGroupSize(int groupSize) { Params p = copy(); p.groupSize = groupSize; return p; }
        public Params withMPercentage(int mPercentage) { Params p = copy(); p.mPercentage = mPercentage; return p; }
        public Params withNumberOfKeywords(int numberOfKeywords) { Params p = copy(); p.numberOfKeywords = numberOfKeywords; return p; }
        public Params withQuerySpaceAreaPercentage(double querySpaceAreaPercentage) { Params p = copy(); p.querySpaceAreaPercentage = querySpaceAreaPercentage; return p; }
        public Params withKeywordSpacePercentage(double keywordSpacePercentage) { Params p = copy(); p.keywordSpacePercentage = keywordSpacePercentage; return p; }
        public Params withTopK(int topk) { Params p = copy(); p.topk = topk; return p; }
        public Params withAlphaDistribution(double alphaDistribution) { Params p = copy(); p.alphaDistribution = alphaDistribution; return p; }
        public Params withRadius(float radius) { Params p = copy(); p.radius = radius; return p; }
        public Params withSpatialDistance(float spatialDistance) { Params p = copy(); p.spatialDistance = spatialDistance; return p; }
        public Params withTextualSimilarity(float textualSimilarity) { Params p = copy(); p.textualSimilarity = textualSimilarity; return p; }
        public Params withRankingSumMode(RankingSumMode rankingSumMode) { Params p = copy(); p.rankingSumMode = RankingSumMode.orDefault(rankingSumMode); return p; }
        public Params withJoinAlgorithm(String joinAlgorithm) { Params p = copy(); p.joinAlgorithm = joinAlgorithm; return p; }
        public Params withJoinStrategy(JoinStrategy joinStrategy) { Params p = copy(); p.joinStrategy = joinStrategy; return p; }
        public Params withSimilarityType(SimilarityType similarityType) { Params p = copy(); p.similarityType = similarityType; return p; }
        public Params withThresholdPolicy(ThresholdPolicy thresholdPolicy) { Params p = copy(); p.thresholdPolicy = thresholdPolicy; return p; }
        public Params withQueryStrategy(QueryStrategy queryStrategy) { Params p = copy(); p.queryStrategy = queryStrategy; return p; }
        public Params withNumberOfQueries(int numberOfQueries) { Params p = copy(); p.numberOfQueries = numberOfQueries; return p; }
    }
}

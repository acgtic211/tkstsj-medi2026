package org.ual.utils.query;

import org.apache.logging.log4j.Level;
import org.ual.documentindex.IDocumentIndex;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.querygeneration.SKNNQueryGenerator;
import org.ual.querytype.SKJoinQuery;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.spatiotextualindex.queries.baseline.join.JoinMultiSetQueryProcessor;
import org.ual.spatiotextualindex.queries.baseline.join.JoinTopKMultiSetQueryProcessor;
import org.ual.spatiotextualindex.queries.baseline.join.JoinTopKQueryProcessor;
import org.ual.spatiotextualindex.queries.JoinConfiguration;
import org.ual.utils.config.DataStructureType;
import org.ual.utils.config.DatasetConfig;
import org.ual.utils.config.DatasetType;
import org.ual.utils.config.IndexConfig;
import org.ual.utils.config.RTreeVariant;
import org.ual.utils.config.SpatialIndexType;
import org.ual.utils.config.TextualIndexType;
import org.ual.utils.experiment.IExperimentConfiguration;
import org.ual.utils.experiment.JoinExperiment;
import org.ual.utils.index.IndexLogicNEW;
import org.ual.utils.io.QueryResultWriter;
import org.ual.utils.main.StatisticsLogic;
import org.ual.utils.stats.QueryStatsData;

import java.util.List;

public class JoinQueryExecutor extends AbstractQueryExecutor {
    private JoinExperiment activeJoinExperiment;
    private String secondaryContextKey;
    private SecondaryJoinContext secondaryJoinContext;

    public JoinQueryExecutor(IndexLogicNEW indexLogic, StatisticsLogic statisticsLogic,
                             String resultsDirectoryPath, DatasetParameters parameters,
                             int seed, boolean writeQueriesToDisk) {
        super(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, seed, writeQueriesToDisk);
    }

    @Override
    public <T extends Enum<T>> void processQueries(T[] queryTypes,
                                                   java.util.ArrayList<QueryLogicNEW.QueryType> queryParams,
                                                   org.ual.algorithm.aggregator.IAggregator aggregator,
                                                   IExperimentConfiguration experimentConfig) {
        JoinExperiment previousExperiment = this.activeJoinExperiment;
        this.activeJoinExperiment = (experimentConfig instanceof JoinExperiment) ? (JoinExperiment) experimentConfig : null;

        try {
            super.processQueries(queryTypes, queryParams, aggregator, experimentConfig);
        } finally {
            this.activeJoinExperiment = previousExperiment;
        }
    }

    @Override
    protected <T extends Enum<T>> QueryStatsData evaluateQuery(T queryType, QueryLogicNEW.QueryType paramType, Params params) {
        QueryLogicNEW.JoinQueryType joinQueryType = (QueryLogicNEW.JoinQueryType) queryType;
        QueryStatsData statsData = new QueryStatsData();
        statsData.queryType = paramType.toString();

        QueryResultWriter resultWriter = new QueryResultWriter();
        tree.setAlphaDistribution((float) params.alphaDistribution);
        SKNNQueryGenerator queryGenerator = new SKNNQueryGenerator(ramdomSeed, parameters);

        logger.info("Evaluating join query: {} with {} keywords, query space area: {}, keyword space size: {}, textual similarity: {}, spatial distance: {}, alpha: {}",
                joinQueryType, params.numberOfKeywords, params.querySpaceAreaPercentage, params.keywordSpacePercentage, params.textualSimilarity, params.spatialDistance, params.alphaDistribution);

        long startTime = System.nanoTime();
        long totalReturnedPairs = 0;
        long totalNodesVisited = 0;
        double totalSpatialCost = 0.0;
        double totalTextualCost = 0.0;
        int queryCount = Math.max(1, params.numberOfQueries);
        JoinConfiguration joinConfiguration = new JoinConfiguration(
                params.thresholdPolicy,
                params.joinStrategy,
                params.similarityType,
                params.queryStrategy
        );

        if (joinQueryType == QueryLogicNEW.JoinQueryType.STSJ) {
            List<SKJoinQuery> queries = queryGenerator.generateJoinSKQueries(queryCount, params.numberOfKeywords, params.querySpaceAreaPercentage, params.keywordSpacePercentage);
            for (SKJoinQuery q : queries) {
                List<SKJoinQuery.Result> results;
                if ("Recursive".equalsIgnoreCase(params.joinAlgorithm)) {
                    results = tree.selfJoinSKQueryRecursive(
                            indexLogic.getTextualIndex(), q, params.spatialDistance, params.textualSimilarity, joinConfiguration);
                } else { // Default to BestFirst
                    results = tree.selfJoinSKQueryBestFirst(
                            indexLogic.getTextualIndex(), q, params.spatialDistance, params.textualSimilarity, joinConfiguration);
                }

                logger.info("STSJ query returned pairs: {}", results.size());
                totalReturnedPairs += results.size();
                for (SKJoinQuery.Result result : results) {
                    totalSpatialCost += result.spatialCost;
                    totalTextualCost += result.textualCost;
                }
                totalNodesVisited += tree.getVisitedNodes();
                if (writeQueriesToDisk) {
                    resultWriter.writeSKJOINResult(results);
                }
            }
        } else if (joinQueryType == QueryLogicNEW.JoinQueryType.STSJ_MULTISET) {
            JoinExperiment experiment = requireActiveJoinExperiment();
            SecondaryJoinContext secondaryContext = getOrCreateSecondaryJoinContext(experiment);
            JoinMultiSetQueryProcessor processor = new JoinMultiSetQueryProcessor(tree);

            List<SKJoinQuery> queries = queryGenerator.generateJoinSKQueries(
                    queryCount,
                    params.numberOfKeywords,
                    params.querySpaceAreaPercentage,
                    params.keywordSpacePercentage);

            for (SKJoinQuery q : queries) {
                List<SKJoinQuery.Result> results;
                if ("Recursive".equalsIgnoreCase(params.joinAlgorithm)) {
                    results = processor.selfJoinSKQueryRecursive(
                            indexLogic.getTextualIndex(),
                            secondaryContext.textualIndex,
                            secondaryContext.tree,
                            q,
                            params.spatialDistance,
                            params.textualSimilarity,
                            joinConfiguration);
                } else {
                    results = processor.selfJoinSKQueryBestFirst(
                            indexLogic.getTextualIndex(),
                            secondaryContext.textualIndex,
                            secondaryContext.tree,
                            q,
                            params.spatialDistance,
                            params.textualSimilarity,
                            joinConfiguration);
                }

                logger.info("STSJ_MULTISET query returned pairs: {}", results.size());
                totalReturnedPairs += results.size();
                for (SKJoinQuery.Result result : results) {
                    totalSpatialCost += result.spatialCost;
                    totalTextualCost += result.textualCost;
                }
                totalNodesVisited += processor.getVisitedNodes();
                if (writeQueriesToDisk) {
                    resultWriter.writeSKJOINResult(results);
                }
            }
        } else if (joinQueryType == QueryLogicNEW.JoinQueryType.STSJ_EX) {
            logger.warn("STSJ_EX is not implemented yet.");
        } else if (joinQueryType == QueryLogicNEW.JoinQueryType.TOPK_STSJ) {
            JoinTopKQueryProcessor processor = new JoinTopKQueryProcessor(tree);
            List<SKJoinQuery> queries = queryGenerator.generateJoinSKQueries(
                    queryCount,
                    params.numberOfKeywords,
                    params.querySpaceAreaPercentage,
                    params.keywordSpacePercentage);

            for (SKJoinQuery q : queries) {
                List<SKJoinQuery.Result> results;
                if ("Recursive".equalsIgnoreCase(params.joinAlgorithm)) {
                    results = processor.selfJoinSKQueryRecursive(
                            indexLogic.getTextualIndex(),
                            q,
                            params.topk,
                            joinConfiguration);
                } else {
                    results = processor.selfJoinSKQueryBestFirst(
                            indexLogic.getTextualIndex(),
                            q,
                            params.topk,
                            joinConfiguration);
                }

                logger.info("TOPK_STSJ query returned pairs: {}", results.size());
                totalReturnedPairs += results.size();
                for (SKJoinQuery.Result result : results) {
                    totalSpatialCost += result.spatialCost;
                    totalTextualCost += result.textualCost;
                }
                totalNodesVisited += processor.getVisitedNodes();
                if (writeQueriesToDisk) {
                    resultWriter.writeSKJOINResult(results);
                }
            }
        } else if (joinQueryType == QueryLogicNEW.JoinQueryType.TOPK_STSJ_EX) {
            JoinExperiment experiment = requireActiveJoinExperiment();
            SecondaryJoinContext secondaryContext = getOrCreateSecondaryJoinContext(experiment);
            JoinTopKMultiSetQueryProcessor processor = new JoinTopKMultiSetQueryProcessor(tree);

            List<SKJoinQuery> queries = queryGenerator.generateJoinSKQueries(
                    queryCount,
                    params.numberOfKeywords,
                    params.querySpaceAreaPercentage,
                    params.keywordSpacePercentage,
                    true); // TODO This should be a parameter in the JSON

            for (SKJoinQuery q : queries) {
                List<SKJoinQuery.Result> results;
                if ("Recursive".equalsIgnoreCase(params.joinAlgorithm)) {
                    results = processor.selfJoinSKQueryRecursive(
                            indexLogic.getTextualIndex(),
                            secondaryContext.textualIndex,
                            secondaryContext.tree,
                            q,
                            params.topk,
                            joinConfiguration);
                } else {
                    results = processor.selfJoinSKQueryBestFirst(
                            indexLogic.getTextualIndex(),
                            secondaryContext.textualIndex,
                            secondaryContext.tree,
                            q,
                            params.topk,
                            joinConfiguration);
                }

                logger.info("TOPK_STSJ_MULTISET query returned pairs: {}", results.size());
                totalReturnedPairs += results.size();
                for (SKJoinQuery.Result result : results) {
                    totalSpatialCost += result.spatialCost;
                    totalTextualCost += result.textualCost;
                }
                totalNodesVisited += processor.getVisitedNodes();
                if (writeQueriesToDisk) {
                    resultWriter.writeSKJOINResult(results);
                }
            }
        } else if (joinQueryType == QueryLogicNEW.JoinQueryType.KNNJQ) {
            logger.warn("KNNJQ is not implemented yet.");
        } else {
            logger.error("Unknown join query type: {}", joinQueryType);
        }

        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1_000_000.0;
        double averageTime = totalTime / queryCount;
        double averageNodesVisited = totalNodesVisited * 1.0 / queryCount;
        double averageResults = totalReturnedPairs * 1.0 / queryCount;
        double averageSpatialCost = totalReturnedPairs == 0 ? 0.0 : totalSpatialCost / totalReturnedPairs;
        double averageTextualCost = totalReturnedPairs == 0 ? 0.0 : totalTextualCost / totalReturnedPairs;

        String prefix = String.format("[%s][alg=%s][strategy=%s][sim=%s][policy=%s][query=%s][%s]",
                joinQueryType,
                params.joinAlgorithm,
                params.joinStrategy,
                params.similarityType,
                params.thresholdPolicy,
                params.queryStrategy,
                paramType);
        logger.printf(Level.INFO, "TotalTime=%.4fms avgT=%.4fms avgNodes=%.2f totalReturnedPairs=%d",
                totalTime, averageTime, averageNodesVisited, totalReturnedPairs);

        statsData.totalTime = totalTime;
        statsData.averageTime = averageTime;
        statsData.totalNodesVisited = totalNodesVisited;
        statsData.averageNodesVisited = averageNodesVisited;
        statsData.averageSpatialCost = averageSpatialCost;
        statsData.averageTextualCost = averageTextualCost;
        // Keep IR populated for compatibility with existing metric consumers.
        statsData.averageIRCost = averageTextualCost;
        statsData.totalResultsReturned = totalReturnedPairs;
        statsData.averageResultsReturned = averageResults;
        statsData.memoryDeltaBytes = 0L;

        logQueryStats(prefix, statsData, resultWriter);

        return statsData;
    }

    private JoinExperiment requireActiveJoinExperiment() {
        if (activeJoinExperiment == null) {
            throw new IllegalStateException("Join experiment context is required for STSJ_MULTISET.");
        }
        return activeJoinExperiment;
    }

    private SecondaryJoinContext getOrCreateSecondaryJoinContext(JoinExperiment experiment) {
        if (experiment.getSecondaryDataset() == null || experiment.getSecondaryIndex() == null) {
            throw new IllegalArgumentException("STSJ_MULTISET requires joinExperiments[].secondaryDataset and joinExperiments[].secondaryIndex");
        }

        String key = buildSecondaryContextKey(experiment);
        if (secondaryJoinContext != null && key.equals(secondaryContextKey)) {
            logger.debug("Reusing cached secondary STSJ_MULTISET context for key: {}", key);
            return secondaryJoinContext;
        }

        logger.info("Building secondary STSJ_MULTISET context for key: {}", key);

        DatasetConfig secondaryDatasetConfig = experiment.getSecondaryDataset();
        IndexConfig secondaryIndexConfig = experiment.getSecondaryIndex();
        DatasetParameters secondaryParameters = ParametersFactory.getParameters(
                convertDatasetEnum(secondaryDatasetConfig.getDatasetType()));

        double usagePercentage = secondaryDatasetConfig.getUsagePercentage();
        if (usagePercentage <= 0 || usagePercentage > 1.0) {
            logger.warn("Invalid secondary usage percentage: {}. Falling back to 1.0", usagePercentage);
            usagePercentage = 1.0;
        }

        IndexLogicNEW secondaryLogic = new IndexLogicNEW(statisticsLogic, secondaryParameters, usagePercentage);
        buildDocumentStore(secondaryLogic, secondaryIndexConfig);
        buildTextualIndex(secondaryLogic, secondaryIndexConfig);
        buildSpatialIndex(secondaryLogic, secondaryIndexConfig);

        if (!(secondaryLogic.getSpatialIndex() instanceof AbstractIRTree)) {
            throw new IllegalStateException("STSJ_MULTISET requires secondary spatial index to be an AbstractIRTree");
        }

        secondaryContextKey = key;
        secondaryJoinContext = new SecondaryJoinContext((AbstractIRTree) secondaryLogic.getSpatialIndex(), secondaryLogic.getTextualIndex());
        return secondaryJoinContext;
    }

    private void buildDocumentStore(IndexLogicNEW logic, IndexConfig indexConfig) {
        float smoothingFactor = indexConfig.getSmoothingFactor();
        if (smoothingFactor <= 0f || smoothingFactor > 1f) {
            logger.warn("Invalid secondary smoothing factor: {}. Using default 0.2", smoothingFactor);
            smoothingFactor = 0.2f;
        }

        if (indexConfig.getDataStructureType() == DataStructureType.TREEMAP) {
            logic.createTreeMapDocStore(smoothingFactor);
        } else {
            logic.createHashMapDocStore(smoothingFactor);
        }
    }

    private void buildTextualIndex(IndexLogicNEW logic, IndexConfig indexConfig) {
        int numClusters = indexConfig.getNumClusters();
        TextualIndexType textualIndexType = indexConfig.getTextualIndexType();
        if (textualIndexType == TextualIndexType.SIGNED_INVERTED_LIST) {
            logic.createSignedInvertedListIndex(numClusters);
        } else if (textualIndexType == TextualIndexType.SIGNED_BLOCK) {
            logic.createSignedBlockTextualIndex();
        } else {
            logic.createInvertedListIndex(numClusters);
        }
    }

    private void buildSpatialIndex(IndexLogicNEW logic, IndexConfig indexConfig) {
        int fanout = indexConfig.getFanout();
        float fillFactor = indexConfig.getFillFactor();
        int dimension = indexConfig.getDimension();
        int treeVariant = convertRTreeVariant(indexConfig.getRTreeVariant());
        int nearMinimumOverlapFactor = indexConfig.getNearMinimumOverlapFactor();
        int numMoves = indexConfig.getNumMoves() > 0 ? indexConfig.getNumMoves() : 300;

        SpatialIndexType spatialIndexType = indexConfig.getSpatialIndexType();
        switch (spatialIndexType) {
            case IR:
                logic.createIRTree(fanout, fillFactor, dimension, treeVariant, nearMinimumOverlapFactor);
                break;
            case IR_BULK:
                logic.createIRTreeWithBulkLoading(
                        fanout,
                        fillFactor,
                        dimension,
                        treeVariant,
                        nearMinimumOverlapFactor,
                        indexConfig.getBulkLoadMethod() == null ? BulkLoadMethod.STR : indexConfig.getBulkLoadMethod());
                break;
            case DIR:
                logic.createDIRTree(fanout, fillFactor, dimension, treeVariant, nearMinimumOverlapFactor,
                        indexConfig.getMaxWord(), indexConfig.getBetaArea());
                break;
            case CIR:
                logic.createCIRTree(fanout, fillFactor, dimension, treeVariant, nearMinimumOverlapFactor,
                        indexConfig.getNumClusters(), numMoves);
                break;
            case CDIR:
                logic.createCDIRTree(fanout, fillFactor, dimension, treeVariant, nearMinimumOverlapFactor,
                        indexConfig.getMaxWord(), indexConfig.getBetaArea(), indexConfig.getNumClusters(), numMoves);
                break;
            default:
                throw new IllegalArgumentException("Unsupported secondary spatial index type: " + spatialIndexType);
        }
    }

    private String buildSecondaryContextKey(JoinExperiment experiment) {
        DatasetConfig dataset = experiment.getSecondaryDataset();
        IndexConfig index = experiment.getSecondaryIndex();
        return dataset.getDatasetType() + "|" + dataset.getUsagePercentage() + "|"
                + index.getSpatialIndexType() + "|" + index.getTextualIndexType() + "|"
                + index.getDataStructureType() + "|" + index.getFanout() + "|" + index.getFillFactor() + "|"
                + index.getDimension() + "|" + index.getNumClusters() + "|" + index.getNumMoves();
    }

    private Dataset convertDatasetEnum(DatasetType datasetType) {
        if (datasetType == null) {
            throw new IllegalArgumentException("secondaryDataset.datasetType must not be null");
        }

        switch (datasetType) {
            case POSTAL_CODES:
                return Dataset.POSTAL_CODES_SET;
            case SPORTS:
                return Dataset.SPORTS_SET;
            case PARKS:
                return Dataset.PARKS_SET;
            case HOTELS:
                return Dataset.HOTEL_SET;
            case TEST:
                return Dataset.TESTING_SET;
            default:
                throw new IllegalArgumentException("Unknown secondary dataset type: " + datasetType);
        }
    }

    private int convertRTreeVariant(RTreeVariant variant) {
        if (variant == null) {
            return SpatialIndex.RtreeVariantRstar;
        }

        switch (variant) {
            case LINEAR:
                return SpatialIndex.RtreeVariantLinear;
            case QUADRATIC:
                return SpatialIndex.RtreeVariantQuadratic;
            case RSTAR:
                return SpatialIndex.RtreeVariantRstar;
            default:
                return SpatialIndex.RtreeVariantRstar;
        }
    }

    private static final class SecondaryJoinContext {
        private final AbstractIRTree tree;
        private final IDocumentIndex textualIndex;

        private SecondaryJoinContext(AbstractIRTree tree, IDocumentIndex textualIndex) {
            this.tree = tree;
            this.textualIndex = textualIndex;
        }
    }
}

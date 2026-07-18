package org.ual;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.utils.ResultQueryTotal;
import org.ual.utils.config.*;
import org.ual.utils.experiment.AggregateExperiment;
import org.ual.utils.experiment.JoinExperiment;
import org.ual.utils.experiment.KnnExperiment;
import org.ual.utils.experiment.RangeExperiment;
import org.ual.utils.index.IndexLogicNEW;
import org.ual.utils.main.StatisticsLogic;
import org.ual.utils.query.QueryLogicNEW;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AlternativeMain {
    private static final Logger logger = LogManager.getLogger(AlternativeMain.class);

    // Keep default aligned with legacy-equivalent parameters (e.g. numberOfKeywordsDefault=2).
    private static final String DEFAULT_CONFIG_PATH = "src/main/resources/json/running-config-full.json";
    private static final String CONFIG_DIRECTORY_PATH = "src/main/resources/json/";
    private static final String TEMP_DIRECTORY_PATH = "src/main/resources/temp/";
    private static final String RESULTS_DIRECTORY_PATH = "src/main/resources/results/";
    private static final String METRICS_DIRECTORY_PATH = "src/main/resources/results/metrics/";
    private static final String LOG_DIRECTORY_PATH = "src/main/resources/log/";

    private ApplicationConfig config;
    private DatasetParameters datasetParameters;
    private IndexLogicNEW indexLogic;
    private QueryLogicNEW queryLogic;
    private StatisticsLogic statisticsLogic;
    private ResultQueryTotal globalQueryResults;
    private String tempDirectoryPath = TEMP_DIRECTORY_PATH;
    private String resultsDirectoryPath = RESULTS_DIRECTORY_PATH;
    private String metricsDirectoryPath = METRICS_DIRECTORY_PATH;
    private String logDirectoryPath = LOG_DIRECTORY_PATH;

    public static void main(String[] args) {
        AlternativeMain main = new AlternativeMain();

        if (args.length > 0 && "--list-configs".equals(args[0])) {
            main.listConfigsToConsole();
            return;
        }

        if (args.length > 0 && "--autonomous".equals(args[0])) {
            String configPath = args.length > 1 ? main.resolveRequestedConfigPath(args[1]) : DEFAULT_CONFIG_PATH;
            main.runAutonomousMode(configPath);
        } else {
            String configPath = args.length > 0 ? main.resolveRequestedConfigPath(args[0]) : DEFAULT_CONFIG_PATH;
            main.runInteractiveMode(configPath);
        }
    }

    /**
     * Autonomous mode: Load configuration and execute without user interaction
     */
    private void runAutonomousMode(String configPath) {
        logger.info("=== Starting Autonomous Mode ===");

        try {
            initializeSystem(configPath);
            buildIndexFromConfig();
            executeQueriesFromConfig();
            logger.info("=== Autonomous execution completed successfully ===");
        } catch (Exception e) {
            logger.error("Error in autonomous mode: {}", e.getMessage(), e);
        }
    }

    /**
     * Interactive mode: Load initial configuration but allow user selection
     */
    private void runInteractiveMode(String configPath) {
        logger.info("=== Starting Interactive Mode ===");

        try {
            initializeSystem(configPath);
            showMainMenu();
        } catch (Exception e) {
            logger.error("Error in interactive mode: {}", e.getMessage(), e);
        }
    }

    /**
     * Initialize system with configuration and setup directories
     */
//    private void initializeSystem(String configPath) throws IOException {
//        // Create directory structure
//        createDirectoryTree();
//        clearTempDirectory();
//        clearResultsDirectory();
//
//        // Load configuration
//        try {
//            config = ConfigLoader.loadFromJson(configPath);
//            logger.info("Configuration loaded from: {}", configPath);
//        } catch (IOException e) {
//            logger.warn("Failed to load configuration from {}, using defaults", configPath);
//            config = new ApplicationConfig();
//        }
//
//        // Initialize dataset parameters
//        Dataset dataset = convertDatasetEnum(config.getDataset().getDatasetType());
//        datasetParameters = ParametersFactory.getParameters(dataset);
//
//        // Initialize statistics logic
//        statisticsLogic = new StatisticsLogic(METRICS_DIRECTORY_PATH);
//
//        // Initialize index logic
//        indexLogic = new IndexLogicNEW(statisticsLogic, datasetParameters, config.getDataset().getUsagePercentage());
//
//        logger.info("System initialized successfully");
//        logConfiguration();
//    }

    private void initializeSystem(String configPath) {
        logger.info("Initializing system with config: {}", configPath);

        // Load configuration first to validate before directory operations
        try {
            config = ConfigLoader.loadFromJson(configPath);
            logger.info("Configuration loaded from: {}", configPath);
        } catch (IOException e) {
            logger.warn("Failed to load configuration from {}, using defaults", configPath);
            config = new ApplicationConfig();
        }

        // Validate configuration
        if (config == null) {
            config = new ApplicationConfig();
        }

        if (config.getPaths() != null) {
            tempDirectoryPath = config.getPaths().getTemp() != null ? config.getPaths().getTemp() : TEMP_DIRECTORY_PATH;
            resultsDirectoryPath = config.getPaths().getResults() != null ? config.getPaths().getResults() : RESULTS_DIRECTORY_PATH;
            metricsDirectoryPath = config.getPaths().getMetrics() != null ? config.getPaths().getMetrics() : METRICS_DIRECTORY_PATH;
            logDirectoryPath = config.getPaths().getLog() != null ? config.getPaths().getLog() : LOG_DIRECTORY_PATH;
        }

        // Create directory structure only after successful config load
        createDirectoryTree();
        clearTempDirectory();
        clearResultsDirectory();

        // Initialize dataset parameters with validation
        if (config.getDataset() == null || config.getDataset().getDatasetType() == null) {
            throw new IllegalStateException("Dataset configuration is incomplete");
        }

        Dataset dataset = convertDatasetEnum(config.getDataset().getDatasetType());
        datasetParameters = ParametersFactory.getParameters(dataset);

        // Initialize statistics logic
        statisticsLogic = new StatisticsLogic(metricsDirectoryPath, config.getCsvFormat());

        // Initialize index logic with validation
        double usagePercentage = config.getDataset().getUsagePercentage();
        if (usagePercentage <= 0 || usagePercentage > 1.0) {
            logger.warn("Invalid usage percentage: {}, using default 1.0", usagePercentage);
            usagePercentage = 1.0;
        }

        indexLogic = createIndexLogicWithSampling(usagePercentage);

        logger.info("System initialized successfully");
        logConfiguration();
    }

    /**
     * Build index based on current configuration
     */
    private void buildIndexFromConfig() {
        logger.info("Building index from configuration...");

        IndexConfig indexConfig = config.getIndex();

        // Build document store
        buildDocumentStore(indexConfig);

        // Build textual index
        buildTextualIndex(indexConfig);

        // Build spatial index
        buildSpatialIndex(indexConfig);

        // Initialize query logic
        queryLogic = new QueryLogicNEW(indexLogic, statisticsLogic, resultsDirectoryPath,
                datasetParameters, config.getExperiment().isWriteQueryResults());

        // Set query parameters
        setQueryParameters();
        // Keep legacy semantics: iterations are driven outside query evaluation loops.
        queryLogic.setExecutionIterations(1);

        logger.info("Index building completed");
    }

    /**
     * Execute queries based on configuration
     */
    protected void executeQueriesFromConfig() {
        ExperimentConfig expConfig = config.getExperiment();
        int iterations = getConfiguredIterations();
        setExecutionIterations(1);
        logger.info("Configured {} legacy-style iterations (full parameter sweep per iteration)", iterations);

        // Execute each query family as a contiguous block (not round-robin across families).
        if (expConfig.isRunAggregateQueries()) {
            executeAggregateQueries();
        }

        if (expConfig.isRunKnnQueries()) {
            executeKnnQueries();
        }

        if (expConfig.isRunRangeQueries()) {
            executeRangeQueries();
        }

        if (expConfig.isRunJoinQueries()) {
            executeJoinQueries();
        }

        // Stats are flushed per iteration/experiment in saveQueryResults().
    }

    protected void flushQueryStats() {
        queryLogic.printStats();
    }

    protected void setExecutionIterations(int iterations) {
        queryLogic.setExecutionIterations(iterations);
    }

    /**
     * Show interactive main menu
     */
    private void showMainMenu() {
        Scanner scanner = new Scanner(System.in);
        boolean exit = false;

        while (!exit) {
            System.out.println("\n=== Spatio-Textual Index System ===");
            System.out.println("Current Dataset: " + config.getDataset().getDatasetType().getDescription());
            System.out.println("Current Index: " + (indexLogic.getSpatialIndex() != null ?
                    config.getIndex().getSpatialIndexType().getDescription() : "None"));
            System.out.println("=========================================");
            System.out.println("1. Dataset Configuration");
            System.out.println("2. Index Configuration");
            System.out.println("3. Build Index");
            System.out.println("4. Query Execution");
            System.out.println("5. Batch Experiments");
            System.out.println("6. View Current Configuration");
            System.out.println("7. Save Configuration");
            System.out.println("8. Quick Load Configuration");
            System.out.println("0. Exit");
            System.out.println("=========================================");
            System.out.print("Select option: ");

            int choice = scanner.nextInt();

            switch (choice) {
                case 1: showDatasetMenu(); break;
                case 2: showIndexMenu(); break;
                case 3: buildIndexFromConfig(); break;
                case 4: showQueryMenu(); break;
                case 5: showExperimentMenu(); break;
                case 6: displayCurrentConfiguration(); break;
                case 7: saveConfiguration(); break;
                case 8: loadNewConfiguration(); break;
                case 0: exit = true; break;
                default: System.out.println("Invalid option. Please try again.");
            }
        }

        logger.info("Exiting interactive mode");
    }

    /**
     * Dataset selection menu
     */
    private void showDatasetMenu() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== Dataset Configuration ===");
        System.out.println("1. Postal Codes (171K)");
        System.out.println("2. Sports (1.75M)");
        System.out.println("3. Parks (9.96M)");
        System.out.println("4. Hotels (20K)");
        System.out.println("5. Test (10)");
        System.out.println("0. Back to main menu");
        System.out.print("Select dataset: ");

        int choice = scanner.nextInt();
        DatasetType newDataset;

        switch (choice) {
            case 1: newDataset = DatasetType.POSTAL_CODES; break;
            case 2: newDataset = DatasetType.SPORTS; break;
            case 3: newDataset = DatasetType.PARKS; break;
            case 4: newDataset = DatasetType.HOTELS; break;
            case 5: newDataset = DatasetType.TEST; break;
            case 0: return;
            default:
                System.out.println("Invalid option");
                return;
        }

        // Update configuration
        config.getDataset().setDatasetType(newDataset);

        // Ask for usage percentage
        System.out.print("Enter dataset usage percentage (10-100): ");
        double percentage = scanner.nextDouble() / 100.0;
        config.getDataset().setUsagePercentage(Math.max(0.1, Math.min(1.0, percentage)));

        // Reinitialize dataset parameters
        Dataset dataset = convertDatasetEnum(newDataset);
        datasetParameters = ParametersFactory.getParameters(dataset);
        indexLogic = createIndexLogicWithSampling(config.getDataset().getUsagePercentage());

        logger.info("Dataset changed to: {}", newDataset.getDescription());
    }

    /**
     * Index configuration menu
     */
    private void showIndexMenu() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== Index Configuration ===");
        System.out.println("1. Spatial Index Type");
        System.out.println("2. Data Structure Type");
        System.out.println("3. Textual Index Type");
        System.out.println("4. R-Tree Parameters");
        System.out.println("0. Back to main menu");
        System.out.print("Select configuration: ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1: configureSpatialIndex(); break;
            case 2: configureDataStructure(); break;
            case 3: configureTextualIndex(); break;
            case 4: configureRTreeParameters(); break;
            case 0: return;
            default: System.out.println("Invalid option");
        }
    }

    /**
     * Query execution menu
     */
    private void showQueryMenu() {
        if (indexLogic.getSpatialIndex() == null) {
            System.out.println("Please build an index first!");
            return;
        }

        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== Query Execution ===");
        System.out.println("1. Aggregate Queries");
        System.out.println("2. K-NN Queries");
        System.out.println("3. Range Queries");
        System.out.println("4. Join Queries");
        System.out.println("0. Back to main menu");
        System.out.print("Select query type: ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1: executeAggregateQueries(); break;
            case 2: executeKnnQueries(); break;
            case 3: executeRangeQueries(); break;
            case 4: executeJoinQueries(); break;
            case 0: return;
            default: System.out.println("Invalid option");
        }

        if (choice >= 1 && choice <= 4 && queryLogic != null) {
            queryLogic.printStats();
        }
    }

    // Helper methods for building components
    private void buildDocumentStore(IndexConfig indexConfig) {
        float smoothingFactor = indexConfig.getSmoothingFactor();
        if (smoothingFactor <= 0f || smoothingFactor > 1f) {
            logger.warn("Invalid smoothing factor: {}, using default 0.2", smoothingFactor);
            smoothingFactor = 0.2f;
        }

        switch (indexConfig.getDataStructureType()) {
            case HASHMAP:
                indexLogic.createHashMapDocStore(smoothingFactor);
                break;
            case TREEMAP:
                indexLogic.createTreeMapDocStore(smoothingFactor);
                break;
        }
    }

    private void buildTextualIndex(IndexConfig indexConfig) {
        int numClusters = indexConfig.getNumClusters();

        switch (indexConfig.getTextualIndexType()) {
            case INVERTED_LIST:
                indexLogic.createInvertedListIndex(numClusters);
                break;
            case SIGNED_INVERTED_LIST:
                indexLogic.createSignedInvertedListIndex(numClusters);
                break;
            case SIGNED_BLOCK:
                indexLogic.createSignedBlockTextualIndex();
                break;
        }
    }

    private void buildSpatialIndex(IndexConfig indexConfig) {
        int fanout = indexConfig.getFanout();
        float fillFactor = indexConfig.getFillFactor();
        int dimension = indexConfig.getDimension();
        int rtreeVariant = convertRTreeVariant(indexConfig.getRTreeVariant());
        int nearMinimumOverlapFactor = indexConfig.getNearMinimumOverlapFactor();
        int numMoves = indexConfig.getNumMoves();

        if (numMoves <= 0) {
            logger.warn("Invalid numMoves: {}, using default 300", numMoves);
            numMoves = 300;
        }

        switch (indexConfig.getSpatialIndexType()) {
            case IR:
                indexLogic.createIRTree(fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor);
                break;
            case IR_BULK:
                indexLogic.createIRTreeWithBulkLoading(fanout, fillFactor, dimension, rtreeVariant,
                        nearMinimumOverlapFactor, indexConfig.getBulkLoadMethod());
                break;
            case DIR:
                indexLogic.createDIRTree(fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor,
                        indexConfig.getMaxWord(), indexConfig.getBetaArea());
                break;
            case CIR:
                indexLogic.createCIRTree(fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor,
                        indexConfig.getNumClusters(), numMoves);
                break;
            case CDIR:
                indexLogic.createCDIRTree(fanout, fillFactor, dimension, rtreeVariant, nearMinimumOverlapFactor,
                        indexConfig.getMaxWord(), indexConfig.getBetaArea(),
                        indexConfig.getNumClusters(), numMoves);
                break;
        }
    }

    // Query execution methods
    protected void executeAggregateQueries() {
        ExperimentConfig expConfig = config.getExperiment();
        int iterations = getConfiguredIterations();
        setExecutionIterations(1);

        if (expConfig.getAggregateExperiments() == null || expConfig.getAggregateExperiments().isEmpty()) {
            logger.warn("No aggregate experiments configured");
            return;
        }

        globalQueryResults = new ResultQueryTotal("Aggregate");
        queryLogic.setQueryResults(globalQueryResults);

        for (AggregateExperiment experiment : expConfig.getAggregateExperiments()) {
            // Get aggregator type from experiment
            String aggregatorType = experiment.getAggregateFunctions().get(0);
            IAggregator aggregator = AggregatorFactory.getAggregator(aggregatorType);

            // Convert query types
            List<QueryLogicNEW.AggregateQueryType> types = new ArrayList<>();
            for (String typeStr : experiment.getQueryTypes()) {
                try {
                    QueryLogicNEW.AggregateQueryType type = QueryLogicNEW.AggregateQueryType.valueOf(typeStr);
                    types.add(type);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown aggregate query type: {}, skipping", typeStr);
                }
            }

            QueryLogicNEW.AggregateQueryType[] typeArray = types.toArray(new QueryLogicNEW.AggregateQueryType[0]);

            // Convert varyParameter to QueryType
            ArrayList<QueryLogicNEW.QueryType> queryTypes = new ArrayList<>();
            QueryLogicNEW.QueryType varyParam = convertStringToQueryType(experiment.getVaryParameter());
            if (varyParam != null) {
                queryTypes.add(varyParam);
            } else {
                logger.warn("Unknown varyParameter: {}, defaulting to 'Defaults'", experiment.getVaryParameter());
                queryTypes.add(QueryLogicNEW.QueryType.Defaults);
            }

            for (int i = 0; i < iterations; i++) {
                queryLogic.processAggregateQuery(typeArray, queryTypes, aggregator, experiment);
                logger.info("Aggregate experiment iteration {}/{} executed - Functions: {}, Types: {}, VaryParam: {}",
                        i + 1, iterations, experiment.getAggregateFunctions(), experiment.getQueryTypes(), experiment.getVaryParameter());
                saveQueryResults("Aggregate");
            }
        }
    }

    protected void executeKnnQueries() {
        ExperimentConfig expConfig = config.getExperiment();
        int iterations = getConfiguredIterations();
        setExecutionIterations(1);

        if (expConfig.getKnnExperiments() == null || expConfig.getKnnExperiments().isEmpty()) {
            logger.warn("No KNN experiments configured");
            return;
        }

        globalQueryResults = new ResultQueryTotal("KNN");
        queryLogic.setQueryResults(globalQueryResults);

        for (KnnExperiment experiment : expConfig.getKnnExperiments()) {
            // Convert query types
            List<QueryLogicNEW.KnnQueryType> types = new ArrayList<>();
            for (String typeStr : experiment.getQueryTypes()) {
                try {
                    QueryLogicNEW.KnnQueryType type = QueryLogicNEW.KnnQueryType.valueOf(typeStr);
                    types.add(type);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown KNN query type: {}, skipping", typeStr);
                }
            }

            QueryLogicNEW.KnnQueryType[] typeArray = types.toArray(new QueryLogicNEW.KnnQueryType[0]);

            // Convert varyParameter to QueryType
            ArrayList<QueryLogicNEW.QueryType> queryTypes = new ArrayList<>();
            QueryLogicNEW.QueryType varyParam = convertStringToQueryType(experiment.getVaryParameter());
            if (varyParam != null) {
                queryTypes.add(varyParam);
            } else {
                queryTypes.add(QueryLogicNEW.QueryType.TopK);
            }

            for (int i = 0; i < iterations; i++) {
                queryLogic.processKnnQuery(typeArray, queryTypes, experiment);
                logger.info("KNN experiment iteration {}/{} executed - Types: {}, VaryParam: {}",
                        i + 1, iterations, experiment.getQueryTypes(), experiment.getVaryParameter());
                saveQueryResults("Knn");
            }
        }
    }

    protected void executeRangeQueries() {
        ExperimentConfig expConfig = config.getExperiment();
        int iterations = getConfiguredIterations();
        setExecutionIterations(1);

        if (expConfig.getRangeExperiments() == null || expConfig.getRangeExperiments().isEmpty()) {
            logger.warn("No range experiments configured");
            return;
        }

        globalQueryResults = new ResultQueryTotal("Range");
        queryLogic.setQueryResults(globalQueryResults);

        for (RangeExperiment experiment : expConfig.getRangeExperiments()) {
            // Convert query types
            List<QueryLogicNEW.RangeQueryType> types = new ArrayList<>();
            for (String typeStr : experiment.getQueryTypes()) {
                try {
                    QueryLogicNEW.RangeQueryType type = QueryLogicNEW.RangeQueryType.valueOf(typeStr);
                    types.add(type);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown range query type: {}, skipping", typeStr);
                }
            }

            QueryLogicNEW.RangeQueryType[] typeArray = types.toArray(new QueryLogicNEW.RangeQueryType[0]);

            // Convert varyParameter to QueryType
            ArrayList<QueryLogicNEW.QueryType> queryTypes = new ArrayList<>();
            QueryLogicNEW.QueryType varyParam = convertStringToQueryType(experiment.getVaryParameter());
            if (varyParam != null) {
                queryTypes.add(varyParam);
            } else {
                queryTypes.add(QueryLogicNEW.QueryType.Radius);
            }

            for (int i = 0; i < iterations; i++) {
                queryLogic.processRangeQuery(typeArray, queryTypes, experiment);
                logger.info("Range experiment iteration {}/{} executed - Types: {}, VaryParam: {}",
                        i + 1, iterations, experiment.getQueryTypes(), experiment.getVaryParameter());
                saveQueryResults("Range");
            }
        }
    }

    protected void executeJoinQueries() {
        ExperimentConfig expConfig = config.getExperiment();
        int iterations = getConfiguredIterations();
        setExecutionIterations(1);

        if (expConfig.getJoinExperiments() == null || expConfig.getJoinExperiments().isEmpty()) {
            logger.warn("No join experiments configured");
            return;
        }

        globalQueryResults = new ResultQueryTotal("JOIN");
        queryLogic.setQueryResults(globalQueryResults);

        for (JoinExperiment experiment : expConfig.getJoinExperiments()) {
            validateJoinExperimentConfiguration(experiment);

            // Convert query types
            List<QueryLogicNEW.JoinQueryType> types = new ArrayList<>();
            for (String typeStr : experiment.getQueryTypes()) {
                try {
                    QueryLogicNEW.JoinQueryType type = QueryLogicNEW.JoinQueryType.valueOf(typeStr);
                    types.add(type);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown join query type: {}, skipping", typeStr);
                }
            }

            QueryLogicNEW.JoinQueryType[] typeArray = types.toArray(new QueryLogicNEW.JoinQueryType[0]);

            // Convert varyParameter to QueryType
            ArrayList<QueryLogicNEW.QueryType> queryTypes = new ArrayList<>();
            QueryLogicNEW.QueryType varyParam = convertStringToQueryType(experiment.getVaryParameter());
            if (varyParam != null) {
                queryTypes.add(varyParam);
            } else {
                queryTypes.add(resolveDefaultJoinVaryParameter(experiment));
            }

            for (int i = 0; i < iterations; i++) {
                queryLogic.processJoinQuery(typeArray, queryTypes, experiment);
                logger.info("Join experiment iteration {}/{} executed - Types: {}, Algorithm: {}, JoinStrategy: {}, SimilarityType: {}, ThresholdPolicy: {}, QueryStrategy: {}, VaryParam: {}",
                        i + 1, iterations, experiment.getQueryTypes(), experiment.getAlgorithm(), experiment.getJoinStrategy(), experiment.getSimilarityType(), experiment.getThresholdPolicy(), experiment.getQueryStrategy(), experiment.getVaryParameter());
                saveQueryResults("JOIN");
            }
        }
    }

    private void validateJoinExperimentConfiguration(JoinExperiment experiment) {
        if (experiment == null || experiment.getQueryTypes() == null || experiment.getQueryTypes().isEmpty()) {
            return;
        }

        boolean requiresSecondaryContext = false;
        for (String queryType : experiment.getQueryTypes()) {
            if ("STSJ_MULTISET".equalsIgnoreCase(queryType)) {
                requiresSecondaryContext = true;
                break;
            }
        }

        if (!requiresSecondaryContext) {
            return;
        }

        if (experiment.getSecondaryDataset() == null || experiment.getSecondaryDataset().getDatasetType() == null) {
            throw new IllegalArgumentException("joinExperiments[].secondaryDataset.datasetType is required for STSJ_MULTISET");
        }

        if (experiment.getSecondaryIndex() == null) {
            throw new IllegalArgumentException("joinExperiments[].secondaryIndex is required for STSJ_MULTISET");
        }
    }

    private int getConfiguredIterations() {
        return Math.max(1, config.getExperiment().getNumIterations());
    }

    QueryLogicNEW.QueryType resolveDefaultJoinVaryParameter(JoinExperiment experiment) {
        if (experiment != null && experiment.getQueryTypes() != null) {
            for (String queryType : experiment.getQueryTypes()) {
                if (queryType == null) {
                    continue;
                }
                if ("TOPK_STSJ".equalsIgnoreCase(queryType)
                        || "TOPK_STSJ_EX".equalsIgnoreCase(queryType)) {
                    return QueryLogicNEW.QueryType.TopK;
                }
            }
        }
        return QueryLogicNEW.QueryType.SpatialDistance;
    }

    // Utility methods
    private void setQueryParameters() {
        QueryConfig queryConfig = config.getQuery();

        queryLogic.setAllParameters(
                queryConfig.getGroupSizes(), queryConfig.getGroupSizeDefault(),
                queryConfig.getMPercentages(), queryConfig.getMPercentageDefault(),
                queryConfig.getNumberOfKeywords(), queryConfig.getNumberOfKeywordsDefault(),
                queryConfig.getSpaceAreaPercentages(), queryConfig.getSpaceAreaPercentageDefault(),
                queryConfig.getKeywordSpaceSizePercentages(), queryConfig.getKeywordSpaceSizePercentageDefault(),
                queryConfig.getTopKValues(), queryConfig.getTopKDefault(),
                queryConfig.getAlphaValues(), queryConfig.getAlphaDefault(),
                queryConfig.getRadiusValues(), queryConfig.getRadiusDefault(),
                queryConfig.getSpatialDistance(), queryConfig.getSpatialDistanceDefault(),
                queryConfig.getTextualSimilarity(), queryConfig.getTextualSimilarityDefault(),
                queryConfig.getRankingSumModeDefault(),
                queryConfig.getNumberOfQueries()
        );
    }

    private Dataset convertDatasetEnum(DatasetType datasetType) {
        switch (datasetType) {
            case POSTAL_CODES: return Dataset.POSTAL_CODES_SET;
            case SPORTS: return Dataset.SPORTS_SET;
            case PARKS: return Dataset.PARKS_SET;
            case HOTELS: return Dataset.HOTEL_SET;
            case TEST: return Dataset.TESTING_SET;
            default: throw new IllegalArgumentException("Unknown dataset type: " + datasetType);
        }
    }

    private int convertRTreeVariant(RTreeVariant variant) {
        switch (variant) {
            case LINEAR: return SpatialIndex.RtreeVariantLinear; // SpatialIndex.RtreeVariantLinear
            case QUADRATIC: return SpatialIndex.RtreeVariantQuadratic; // SpatialIndex.RtreeVariantQuadratic
            case RSTAR: return SpatialIndex.RtreeVariantRstar; // SpatialIndex.RtreeVariantRstar
            default: return SpatialIndex.RtreeVariantRstar;
        }
    }

    private QueryLogicNEW.QueryType convertStringToQueryType(String parameterName) {
        if (parameterName == null) {
            return null;
        }

        switch (parameterName.toLowerCase()) {
            case "groupsize":
                return QueryLogicNEW.QueryType.GroupSize;
            case "percentage":
                return QueryLogicNEW.QueryType.Percentage;
            case "numberofkeywords":
                return QueryLogicNEW.QueryType.NumberOfKeywords;
            case "queryspaceareapercentage":
                return QueryLogicNEW.QueryType.SpaceAreaPercentage;
            case "keywordspacesizepercentage":
                return QueryLogicNEW.QueryType.KeywordSpaceSizePercentage;
            case "topk":
                return QueryLogicNEW.QueryType.TopK;
            case "alpha":
                return QueryLogicNEW.QueryType.Alpha;
            case "radius":
                return QueryLogicNEW.QueryType.Radius;
            case "spatialdistance":
                return QueryLogicNEW.QueryType.SpatialDistance;
            case "textualsimilarity":
                return QueryLogicNEW.QueryType.TextualSimilarity;
            case "defaults":
                return QueryLogicNEW.QueryType.Defaults;
            case "combined":
                return QueryLogicNEW.QueryType.Combined;
            default:
                logger.warn("Unknown parameter name: {}", parameterName);
                return null;
        }
    }

    // Configure spatial index type selection
    private void configureSpatialIndex() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== Spatial Index Configuration ===");
        System.out.println("1. IR-Tree");
        System.out.println("2. IR-Tree with Bulk Loading");
        System.out.println("3. DIR-Tree");
        System.out.println("4. CIR-Tree");
        System.out.println("5. CDIR-Tree");
        System.out.println("0. Back");
        System.out.print("Select spatial index type: ");

        int choice = scanner.nextInt();
        SpatialIndexType newType;

        switch (choice) {
            case 1: newType = SpatialIndexType.IR; break;
            case 2: newType = SpatialIndexType.IR_BULK; break;
            case 3: newType = SpatialIndexType.DIR; break;
            case 4: newType = SpatialIndexType.CIR; break;
            case 5: newType = SpatialIndexType.CDIR; break;
            case 0: return;
            default:
                System.out.println("Invalid option");
                return;
        }

        config.getIndex().setSpatialIndexType(newType);
        logger.info("Spatial index type changed to: {}", newType.getDescription());
    }

    /**
     * Configure data structure type selection
     */
    private void configureDataStructure() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== Data Structure Configuration ===");
        System.out.println("1. HashMap");
        System.out.println("2. TreeMap");
        System.out.println("0. Back");
        System.out.print("Select data structure type: ");

        int choice = scanner.nextInt();
        DataStructureType newType;

        switch (choice) {
            case 1: newType = DataStructureType.HASHMAP; break;
            case 2: newType = DataStructureType.TREEMAP; break;
            case 0: return;
            default:
                System.out.println("Invalid option");
                return;
        }

        config.getIndex().setDataStructureType(newType);
        logger.info("Data structure type changed to: {}", newType.getDescription());
    }

    /**
     * Configure textual index type selection
     */
    private void configureTextualIndex() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== Textual Index Configuration ===");
        System.out.println("1. Inverted List (HashMap)");
        System.out.println("2. Signed Inverted List (Bloom + Stats)");
        System.out.println("3. Signed Block");
        System.out.println("0. Back");
        System.out.print("Select textual index type: ");

        int choice = scanner.nextInt();
        TextualIndexType newType;

        switch (choice) {
            case 1: newType = TextualIndexType.INVERTED_LIST; break;
//            case 2: newType = TextualIndexType.SIGNED_INVERTED_LIST; break;
//            case 3: newType = TextualIndexType.SIGNED_BLOCK; break;
            case 0: return;
            default:
                System.out.println("Invalid option");
                return;
        }

        config.getIndex().setTextualIndexType(newType);
        logger.info("Textual index type changed to: {}", newType.getDescription());
    }

    /**
     * Configure R-Tree specific parameters
     */
    private void configureRTreeParameters() {
        Scanner scanner = new Scanner(System.in);
        IndexConfig indexConfig = config.getIndex();

        System.out.println("\n=== R-Tree Parameters Configuration ===");
        System.out.println("Current values:");
        System.out.println("Fanout: " + indexConfig.getFanout());
        System.out.println("Fill Factor: " + indexConfig.getFillFactor());
        System.out.println("Dimension: " + indexConfig.getDimension());
        System.out.println("R-Tree Variant: " + indexConfig.getRTreeVariant());
        System.out.println("=====================================");

        System.out.print("Enter new fanout (current: " + indexConfig.getFanout() + "): ");
        int fanout = scanner.nextInt();
        indexConfig.setFanout(fanout);

        System.out.print("Enter new fill factor (0.1-1.0, current: " + indexConfig.getFillFactor() + "): ");
        String fillFactorInput = scanner.next().trim().replace(',', '.');
        float fillFactor = Float.parseFloat(fillFactorInput);
        indexConfig.setFillFactor(Math.max(0.1f, Math.min(1.0f, fillFactor)));

        System.out.print("Enter dimension (current: " + indexConfig.getDimension() + "): ");
        int dimension = scanner.nextInt();
        indexConfig.setDimension(dimension);

        System.out.println("Select R-Tree variant:");
        System.out.println("1. Linear");
        System.out.println("2. Quadratic");
        System.out.println("3. R*-Tree");
        System.out.print("Choice: ");
        int variantChoice = scanner.nextInt();

        switch (variantChoice) {
            case 1: indexConfig.setRTreeVariant(RTreeVariant.LINEAR); break;
            case 2: indexConfig.setRTreeVariant(RTreeVariant.QUADRATIC); break;
            case 3: indexConfig.setRTreeVariant(RTreeVariant.RSTAR); break;
            default: System.out.println("Invalid choice, keeping current variant");
        }

        logger.info("R-Tree parameters updated");
    }


    /**
     * Show batch experiment configuration and execution menu
     */
    private void showExperimentMenu() {
        if (indexLogic.getSpatialIndex() == null) {
            System.out.println("Please build an index first!");
            return;
        }

        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== Batch Experiments ===");
        System.out.println("1. Run All Configured Experiments");
        System.out.println("2. Configure Experiment Parameters");
        System.out.println("3. Run Specific Query Type Experiments");
        System.out.println("0. Back to main menu");
        System.out.print("Select option: ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1: runAllExperiments(); break;
            case 2: configureExperiments(); break;
            case 3: runSpecificExperiments(); break;
            case 0: return;
            default: System.out.println("Invalid option");
        }
    }

    private void displayCurrentConfiguration() {
        logger.info("=== Current Configuration ===");
        logger.info("Dataset: {}", config.getDataset().getDatasetType().getDescription());
        logger.info("Usage: {}%", config.getDataset().getUsagePercentage() * 100);
        logger.info("Spatial Index: {}", config.getIndex().getSpatialIndexType().getDescription());
        logger.info("Data Structure: {}", config.getIndex().getDataStructureType().getDescription());
        logger.info("Textual Index: {}", config.getIndex().getTextualIndexType().getDescription());
    }

    /**
     * Save current configuration to JSON file
     */
    private void saveConfiguration() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter filename to save configuration (without .json extension): ");
        String filename = scanner.nextLine();

        try {
            String filepath = CONFIG_DIRECTORY_PATH + filename + ".json";
            ConfigLoader.saveToJson(config, filepath);
            System.out.println("Configuration saved to: " + filepath);
            logger.info("Configuration saved to: {}", filepath);
        } catch (IOException e) {
            System.out.println("Failed to save configuration: " + e.getMessage());
            logger.error("Failed to save configuration", e);
        }
    }

    /**
     * Load new configuration from file
     */
    private void loadNewConfiguration() {
        Scanner scanner = new Scanner(System.in);

        List<String> configs = ConfigCatalog.listJsonConfigs(CONFIG_DIRECTORY_PATH);
        if (configs.isEmpty()) {
            System.out.print("No config presets found. Enter configuration file path: ");
        } else {
            System.out.println("Available presets:");
            for (int i = 0; i < configs.size(); i++) {
                System.out.println((i + 1) + ". " + configs.get(i));
            }
            System.out.print("Choose preset number or enter config name/path: ");
        }

        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            input = scanner.nextLine().trim();
        }
        String configPath = resolveConfigSelection(input, configs);

        try {
            config = ConfigLoader.loadFromJson(configPath);

            if (config.getPaths() != null) {
                tempDirectoryPath = config.getPaths().getTemp() != null ? config.getPaths().getTemp() : TEMP_DIRECTORY_PATH;
                resultsDirectoryPath = config.getPaths().getResults() != null ? config.getPaths().getResults() : RESULTS_DIRECTORY_PATH;
                metricsDirectoryPath = config.getPaths().getMetrics() != null ? config.getPaths().getMetrics() : METRICS_DIRECTORY_PATH;
                logDirectoryPath = config.getPaths().getLog() != null ? config.getPaths().getLog() : LOG_DIRECTORY_PATH;
            }

            // Reinitialize system with new configuration
            Dataset dataset = convertDatasetEnum(config.getDataset().getDatasetType());
            datasetParameters = ParametersFactory.getParameters(dataset);
            indexLogic = createIndexLogicWithSampling(config.getDataset().getUsagePercentage());
            statisticsLogic.setCsvFormatConfig(config.getCsvFormat());

            System.out.println("Configuration loaded successfully from: " + configPath);
            logger.info("New configuration loaded from: {}", configPath);
            logConfiguration();
        } catch (IOException e) {
            System.out.println("Failed to load configuration: " + e.getMessage());
            logger.error("Failed to load configuration from: {}", configPath, e);
        }
    }

    private void logConfiguration() {
        logger.info("=== Configuration Loaded ===");
        logger.info("Dataset: {}", config.getDataset().getDatasetType().getDescription());
        logger.info("Usage: {}%", config.getDataset().getUsagePercentage() * 100);
        logger.info("Sampling: method={}, seed={}, startLine={}",
                config.getDataset().getSamplingMethod(),
                config.getDataset().getSamplingRandomSeed(),
                config.getDataset().getSamplingStartLine());
        logger.info("Spatial Index: {}", config.getIndex().getSpatialIndexType().getDescription());
        logger.info("Data Structure: {}", config.getIndex().getDataStructureType().getDescription());
        logger.info("Textual Index: {}", config.getIndex().getTextualIndexType().getDescription());
    }

    private IndexLogicNEW createIndexLogicWithSampling(double usagePercentage) {
        if (usagePercentage <= 0 || usagePercentage > 1.0) {
            logger.warn("Invalid usage percentage: {}, using default 1.0", usagePercentage);
            usagePercentage = 1.0;
            config.getDataset().setUsagePercentage(usagePercentage);
        }
        if (config.getDataset().getSamplingMethod() == null) {
            logger.warn("Missing sampling method, using RANDOMIZED");
            config.getDataset().setSamplingMethod(org.ual.utils.sampling.SamplingStrategy.SamplingMethod.RANDOMIZED);
        }
        if (config.getDataset().getSamplingStartLine() < 0) {
            logger.warn("Invalid samplingStartLine: {}, using default 0", config.getDataset().getSamplingStartLine());
            config.getDataset().setSamplingStartLine(0);
        }

        return new IndexLogicNEW(
                statisticsLogic,
                datasetParameters,
                usagePercentage,
                config.getDataset().getSamplingMethod(),
                config.getDataset().getSamplingRandomSeed(),
                config.getDataset().getSamplingStartLine()
        );
    }

    // Additional helper methods for experiment management

    private void runAllExperiments() {
        logger.info("Starting batch experiments...");
        executeQueriesFromConfig();
        System.out.println("All experiments completed. Check results directory for output.");
    }

    private void configureExperiments() {
        Scanner scanner = new Scanner(System.in);
        ExperimentConfig expConfig = config.getExperiment();

        System.out.println("\n=== Experiment Configuration ===");
        System.out.print("Number of iterations (current: " + expConfig.getNumIterations() + "): ");
        int iterations = scanner.nextInt();
        expConfig.setNumIterations(iterations);

        System.out.print("Run aggregate queries? (true/false, current: " + expConfig.isRunAggregateQueries() + "): ");
        boolean runAgg = scanner.nextBoolean();
        expConfig.setRunAggregateQueries(runAgg);

        System.out.print("Run KNN queries? (true/false, current: " + expConfig.isRunKnnQueries() + "): ");
        boolean runKnn = scanner.nextBoolean();
        expConfig.setRunKnnQueries(runKnn);

        System.out.print("Run range queries? (true/false, current: " + expConfig.isRunRangeQueries() + "): ");
        boolean runRange = scanner.nextBoolean();
        expConfig.setRunRangeQueries(runRange);

        System.out.print("Run join queries? (true/false, current: " + expConfig.isRunJoinQueries() + "): ");
        boolean runJoin = scanner.nextBoolean();
        expConfig.setRunJoinQueries(runJoin);

        System.out.print("Write query results to files? (true/false, current: " + expConfig.isWriteQueryResults() + "): ");
        boolean writeResults = scanner.nextBoolean();
        expConfig.setWriteQueryResults(writeResults);

        logger.info("Experiment configuration updated");
    }

    private void runSpecificExperiments() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== Specific Experiments ===");
        System.out.println("1. Aggregate Queries Only");
        System.out.println("2. KNN Queries Only");
        System.out.println("3. Range Queries Only");
        System.out.println("4. Join Queries Only");
        System.out.print("Select experiment type: ");

        int choice = scanner.nextInt();

        switch (choice) {
            case 1: executeAggregateQueries(); break;
            case 2: executeKnnQueries(); break;
            case 3: executeRangeQueries(); break;
            case 4: executeJoinQueries(); break;
            default: System.out.println("Invalid option");
                return;
        }

        if (queryLogic != null) {
            queryLogic.printStats();
        }
    }

    private void saveQueryResults(String queryType) {
        if (globalQueryResults != null) {
            logger.info("Query batch '{}' completed. Flushing metrics before next experiment.", queryType);
            flushQueryStats();
        }
    }

    // Directory management methods (copied from OldMain)
    private void createDirectoryTree() {
        try {
            logger.info("Creating directory tree...");
            Files.createDirectories(Paths.get(metricsDirectoryPath));
            Files.createDirectories(Paths.get(tempDirectoryPath));
            Files.createDirectories(Paths.get(logDirectoryPath));
            logger.info("Directory tree created successfully");
        } catch (IOException e) {
            logger.error("Failed to create directories", e);
            throw new RuntimeException(e);
        }
    }

    private void clearTempDirectory() {
        File file = new File(tempDirectoryPath);
        logger.info("Clearing temp directory...");
        deleteFilesInPath(file);
        logger.info("Temp directory cleared");
    }

    private void clearResultsDirectory() {
        File file = new File(resultsDirectoryPath);
        logger.info("Clearing results directory...");
        deleteFilesInPath(file);
        logger.info("Results directory cleared");
    }

    private void deleteFilesInPath(File file) {
        if (file.isDirectory()) {
            String[] dirFiles = file.list();
            if (dirFiles != null) {
                for (String filePath : dirFiles) {
                    File dirFile = new File(file, filePath);
                    if (!dirFile.isDirectory()) {
                        if (!dirFile.delete()) {
                            logger.warn("Failed to delete file: {}", dirFile.getPath());
                        }
                    } else {
                        deleteFilesInPath(dirFile);
                    }
                }
            }
        }
    }

    private String resolveRequestedConfigPath(String requestedConfig) {
        return ConfigCatalog.resolveConfigPath(requestedConfig, CONFIG_DIRECTORY_PATH, DEFAULT_CONFIG_PATH);
    }

    private String resolveConfigSelection(String input, List<String> configs) {
        if (input == null || input.isEmpty()) {
            return DEFAULT_CONFIG_PATH;
        }

        try {
            int selected = Integer.parseInt(input);
            if (selected >= 1 && selected <= configs.size()) {
                return CONFIG_DIRECTORY_PATH + configs.get(selected - 1);
            }
        } catch (NumberFormatException ignored) {
            // Non-numeric input is treated as file/config name.
        }

        return resolveRequestedConfigPath(input);
    }

    private void listConfigsToConsole() {
        List<String> configs = ConfigCatalog.listJsonConfigs(CONFIG_DIRECTORY_PATH);
        if (configs.isEmpty()) {
            System.out.println("No JSON configs found in " + CONFIG_DIRECTORY_PATH);
            return;
        }

        System.out.println("Available JSON configs:");
        for (String configName : configs) {
            System.out.println("- " + configName);
        }
    }
}

package org.ual;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.spatialindex.rtree.RTree;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.utils.ResultQueryTotal;
import org.ual.utils.main.IndexLogic;
import org.ual.utils.main.QueryLogic;
import org.ual.utils.main.StatisticsLogic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

public class OldMain {
    static int fanout = 25;
    static float fillFactor = 0.7f;
    static int dimension = 2;
    static float betaArea = 0.5f;   // RtreeEnhanced betaArea 0.9
    static int maxWord = 4; // RtreeEnhanced maxWord 10000
    static int numClusters = 8; // CIRtree clusters
    static int numMoves = 300; // KMean numMoves 4
    static int rTreeVariant = SpatialIndex.RtreeVariantRstar;
    static int nearMinimumOverlapFactor = (int)(fanout * 0.35f);
    static float smoothingFactor = 0.2f; // Smoothing factor for term weights

//    static String keywordsFilePath = "src/main/resources/data/postal_doc.txt";
//    static String locationsFilePath = "src/main/resources/data/postal_loc.txt";
//    static String keywordsFilePath = "src/main/resources/data/sports_doc.txt";
//    static String locationsFilePath = "src/main/resources/data/sports_loc.txt";
//    static String keywordsFilePath = "src/main/resources/data/hotel_doc";
//    static String locationsFilePath = "src/main/resources/data/hotel_loc";
//    static String keywordsFilePath = "src/main/resources/data/key_test.txt";
//    static String locationsFilePath = "src/main/resources/data/loc_test.txt";
//    static String keywordsFilePath = "src/main/resources/data/keywords.txt";
//    static String locationsFilePath = "src/main/resources/data/locations.txt";

    static DatasetParameters parameters;
    static Dataset dataset;
    static boolean writeQueryResults = false; // Write query results to disk

    // Results and Temp paths
    static String tempDirectoryPath = "src/main/resources/temp/";
    static String resultsDirectoryPath = "src/main/resources/results/";
    static String metricsDirectoryPath = "src/main/resources/results/metrics/";
    static String logDirectoryPath = "src/main/resources/log/";

    // Datastructures
    enum DataStructureType {
        HashMap,
        TreeMap
    }
    static DataStructureType selectedDataStructure;

    enum TextualIndexType {
        HashMap,
        ArrayList
    }
    static TextualIndexType selectedTextualIndex;

    // Spatial Index Type
    enum SpatialIndexType {
        IR,
        IR_Bulk,
        DIR,
        CIR,
        CDIR
    }
    static SpatialIndexType selectedSpatialIndex;

    // BulkLoader Method
    static BulkLoadMethod bulkLoadMethod = BulkLoadMethod.STR;

    enum QueryTypeGroup {
        AGGREGATE,
        RANGE,
        KNN,
        JOIN
    }
    static QueryTypeGroup selectedQueryTypeGroup;

    static IAggregator selectedAggregator;

    // Specify aggregate query types to use
    static QueryLogic.AggregateQueryType[] aggregateQueryTypes = {
            QueryLogic.AggregateQueryType.GNNK,
            QueryLogic.AggregateQueryType.SGNNK};

    // Specify knn query types to use
    static QueryLogic.KnnQueryType[] knnQueryTypes = {
            QueryLogic.KnnQueryType.BkSK,
            QueryLogic.KnnQueryType.TkSK };

    // Specify range query types to use
    static QueryLogic.RangeQueryType[] rangeQueryTypes = {
            QueryLogic.RangeQueryType.BRSK };

    // Specify join query types to use
    static QueryLogic.JoinQueryType[] joinQueryTypes = {
            QueryLogic.JoinQueryType.STSJ
    };

    enum QueryParameters {
        GroupSize,
        PercentQuery,
        NumberOfKeywords,
        SpaceAreaPercentage,
        KeywordSpaceSizePercentage,
        TopK,
        Alpha,
        Radius,
        SpatialDistance,
        TextualSimilarity,
        CombinedST, // Combined Spatial and Textual
        All
    }
    static QueryParameters selectedQueryParameter;

    // Number of keywords
    static int numberOfQueries = 20;//

    static int[] groupSizes = {10, 20, 40, 60, 80}; // Group Size
    static int groupSizeDefault = 10;
    static int[] mPercentages = {40, 50, 60, 70, 80};
    static int mPercentageDefault = 60;
    static int[] numberOfKeywords = {1, 2, 4, 8, 10};
    static int numberOfKeywordsDefault = 2;// 4
    static double[] querySpaceAreaPercentages = {.001, .01, .02, .03, .04, .05};
    static double querySpaceAreaPercentageDefault = 0.01;
    static int[] keywordSpaceSizePercentages = {1, 2, 3, 4, 5};
    static int keywordSpaceSizePercentageDefault = 3;
    static int[] topks = {1, 10, 20, 30, 40, 50};
    //static int[] topks = {1, 10, 100, 200, 400, 600, 800, 1000};
    static int topkDefault = 10;
    //static int[] topks = {10, 50, 90, 130, 170, 200};
    //static int topkDefault = 90;
    static double[] alphas = {0.1, 0.3, 0.5, 0.7, 0.9};
    static double alphaDefault = 0.5;
    static float[] radius = {1f, 2f, 5f, 10f, 20f};
    //static float[] radius = {1f, 10f, 20f, 40f, 60f, 80f, 100f, 120f};
    //static float[] radius = {1f, 2f, 4f, 6f, 8f, 10f, 12f, 14f, 16f, 18f, 20f, 40f, 60f, 80f, 100f, 120f};
    static float radiusDefault = 10f;

    // TODO JOIN variables
    //static float[] spatialDistance = {1f, 2f, 5f, 10f, 20f};
    static float[] spatialDistance = {0.001f, 0.005f, 0.01f, 0.05f, 0.1f};
    static float spatialDistanceDefault = 0.01f;
    //static float spatialDistanceDefault = 0.01f;
    static float[] textualSimilarity = {0.1f, 0.3f, 0.5f, 0.7f, 0.9f};//{0.1f, 0.3f, 0.5f, 0.7f, 0.9f};
    static float textualSimilarityDefault = 0.3f; //0.5f

    // TODO NEW variables
    static double datasetUsagePercentage = 1.0; // Use 100% by default

    static ResultQueryTotal globalQueryResults;

    private static final Logger logger = LogManager.getLogger(OldMain.class);


    public static void main(String[] argv) {
        boolean runLegacyMode = argv != null && argv.length > 0 && "--legacy".equalsIgnoreCase(argv[0]);
//        if (!runLegacyMode) {
//            logger.warn("OldMain is deprecated. Delegating execution to AlternativeMain. Use --legacy to run old flow.");
//            if (argv == null || argv.length == 0) {
//                AlternativeMain.main(new String[]{"--autonomous"});
//            } else {
//                AlternativeMain.main(argv);
//            }
//            return;
//        }

        logger.warn("Running legacy OldMain flow because --legacy was provided.");

        // ****************************************************** //
        //                    CLEANING DATA                       //
        // ****************************************************** //
        // Create directories (log, temp and results) if not present
        createDirectoryTree();

        // Clear old indexes from temp directory
        clearTempDirectory();

        // Clear results directory
        clearResultsDirectory();

        // ****************************************************** //
        //                    PROCESSING DATA                     //
        // ****************************************************** //

        // Select Dataset
        chooseDataSetMenu();

        // Select Dataset Usage Percentage
        chooseDatasetPercentageMenu();

        // Init Statistics Logic
        StatisticsLogic statisticsLogic = new StatisticsLogic(metricsDirectoryPath);

        // Init Index Logic
        IndexLogic indexLogic = new IndexLogic(statisticsLogic, parameters, datasetUsagePercentage);

        // Select Document Index Structure Type & Compute weights and store in memory
        chooseDocumentDataStructureMenu();

        if (selectedDataStructure == DataStructureType.HashMap) {
            indexLogic.createHashMapDS(smoothingFactor);
        } else if (selectedDataStructure == DataStructureType.TreeMap) {
            indexLogic.createTreeMapDS(smoothingFactor);
        } else {
            logger.error("Data Structure type selected is invalid: {}; exiting...", selectedDataStructure.toString());
            System.exit(-1);
        }

        // Select Document Index Structure Type
        chooseTextualIndexMenu();

        if (selectedTextualIndex == TextualIndexType.HashMap) {
            indexLogic.createHashMapTextualIndex(numClusters);
        } else if (selectedTextualIndex == TextualIndexType.ArrayList) {
            //indexLogic.createArrayListTextualIndex(numClusters);
            // TODO Using this to test legacy HASHMAP
            indexLogic.createArrayListTextualIndex(numClusters);
        } else {
            logger.error("Textual Index type selected is invalid: {}; exiting...", selectedTextualIndex.toString());
            System.exit(-1);
        }

        // Select SpatialIndex Tree Type
        chooseSpatialTypeMenu();

        if(selectedSpatialIndex == SpatialIndexType.IR) {
            //indexLogic.createIRtree(fanout, fillFactor, dimension);
            indexLogic.createIRtreeNEW(fanout, fillFactor, dimension, rTreeVariant, nearMinimumOverlapFactor);
        } else if(selectedSpatialIndex == SpatialIndexType.IR_Bulk) {
            //indexLogic.createIRtreeWithBulkLoading(fanout, fillFactor, dimension, bulkLoadMethod);//fanout, fanout);//, 10000, 100);// TODO expose more parameters
            indexLogic.createIRtreeWithBulkLoadingNEW(fanout, fillFactor, dimension, rTreeVariant, nearMinimumOverlapFactor, BulkLoadMethod.STR);
        } else if(selectedSpatialIndex == SpatialIndexType.DIR) {
            //indexLogic.createDIRtree(fanout, fillFactor, dimension, maxWord, betaArea);
            indexLogic.createDIRtreeNEW(fanout, fillFactor, dimension, rTreeVariant, nearMinimumOverlapFactor, maxWord, betaArea);
        } else if(selectedSpatialIndex == SpatialIndexType.CIR) {
            //indexLogic.createCIRtree(fanout, fillFactor, dimension, numClusters, numMoves);
            indexLogic.createCIRtreeNEW(fanout, fillFactor, dimension, rTreeVariant, nearMinimumOverlapFactor, numClusters, numMoves);
        } else if(selectedSpatialIndex == SpatialIndexType.CDIR) {
            //indexLogic.createCDIRtree(fanout, fillFactor, dimension, maxWord, betaArea, numClusters, numMoves);
            indexLogic.createCDIRtreeNEW(fanout, fillFactor, dimension, rTreeVariant, nearMinimumOverlapFactor, maxWord, betaArea, numClusters, numMoves);
        } else {
            logger.error("Invalid Spatial-Index type selected: {}", selectedSpatialIndex.toString());
            System.exit(-1);
        }


        // ****************************************************** //
        //                       QUERIES                          //
        // ****************************************************** //

        QueryLogic queryLogic = new QueryLogic(indexLogic, statisticsLogic, resultsDirectoryPath, parameters, writeQueryResults);
        queryLogic.initQueryVariables(groupSizes, groupSizeDefault, mPercentages, mPercentageDefault, numberOfKeywords, numberOfKeywordsDefault,
                querySpaceAreaPercentages, querySpaceAreaPercentageDefault, keywordSpaceSizePercentages, keywordSpaceSizePercentageDefault, topks, topkDefault,
                alphas, alphaDefault, radius, radiusDefault, numberOfQueries);

        //TODO Set join query parameters
        queryLogic.setSpatialDistances(spatialDistance);
        queryLogic.setSpatialDistanceDefault(spatialDistanceDefault);
        queryLogic.setTextualSimilarities(textualSimilarity);
        queryLogic.setTextualSimilarityDefault(textualSimilarityDefault);

//        // TEST
//        queryLogic.processKnnQuery(new QueryLogic.KnnQueryType[]{QueryLogic.KnnQueryType.BkSK});

        do {
            // Choose number of iterations
            int numIterations = chooseNumberIterationsMenu();

            // Launch Query Group Selector Menu
            chooseQueryTypeGroupMenu();


            // ****************************************************** //
            //                      AGGREGATE                         //
            // ****************************************************** //
            if (selectedQueryTypeGroup == QueryTypeGroup.AGGREGATE) {
                // Launch Aggregator Menu
                chooseAggregateTypeMenu();

                // Query generation and evaluation
                // Result set
                globalQueryResults = new ResultQueryTotal("Aggregate");
                queryLogic.setQueryResults(globalQueryResults);

                if (dataset == Dataset.SPORTS_SET || dataset == Dataset.PARKS_SET) {
                    // For testing dataset, set number of queries to 1
                    queryLogic.setNumberOfQueries(1);
                } else {
                    queryLogic.setNumberOfQueries(numberOfQueries); // Set number of queries to default
                }

                // Launch Query parameter Menu
                ArrayList<QueryLogic.QueryType> queryTypes = new ArrayList<>(7);
                chooseAggregateQueryTypeMenu(queryTypes);

                // Process aggregate queries
                for (int i = 0; i < numIterations; i++) {
                    queryLogic.processAggregateQuery(aggregateQueryTypes, queryTypes, selectedAggregator);
                    queryLogic.printStats();
                }

                queryLogic.setNumberOfQueries(numberOfQueries); // Set number of queries to default
            }


            // ****************************************************** //
            //                         RANGE                          //
            // ****************************************************** //
            if (selectedQueryTypeGroup == QueryTypeGroup.RANGE) {
                // Query generation and evaluation
                // Result set
                globalQueryResults = new ResultQueryTotal("Range");
                queryLogic.setQueryResults(globalQueryResults);

                // Launch Query parameter Menu
                ArrayList<QueryLogic.QueryType> queryTypes = new ArrayList<>(7);
                chooseRangeQueryTypeMenu(queryTypes);

                // Process aggregate queries
                for(int i = 0; i < numIterations; i++) {
                    queryLogic.processRangeQuery(rangeQueryTypes, queryTypes);
                    queryLogic.printStats();
                }
            }

            // ****************************************************** //
            //                         KNN                            //
            // ****************************************************** //
            if (selectedQueryTypeGroup == QueryTypeGroup.KNN) {
                // Query generation and evaluation
                // Result set
                globalQueryResults = new ResultQueryTotal("KNN");
                queryLogic.setQueryResults(globalQueryResults);

                // Launch Query Menu
                ArrayList<QueryLogic.QueryType> queryTypes = new ArrayList<>(7);
                chooseKnnQueryTypeMenu(queryTypes);

                // Process aggregate queries
                for(int i = 0; i < numIterations; i++) {
                    queryLogic.processKnnQuery(knnQueryTypes, queryTypes);
                    queryLogic.printStats();
                }
            }


            // ****************************************************** //
            //                         JOIN                           //
            // ****************************************************** //
            if (selectedQueryTypeGroup == QueryTypeGroup.JOIN) {
                // Query generation and evaluation
                // Result set
                globalQueryResults = new ResultQueryTotal("JOIN");
                queryLogic.setQueryResults(globalQueryResults);
                queryLogic.setNumberOfQueries(1); // Set number of queries to 1 for JOIN queries

                // Launch Query Menu
                ArrayList<QueryLogic.QueryType> queryTypes = new ArrayList<>(7);
                chooseJoinQueryTypeMenu(queryTypes);

                // Process aggregate queries
                for(int i = 0; i < numIterations; i++) {
                    queryLogic.processJoinQuery(joinQueryTypes, queryTypes);
                    queryLogic.printStats();
                }
                queryLogic.setNumberOfQueries(numberOfQueries); // Reset number of queries to default for other query types
            }


            logger.info("---------------------------------");
            logger.info(" All queries have been evaluated ");
            logger.info("---------------------------------");

            //queryLogic.printStats();



//            logger.info("Query Times:");
//            for (long time : memTimes)
//                logger.info("{} ms", time);
//
//
//            // Always write data stats
//            writeResults();
//            logger.info("Writing results in disk ...");

//            boolean exitLoop = printExitLoopMenu();
//
//            // Temp fix to do multiple queries
//            if (exitLoop) {
//                logger.info("Writing results in disk ...");
//                //writeResults();
//                //writeResults(memResults, "[MEM]");
//            } else {
//                logger.info("Discarding Results ...");
//                break; // TODO DELETE
//            }


        logger.info("Exiting...");

        } while (chooseExitLoopMenu());

    }




    //******************************************************************//
    //                              MENUS                               //
    //******************************************************************//

    public static void chooseDataSetMenu() {
        System.out.println("\nChoose dataset:");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - Postal codes (171K)");
        System.out.println("\t2 - Sports (1.75M)");
        System.out.println("\t3 - Parks (9.96M)");
        System.out.println("\t4 - Hotels (20K)");
        System.out.println("\t5 - Test (10)");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                dataset = Dataset.POSTAL_CODES_SET;
                parameters = ParametersFactory.getParameters(dataset);
                break;
            case 2:
                dataset = Dataset.SPORTS_SET;
                parameters = ParametersFactory.getParameters(Dataset.SPORTS_SET);
                break;
            case 3:
                dataset = Dataset.PARKS_SET;
                parameters = ParametersFactory.getParameters(Dataset.PARKS_SET);
                break;
            case 4:
                dataset = Dataset.HOTEL_SET;
                parameters = ParametersFactory.getParameters(Dataset.HOTEL_SET);
                break;
            case 5:
                dataset = Dataset.TESTING_SET;
                parameters = ParametersFactory.getParameters(Dataset.TESTING_SET);
                break;
            default:
                logger.error("Wrong dataset selected. Exiting...");
                System.exit(0);
        }

        logger.info("Dataset selected: {}", dataset.toString());
    }

    // Add this method to choose percentage
    public static void chooseDatasetPercentageMenu() {
        System.out.println("\nChoose dataset usage percentage:");
        System.out.println("\n[WIP] ONLY FOR THE IR-tree:");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - 10%");
        System.out.println("\t2 - 25%");
        System.out.println("\t3 - 50%");
        System.out.println("\t4 - 75%");
        System.out.println("\t5 - 100% (Full dataset)");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        Scanner input = new Scanner(System.in);
        int selection = input.nextInt();

        switch (selection) {
            case 1: datasetUsagePercentage = 0.1; break;
            case 2: datasetUsagePercentage = 0.25; break;
            case 3: datasetUsagePercentage = 0.5; break;
            case 4: datasetUsagePercentage = 0.75; break;
            case 5: datasetUsagePercentage = 1.0; break;
            default:
                logger.error("Wrong percentage selected. Exiting...");
                System.exit(0);
        }

        logger.info("Dataset usage percentage selected: {}%", datasetUsagePercentage * 100);
    }


    public static void chooseDocumentDataStructureMenu() {
        System.out.println("\nChoose document index type:");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - HashMap");
        System.out.println("\t2 - TreeMap");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                selectedDataStructure = DataStructureType.HashMap;
                break;
            case 2:
                selectedDataStructure = DataStructureType.TreeMap;
                break;
            default:
                logger.error("Wrong document index type selected. Exiting...");
                System.exit(0);
        }

        logger.info("Document index type selected: {}", selectedDataStructure.toString());
    }

    private static void chooseTextualIndexMenu() {
        System.out.println("\nChoose Inverted List data structure type:");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - HashMap");
        System.out.println("\t2 - ArrayList");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                selectedTextualIndex = TextualIndexType.HashMap;
                break;
            case 2:
                selectedTextualIndex = TextualIndexType.ArrayList;
                break;
            default:
                logger.error("Wrong structure type selected. Exiting...");
                System.exit(0);
        }

        logger.info("Textual index structure type selected: {}", selectedTextualIndex.toString());
    }


    public static void chooseSpatialTypeMenu() {
        System.out.println("\nChoose Spatio-Textual tree type");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - IR-Tree");
        System.out.println("\t2 - IR-Tree with Bulk Loading");
        System.out.println("\t3 - DIR-Tree");
        System.out.println("\t4 - CIR-Tree");
        System.out.println("\t5 - CDIR-Tree");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Tree selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                selectedSpatialIndex = SpatialIndexType.IR;
                break;
            case 2:
                selectedSpatialIndex = SpatialIndexType.IR_Bulk;
                break;
            case 3:
                selectedSpatialIndex = SpatialIndexType.DIR;
                break;
            case 4:
                selectedSpatialIndex = SpatialIndexType.CIR;
                break;
            case 5:
                selectedSpatialIndex = SpatialIndexType.CDIR;
                break;
            default:
                logger.error("Wrong Spatio-Textual tree type selected. Exiting...");
                System.exit(0);
        }
        logger.info("Spatio-Textual tree type selected: {}", selectedSpatialIndex.toString());
    }

    public static void chooseQueryTypeGroupMenu() {
        System.out.println("\nChoose Spatial Query type");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - Aggregate SK");
        System.out.println("\t2 - Range SK");
        System.out.println("\t3 - Knn SK");
        System.out.println("\t4 - JOIN SK");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                selectedQueryTypeGroup = QueryTypeGroup.AGGREGATE;
                break;
            case 2:
                selectedQueryTypeGroup = QueryTypeGroup.RANGE;
                break;
            case 3:
                selectedQueryTypeGroup = QueryTypeGroup.KNN;
                break;
            case 4:
                selectedQueryTypeGroup = QueryTypeGroup.JOIN;
                break;
            default:
                logger.error("Wrong Spatial Query type selected. Exiting...");
                System.exit(0);
        }
        logger.info("Spatial Query type selected: {}", selectedQueryTypeGroup.toString());
    }

    public static void chooseAggregateTypeMenu() {
        System.out.println("\nChoose aggregator type");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - SUM");
        System.out.println("\t2 - MAX");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);
        String aggregator = "";

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                aggregator = "SUM";
                selectedAggregator = AggregatorFactory.getAggregator(aggregator);
                break;
            case 2:
                aggregator = "MAX";
                selectedAggregator = AggregatorFactory.getAggregator(aggregator);
                break;
            default:
                logger.error("Wrong aggregator type selected. Exiting...");
                System.exit(0);
        }
        logger.info("Aggregator type selected: {}", aggregator);
    }


    public static void chooseAggregateQueryTypeMenu(ArrayList<QueryLogic.QueryType> queryTypes) {
        System.out.println("\nChoose query parameter for Aggregate Query");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - Group Size");
        System.out.println("\t2 - Percent");
        System.out.println("\t3 - Number of Keywords");
        System.out.println("\t4 - Space Area Percentage");
        System.out.println("\t5 - Keyword Space Size Percentage");
        System.out.println("\t6 - Top K");
        System.out.println("\t7 - Alpha");
        System.out.println("\t");
        System.out.println("\t8 - Run All Queries");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                queryTypes.add(QueryLogic.QueryType.GroupSize);
                selectedQueryParameter = QueryParameters.GroupSize;
                break;
            case 2:
                queryTypes.add(QueryLogic.QueryType.Percentage);
                selectedQueryParameter = QueryParameters.PercentQuery;
                break;
            case 3:
                queryTypes.add(QueryLogic.QueryType.NumberOfKeywords);
                selectedQueryParameter = QueryParameters.NumberOfKeywords;
                break;
            case 4:
                queryTypes.add(QueryLogic.QueryType.SpaceAreaPercentage);
                selectedQueryParameter = QueryParameters.SpaceAreaPercentage;
                break;
            case 5:
                queryTypes.add(QueryLogic.QueryType.KeywordSpaceSizePercentage);
                selectedQueryParameter = QueryParameters.KeywordSpaceSizePercentage;
                break;
            case 6:
                queryTypes.add(QueryLogic.QueryType.TopK);
                selectedQueryParameter = QueryParameters.TopK;
                break;
            case 7:
                queryTypes.add(QueryLogic.QueryType.Alpha);
                selectedQueryParameter = QueryParameters.Alpha;
                break;
            case 8:
                queryTypes.add(QueryLogic.QueryType.GroupSize);
                queryTypes.add(QueryLogic.QueryType.Percentage);
                queryTypes.add(QueryLogic.QueryType.NumberOfKeywords);
                queryTypes.add(QueryLogic.QueryType.SpaceAreaPercentage);
                queryTypes.add(QueryLogic.QueryType.KeywordSpaceSizePercentage);
                queryTypes.add(QueryLogic.QueryType.TopK);
                queryTypes.add(QueryLogic.QueryType.Alpha);
                selectedQueryParameter = QueryParameters.All;
                break;
            default:
                logger.error("Wrong query parameter selected for aggregate queries. Exiting...");
                System.exit(0);
        }
        logger.info("Query parameter selected for Aggregate: {}", selectedQueryParameter.toString());
    }

    public static void chooseRangeQueryTypeMenu(ArrayList<QueryLogic.QueryType> queryTypes) {
        System.out.println("\nChoose query parameter for Range Query");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - Number of Keywords");
        System.out.println("\t2 - Space Area Percentage");
        System.out.println("\t3 - Keyword Space Size Percentage");
        System.out.println("\t4 - Range");
        System.out.println("\t5 - Alpha");
        System.out.println("\t");
        System.out.println("\t6 - Run All Queries");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                queryTypes.add(QueryLogic.QueryType.NumberOfKeywords);
                selectedQueryParameter = QueryParameters.NumberOfKeywords;
                break;
            case 2:
                queryTypes.add(QueryLogic.QueryType.SpaceAreaPercentage);
                selectedQueryParameter = QueryParameters.SpaceAreaPercentage;
                break;
            case 3:
                queryTypes.add(QueryLogic.QueryType.KeywordSpaceSizePercentage);
                selectedQueryParameter = QueryParameters.KeywordSpaceSizePercentage;
                break;
            case 4:
                queryTypes.add(QueryLogic.QueryType.Radius);
                selectedQueryParameter = QueryParameters.Radius;
                break;
            case 5:
                queryTypes.add(QueryLogic.QueryType.Alpha);
                selectedQueryParameter = QueryParameters.Alpha;
                break;
            case 6:
                queryTypes.add(QueryLogic.QueryType.NumberOfKeywords);
                queryTypes.add(QueryLogic.QueryType.SpaceAreaPercentage);
                queryTypes.add(QueryLogic.QueryType.KeywordSpaceSizePercentage);
                queryTypes.add(QueryLogic.QueryType.Radius);
                queryTypes.add(QueryLogic.QueryType.Alpha);
                selectedQueryParameter = QueryParameters.All;
                break;
            default:
                logger.error("Wrong query parameter selected for range queries. Exiting...");
                System.exit(0);
        }
        logger.info("Query parameter selected for Range: {}", selectedQueryParameter.toString());
    }


    public static void chooseKnnQueryTypeMenu(ArrayList<QueryLogic.QueryType> queryTypes) {
        System.out.println("\nChoose query parameter for KNN Query");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - Number of Keywords");
        System.out.println("\t2 - Space Area Percentage");
        System.out.println("\t3 - Keyword Space Size Percentage");
        System.out.println("\t4 - TopK");
        System.out.println("\t5 - Alpha");
        System.out.println("\t");
        System.out.println("\t6 - Run All Queries");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                queryTypes.add(QueryLogic.QueryType.NumberOfKeywords);
                selectedQueryParameter = QueryParameters.NumberOfKeywords;
                break;
            case 2:
                queryTypes.add(QueryLogic.QueryType.SpaceAreaPercentage);
                selectedQueryParameter = QueryParameters.SpaceAreaPercentage;
                break;
            case 3:
                queryTypes.add(QueryLogic.QueryType.KeywordSpaceSizePercentage);
                selectedQueryParameter = QueryParameters.KeywordSpaceSizePercentage;
                break;
            case 4:
                queryTypes.add(QueryLogic.QueryType.TopK);
                selectedQueryParameter = QueryParameters.TopK;
                break;
            case 5:
                queryTypes.add(QueryLogic.QueryType.Alpha);
                selectedQueryParameter = QueryParameters.Alpha;
                break;
            case 6:
                queryTypes.add(QueryLogic.QueryType.NumberOfKeywords);
                queryTypes.add(QueryLogic.QueryType.SpaceAreaPercentage);
                queryTypes.add(QueryLogic.QueryType.KeywordSpaceSizePercentage);
                queryTypes.add(QueryLogic.QueryType.TopK);
                queryTypes.add(QueryLogic.QueryType.Alpha);
                selectedQueryParameter = QueryParameters.All;
                break;
            default:
                logger.error("Wrong query parameter selected for knn queries. Exiting...");
                System.exit(0);
        }
        logger.info("Query parameter selected for KNN: {}", selectedQueryParameter.toString());
    }


    public static void chooseJoinQueryTypeMenu(ArrayList<QueryLogic.QueryType> queryTypes) {
        System.out.println("\nChoose query parameter for JOIN Query");
        System.out.println("-------------------------\n");
        System.out.println("\t1 - Number of Keywords");
        System.out.println("\t2 - Space Area Percentage");
        System.out.println("\t3 - Keyword Space Size Percentage");
        System.out.println("\t4 - Spatial Distance");
        System.out.println("\t5 - Textual Similarity");
        System.out.println("\t6 - Combined Spatio-Textual Similarity");
        System.out.println("\t7 - Alpha");
        System.out.println("\t");
        System.out.println("\t8 - Run All Queries");
        System.out.println("\t");
        System.out.println("\t0 - Quit");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                queryTypes.add(QueryLogic.QueryType.NumberOfKeywords);
                selectedQueryParameter = QueryParameters.NumberOfKeywords;
                break;
            case 2:
                queryTypes.add(QueryLogic.QueryType.SpaceAreaPercentage);
                selectedQueryParameter = QueryParameters.SpaceAreaPercentage;
                break;
            case 3:
                queryTypes.add(QueryLogic.QueryType.KeywordSpaceSizePercentage);
                selectedQueryParameter = QueryParameters.KeywordSpaceSizePercentage;
                break;
            case 4:
                queryTypes.add(QueryLogic.QueryType.SpatialDistance);
                selectedQueryParameter = QueryParameters.SpatialDistance;
                break;
            case 5:
                queryTypes.add(QueryLogic.QueryType.TextualSimilarity);
                selectedQueryParameter = QueryParameters.TextualSimilarity;
                break;
            case 6:
                queryTypes.add(QueryLogic.QueryType.Combined);
                selectedQueryParameter = QueryParameters.CombinedST;
                break;
            case 7:
                queryTypes.add(QueryLogic.QueryType.Alpha);
                selectedQueryParameter = QueryParameters.Alpha;
                break;
            case 8:
                queryTypes.add(QueryLogic.QueryType.NumberOfKeywords);
                queryTypes.add(QueryLogic.QueryType.SpaceAreaPercentage);
                queryTypes.add(QueryLogic.QueryType.KeywordSpaceSizePercentage);
                queryTypes.add(QueryLogic.QueryType.Radius);
                queryTypes.add(QueryLogic.QueryType.Alpha);
                selectedQueryParameter = QueryParameters.All;
                break;
            default:
                logger.error("Wrong query parameter selected for range queries. Exiting...");
                System.exit(0);
        }
        logger.info("Query parameter selected for Range: {}", selectedQueryParameter.toString());
    }


    private static int chooseNumberIterationsMenu() {
        System.out.println("\nInsert number of iterations: ");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);

        // Number of iterations selection
        int selection = input.nextInt();

        return selection;
    }

    private static boolean chooseExitLoopMenu() {
        System.out.println("\nGenerate another query?");
        System.out.println("-------------------------\n");
        System.out.println("\t 1 - Yes");
        System.out.println("\t 2 - No");
        System.out.println("-------------------------\n");

        // Start Simple Menu
        Scanner input = new Scanner(System.in);
        boolean write = false;

        // Aggregator selection
        int selection = input.nextInt();

        switch (selection) {
            case 1:
                write = true;
                break;
            case 2:
                break;
            default:
        }

        return write;
    }



    //******************************************************************//
    //                         UTILITY METHODS                          //
    //******************************************************************//

    public static void createDirectoryTree() {
        // Create directories if not present
        try {
            logger.info("Creating directory tree in resources...");
            Files.createDirectories(Paths.get(metricsDirectoryPath));
            Files.createDirectories(Paths.get(tempDirectoryPath));
            Files.createDirectories(Paths.get(logDirectoryPath));
            logger.info("Done");
        } catch (IOException e) {
            logger.error("Fail to create directories", e);
            throw new RuntimeException(e);
        }
    }

    public static void clearTempDirectory() {
        // Delete old temp files
        File file = new File(tempDirectoryPath);
        logger.info("Deleting existing temp files ...");
        deleteFilesInPath(file);
        logger.info("Done");
    }

    public static void clearResultsDirectory() {
        File file = new File(resultsDirectoryPath);
        logger.info("Deleting existing result files ...");
        deleteFilesInPath(file);
        logger.info("Done");
    }

    private static void deleteFilesInPath(File file) {
        // Check if file is a directory
        if (file.isDirectory()) {
            String[] dirFiles = file.list();

            if (dirFiles != null) {
                for (String filePath : dirFiles) {
                    File dirFile = new File(file, filePath);
                    //System.out.println(dirFile);

                    if (!dirFile.isDirectory()) {
                        dirFile.delete();
                    } else {
                        deleteFilesInPath(dirFile); // recursive call to delete cpu and io directories
                    }
                }
            }
        } else {
            //file.delete();
            logger.error("Path is not a directory...");
        }
    }
}

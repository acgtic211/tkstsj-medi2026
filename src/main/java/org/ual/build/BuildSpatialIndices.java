package org.ual.build;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.documentindex.IDocumentIndex;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.spatialindex.rtree.RTree;
import org.ual.spatialindex.spatialindex.ISpatialIndex;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatialindex.storage.AbstractDocumentStore;
import org.ual.spatialindex.storage.WeightEntry;
import org.ual.spatialindex.storagemanager.IStorageManager;
import org.ual.spatialindex.storagemanager.NodeStorageManager;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.cdirtree.CDIRTree;
import org.ual.spatiotextualindex.cirtree.CIRTree;
import org.ual.spatiotextualindex.dirtree.DIRTree;
import org.ual.spatiotextualindex.irtree.IRTree;
import org.ual.utils.main.StatisticsLogic;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;

@Deprecated
public class BuildSpatialIndices {
    private static final Logger logger = LogManager.getLogger(BuildSpatialIndices.class);

    //=============================================================================
    //============================ Spatial Constructors ===========================
    //=============================================================================

    /**
     * Builds an RTree using the specified parameters and data points from the dataset.
     * This method is suitable for smaller datasets where incremental insertion is feasible.
     *
     * @param datasetParameters Parameters containing dataset file paths and configurations
     * @param fanout Maximum number of entries in each node
     * @param fillFactor Minimum fill factor for nodes (between 0 and 1)
     * @param dimension Dimensionality of the spatial data
     * @param treeVariant Type of RTree variant to use (e.g., R*, Linear, Quadratic)
     * @return A new RTree containing all data points from the dataset
     */
    public static RTree buildRTree(DatasetParameters datasetParameters, int fanout,
                                   float fillFactor, int dimension, int treeVariant, int nearMinimumOverlapFactor) {
        try (LineNumberReader locationReader = new LineNumberReader((new FileReader(datasetParameters.locationFile)))) {
            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            RTree tree = new RTree(propertySet, storageManager, datasetParameters, false);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Process data points
            int count = processDataPoints(locationReader, tree, false);

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(tree, count, SpatialIndex.getTreeVariantString(treeVariant));

            // Validate tree structure
            validateTree(tree, propertySet);

            return tree;
        } catch (IOException e) {
            logger.error("Fail to operate with file: ", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Bulk loads the RTree with data points from the dataset using the specified bulk loading method.
     * This method provides better performance than incremental insertion for large datasets.
     *
     * @param datasetParameters Parameters containing dataset file paths and configurations
     * @param fanout Maximum number of entries in each node
     * @param fillFactor Minimum fill factor for nodes (between 0 and 1)
     * @param dimension Dimensionality of the spatial data
     * @param treeVariant Type of RTree variant to use (e.g., R*, Linear, Quadratic)
     * @param bulkLoadMethod Method to use for bulk loading (e.g., STR, Sort-Tile-Recursive)
     * @return A new RTree containing all data points from the dataset
     */
    public static RTree bulkloadRTree(DatasetParameters datasetParameters, int fanout,
                                      float fillFactor, int dimension, int treeVariant, int nearMinimumOverlapFactor, BulkLoadMethod bulkLoadMethod) {
        logger.info("Starting bulk load RTree construction with method: {}", bulkLoadMethod);

        try (LineNumberReader locationReader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            RTree tree = new RTree(propertySet, storageManager, datasetParameters, false);

            logger.debug("RTree initialized with fanout: {}, fillFactor: {}, dimension: {}", fanout, fillFactor, dimension);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Read all points into memory for bulk loading
            int count = 0;
            String line;
            logger.info("Reading data points for bulk loading...");

            while ((line = locationReader.readLine()) != null) {
                String[] temp = line.split(",");
                if (temp.length < 3) {
                    logger.warn("Skipping invalid line: {}", line);
                    continue;
                }

                int id = Integer.parseInt(temp[0]);
                double x = Double.parseDouble(temp[1]);
                double y = Double.parseDouble(temp[2]);

                double[] f1 = {x, y};
                double[] f2 = {x, y};
                Region region = new Region(f1, f2);

                tree.storePseudoNodes(id, region);
                count++;

                if (count % 10000 == 0) {
                    logger.debug("Processed {} data points", count);
                }
            }

            logger.info("Finished reading {} data points", count);

            // Perform bulk loading
            logger.info("Processing pseudo nodes and creating the tree structure using {}", bulkLoadMethod);
            long bulkLoadStart = System.currentTimeMillis();
            tree.bulkLoadRTree(bulkLoadMethod);
            long bulkLoadTime = System.currentTimeMillis() - bulkLoadStart;
            logger.info("Bulk loading completed in {} ms", bulkLoadTime);

            // Clear pseudo nodes to free memory
            int pseudoNodeCount = tree.pseudoNodes.size();
            tree.pseudoNodes.clear();
            logger.info("Cleared {} pseudo nodes from memory", pseudoNodeCount);

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            String treeDescription = "Bulk-loaded (" + bulkLoadMethod.toString() + ")";
            logSpatialComponentStatistics(tree, count, treeDescription);

            // Validate tree structure
            validateTree(tree, propertySet);

            logger.info("RTree bulk loading completed successfully");
            return tree;

        } catch (IOException e) {
            logger.error("Failed to read from location file: {}", datasetParameters.locationFile, e);
            throw new RuntimeException("RTree bulk loading failed", e);
        } catch (NumberFormatException e) {
            logger.error("Invalid number format in location file", e);
            throw new RuntimeException("RTree bulk loading failed due to invalid data format", e);
        }
    }

    //=============================================================================
    //======================== Spatio-Textual Constructors ========================
    //=============================================================================

    /**
     * Builds an IR-Tree (Inverted R-Tree) index with the provided parameters.
     * The method first constructs the spatial component (R-Tree) and then builds the text component (inverted file).
     *
     * @param datasetParameters Parameters containing dataset file paths and configurations
     * @param fanout Maximum number of entries in each node
     * @param fillFactor Minimum fill factor for nodes (between 0 and 1)
     * @param dimension Dimensionality of the spatial data
     * @param dms Document store containing text data
     * @param invertedFile Inverted file index to be populated
     * @return A new IR-Tree containing spatial and textual data
     */
    @Deprecated
    public static IRTree buildIRTree(DatasetParameters datasetParameters, int fanout, float fillFactor,
                                     int dimension, int treeVariant, int nearMinimumOverlapFactor, AbstractDocumentStore dms,
                                     IDocumentIndex invertedFile) {
        IRTree irTree;
        try (LineNumberReader locationReader = new LineNumberReader((new FileReader(datasetParameters.locationFile)))) {
            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            irTree = new IRTree(propertySet, storageManager, datasetParameters);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Process data points
            int count = processDataPoints(locationReader, irTree, false);

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(irTree, count, SpatialIndex.getTreeVariantString(treeVariant));

            // Validate tree structure
            validateTree(irTree, propertySet);

        } catch (IOException e) {
            logger.error("Fail to operate with file: ", e);
            throw new RuntimeException(e);
        }

        // Build the textual component (inverted file)
        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        // Build the inverted file
        List<WeightEntry> invertedIndex = irTree.createIRTree(dms, invertedFile);

        // Collect and log performance metrics for IR-Tree
        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("IR");

        // Final validation
        boolean ret = irTree.isIndexValid();
        if (!ret) {
            logger.error("Structure is INVALID!");
        }

        return irTree;
    }


    public static IRTree buildRefactorIRTree(DatasetParameters datasetParameters, int fanout, float fillFactor,
                                             int dimension, int treeVariant, int nearMinimumOverlapFactor, AbstractDocumentStore dms,
                                             IDocumentIndex invertedFile) {
        IRTree IRTree;
        try (LineNumberReader locationReader = new LineNumberReader((new FileReader(datasetParameters.locationFile)))) {
            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            IRTree = new IRTree(propertySet, storageManager, datasetParameters);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Process data points
            int count = processDataPoints(locationReader, IRTree, false);

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(IRTree, count, SpatialIndex.getTreeVariantString(treeVariant));

            // Validate tree structure
            validateTree(IRTree, propertySet);

        } catch (IOException e) {
            logger.error("Fail to operate with file: ", e);
            throw new RuntimeException(e);
        }

        // Build the textual component (inverted file)
        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        // Build the inverted file
        List<WeightEntry> invertedIndex = IRTree.createIRTree(dms, invertedFile);

        // Collect and log performance metrics for IR-Tree
        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("IR");

        // Final validation
        boolean ret = IRTree.isIndexValid();
        if (!ret) {
            logger.error("Structure is INVALID!");
        }

        return IRTree;
    }


    public static ISpatialIndex buildRefactorIRTree(DatasetParameters datasetParameters, int fanout, float fillFactor,
                                                    int dimension, int treeVariant, int nearMinimumOverlapFactor, AbstractDocumentStore dms,
                                                    IDocumentIndex invertedFile, double datasetUsagePercentage) {
        IRTree IRTree;
        try (LineNumberReader locationReader = new LineNumberReader((new FileReader(datasetParameters.locationFile)))) {
            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            IRTree = new IRTree(propertySet, storageManager, datasetParameters);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Process data points
            int count = processDataPoints(locationReader, IRTree, false, datasetUsagePercentage, datasetParameters.locationFile);

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(IRTree, count, SpatialIndex.getTreeVariantString(treeVariant));

            // Validate tree structure
            validateTree(IRTree, propertySet);

        } catch (IOException e) {
            logger.error("Fail to operate with file: ", e);
            throw new RuntimeException(e);
        }

        // Build the textual component (inverted file)
        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        // Build the inverted file
        List<WeightEntry> invertedIndex = IRTree.createIRTree(dms, invertedFile);

        // Collect and log performance metrics for IR-Tree
        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("IR");

        // Final validation
        boolean ret = IRTree.isIndexValid();
        if (!ret) {
            logger.error("Structure is INVALID!");
        }

        return IRTree;
    }


    /**
     * Builds an IR-Tree using bulk loading, which provides better performance for large datasets.
     * This method first constructs the spatial component using bulk loading and then builds the text component.
     *
     * @param datasetParameters Parameters containing dataset file paths and configurations
     * @param fanout Maximum number of entries in each node
     * @param fillFactor Minimum fill factor for nodes (between 0 and 1)
     * @param dimension Dimensionality of the spatial data
     * @param treeVariant Type of RTree variant to use (e.g., R*, Linear, Quadratic)
     * @param bulkLoadMethod Method to use for bulk loading (e.g., STR, Sort-Tile-Recursive)
     * @param dms Document store containing text data
     * @param invertedFile Inverted file index to be populated
     * @return A new IR-Tree containing spatial and textual data
     */
    public static IRTree bulkloadRefactorIRTree(DatasetParameters datasetParameters, int fanout, float fillFactor,
                                                int dimension, int treeVariant, int nearMinimumOverlapFactor, BulkLoadMethod bulkLoadMethod,
                                                AbstractDocumentStore dms, IDocumentIndex invertedFile) {
        try (LineNumberReader locationReader = new LineNumberReader((new FileReader(datasetParameters.locationFile)))) {
            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            IRTree tree = new IRTree(propertySet, storageManager, datasetParameters);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Read all points into memory for bulk loading
            int count = 0;
            String line;
            String[] temp;

            long lastCallTime = System.currentTimeMillis();
            long maxTimeBetweenCalls = 0;

            while ((line = locationReader.readLine()) != null) {
                temp = line.split(",");
                if (temp.length < 3) {
                    logger.warn("Skipping invalid line: {}", line);
                    continue;
                }
                int id = Integer.parseInt(temp[0]);
                double x = Double.parseDouble(temp[1]);
                double y = Double.parseDouble(temp[2]);

                double[] f1 = {x, y};
                double[] f2 = {x, y};
                Region region = new Region(f1, f2);

                // Track insertion time
                long currentCallTime = System.currentTimeMillis();
                long timeBetweenCalls = currentCallTime - lastCallTime;
                maxTimeBetweenCalls = Math.max(maxTimeBetweenCalls, timeBetweenCalls);
                lastCallTime = currentCallTime;

                tree.storePseudoNodes(id, region);
                count++;
            }

            logger.info("Maximum time between calls: {} ms", maxTimeBetweenCalls);

            // Perform bulk loading
            logger.info("Processing pseudo nodes and creating the tree structure");
            tree.bulkLoadRTree(bulkLoadMethod);

            // Clear pseudo nodes to free memory
            tree.pseudoNodes.clear();
            logger.info("Done processing pseudo nodes");

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            String treeDesc = "Bulk-loaded (" + bulkLoadMethod.toString() + ")";
            logSpatialComponentStatistics(tree, count, treeDesc);

            // Validate tree structure
            validateTree(tree, propertySet);

            // Build the textual component (inverted file)
            initMem = StatisticsLogic.getClearedMem();
            startTime = System.currentTimeMillis();
            StatisticsLogic.startMemoryMonitoring();

            // Build the inverted file
            List<WeightEntry> invertedIndex = tree.createIRTree(dms, invertedFile);

            // Collect and log performance metrics
            collectTextualComponentMetrics(initMem, startTime);
            logTextualComponentStatistics("[IR] " + treeDesc);

            // Final validation
            boolean isValid = tree.isIndexValid();
            if (!isValid) {
                logger.error("Structure is INVALID!");
            }

            return tree;
        } catch (IOException e) {
            logger.error("Fail to operate with file: ", e);
            throw new RuntimeException(e);
        }
    }


    /**
     * Builds a CIR-Tree (Clustered Inverted R-Tree) index with the provided parameters.
     * The method first constructs the spatial component (R-Tree) and then builds the text component (inverted file).
     *
     * @param datasetParameters Parameters containing dataset file paths and configurations
     * @param fanout Maximum number of entries in each node
     * @param fillFactor Minimum fill factor for nodes (between 0 and 1)
     * @param dimension Dimensionality of the spatial data
     * @param treeVariant Type of RTree variant to use (e.g., R*, Linear, Quadratic)
     * @param numOfClusters Number of clusters to be used in the CIR-Tree
     * @param clusterTree Mapping of document IDs to cluster IDs
     * @param dms Document store containing text data
     * @param invertedFile Inverted file index to be populated
     * @return A new CIR-Tree containing spatial and textual data
     */
    public static CIRTree buildRefactorCIRTree(DatasetParameters datasetParameters, int fanout, float fillFactor,
                                               int dimension, int treeVariant, int nearMinimumOverlapFactor, int numOfClusters, HashMap<Integer, Integer> clusterTree,
                                               AbstractDocumentStore dms, IDocumentIndex invertedFile) {
        CIRTree cirTree;
        try (LineNumberReader locationReader = new LineNumberReader((new FileReader(datasetParameters.locationFile)))) {
            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);
            propertySet.setProperty("NumberOfClusters", numOfClusters);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            cirTree = new CIRTree(propertySet, storageManager, datasetParameters);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Process data points
            int count = processDataPoints(locationReader, cirTree, false);

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(cirTree, count, SpatialIndex.getTreeVariantString(treeVariant));

            // Validate tree structure
            validateTree(cirTree, propertySet);

        } catch (IOException e) {
            logger.error("Fail to operate with file: ", e);
            throw new RuntimeException(e);
        }

        // Build the textual component (inverted file)
        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        // Build the inverted file
        List<List<WeightEntry>> invertedIndex = cirTree.createCIRTree(clusterTree, dms, invertedFile);

        // Collect and log performance metrics for CIR-Tree
        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("CIR");

        // Final validation
        boolean ret = cirTree.isIndexValid();
        if (!ret) {
            logger.error("Structure is INVALID!");
        }

        return cirTree;
    }


    /**
     * Builds a DIR-Tree (Document Inverted R-Tree) index with the provided parameters.
     * The method first constructs the spatial component (R-Tree) and then builds the text component (inverted file).
     *
     * @param datasetParameters Parameters containing dataset file paths and configurations
     * @param fanout Maximum number of entries in each node
     * @param fillFactor Minimum fill factor for nodes (between 0 and 1)
     * @param dimension Dimensionality of the spatial data
     * @param treeVariant Type of RTree variant to use (e.g., R*, Linear, Quadratic)
     * @param betaArea Area factor for node splitting decisions
     * @param dms Document store containing text data
     * @param invertedFile Inverted file index to be populated
     * @return A new DIR-Tree containing spatial and textual data
     */
    public static DIRTree buildRefactorDIRTree(DatasetParameters datasetParameters, int fanout, float fillFactor,
                                               int dimension, int treeVariant, int nearMinimumOverlapFactor, int maxWord, double betaArea,
                                               AbstractDocumentStore dms, IDocumentIndex invertedFile) {
        DIRTree dirTree;
        try (LineNumberReader locationReader = new LineNumberReader((new FileReader(datasetParameters.locationFile)))) {
            //maxWord is used to control the number of words involved in tree building.
            //Large maxWord may incur high construction cost.
            AbstractDocumentStore.maxWord = maxWord;    // TODO: This is a global variable, consider refactoring to avoid global state

            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);
            propertySet.setProperty("BetaArea", betaArea);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            dirTree = new DIRTree(propertySet, storageManager, dms, datasetParameters);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Process data points
            int count = processDataPoints(locationReader, dirTree,true);

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(dirTree, count, SpatialIndex.getTreeVariantString(treeVariant));

            // Validate tree structure
            validateTree(dirTree, propertySet);

        } catch (IOException e) {
            logger.error("Fail to operate with file: ", e);
            throw new RuntimeException(e);
        }

        // Build the textual component (inverted file)
        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        // Build the inverted file
        List<WeightEntry> invertedIndex = dirTree.createDIRTree(dms, invertedFile);

        // Collect and log performance metrics for DIR-Tree
        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("DIR");

        // Final validation
        boolean ret = dirTree.isIndexValid();
        if (!ret) {
            logger.error("Structure is INVALID!");
        }

        return dirTree;
    }


    /**
     * Builds a CDIR-Tree (Clustered Document Inverted R-Tree) index with the provided parameters.
     * The method first constructs the spatial component (R-Tree) and then builds the text component (inverted file).
     *
     * @param datasetParameters Parameters containing dataset file paths and configurations
     * @param fanout Maximum number of entries in each node
     * @param fillFactor Minimum fill factor for nodes (between 0 and 1)
     * @param dimension Dimensionality of the spatial data
     * @param treeVariant Type of RTree variant to use (e.g., R*, Linear, Quadratic)
     * @param betaArea Area factor for node splitting decisions
     * @param numOfClusters Number of clusters to be used in the CDIR-Tree
     * @param clusterTree Mapping of document IDs to cluster IDs
     * @param dms Document store containing text data
     * @param invertedFile Inverted file index to be populated
     * @return A new CDIR-Tree containing spatial and textual data
     */
    public static CDIRTree buildRefactorCDIRTree(DatasetParameters datasetParameters, int fanout, float fillFactor,
                                                 int dimension, int treeVariant, int nearMinimumOverlapFactor, int maxWord, double betaArea, int numOfClusters,
                                                 HashMap<Integer, Integer> clusterTree, AbstractDocumentStore dms, IDocumentIndex invertedFile) {
        CDIRTree cdirTree;
        try (LineNumberReader locationReader = new LineNumberReader((new FileReader(datasetParameters.locationFile)))) {
            AbstractDocumentStore.maxWord = maxWord;    // TODO: This is a global variable, consider refactoring to avoid global state
            // Initialize common RTree properties
            PropertySet propertySet = createRTreeProperties(fillFactor, fanout, dimension, treeVariant, nearMinimumOverlapFactor);
            propertySet.setProperty("BetaArea", betaArea);
            propertySet.setProperty("NumberOfClusters", numOfClusters);

            // Create storage manager and initialize the tree
            IStorageManager storageManager = new NodeStorageManager();
            cdirTree = new CDIRTree(propertySet, storageManager, dms, datasetParameters);

            // Start performance monitoring
            StatisticsLogic.startMemoryMonitoring();
            long initMem = StatisticsLogic.getClearedMem();
            long startTime = System.currentTimeMillis();

            // Process data points
            int count = processDataPoints(locationReader, cdirTree, true);

            // Collect and log performance metrics
            collectSpatialComponentMetrics(initMem, startTime);
            logSpatialComponentStatistics(cdirTree, count, SpatialIndex.getTreeVariantString(treeVariant));

            // Validate tree structure
            validateTree(cdirTree, propertySet);

        } catch (IOException e) {
            logger.error("Fail to operate with file: ", e);
            throw new RuntimeException(e);
        }

        // Build the textual component (inverted file)
        long initMem = StatisticsLogic.getClearedMem();
        long startTime = System.currentTimeMillis();
        StatisticsLogic.startMemoryMonitoring();

        // Build the inverted file
        List<List<WeightEntry>> invertedIndex = cdirTree.createCDIRTree(clusterTree, dms, invertedFile);

        // Collect and log performance metrics for DIR-Tree
        collectTextualComponentMetrics(initMem, startTime);
        logTextualComponentStatistics("CDIR");

        // Final validation
        boolean ret = cdirTree.isIndexValid();
        if (!ret) {
            logger.error("Structure is INVALID!");
        }

        return cdirTree;
    }

    //=============================================================================
    //======================== Private Helper Methods =============================
    //=============================================================================

    /**
     * Creates the common PropertySet for RTree initialization
     */
    private static PropertySet createRTreeProperties(float fillFactor, int fanout, int dimension, int treeVariant, int nearMinimumOverlapFactor) {
        PropertySet propertySet = new PropertySet();
        propertySet.setProperty("FillFactor", fillFactor);
        propertySet.setProperty("IndexCapacity", fanout);
        propertySet.setProperty("LeafCapacity", fanout);
        propertySet.setProperty("Dimension", dimension);
        propertySet.setProperty("TreeVariant", treeVariant);
        propertySet.setProperty("NearMinimumOverlapFactor", nearMinimumOverlapFactor);

        return propertySet;
    }


    private static int processDataPoints(LineNumberReader reader, ISpatialIndex tree, boolean loadDocuments) throws IOException {
        int count = 0;
        String line;
        String[] temp;
        Set<Integer> ids = new HashSet<>();

        long lastCallTime = System.currentTimeMillis();
        long maxTimeBetweenCalls = 0;

        while ((line = reader.readLine()) != null) {
            temp = line.split(",");
            int id = Integer.parseInt(temp[0]);
            double x1 = Double.parseDouble(temp[1]);
            double y1 = Double.parseDouble(temp[2]);

            if (!ids.add(id)) {
                continue; // Skip duplicate IDs
            }

            double[] f1 = {x1, y1};
            double[] f2 = {x1, y1};
            Region region = new Region(f1, f2);

            // Track insertion time
            long currentCallTime = System.currentTimeMillis();
            long timeBetweenCalls = currentCallTime - lastCallTime;
            maxTimeBetweenCalls = Math.max(maxTimeBetweenCalls, timeBetweenCalls);
            lastCallTime = currentCallTime;

            HashSet<Integer> documents = null;
            if (loadDocuments) {
                if (tree instanceof DIRTree) {
                    documents = ((DIRTree) tree).getDocumentStore().readSet(id);
                } else if (tree instanceof CDIRTree) {
                    documents = ((CDIRTree) tree).getDocumentStore().readSet(id);
                }
            }

            if (documents != null) {
                if (tree instanceof DIRTree) {
                    ((DIRTree) tree).insertData(id, region, documents);
                } else {
                    ((CDIRTree) tree).insertData(id, region, documents);
                }
            } else {
                tree.insertData(id, region); // Fallback for other tree types
            }
            count++;
        }

        logger.info("Maximum time between calls: {} ms", maxTimeBetweenCalls);
        return count;
    }

    // TODO Testing with datasetUsagePercentage
    private static int processDataPoints(LineNumberReader reader, ISpatialIndex tree, boolean loadDocuments, double datasetUsagePercentage, String locationsFilePath) throws IOException {
        int count = 0;
        int targetLineCount = Integer.MAX_VALUE;

        // Calculate target line count if using percentage
        if (datasetUsagePercentage < 1.0) {
            try (LineNumberReader countReader = new LineNumberReader(new FileReader(locationsFilePath))) {
                // Note: You'll need to pass the file path instead of using the reader
                while (countReader.readLine() != null) {
                    // Just count lines
                }
                int totalLines = countReader.getLineNumber();
                targetLineCount = (int) Math.ceil(totalLines * datasetUsagePercentage);
                logger.info("Using {}% of spatial dataset: {} out of {} lines",
                        datasetUsagePercentage * 100, targetLineCount, totalLines);
            } catch (Exception e) {
                logger.error("Error counting lines in location file", e);
                return 0;
            }
        }


        String line;
        String[] temp;
        Set<Integer> ids = new HashSet<>();
        long lastCallTime = System.currentTimeMillis();
        long maxTimeBetweenCalls = 0;
        int linesRead = 0;

        while ((line = reader.readLine()) != null && linesRead < targetLineCount) {
            temp = line.split(",");
            if (temp.length < 3) {
                logger.warn("Skipping invalid line: {}", line);
                continue;
            }

            int id = Integer.parseInt(temp[0]);
            double x1 = Double.parseDouble(temp[1]);
            double y1 = Double.parseDouble(temp[2]);

            if (!ids.add(id)) {
                continue; // Skip duplicate IDs
            }

            double[] f1 = {x1, y1};
            double[] f2 = {x1, y1};
            Region region = new Region(f1, f2);

            // Track insertion time
            long currentCallTime = System.currentTimeMillis();
            long timeBetweenCalls = currentCallTime - lastCallTime;
            maxTimeBetweenCalls = Math.max(maxTimeBetweenCalls, timeBetweenCalls);
            lastCallTime = currentCallTime;

            HashSet<Integer> documents = null;
            if (loadDocuments) {
                if (tree instanceof DIRTree) {
                    documents = ((DIRTree) tree).getDocumentStore().readSet(id);
                } else if (tree instanceof CDIRTree) {
                    documents = ((CDIRTree) tree).getDocumentStore().readSet(id);
                }
            }

            if (documents != null) {
                if (tree instanceof DIRTree) {
                    ((DIRTree) tree).insertData(id, region, documents);
                } else {
                    ((CDIRTree) tree).insertData(id, region, documents);
                }
            } else {
                tree.insertData(id, region); // Fallback for other tree types
            }
            count++;
            linesRead++;
        }

        logger.info("Maximum time between calls: {} ms", maxTimeBetweenCalls);
        logger.info("Processed {} spatial data points ({}% of dataset)", count, datasetUsagePercentage * 100);
        return count;
    }


    /**
     * Collects performance metrics after spatial component construction
     */
    private static void collectSpatialComponentMetrics(long initMem, long startTime) {
        long endTime = System.currentTimeMillis();
        StatisticsLogic.stopMemoryMonitoring();

        long maxMemoryUsage = StatisticsLogic.getMaxMemoryUsage();
        StatisticsLogic.rTreePeakMemUsed = (maxMemoryUsage - initMem);
        StatisticsLogic.rTreeMemUsed = StatisticsLogic.getClearedMem() - initMem;
        StatisticsLogic.rTreeJVMPeakMemUsed = maxMemoryUsage;
        StatisticsLogic.rTreeBuildTime = (endTime - startTime);
    }

    /**
     * Collects performance metrics after textual component construction
     */
    private static void collectTextualComponentMetrics(long initMem, long startTime) {
        long endTime = System.currentTimeMillis();
        StatisticsLogic.stopMemoryMonitoring();

        long maxMemoryUsage = StatisticsLogic.getMaxMemoryUsage();
        StatisticsLogic.irTreePeakMemUsed = (maxMemoryUsage - initMem);
        StatisticsLogic.irTreeMemUsed = StatisticsLogic.getClearedMem() - initMem;
        StatisticsLogic.irTreeJVMPeakMemUsed = maxMemoryUsage;
        StatisticsLogic.irTreeBuildTime = (endTime - startTime);
    }

    /**
     * Logs tree statistics
     */
    private static void logSpatialComponentStatistics(ISpatialIndex tree, int count, String treeType) {
        logger.info("Operations: {}", count);
        logger.info("Tree: {}", tree);
        logger.info("Rtree ({}) build time: {} ms", treeType, StatisticsLogic.rTreeBuildTime);
        logger.info("Rtree ({}) memory usage: {} Megabytes", treeType, (StatisticsLogic.rTreeMemUsed/1024)/1024);
        logger.info("Rtree ({}) peak memory usage: {} Megabytes", treeType, (StatisticsLogic.rTreePeakMemUsed/1024)/1024);
        logger.info("Rtree ({}) JVM peak memory usage: {} Megabytes", treeType, (StatisticsLogic.rTreeJVMPeakMemUsed/1024)/1024);
    }

    /**
     * Logs textual component statistics
     */
    private static void logTextualComponentStatistics(String treeType) {
        logger.info("({})tree build in: {} ms", treeType, StatisticsLogic.irTreeBuildTime);
        logger.info("({})tree memory usage: {} Megabytes", treeType, (StatisticsLogic.irTreeMemUsed / 1024) / 1024);
        logger.info("({})tree peak memory usage: {} Megabytes", treeType, (StatisticsLogic.irTreePeakMemUsed / 1024) / 1024);
        logger.info("({})tree JVM peak memory usage: {} Megabytes", treeType, (StatisticsLogic.irTreeJVMPeakMemUsed / 1024) / 1024);
    }

    /**
     * Validates the tree structure and logs the index ID
     */
    private static void validateTree(ISpatialIndex tree, PropertySet propertySet) {
        Integer indexID = (Integer) propertySet.getProperty("IndexIdentifier");
        logger.debug("Index ID: {}", indexID);

        boolean isValid = tree.isIndexValid();
        if (!isValid) {
            logger.error("Structure is INVALID!");
        }
    }


}

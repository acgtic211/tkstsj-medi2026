package org.ual.build;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.build.IndexBuilder;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.spatialindex.spatialindex.ISpatialIndex;
import org.ual.spatialindex.spatialindex.Region;
import org.ual.spatialindex.storagemanager.PropertySet;
import org.ual.spatiotextualindex.cdirtree.CDIRTree;
import org.ual.spatiotextualindex.dirtree.DIRTree;
import org.ual.utils.main.StatisticsLogic;
import org.ual.utils.sampling.ContiguousWindowSampler;
import org.ual.utils.sampling.SamplingStrategy;
import org.ual.utils.sampling.SystematicSampler;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractIndexBuilder <T extends AbstractIndexBuilder<T>> implements IndexBuilder {

    private static final Logger logger = LogManager.getLogger(AbstractIndexBuilder.class);

    protected DatasetParameters datasetParameters;
    protected int fanout;
    protected float fillFactor;
    protected int dimension;
    protected int treeVariant;
    protected int nearMinimumOverlapFactor;
    protected BulkLoadMethod bulkLoadMethod;
    protected double datasetUsagePercentage = 1.0;
//    protected SamplingStrategy.SamplingMethod samplingMethod = SamplingStrategy.SamplingMethod.SYSTEMATIC;
    protected SamplingStrategy.SamplingMethod samplingMethod = SamplingStrategy.SamplingMethod.RANDOMIZED;
//    protected SamplingStrategy.SamplingMethod samplingMethod = SamplingStrategy.SamplingMethod.CONTIGUOUS;
    protected long samplingRandomSeed = 42L;
    protected int samplingStartLine = 0;

    public T setDatasetParameters(DatasetParameters datasetParameters) {
        this.datasetParameters = datasetParameters;
        return self();
    }

    public T setFanout(int fanout) {
        this.fanout = fanout;
        return self();
    }

    public T setFillFactor(float fillFactor) {
        this.fillFactor = fillFactor;
        return self();
    }

    public T setDimension(int dimension) {
        this.dimension = dimension;
        return self();
    }

    public T setTreeVariant(int treeVariant) {
        this.treeVariant = treeVariant;
        return self();
    }

    public T setNearMinimumOverlapFactor(int nearMinimumOverlapFactor) {
        this.nearMinimumOverlapFactor = nearMinimumOverlapFactor;
        return self();
    }

    public T setBulkLoadMethod(BulkLoadMethod bulkLoadMethod) {
        this.bulkLoadMethod = bulkLoadMethod;
        return self();
    }

    /**
     * Sets the percentage of the dataset to be used for building the index.
     * A value of 1.0 means 100% of the dataset will be used.
     *
     * @param datasetUsagePercentage the percentage of the dataset to use (between 0.0 and 1.0)
     * @return the current builder instance
     */
    public T setDatasetUsagePercentage(double datasetUsagePercentage) {
        this.datasetUsagePercentage = datasetUsagePercentage;
        return self();
    }

    /**
     * Sets the sampling method (SYSTEMATIC, RANDOMIZED, or CONTIGUOUS).
     * SYSTEMATIC uses even distribution across the file (deterministic).
     * RANDOMIZED uses random selection for less bias (requires seed for reproducibility).
     * CONTIGUOUS uses a contiguous block of lines from the dataset.
     *
     * @param samplingMethod the sampling method to use
     * @return the current builder instance
     */
    public T setSamplingMethod(SamplingStrategy.SamplingMethod samplingMethod) {
        this.samplingMethod = samplingMethod;
        return self();
    }

    /**
     * Sets the random seed for RANDOMIZED sampling.
     * The same seed with the same dataset ensures reproducible results.
     *
     * @param seed the random seed
     * @return the current builder instance
     */
    public T setSamplingRandomSeed(long seed) {
        this.samplingRandomSeed = seed;
        return self();
    }

    /**
     * Sets the desired zero-based start line for CONTIGUOUS sampling.
     * If the requested start does not leave enough lines for the target size,
     * it is automatically clamped to the latest valid start.
     *
     * @param startLine zero-based requested start line
     * @return the current builder instance
     */
    public T setSamplingStartLine(int startLine) {
        this.samplingStartLine = startLine;
        return self();
    }

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    protected PropertySet createRTreeProperties() {
        PropertySet propertySet = new PropertySet();
        propertySet.setProperty("FillFactor", fillFactor);
        propertySet.setProperty("IndexCapacity", fanout);
        propertySet.setProperty("LeafCapacity", fanout);
        propertySet.setProperty("Dimension", dimension);
        propertySet.setProperty("TreeVariant", treeVariant);
        propertySet.setProperty("NearMinimumOverlapFactor", nearMinimumOverlapFactor);
        return propertySet;
    }

//    protected int processDataPoints(LineNumberReader reader, ISpatialIndex tree, boolean loadDocuments) throws IOException {
//        int count = 0;
//        int targetLineCount = Integer.MAX_VALUE;
//
//        if (datasetUsagePercentage < 1.0) {
//            try (LineNumberReader countReader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
//                while (countReader.readLine() != null) {}
//                int totalLines = countReader.getLineNumber();
//                targetLineCount = (int) Math.ceil(totalLines * datasetUsagePercentage);
//                logger.info("Using {}% of spatial dataset: {} out of {} lines",
//                        datasetUsagePercentage * 100, targetLineCount, totalLines);
//            } catch (Exception e) {
//                logger.error("Error counting lines in location file", e);
//                return 0;
//            }
//        }
//
//        String line;
//        Set<Integer> ids = new HashSet<>();
//        int linesRead = 0;
//
//        while ((line = reader.readLine()) != null && linesRead < targetLineCount) {
//            try {
//                String[] temp = line.split(",");
//                if (temp.length < 3) {
//                    logger.warn("Skipping invalid line: {}", line);
//                    continue;
//                }
//
//                int id = Integer.parseInt(temp[0]);
//                if (!ids.add(id)) continue;
//
//                double x1 = Double.parseDouble(temp[1]);    // Longitude
//                double y1 = Double.parseDouble(temp[2]);    // Latitude
//                Region region = new Region(new double[]{x1, y1}, new double[]{x1, y1});
//
//                if (loadDocuments) {
//                    HashSet<Integer> documents = null;
//                    if (tree instanceof DIRTree) {
//                        documents = ((DIRTree) tree).getDocumentStore().readSet(id);
//                        ((DIRTree) tree).insertData(id, region, documents);
//                    } else if (tree instanceof CDIRTree) {
//                        documents = ((CDIRTree) tree).getDocumentStore().readSet(id);
//                        ((CDIRTree) tree).insertData(id, region, documents);
//                    } else {
//                        // Fallback for other tree types when loadDocuments is true but tree is not DIR/CDIR
//                        tree.insertData(id, region);
//                    }
//                } else {
//                    tree.insertData(id, region);
//                }
//                count++;
//                linesRead++;
//            } catch (Exception e) {
//                logger.warn("Error processing line '{}': {}", line, e.getMessage());
//            }
//        }
//        logger.info("Processed {} spatial data points.", count);
//        return count;
//    }

    protected int processDataPoints(LineNumberReader reader, ISpatialIndex tree, boolean loadDocuments) throws IOException {
        int count = 0;
        int targetLineCount = Integer.MAX_VALUE;
        int totalLines = Integer.MAX_VALUE; // Kept as MAX_VALUE for cases where usage percentage is 1.0

        if (datasetUsagePercentage < 1.0) {
            try (LineNumberReader countReader = new LineNumberReader(new FileReader(datasetParameters.locationFile))) {
                while (countReader.readLine() != null) {}
                totalLines = countReader.getLineNumber();
                targetLineCount = (int) Math.ceil(totalLines * datasetUsagePercentage);
                logger.info("Using {}% of spatial dataset: {} out of {} lines",
                        datasetUsagePercentage * 100, targetLineCount, totalLines);
                logger.info("Sampling method: {} {}", samplingMethod,
                        samplingMethod == SamplingStrategy.SamplingMethod.RANDOMIZED ? 
                        "(seed=" + samplingRandomSeed + ")" : "");
            } catch (Exception e) {
                logger.error("Error counting lines in location file", e);
                return 0;
            }
        }

        String line;
        Set<Integer> ids = new HashSet<>();

        // Create appropriate sampler based on configured method
        SamplingStrategy samplingStrategy = null;
        SystematicSampler systematicSampler = null;
        ContiguousWindowSampler contiguousWindowSampler = null;

        if (datasetUsagePercentage < 1.0) {
            if (samplingMethod == SamplingStrategy.SamplingMethod.RANDOMIZED) {
                samplingStrategy = SamplingStrategy.randomized(samplingRandomSeed);
            } else if (samplingMethod == SamplingStrategy.SamplingMethod.CONTIGUOUS) {
                contiguousWindowSampler = new ContiguousWindowSampler(totalLines, targetLineCount, samplingStartLine);
                logger.info("Contiguous sampling window: requestedStart={}, effectiveStart={}, endExclusive={}",
                        samplingStartLine,
                        contiguousWindowSampler.getStartLineInclusive(),
                        contiguousWindowSampler.getEndLineExclusive());
            } else {
                systematicSampler = new SystematicSampler();
            }
        }

        // FIX: Removed "linesRead < targetLineCount" so the loop reads all the way to the end of the file
        while ((line = reader.readLine()) != null) {

            // Apply sampling if we are loading a partial dataset
            if (datasetUsagePercentage < 1.0) {
                boolean shouldSelect;
                if (samplingMethod == SamplingStrategy.SamplingMethod.RANDOMIZED) {
                    shouldSelect = samplingStrategy.shouldSelectRandomized(totalLines, targetLineCount);
                } else if (samplingMethod == SamplingStrategy.SamplingMethod.CONTIGUOUS) {
                    shouldSelect = contiguousWindowSampler.shouldSelect();
                } else {
                    shouldSelect = systematicSampler.shouldSelect(totalLines, targetLineCount);
                }
                if (!shouldSelect) {
                    continue; // Skip this line
                }
            }

            try {
                String[] temp = line.split(",");
                if (temp.length < 3) {
                    logger.warn("Skipping invalid line: {}", line);
                    continue;
                }

                int id = Integer.parseInt(temp[0]);
                if (!ids.add(id)) continue;

                double x1 = Double.parseDouble(temp[1]);    // Longitude
                double y1 = Double.parseDouble(temp[2]);    // Latitude
                Region region = new Region(new double[]{x1, y1}, new double[]{x1, y1});

                if (loadDocuments) {
                    HashSet<Integer> documents = null;
                    if (tree instanceof DIRTree) {
                        documents = ((DIRTree) tree).getDocumentStore().readSet(id);
                        ((DIRTree) tree).insertData(id, region, documents);
                    } else if (tree instanceof CDIRTree) {
                        documents = ((CDIRTree) tree).getDocumentStore().readSet(id);
                        ((CDIRTree) tree).insertData(id, region, documents);
                    } else {
                        tree.insertData(id, region);
                    }
                } else {
                    tree.insertData(id, region);
                }
                count++;
            } catch (Exception e) {
                logger.warn("Error processing line '{}': {}", line, e.getMessage());
            }
        }
        logger.info("Processed {} spatial data points.", count);
        return count;
    }

    protected void collectSpatialComponentMetrics(long initMem, long startTime) {
        long endTime = System.currentTimeMillis();
        StatisticsLogic.stopMemoryMonitoring();
        long maxMemoryUsage = StatisticsLogic.getMaxMemoryUsage();
        StatisticsLogic.rTreePeakMemUsed = (maxMemoryUsage - initMem);
        StatisticsLogic.rTreeMemUsed = StatisticsLogic.getClearedMem() - initMem;
        StatisticsLogic.rTreeJVMPeakMemUsed = maxMemoryUsage;
        StatisticsLogic.rTreeBuildTime = (endTime - startTime);
    }

    protected void collectTextualComponentMetrics(long initMem, long startTime) {
        long endTime = System.currentTimeMillis();
        StatisticsLogic.stopMemoryMonitoring();
        long maxMemoryUsage = StatisticsLogic.getMaxMemoryUsage();
        StatisticsLogic.irTreePeakMemUsed = (maxMemoryUsage - initMem);
        StatisticsLogic.irTreeMemUsed = StatisticsLogic.getClearedMem() - initMem;
        StatisticsLogic.irTreeJVMPeakMemUsed = maxMemoryUsage;
        StatisticsLogic.irTreeBuildTime = (endTime - startTime);
    }

    protected void logSpatialComponentStatistics(ISpatialIndex tree, int count, String treeType) {
        logger.info("Operations: {}", count);
        logger.info("Tree: {}", tree);
        logger.info("Rtree ({}) build time: {} ms", treeType, StatisticsLogic.rTreeBuildTime);
        logger.info("Rtree ({}) memory usage: {} Megabytes", treeType, (StatisticsLogic.rTreeMemUsed / 1024) / 1024);
        logger.info("Rtree ({}) peak memory usage: {} Megabytes", treeType, (StatisticsLogic.rTreePeakMemUsed / 1024) / 1024);
        logger.info("Rtree ({}) JVM peak memory usage: {} Megabytes", treeType, (StatisticsLogic.rTreeJVMPeakMemUsed / 1024) / 1024);
    }

    protected void logTextualComponentStatistics(String treeType) {
        logger.info("({})tree build in: {} ms", treeType, StatisticsLogic.irTreeBuildTime);
        logger.info("({})tree memory usage: {} Megabytes", treeType, (StatisticsLogic.irTreeMemUsed / 1024) / 1024);
        logger.info("({})tree peak memory usage: {} Megabytes", treeType, (StatisticsLogic.irTreePeakMemUsed / 1024) / 1024);
        logger.info("({})tree JVM peak memory usage: {} Megabytes", treeType, (StatisticsLogic.irTreeJVMPeakMemUsed / 1024) / 1024);
    }

    protected void validateTree(ISpatialIndex tree) {
        if (!tree.isIndexValid()) {
            logger.error("Structure is INVALID!");
        }
    }
}

package org.ual.utils.main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ual.utils.ResultQueryTotal;
import org.ual.utils.config.CsvFormatConfig;
import org.ual.utils.stats.QueryStats;
import org.ual.utils.stats.QueryStatsData;
import org.ual.utils.stats.QueryStatisticsNEW;

import java.io.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public class StatisticsLogic {
    private final String metricsDirectoryPath;
    private CsvFormatConfig csvFormatConfig;
    public static long weightIndexMemUsed;
    public static long weightIndexPeakMemUsed;
    public static long rTreeMemUsed;
    public static long rTreePeakMemUsed;
    public static long rTreeJVMPeakMemUsed;
    public static long irTreeMemUsed;
    public static long irTreePeakMemUsed;
    public static long irTreeJVMPeakMemUsed;
    public static long weightIndexBuildTime;
    public static long rTreeBuildTime;
    public static long irTreeBuildTime;
    public ResultQueryTotal globalQueryResults;
    public HashMap<String, QueryStats> queriesStats = new HashMap<>();
    public HashMap<String, QueryStatisticsNEW> queriesStatsNew = new HashMap<>();

    // Testing memory usage using threads
    public static long maxMemoryUsage = 0;
    private static boolean monitoring = false;
    private static Thread memoryMonitorThread;
    // Elapsed time tracking
    private static long startTime = 0;
    private static long elapsedTime = 0;

    private static final Logger logger = LogManager.getLogger(StatisticsLogic.class);


    public StatisticsLogic(String metricsDirectoryPath) {
        this(metricsDirectoryPath, CsvFormatConfig.defaultConfig());
    }

    public StatisticsLogic(String metricsDirectoryPath, CsvFormatConfig csvFormatConfig) {
        this.metricsDirectoryPath = metricsDirectoryPath;
        this.csvFormatConfig = csvFormatConfig != null ? csvFormatConfig : CsvFormatConfig.defaultConfig();
    }

    public void setCsvFormatConfig(CsvFormatConfig csvFormatConfig) {
        this.csvFormatConfig = csvFormatConfig != null ? csvFormatConfig : CsvFormatConfig.defaultConfig();
    }

    /**
     * Get the memory used by the JVM without garbage collection
     * @return the memory used by the JVM
     */
    public static long getMemUsed() {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        return totalMemory - freeMemory;
    }

    /**
      * Get the memory used by the JVM after forcing garbage collection.
      * WARNING: This method introduces a 500ms delay due to multiple GC cycles.
      * Do not use between time measurements as it will affect the results.
      * @return the memory used by the JVM after garbage collection
      */
    public static long getClearedMem() {
        long previousMemory = Long.MAX_VALUE;
        long currentMemory = getMemUsed();

        for (int i = 0; i < 5 && currentMemory < previousMemory; i++) {
            previousMemory = currentMemory;

            System.gc();
            System.runFinalization();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error("Fail to clean memory", e);
                Thread.currentThread().interrupt();
                break;
            }

            currentMemory = getMemUsed();
        }

        //return getMemUsed();
        return currentMemory;
    }

    /**
     * Start monitoring the memory usage
     * WARNING!: This method should be called before the memory usage is expected to increase
     */
    public static void startMemoryMonitoring() {
        monitoring = true;
        maxMemoryUsage = 0; // Reset the max memory usage
        memoryMonitorThread = new Thread(() -> {
            while (monitoring) {
                long currentMemoryUsage = getMemUsed();
                if (currentMemoryUsage > maxMemoryUsage) {
                    maxMemoryUsage = currentMemoryUsage;
                }
                try {
                    Thread.sleep(100); // Adjust the interval as needed
                } catch (InterruptedException e) {
                    logger.error("Memory monitoring thread interrupted", e);
                }
            }
        });
        memoryMonitorThread.start();
    }

    /**
     * Stop monitoring the memory usage
     */
    public static void stopMemoryMonitoring() {
        monitoring = false;
        if (memoryMonitorThread != null) {
            try {
                memoryMonitorThread.join();
            } catch (InterruptedException e) {
                logger.error("Failed to stop memory monitoring thread", e);
            }
        }
    }

    /**
     * Get the maximum memory usage
     * WARNING!: This method should NOT be called while the memory monitor is running to prevent a race condition
     * @return the maximum memory usage by the JVM
     */
    public static long getMaxMemoryUsage() {
        return maxMemoryUsage;
    }


    /**
     * Start measuring elapsed time
     */
    public static void startTimeMeasurement() {
        startTime = System.nanoTime();
    }

    /**
     * Stop measuring elapsed time and return the elapsed time in milliseconds
     * @return elapsed time in milliseconds
     */
    public static long stopTimeMeasurement() {
        if (startTime == 0) {
            logger.warn("Time measurement was not started properly");
            return 0;
        }
        elapsedTime = System.nanoTime() - startTime;
        startTime = 0;
        return elapsedTime / 1_000_000; // Convert nanoseconds to milliseconds
    }

    /**
     * Get the last measured elapsed time in milliseconds
     * @return elapsed time in milliseconds
     */
    public static long getElapsedTimeMillis() {
        return elapsedTime / 1_000_000;
    }

    /**
     * Get the last measured elapsed time in nanoseconds
     * @return elapsed time in nanoseconds
     */
    public static long getElapsedTimeNanos() {
        return elapsedTime;
    }

    /**
     * Write the stats of the queries results in the metrics directory, int txt and csv format
     */
    public void writeResults() {
        logger.info("Writing Results...");

        StatisticsLogic.resultWriter(queriesStats, metricsDirectoryPath, true, csvFormatConfig);
        StatisticsLogic.resultWriter(queriesStats, metricsDirectoryPath, false);
        StatisticsLogic.resultWriterDetailed(queriesStatsNew, metricsDirectoryPath, csvFormatConfig);
        logDetailedStatsSummary();
        queriesStats.clear(); // Fix a "buffer leak" when changing the query type
        queriesStatsNew.clear();

        logger.info("Done");
    }


    private static void resultWriter(HashMap<String, QueryStats> queriesStats, String metricsDirectoryPath, boolean writeCSV, CsvFormatConfig csvFormatConfig) {
        for (Map.Entry<String, QueryStats> qryType : queriesStats.entrySet()) {
            writeData(metricsDirectoryPath, qryType.getKey(), "GroupSize", qryType.getValue().groupSizes, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "Alpha", qryType.getValue().alphas, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "NumberKeywords", qryType.getValue().numKeywords, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "Percentages", qryType.getValue().percentages, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "KeywordSpaceSize", qryType.getValue().keyboardSpaceSizes, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "SpaceArea", qryType.getValue().querySpaceAreas, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "Radius", qryType.getValue().radii, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "TopK", qryType.getValue().topks, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "SpatialDistance", qryType.getValue().spatialDistance, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "TextualSimilarity", qryType.getValue().textualSimilarity, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "CombinedST", qryType.getValue().combinedST, writeCSV, csvFormatConfig);
            writeData(metricsDirectoryPath, qryType.getKey(), "Defaults", qryType.getValue().defaults, writeCSV, csvFormatConfig);
        }
    }

    private static void resultWriter(HashMap<String, QueryStats> queriesStats, String metricsDirectoryPath, boolean writeCSV) {
        resultWriter(queriesStats, metricsDirectoryPath, writeCSV, CsvFormatConfig.defaultConfig());
    }

    private static void writeData(String metricsDirectoryPath, String queryKey, String dataType, List<QueryStatsData> data, boolean writeCSV, CsvFormatConfig csvFormatConfig) {
        if (!data.isEmpty()) {
            String fileName = "[" + queryKey + "]" + dataType;
            if (writeCSV)
                writeCSV(metricsDirectoryPath, fileName, data, csvFormatConfig);
            else
                writeTXT(metricsDirectoryPath, fileName, data);
        }
    }


    private static void writeTXT(String metricsDirectoryPath, String fileName, List<QueryStatsData> qryData) {
        try (FileWriter fw = new FileWriter(metricsDirectoryPath + fileName + ".txt", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println("==================================================");
            out.println("");
            for (QueryStatsData resultData : qryData) {
                out.println("Parameter: " + resultData.queryType + " - Value: " + resultData.value);
                out.printf("[%s] totalTime= %.4fms | medianTime= %.4fms | minTime= %.4fms | maxTime= %.4fms | avgTime= %.4fms | totalNodesVisited= %d | avgNodesVisited= %.4f | avgSpatCost= %.6f | avgIRCost= %.6f | memDeltaBytes= %d \n",
                        resultData.queryType,
                        resultData.totalTime,
                        resultData.medianTime,
                        resultData.minTime,
                        resultData.maxTime,
                        resultData.averageTime,
                        resultData.totalNodesVisited,
                        resultData.averageNodesVisited,
                        resultData.averageSpatialCost,
                        resultData.averageIRCost,
                        resultData.memoryDeltaBytes);
                out.println("");
            }
            out.println("");
            out.println("==================================================");
        } catch (IOException e) {
            logger.error("Fail to write results", e);
        }
    }

    private static void writeCSV(String metricsDirectoryPath, String fileName, List<QueryStatsData> qryData, CsvFormatConfig csvFormatConfig) {
        ArrayList<String> headers = new ArrayList<>();
        boolean writeHeaders = csvFormatConfig != null && csvFormatConfig.isIncludeHeaders();
        String separator = csvFormatConfig != null ? csvFormatConfig.normalizedSeparator() : ",";

        for (QueryStatsData resultData : qryData) {
            headers.add(resultData.value);
        }

        int maxIterations = 1;
        for (QueryStatsData resultData : qryData) {
            if (resultData.perIterationTimes != null && !resultData.perIterationTimes.isEmpty()) {
                maxIterations = Math.max(maxIterations, resultData.perIterationTimes.size());
            }
        }

        // Check if file exist to skip writing headers
        File file = new File(metricsDirectoryPath + fileName + ".csv");
        if (file.exists())
            writeHeaders = false;

        try (FileWriter fw = new FileWriter(metricsDirectoryPath + fileName + ".csv", true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            // Write headers
            if (writeHeaders)
                out.println(String.join(separator, headers));

            // Write rows
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                ArrayList<String> row = new ArrayList<>();
                for (QueryStatsData resultData : qryData) {
                    double value = resultData.totalTime;
                    if (resultData.perIterationTimes != null && resultData.perIterationTimes.size() > iteration) {
                        value = resultData.perIterationTimes.get(iteration);
                    }
                    row.add(formatNumber(value, csvFormatConfig));
                }
                out.println(String.join(separator, row));
            }

            if (csvFormatConfig != null && csvFormatConfig.isWriteGnuplotMetadata()) {
                writeGnuplotMetadata(metricsDirectoryPath, fileName, separator, csvFormatConfig.normalizedDecimalSymbol());
            }

        } catch (IOException e) {
            logger.error("Fail to write results in csv: ", e);
        }
    }

    private static void resultWriterDetailed(HashMap<String, QueryStatisticsNEW> queriesStatsNew,
                                             String metricsDirectoryPath,
                                             CsvFormatConfig csvFormatConfig) {
        String separator = csvFormatConfig != null ? csvFormatConfig.normalizedSeparator() : ",";
        boolean includeHeaders = csvFormatConfig != null && csvFormatConfig.isIncludeHeaders();

        for (Map.Entry<String, QueryStatisticsNEW> entry : queriesStatsNew.entrySet()) {
            String fileName = "[" + entry.getKey() + "]DetailedMetrics.csv";
            File output = new File(metricsDirectoryPath + fileName);
            boolean shouldWriteHeader = includeHeaders && !output.exists();

            try (FileWriter fw = new FileWriter(output, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {

                if (shouldWriteHeader) {
                    out.println(String.join(separator,
                            "queryType", "value", "numIterations", "totalTimeMs", "medianTimeMs", "minTimeMs", "maxTimeMs", "avgTimeMs", "totalNodesVisited", "avgNodesVisited",
                            "avgSpatialCost", "avgTextualCost", "avgIRCost",
                            "totalResults", "avgResults", "totalReturnedPairs", "avgReturnedPairs",
                            "memoryDeltaBytes"));
                }

                for (QueryStatsData data : entry.getValue().getEntries()) {
                    out.println(String.join(separator,
                            safeString(data.queryType),
                            safeString(data.value),
                            String.valueOf(data.numIterations),
                            formatNumber(data.totalTime, csvFormatConfig),
                            formatNumber(data.medianTime, csvFormatConfig),
                            formatNumber(data.minTime, csvFormatConfig),
                            formatNumber(data.maxTime, csvFormatConfig),
                            formatNumber(data.averageTime, csvFormatConfig),
                            String.valueOf(data.totalNodesVisited),
                            formatNumber(data.averageNodesVisited, csvFormatConfig),
                            formatNumber(data.averageSpatialCost, csvFormatConfig),
                            formatNumber(data.averageTextualCost, csvFormatConfig),
                            formatNumber(data.averageIRCost, csvFormatConfig),
                            String.valueOf(data.totalResultsReturned),
                            formatNumber(data.averageResultsReturned, csvFormatConfig),
                            String.valueOf(data.totalResultsReturned),
                            formatNumber(data.averageResultsReturned, csvFormatConfig),
                            String.valueOf(data.memoryDeltaBytes)));
                }

                if (csvFormatConfig != null && csvFormatConfig.isWriteGnuplotMetadata()) {
                    writeGnuplotMetadata(metricsDirectoryPath, "[" + entry.getKey() + "]DetailedMetrics", separator,
                            csvFormatConfig.normalizedDecimalSymbol());
                }
            } catch (IOException e) {
                logger.error("Fail to write detailed csv metrics", e);
            }
        }
    }

    private void logDetailedStatsSummary() {
        for (Map.Entry<String, QueryStatisticsNEW> entry : queriesStatsNew.entrySet()) {
            QueryStatisticsNEW stats = entry.getValue();
            double totalTime = 0;
            long totalResults = 0;
            double avgNodes = 0;

            for (QueryStatsData data : stats.getEntries()) {
                totalTime += data.totalTime;
                totalResults += data.totalResultsReturned;
                avgNodes += data.averageNodesVisited;
            }

            int size = stats.getEntries().size();
            double avgNodesAcrossParams = size == 0 ? 0 : avgNodes / size;
            logger.info("DetailedStats [{}] paramRuns={} totalTimeMs={} totalResults={} avgNodes={} avgMemDeltaBytes={}",
                    entry.getKey(), size,
                    formatNumber(totalTime, csvFormatConfig),
                    totalResults,
                    formatNumber(avgNodesAcrossParams, csvFormatConfig),
                    size == 0 ? 0 : Math.round(stats.getEntries().stream().mapToLong(d -> d.memoryDeltaBytes).average().orElse(0)));
        }
    }

    private static void writeGnuplotMetadata(String metricsDirectoryPath, String fileName, String separator, char decimalSymbol) {
        File metadataFile = new File(metricsDirectoryPath + fileName + ".gnuplot.meta");
        try (FileWriter fw = new FileWriter(metadataFile, false);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println("separator=" + separator);
            out.println("decimalSymbol=" + decimalSymbol);
        } catch (IOException e) {
            logger.error("Fail to write gnuplot metadata", e);
        }
    }

    private static String formatNumber(double value, CsvFormatConfig csvFormatConfig) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        char decimal = csvFormatConfig != null ? csvFormatConfig.normalizedDecimalSymbol() : '.';
        symbols.setDecimalSeparator(decimal);
        DecimalFormat decimalFormat = new DecimalFormat("0.########", symbols);
        return decimalFormat.format(value);
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }



//    private static void writeCSV(String metricsDirectoryPath, String fileName, List<QueryStatsData> qryData) {
//        ArrayList<String> headers = new ArrayList<>();
//        ArrayList<String> row = new ArrayList<>();
//        boolean writeHeaders = true;
//
//        //HashMap<String, HashMap<String, String>> values = new HashMap<>();
//        HashMap<String, ArrayList<String>> values = new HashMap<>();
//        // paramName, paramVal, paramVal, ...
//        // queryName, val, val, ...
//
//        for (QueryStatsData resultData : qryData) {
//            headers.add(resultData.value);
//            row.add(String.valueOf(resultData.totalTime));
//
//            // Check if file exist to skip writing headers
//            File file = new File(metricsDirectoryPath + fileName + ".csv");
//            if (file.exists())
//                writeHeaders = false;
//
//            try (FileWriter fw = new FileWriter(metricsDirectoryPath + fileName + ".csv", true);
//                 BufferedWriter bw = new BufferedWriter(fw);
//                 PrintWriter out = new PrintWriter(bw)) {
//
//                // Write headers
//                if (writeHeaders)
//                    out.println(String.join(",", headers));
//
//                // Write rows
//                out.println(String.join(",", row));
//
//            } catch (IOException e) {
//                logger.error("Fail to write results", e);
//            }
//
//        }
//
//    }
}
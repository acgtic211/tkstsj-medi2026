package org.ual.utils.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds statistics for one evaluated (queryType, parameterValue) combination.
 * When numIterations > 1 the fields represent averages/medians across iterations;
 * raw per-iteration data is stored in the {@code perIteration*} lists.
 */
public class QueryStatsData {
    public String queryType;
    public String value;

    // Time (milliseconds)
    /** Mean total time across iterations (ms). */
    public double totalTime;
    /** Mean per-query time (totalTime / numberOfQueries). */
    public double averageTime;
    /** Median total time across iterations (ms). */
    public double medianTime;
    public double minTime;
    public double maxTime;

    // Nodes visited
    /** Mean total nodes visited across all queries in one iteration. */
    public long totalNodesVisited;
    /** Mean per-query nodes visited (totalNodesVisited / numberOfQueries). */
    public double averageNodesVisited;

    // Cost (aggregate queries)
    public double averageSpatialCost;
    public double averageTextualCost;
    public double averageIRCost;

    // Results
    public long totalResultsReturned;
    public double averageResultsReturned;

    // Memory (bytes)
    public long memoryDeltaBytes;

    // Per-iteration raw data
    public int numIterations = 1;
    /** Total execution time (ms) per iteration. */
    public List<Double> perIterationTimes = new ArrayList<>();
    /** Total nodes visited per iteration (summed over all queries). */
    public List<Long> perIterationNodesVisited = new ArrayList<>();

}

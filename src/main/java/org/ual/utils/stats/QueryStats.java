package org.ual.utils.stats;

import java.util.ArrayList;
import java.util.List;

public class QueryStats {
    public String queryName;
    //public ArrayList<QueryStatsData> results;

    // Group Size
    public List<QueryStatsData> groupSizes;

    // Percentage
    public List<QueryStatsData> percentages;

    // Number of Keywords
    public List<QueryStatsData> numKeywords;

    // Query Space Area Percentage
    public List<QueryStatsData> querySpaceAreas;

    // Keyword Space Size Percentage
    public List<QueryStatsData> keyboardSpaceSizes;

    // Top K
    public List<QueryStatsData> topks;

    // Alpha
    public List<QueryStatsData> alphas;

    // Radius
    public List<QueryStatsData> radii;

    public List<QueryStatsData> spatialDistance;

    public List<QueryStatsData> textualSimilarity;

    public List<QueryStatsData> combinedST;

    public List<QueryStatsData> defaults;

    public QueryStats(String queryName) {
        this.queryName = queryName;

        groupSizes = new ArrayList<>();
        percentages = new ArrayList<>();
        numKeywords = new ArrayList<>();
        querySpaceAreas = new ArrayList<>();
        keyboardSpaceSizes = new ArrayList<>();
        topks = new ArrayList<>();
        alphas = new ArrayList<>();
        radii = new ArrayList<>();
        spatialDistance = new ArrayList<>();
        textualSimilarity = new ArrayList<>();
        combinedST = new ArrayList<>();
        defaults = new ArrayList<>();
    }

//    public QueryStats() {
//        this.results = new ArrayList<>();
//    }

//    public QueryStats(String queryName) {
//        this.queryName = queryName;
//        this.results = new ArrayList<>();
//    }

//    public QueryStats(String queryName, ArrayList<QueryStatsData> results) {
//        this.queryName = queryName;
//        this.results = results;
//    }
}

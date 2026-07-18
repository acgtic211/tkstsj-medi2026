package org.ual.utils;

import java.util.ArrayList;
import java.util.List;

public class ResultQueryTotal {
    public String queryType;

    // Group Size
    public List<ResultQueryParameter> groupSizes;

    // Percentage
    public List<ResultQueryParameter> percentages;

    // Number of Keywords
    public List<ResultQueryParameter> numKeywords;

    // Query Space Area Percentage
    public List<ResultQueryParameter> querySpaceAreas;

    // Keyword Space Size Percentage
    public List<ResultQueryParameter> keyboardSpaceSizes;

    // Top K
    public List<ResultQueryParameter> topks;

    // Alpha
    public List<ResultQueryParameter> alphas;

    // Radius
    public List<ResultQueryParameter> radii;

    public ResultQueryTotal(String queryType) {
        this.queryType = queryType;

        groupSizes = new ArrayList<>();
        percentages = new ArrayList<>();
        numKeywords = new ArrayList<>();
        querySpaceAreas = new ArrayList<>();
        keyboardSpaceSizes = new ArrayList<>();
        topks = new ArrayList<>();
        alphas = new ArrayList<>();
        radii = new ArrayList<>();
    }
}

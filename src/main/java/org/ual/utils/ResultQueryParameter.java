package org.ual.utils;

import java.util.ArrayList;

public class ResultQueryParameter {
    public String paramName;
    public String paramValue;

    public ArrayList<ResultQueryCost> results;

    public ResultQueryParameter() {
        this.results = new ArrayList<>();
    }

    public ResultQueryParameter(String paramName, String paramValue, ArrayList<ResultQueryCost> results) {
        this.paramName = paramName;
        this.paramValue = paramValue;
        this.results = results;
    }
}

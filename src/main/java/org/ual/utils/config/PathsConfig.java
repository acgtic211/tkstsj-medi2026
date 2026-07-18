package org.ual.utils.config;

public class PathsConfig {
    private String temp = "src/main/resources/temp/";
    private String results = "src/main/resources/results/";
    private String metrics = "src/main/resources/results/";
    private String log = "src/main/resources/log/";

    // Getters and setters
    public String getTemp() {
        return temp;
    }

    public void setTemp(String temp) {
        this.temp = temp;
    }

    public String getResults() {
        return results;
    }

    public void setResults(String results) {
        this.results = results;
    }

    public String getMetrics() {
        return metrics;
    }

    public void setMetrics(String metrics) {
        this.metrics = metrics;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }
}

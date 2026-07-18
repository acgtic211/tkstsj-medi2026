package org.ual.utils.config;

import org.ual.utils.experiment.AggregateExperiment;
import org.ual.utils.experiment.JoinExperiment;
import org.ual.utils.experiment.KnnExperiment;
import org.ual.utils.experiment.RangeExperiment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExperimentConfig {
    private int numIterations = 1;
    private boolean writeQueryResults = false;
    private boolean runAggregateQueries = true;
    private boolean runKnnQueries = true;
    private boolean runRangeQueries = true;
    private boolean runJoinQueries = false;
    private List<AggregateExperiment> aggregateExperiments;
    private List<KnnExperiment> knnExperiments;
    private List<RangeExperiment> rangeExperiments;
    private List<JoinExperiment> joinExperiments;

    // Getters and setters
    public int getNumIterations() { return numIterations; }
    public void setNumIterations(int numIterations) { this.numIterations = numIterations; }

    public boolean isWriteQueryResults() { return writeQueryResults; }
    public void setWriteQueryResults(boolean writeQueryResults) { this.writeQueryResults = writeQueryResults; }

    public boolean isRunAggregateQueries() { return runAggregateQueries; }
    public void setRunAggregateQueries(boolean runAggregateQueries) { this.runAggregateQueries = runAggregateQueries; }

    public boolean isRunKnnQueries() { return runKnnQueries; }
    public void setRunKnnQueries(boolean runKnnQueries) { this.runKnnQueries = runKnnQueries; }

    public boolean isRunRangeQueries() { return runRangeQueries; }
    public void setRunRangeQueries(boolean runRangeQueries) { this.runRangeQueries = runRangeQueries; }

    public boolean isRunJoinQueries() { return runJoinQueries; }
    public void setRunJoinQueries(boolean runJoinQueries) { this.runJoinQueries = runJoinQueries; }

    public List<AggregateExperiment> getAggregateExperiments() { return aggregateExperiments; }
    public void setAggregateExperiments(List<AggregateExperiment> aggregateExperiments) { this.aggregateExperiments = aggregateExperiments; }

    public List<KnnExperiment> getKnnExperiments() { return knnExperiments; }
    public void setKnnExperiments(List<KnnExperiment> knnExperiments) { this.knnExperiments = knnExperiments; }

    public List<RangeExperiment> getRangeExperiments() { return rangeExperiments; }
    public void setRangeExperiments(List<RangeExperiment> rangeExperiments) { this.rangeExperiments = rangeExperiments; }

    public List<JoinExperiment> getJoinExperiments() { return joinExperiments; }
    public void setJoinExperiments(List<JoinExperiment> joinExperiments) { this.joinExperiments = joinExperiments; }

    public String getAggregatorType() {
        // Extract aggregator type from aggregateExperiments
        if (aggregateExperiments != null && !aggregateExperiments.isEmpty()) {
            List<String> aggregateFunctions = aggregateExperiments.get(0).getAggregateFunctions();
            if (aggregateFunctions != null && !aggregateFunctions.isEmpty()) {
                return aggregateFunctions.get(0); // Return first aggregator type
            }
        }
        return "SUM"; // Default fallback
    }

    public List<String> getAggregateQueryTypes() {
        List<String> allTypes = new ArrayList<>();
        if (aggregateExperiments != null) {
            for (AggregateExperiment exp : aggregateExperiments) {
                if (exp.getQueryTypes() != null) {
                    allTypes.addAll(exp.getQueryTypes());
                }
            }
        }
        return allTypes.isEmpty() ? Arrays.asList("GNNK") : allTypes;
    }

    public List<String> getKnnQueryTypes() {
        List<String> allTypes = new ArrayList<>();
        if (knnExperiments != null) {
            for (KnnExperiment exp : knnExperiments) {
                if (exp.getQueryTypes() != null) {
                    allTypes.addAll(exp.getQueryTypes());
                }
            }
        }
        return allTypes.isEmpty() ? Arrays.asList("BkSK") : allTypes;
    }

    public List<String> getRangeQueryTypes() {
        List<String> allTypes = new ArrayList<>();
        if (rangeExperiments != null) {
            for (RangeExperiment exp : rangeExperiments) {
                if (exp.getQueryTypes() != null) {
                    allTypes.addAll(exp.getQueryTypes());
                }
            }
        }
        return allTypes.isEmpty() ? Arrays.asList("BRSK") : allTypes;
    }

    public List<String> getJoinQueryTypes() {
        List<String> allTypes = new ArrayList<>();
        if (joinExperiments != null) {
            for (JoinExperiment exp : joinExperiments) {
                if (exp.getQueryTypes() != null) {
                    allTypes.addAll(exp.getQueryTypes());
                }
            }
        }
        return allTypes.isEmpty() ? Arrays.asList("STSJ") : allTypes;
    }
}

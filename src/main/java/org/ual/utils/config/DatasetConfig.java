package org.ual.utils.config;

import org.ual.utils.sampling.SamplingStrategy;

public class DatasetConfig {
    private DatasetType datasetType = DatasetType.TEST;
    private double usagePercentage = 1.0;
    private SamplingStrategy.SamplingMethod samplingMethod = SamplingStrategy.SamplingMethod.RANDOMIZED;
    private long samplingRandomSeed = 42L;
    private int samplingStartLine = 0;

    public DatasetConfig() {}

    public DatasetType getDatasetType() {
        return datasetType;
    }

    public void setDatasetType(DatasetType datasetType) {
        this.datasetType = datasetType;
    }

    public double getUsagePercentage() {
        return usagePercentage;
    }

    public void setUsagePercentage(double usagePercentage) {
        this.usagePercentage = usagePercentage;
    }

    public SamplingStrategy.SamplingMethod getSamplingMethod() {
        return samplingMethod;
    }

    public void setSamplingMethod(SamplingStrategy.SamplingMethod samplingMethod) {
        this.samplingMethod = samplingMethod;
    }

    public long getSamplingRandomSeed() {
        return samplingRandomSeed;
    }

    public void setSamplingRandomSeed(long samplingRandomSeed) {
        this.samplingRandomSeed = samplingRandomSeed;
    }

    public int getSamplingStartLine() {
        return samplingStartLine;
    }

    public void setSamplingStartLine(int samplingStartLine) {
        this.samplingStartLine = samplingStartLine;
    }
}

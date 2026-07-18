package org.ual.utils.config;

public class ApplicationConfig {
    private IndexConfig index = new IndexConfig();
    private QueryConfig query = new QueryConfig();
    private DatasetConfig dataset = new DatasetConfig();
    private PathsConfig paths = new PathsConfig();
    private ExperimentConfig experiment = new ExperimentConfig();
    private CsvFormatConfig csvFormat = CsvFormatConfig.defaultConfig();

    // Getters and setters
    public IndexConfig getIndex() {
        return index;
    }

    public void setIndex(IndexConfig index) {
        this.index = index;
    }

    public QueryConfig getQuery() {
        return query;
    }

    public void setQuery(QueryConfig query) {
        this.query = query;
    }

    public DatasetConfig getDataset() {
        return dataset;
    }

    public void setDataset(DatasetConfig dataset) {
        this.dataset = dataset;
    }

    public PathsConfig getPaths() {
        return paths;
    }

    public void setPaths(PathsConfig paths) {
        this.paths = paths;
    }

    public ExperimentConfig getExperiment() {
        return experiment;
    }

    public void setExperiment(ExperimentConfig experiment) {
        this.experiment = experiment;
    }

    public CsvFormatConfig getCsvFormat() {
        return csvFormat;
    }

    public void setCsvFormat(CsvFormatConfig csvFormat) {
        this.csvFormat = csvFormat;
    }
}

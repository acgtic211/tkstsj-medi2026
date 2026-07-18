package org.ual.spatialindex.rtree;

import org.ual.spatialindex.rtreebase.AbstractStatistics;


public class Statistics extends AbstractStatistics {
    public Statistics(AbstractStatistics s) {
        super(s);
    }

    public Statistics() {
        super();
    }

    @Override
    public AbstractStatistics clone() {
        return new Statistics(this);
    }
}

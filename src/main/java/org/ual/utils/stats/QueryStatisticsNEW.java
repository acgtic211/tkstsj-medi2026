package org.ual.utils.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueryStatisticsNEW {
    private final String queryName;
    private final List<QueryStatsData> entries = new ArrayList<>();

    public QueryStatisticsNEW(String queryName) {
        this.queryName = queryName;
    }

    public String getQueryName() {
        return queryName;
    }

    public void addEntry(QueryStatsData data) {
        entries.add(data);
    }

    public List<QueryStatsData> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}


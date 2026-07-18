package org.ual.querytype;

import org.ual.algorithm.aggregator.IAggregator;
import org.ual.query.Query;
import org.ual.query.QueryResult;
import org.ual.spatialindex.spatialindex.Cost;

import java.util.ArrayList;
import java.util.List;

public class AggregateSKNNQuery extends Query {
    public List<Query> queries;
    public IAggregator aggregator;
    public int groupSize;
    public int subGroupSize;



    public AggregateSKNNQuery(int id) {
        super(id);
    }

    public AggregateSKNNQuery(int id, List<Query> queries, int groupSize, IAggregator aggregator) {
        super(id);
        this.queries = queries;
        this.aggregator = aggregator;
        this.groupSize = groupSize;
    }

    public AggregateSKNNQuery(int id, int groupSize, IAggregator aggregator) {
        super(id);
        this.groupSize = groupSize;
        this.aggregator = aggregator;
    }

    // SGNNKQuery
    public void setSGNNKQuery(List<Query> queries, int groupSize, int subGroupSize, IAggregator aggregator) {
        this.queries = queries;
        this.aggregator = aggregator;
        this.groupSize = groupSize;
        this.subGroupSize = subGroupSize;

        assert subGroupSize <= queries.size() :
                "Sub-group size must be less then the number of queries";
    }

    // GNNKQuery
    public void setGNNKQuery(List<Query> queries, int groupSize, IAggregator aggregator) {
        this.queries = queries;
        this.aggregator = aggregator;
        this.groupSize = groupSize;
    }



    public List<Double> getWeights() {
        List<Double> weights = new ArrayList<>();
        for (Query query : queries) {
            weights.add(query.getWeight());
        }

        return weights;
    }

    @Override
    public String toString() {
        return "AggregateSKNNQueryNew{" +
                "id=" + getId() +
                ", weight=" + getWeight() +
                ", location=" + getLocation() +
                ", keywords=" + getKeywords() +
                ", keywordWeights=" + getKeywordWeights() +
                ", queries=" + queries +
                '}';
    }



    public static class Result extends QueryResult implements Comparable<Result>{
        private List<Integer> queryIds;
        private Cost aggregateCost;

//        public Result(int id, int minDistance) {
//            super(id, minDistance);
//        }
//
//        public Result(int id, Cost cost, int minDistance) {
//            super(id, cost, minDistance);
//        }

//        public Result(int id, Cost cost) {
//            super(id, cost);
//        }

        public Result(int id, Cost aggregateCost) {
            super(id);
            this.aggregateCost = aggregateCost;
        }

        public Result(int id, Cost aggregateCost, List<Integer> minimumCostQueryIds) {
            super(id);
            this.aggregateCost = aggregateCost;
            this.queryIds = minimumCostQueryIds;
        }

        public int getId() {
            return id;
        }

        public List<Integer> getQueryIds() {
            return queryIds;
        }

        public Cost getAggregateCost() {
            return aggregateCost;
        }

        @Override
        public int compareTo(Result other) {
            if (this.aggregateCost.getCombinedCost() < other.aggregateCost.getCombinedCost())
                return -1;
            else if (this.aggregateCost.getCombinedCost() > other.aggregateCost.getCombinedCost())
                return 1;
            else {
                if (this.id < other.id)
                    return -1;
                else if (this.id > other.id)
                    return 1;
                return 0;
            }
        }

        @Override
        public String toString() {
            return "AggregateSKNNQuery.Result{" +
                    "id=" + id +
                    ", aggregateCost=" + aggregateCost +
                    ", queryIds=" + queryIds +
                    '}';
        }
    }

}

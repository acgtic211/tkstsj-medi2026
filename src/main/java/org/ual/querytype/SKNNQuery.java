package org.ual.querytype;

import org.ual.query.Query;
import org.ual.query.QueryResult;
import org.ual.spatialindex.spatialindex.Point;

import java.util.List;

public class SKNNQuery extends Query {
    //public Query query;

    public SKNNQuery(int id) {
        super(id);
    }

    public SKNNQuery(int id, double weight, Point location, List<Integer> keywords, List<Double> keywordWeights) {
        super(id, weight, location, keywords, keywordWeights);
    }

    public SKNNQuery(int id, Point location, List<Integer> keywords) {
        super(id, location, keywords);
    }

    public SKNNQuery(Query query) {
        super(query);
    }

    // GNNKQuery
//    public void setKNNKQuery(Query query) {
//        this.query = query;
//    }


    @Override
    public String toString() {
        return "SKNNQueryNew{" +
                "id=" + getId() +
                ", weight=" + getWeight() +
                ", location=" + getLocation() +
                ", keywords=" + getKeywords() +
                ", keywordWeights=" + getKeywordWeights() +
                '}';
    }

    public static class Result extends QueryResult implements Comparable<Result> {
        private double spatialCost; // Minimum spatial distance to the nearest object
        private double combinedCost;

        public Result(int id, double spatialCost) {
            super(id);
            this.spatialCost = spatialCost;
        }

        public Result(int id, double combinedCost, double spatialCost) {
            super(id);
            this.combinedCost = combinedCost;
            this.spatialCost = spatialCost;
        }

        public int getId() {
            return id;
        }

        public double getSpatialCost() {
            return spatialCost;
        }

        public double getCombinedCost() {
            return combinedCost;
        }

        @Override
        public int compareTo(Result o) {
            if (this.spatialCost < o.spatialCost)
                return -1;
            else if (this.spatialCost > o.spatialCost)
                return 1;
            else {
                if (this.id < o.id)
                    return -1;
                else if (this.id > o.id)
                    return 1;
                return 0;
            }
        }

        @Override
        public String toString() {
            return "Result{" +
                    "id=" + id +
                    ", minDistance=" + spatialCost +
                    ", cost=" + combinedCost +
                    '}';
        }
    }
}

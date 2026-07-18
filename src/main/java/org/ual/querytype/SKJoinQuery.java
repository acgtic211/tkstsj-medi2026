package org.ual.querytype;

import org.ual.query.Query;
import org.ual.query.QueryResult;
import org.ual.spatialindex.spatialindex.Point;
import org.ual.spatialindex.spatialindex.Region;

import java.util.List;

public class SKJoinQuery extends Query {
    private Region spatialWindow;

    public SKJoinQuery(int id) {
        super(id);
    }

    public SKJoinQuery(int id, double weight, Point location, List<Integer> keywords, List<Double> keywordWeights) {
        super(id, weight, location, keywords, keywordWeights);
    }

    public SKJoinQuery(int id, double weight, Region spatialWindow, List<Integer> keywords, List<Double> keywordWeights) {
        super(id, weight, null, keywords, keywordWeights);
        this.spatialWindow = spatialWindow;
    }

    public SKJoinQuery(Query query) {
        super(query);
        if (query instanceof SKJoinQuery) {
            this.spatialWindow = ((SKJoinQuery) query).spatialWindow;
        }
    }

    public Region getSpatialWindow() {
        return spatialWindow;
    }

    public void setSpatialWindow(Region spatialWindow) {
        this.spatialWindow = spatialWindow;
    }


    public static class Result extends QueryResult implements Comparable<Result> {
        public double spatialCost;
        public double textualCost;
        public double combineCost;
        public int[] pairId = new int[2]; // Stores the IDs of the paired objects

        public Result(int id, double spatialCost) {
            super(id);
            this.spatialCost = spatialCost;
        }

        public Result(int pairId1, int pairId2, double combineCost) {
            super(pairId1);
            this.pairId[0] = pairId1;
            this.pairId[1] = pairId2;
            this.combineCost = combineCost;
        }

        public Result(int pairId1, int pairId2, double spatialCost, double textualCost) {
            super(pairId1);
            this.pairId[0] = pairId1;
            this.pairId[1] = pairId2;
            this.textualCost = textualCost;
            this.spatialCost = spatialCost;
        }

        public Result(int pairId1, int pairId2, double spatialCost, double textualCost, double combineCost) {
            super(pairId1);
            this.pairId[0] = pairId1;
            this.pairId[1] = pairId2;
            this.combineCost = combineCost;
            this.spatialCost = spatialCost;
            this.textualCost = textualCost;
        }

        public int getPairId1() {
            return pairId[0];
        }

        public int getPairId2() {
            return pairId[1];
        }

        // TODO CLEANUP: Remove duplicate method
        public double getSpatialCost() {
            return spatialCost;
        }

        public double getSpatialDistance() {
            return spatialCost;
        }

        public double getTextualSimilarity() {
            return textualCost;
        }

        public double getCombineCost() {
            return combineCost;
        }

//        public Result(int id, Cost cost, int minDistance) {
//            super(id, cost, minDistance);
//        }
//
//        public Result(int id, double cost, double minDistance) {
//            super(id, cost, minDistance);
//        }

        /**
         * Compare results primarily by distance and secondarily by ID
         */
        @Override
        public int compareTo(Result o) {
            if (this.combineCost < o.combineCost)
                return -1;
            else if (this.combineCost > o.combineCost)
                return 1;
            else {
                // If combined costs are equal, compare by the first ID in the pair
                if (this.pairId[0] < o.pairId[0])
                    return -1;
                else if (this.pairId[0] > o.pairId[0])
                    return 1;
                else {
                    // If first IDs are equal, compare by the second ID
                    if (this.pairId[1] < o.pairId[1])
                        return -1;
                    else if (this.pairId[1] > o.pairId[1])
                        return 1;
                    return 0;
                }
            }
        }

        @Override
        public String toString() {
            return "Result{" +
                    "pair=[" + pairId[0] + "," + pairId[1] + "]" +
                    ", spatialCost=" + spatialCost +
                    ", textualCost=" + textualCost +
                    ", combineCost=" + combineCost +
                    '}';
        }

    }

}

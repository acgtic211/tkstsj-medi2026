package org.ual.query;

public abstract class QueryResult /*implements Comparable<QueryResult>*/ {
    protected int id;
    //public double cost;
    //public Cost cost;
    //public double minDistance;

    public QueryResult(int id) {
        this.id = id;
    }

//    public QueryResult(int id, Cost cost) {
//        this.id = id;
//        this.cost = cost;
//    }
//
//    public QueryResult(int id, Cost cost, double minDistance) {
//        this.id = id;
//        this.cost = cost;
//        this.minDistance = minDistance;
//    }
//
//    public QueryResult(int id, double minDistance) {
//        this.id = id;
//        this.minDistance = minDistance;
//    }

//    @Override
//    public int compareTo(QueryResult o) {
//        if (cost < o.cost)
//            return -1;
//        else if (cost > o.cost)
//            return 1;
//        else
//            return 0;
//    }

    //public abstract int compareTo(QueryResult o);
}

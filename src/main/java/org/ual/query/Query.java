package org.ual.query;

import org.ual.spatialindex.spatialindex.Point;

import java.util.ArrayList;
import java.util.List;

public class Query {
    private int id;
    private double weight;
    private Point location;
    private List<Integer> keywords;
    private List<Double> keywordWeights;

    //TEST
    QueryResult result;

    public Query(int id) {
        this.id = id;
        this.keywords = new ArrayList<>();
        this.keywordWeights = new ArrayList<>();
    }

    public Query(int id, double weight, Point location, List<Integer> keywords, List<Double> keywordWeights) {
        this.id = id;
        this.weight = weight;
        this.location = location;
        this.keywords = keywords;
        this.keywordWeights = keywordWeights;
    }

    public Query(int queryId, Point point, List<Integer> keywords, List<Double> keywordWeights) {
        this.id = queryId;
        this.location = point;
        this.keywords = keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
        this.keywordWeights = keywordWeights != null ? new ArrayList<>(keywordWeights) : new ArrayList<>();
    }

    // TODO This is a pure spatial query, no weights.
    @Deprecated
    public Query(int id, Point location, List<Integer> keywords) {
        this.id = id;
        this.location = location;
        this.keywords = keywords;
        this.keywordWeights = new ArrayList<>(); // Initialize keywordWeights
    }

    public Query(Query query) {
        this.id = query.id;
        this.weight = query.weight;
        this.location = query.location;

        if (query.keywords != null) {
            this.keywords = new ArrayList<>(query.keywords);
        } else {
            this.keywords = new ArrayList<>(); // Initialize as empty list if source is null
        }

        if (query.keywordWeights != null) {
            this.keywordWeights = new ArrayList<>(query.keywordWeights);
        } else {
            this.keywordWeights = new ArrayList<>(); // Initialize as empty list if source is null
        }

        this.result = query.result;
    }


    //=============================================================
    //================ Getters and Setters ========================
    //=============================================================

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }

    public List<Integer> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<Integer> keywords) {
        this.keywords = keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
    }

    public List<Double> getKeywordWeights() {
        return keywordWeights;
    }

    public void setKeywordWeights(List<Double> keywordWeights) {
        this.keywordWeights = keywordWeights != null ? new ArrayList<>(keywordWeights) : new ArrayList<>();
    }

    public QueryResult getResult() {
        return result;
    }

    public void setResult(QueryResult result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "Query{" +
                "id=" + id +
                ", weight=" + weight +
                ", location=" + location +
                ", keywords=" + keywords +
                ", keywordWeights=" + keywordWeights +
                '}';
    }
}

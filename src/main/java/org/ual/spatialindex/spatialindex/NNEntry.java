package org.ual.spatialindex.spatialindex;


import java.util.List;
import java.util.stream.Collectors;

public class NNEntry implements Comparable<NNEntry> {
    public IEntry entry;
    public List<Integer> queryIndices;
    public List<Cost> queryComponentCosts; // Stores component-wise costs if applicable
    public Cost cost; // Primary cost object (includes irCost, spatialCost, totalCost)

    /**
     * Constructor for cases previously using minDistance as the primary cost.
     * Assumes minDistance is a spatial cost, and total cost, with IR cost being 0.
     * @param entry The entry.
     * @param minDistanceValue The spatial distance/cost.
     */
    public NNEntry(IEntry entry, double minDistanceValue) {
        this.entry = entry;
        this.cost = new Cost(0.0, minDistanceValue);
    }

    /**
     * Constructor based on NNEntryExtended (entry, queryIndices, Cost object).
     * @param entry The entry.
     * @param queryIndices Associated query indices.
     * @param costObj The Cost object.
     */
    public NNEntry(IEntry entry, List<Integer> queryIndices, Cost costObj) {
        this.entry = entry;
        this.queryIndices = queryIndices;
        this.cost = costObj;
    }

    /**
     * Constructor based on NNEntryExtended (entry, Cost object, List of Cost objects).
     * @param entry The entry.
     * @param costObj The primary Cost object.
     * @param objectQueryCosts List of component Cost objects.
     */
    public NNEntry(IEntry entry, Cost costObj, List<Cost> objectQueryCosts) {
        this.entry = entry;
        this.cost = costObj;
        this.queryComponentCosts = objectQueryCosts;
    }

    /**
     * Constructor based on NNEntryExtended (entry, Cost object).
     * @param entry The entry.
     * @param costObj The Cost object.
     */
    public NNEntry(IEntry entry, Cost costObj) {
        this(entry, null, costObj); // Delegates to the more general constructor
    }

    @Override
    public int compareTo(NNEntry other) {
        if (this.cost == null && other.cost == null) return 0;
        if (this.cost == null) return -1; // Consistent null handling (nulls first)
        if (other.cost == null) return 1;

        return Double.compare(this.cost.getCombinedCost(), other.cost.getCombinedCost());
    }

    @Override
    public String toString() {
        String costString = (cost != null) ? String.valueOf(cost.getCombinedCost()) : "null";
        return "NNEntry [node=" + entry + ", totalCost=" + costString + "]";
    }


    public double getSpatialCost() {
        return (this.cost != null) ? this.cost.getSpatialCost() : Double.POSITIVE_INFINITY;
    }

    public double getIrCost() {
        return (this.cost != null) ? this.cost.getIrCost() : Double.POSITIVE_INFINITY;
    }

    public double getCombinedCost() {
        return (this.cost != null) ? this.cost.getCombinedCost() : Double.POSITIVE_INFINITY;
    }
}

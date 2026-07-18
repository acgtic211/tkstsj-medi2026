package org.ual.spatialindex.spatialindex;

public class Cost {
    private double irCost;
    private double spatialCost;
    private double combinedCost;

    public Cost(double irCost, double spatialCost) {
        this.irCost = irCost;
        this.spatialCost = spatialCost;
    }

    public Cost(double irCost, double spatialCost, double combinedCost) {
        this.irCost = irCost;
        this.spatialCost = spatialCost;
        this.combinedCost = combinedCost; // Use the provided totalCost
    }

    public double getIrCost() {
        return irCost;
    }

    public double getSpatialCost() {
        return spatialCost;
    }

    public double getCombinedCost() {
        return combinedCost;
    }

    public double getSumCost() {
        return irCost + spatialCost; // This is the sum of the individual costs
    }

    @Override
    public String toString() {
        return "Cost [irCost=" + irCost + ", spatialCost=" + spatialCost + ", combinedCost=" + combinedCost + "]";
    }
}

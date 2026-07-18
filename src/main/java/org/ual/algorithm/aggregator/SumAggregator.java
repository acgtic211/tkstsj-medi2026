package org.ual.algorithm.aggregator;

import org.ual.spatialindex.spatialindex.Cost;

import java.util.List;

public class SumAggregator implements IAggregator {

    double totalSum;
    double spatialSum;
    double irSum;

    public SumAggregator() {
        initializeAccumulator();
    }

    @Override
    public Cost getAggregateValue(List<Cost> values, List<Double> weights) {
        double totalSum = 0;
        double spatialSum = 0;
        double irSum = 0;
        for (int i = 0; i < values.size(); i++) {
            totalSum += values.get(i).getCombinedCost() * weights.get(i);
            spatialSum += values.get(i).getSpatialCost() * weights.get(i);
            irSum += values.get(i).getIrCost() * weights.get(i);
        }
        return new Cost(irSum, spatialSum, totalSum);
    }

    @Override
    public String getName() {
        return "SUM";
    }

    @Override
    public Cost getAggregateValue(Cost value, int m) {
        return new Cost(m * value.getIrCost(), m * value.getSpatialCost(), m * value.getCombinedCost());
    }

    @Override
    public void initializeAccumulator() {
        totalSum = 0;
        spatialSum = 0;
        irSum = 0;
    }

    @Override
    public void accumulate(Cost value, Double weight) {
        totalSum += value.getCombinedCost() * weight;
        spatialSum += value.getSpatialCost() * weight;
        irSum += value.getIrCost() * weight;
    }

    @Override
    public Cost getAccumulatedValue() {
        return new Cost(irSum, spatialSum, totalSum);
    }
}

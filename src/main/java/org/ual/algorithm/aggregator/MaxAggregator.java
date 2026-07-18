package org.ual.algorithm.aggregator;

import org.ual.spatialindex.spatialindex.Cost;

import java.util.List;

public class MaxAggregator implements IAggregator {

    private Cost maximum;

    public MaxAggregator() {
        initializeAccumulator();
    }

    @Override
    public Cost getAggregateValue(List<Cost> values, List<Double> weights) {
        double maxTotal = -1;
        Cost max = null;
        for (int i = 0; i < values.size(); i++) {
            if (maxTotal < values.get(i).getCombinedCost() * weights.get(i)) {
                max = new Cost(values.get(i).getIrCost() * weights.get(i),
                        values.get(i).getSpatialCost() * weights.get(i),
                        values.get(i).getCombinedCost() * weights.get(i));
            }
        }
        return max;
    }

    @Override
    public String getName() {
        return "MAX";
    }

    @Override
    public Cost getAggregateValue(Cost value, int m) {
        return value;
    }

    @Override
    public void initializeAccumulator() {
        maximum = new Cost(0, 0, 0);
    }

    @Override
    public void accumulate(Cost value, Double weight) {
        if (maximum.getCombinedCost() < value.getCombinedCost() * weight)
            maximum = new Cost(value.getIrCost() * weight, value.getSpatialCost() * weight, value.getCombinedCost() * weight);
    }

    @Override
    public Cost getAccumulatedValue() {
        return maximum;
    }

}

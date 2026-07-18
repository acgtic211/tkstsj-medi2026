package org.ual.algorithm.aggregator;

import org.ual.spatialindex.spatialindex.Cost;

import java.util.List;

public interface IAggregator {
    /**
     * Apply the aggregate function over the values and return the result.
     *
     * @return f(value[0], value[1], ... , value[n - 1])
     */
    Cost getAggregateValue(List<Cost> values, List<Double> weights);

    void initializeAccumulator();
    void accumulate(Cost value, Double weight);
    Cost getAccumulatedValue();

    /**
     * Apply the aggregate function <code>m</code> times over <code>value</code>
     *
     * @return f(value, value, ... , value)
     */
    Cost getAggregateValue(Cost value, int m);

    String getName();
}

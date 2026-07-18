package org.ual.querygeneration;

import org.ual.algorithm.aggregator.IAggregator;
import org.ual.query.Query;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.spatialindex.parameters.DatasetParameters;

import java.util.ArrayList;
import java.util.List;

public class AggregateSKNNQueryGenerator extends QueryGenerator {

    public AggregateSKNNQueryGenerator(int seed, DatasetParameters parameters) {
        super(seed, parameters);
    }

//    public List<AggregateSKNNQuery> generateGNNKQuery(int numQueries, int groupSize, int numberOfKeywords, double querySpaceAreaPercentage,
//                                                      double keywordSpaceSizePercentage, IAggregator aggregator) {
//
//        resetRandomSeed();   // Force reset of random seed to obtain reproducible results
//        List<AggregateSKNNQuery> gnnkQueries = new ArrayList<>();
//
//        // Calculate QueryID, Weight, X, Y, List<Int> Keywords, List<Double> KeyWeights
//        for (int gnnk = 0; gnnk < numQueries; gnnk++) {
//            // Aggregator name
//            // Group size
//
//            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart)
//                    * Math.sqrt(querySpaceAreaPercentage / 100);
//            double longitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart)
//                    * Math.sqrt(querySpaceAreaPercentage / 100);
//
//            double centroidLatitude = parameters.latitudeStart +
//                    RANDOM.nextDouble() * (parameters.latitudeEnd - parameters.latitudeStart);
//            double centroidLongitude = parameters.longitudeStart +
//                    RANDOM.nextDouble() * (parameters.longitudeEnd - parameters.longitudeStart);
//
//            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100);
//            int keywordSpaceMiddle = RANDOM.nextInt(parameters.uniqueKeywords - keywordSpaceSpan + 1);
//
//            List<Query> queries = new ArrayList<>();
//
//            int[] queryWeights = new int[groupSize];
//            double queryWeightSum = 0;
//
//            for (int i = 0; i < queryWeights.length; i++) {
//                queryWeights[i] = 1; //+ RANDOM.nextInt(Integer.MAX_VALUE / numberOfQueries);
//                queryWeightSum += queryWeights[i];
//            }
//
//            for (int i = 0; i < queryWeights.length; i++) {
//                double queryWeight = queryWeights[i] / queryWeightSum * groupSize;
//
//                queries.add(createKWQuery(i, queryWeight, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan,
//                        centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan, parameters.topkWords));
//            }
//
//            //IAggregator aggregator = AggregatorFactory.getAggregator(aggregator);
//
//            //GNNKQuery gnnkQuery = new GNNKQuery(queries, aggregator);
//            AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(gnnk);
//            gnnkQuery.setGNNKQuery(queries, groupSize, aggregator);
//            gnnkQueries.add(gnnkQuery);
//        }
//        //for(GNNKQuery q : gnnkQueries) System.out.println(q.queries.get(0).location);
//        return gnnkQueries;
//
//    }

    public List<AggregateSKNNQuery> generateGNNKQuery(int numQueries, int groupSize, int numberOfKeywords, double querySpaceAreaPercentage,
                                                    double keywordSpaceSizePercentage, IAggregator aggregator) {

        resetRandomSeed();   // Force reset of random seed to obtain reproducible results
        List<AggregateSKNNQuery> gnnkQueries = new ArrayList<>();

        // Calculate QueryID, Weight, X, Y, List<Int> Keywords, List<Double> KeyWeights
        for (int queryId = 0; queryId < numQueries; queryId++) {
            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart)
                    * Math.sqrt(querySpaceAreaPercentage / 100);
            double longitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart)
                    * Math.sqrt(querySpaceAreaPercentage / 100);

            double centroidLatitude = parameters.latitudeStart +
                    RANDOM.nextDouble() * (parameters.latitudeEnd - parameters.latitudeStart);
            double centroidLongitude = parameters.longitudeStart +
                    RANDOM.nextDouble() * (parameters.longitudeEnd - parameters.longitudeStart);

            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100);
            int keywordSpaceMiddle = RANDOM.nextInt(parameters.uniqueKeywords - keywordSpaceSpan + 1);

            List<Query> queries = new ArrayList<>();

            // Generate individual weighted queries for the group
            for (int i = 0; i < groupSize; i++) {
                // Each query in the group has equal weight (1/groupSize)
                double queryWeight = 1.0 / groupSize;

                Query query = createKWQuery(i, queryWeight, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan,
                        centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan, parameters.topkWords);
                queries.add(query);
            }

            // Create the aggregate query with proper initialization
            AggregateSKNNQuery gnnkQuery = new AggregateSKNNQuery(queryId);
            gnnkQuery.setGNNKQuery(queries, groupSize, aggregator);
            gnnkQueries.add(gnnkQuery);
        }

        return gnnkQueries;
    }

    public List<AggregateSKNNQuery> generateSGNNKQuery(int numQueries, int groupSize, double subgroupSize,
                                                       int numberOfKeywords, double querySpaceAreaPercentage,
                                                       double keywordSpaceSizePercentage, IAggregator aggregator) {

        resetRandomSeed();   // Force reset of random seed to obtain reproducible results
        List<AggregateSKNNQuery> sgnnkQueries = new ArrayList<>();
        int subGroup = (int) (groupSize * subgroupSize / 100);

        // Calculate QueryID, Weight, X, Y, List<Int> Keywords, List<Double> KeyWeights
        for (int sgnnk = 0; sgnnk < numQueries; sgnnk++) {

            double latitudeSpan = (parameters.latitudeEnd - parameters.latitudeStart)
                    * Math.sqrt(querySpaceAreaPercentage / 100);
            double longitudeSpan = (parameters.longitudeEnd - parameters.longitudeStart)
                    * Math.sqrt(querySpaceAreaPercentage / 100);

            double centroidLatitude = parameters.latitudeStart +
                    RANDOM.nextDouble() * (parameters.latitudeEnd - parameters.latitudeStart);
            double centroidLongitude = parameters.longitudeStart +
                    RANDOM.nextDouble() * (parameters.longitudeEnd - parameters.longitudeStart);

            int keywordSpaceSpan = (int) Math.ceil(parameters.uniqueKeywords * keywordSpaceSizePercentage / 100);
            int keywordSpaceMiddle = RANDOM.nextInt(parameters.uniqueKeywords - keywordSpaceSpan + 1);

            // Query writer
            List<Query> queries = new ArrayList<>();

            int[] queryWeights = new int[groupSize];
            double queryWeightSum = 0;

            for (int i = 0; i < queryWeights.length; i++) {
                queryWeights[i] = 1; //+ RANDOM.nextInt(Integer.MAX_VALUE / numberOfQueries);
                queryWeightSum += queryWeights[i];
            }

            for (int i = 0; i < queryWeights.length; i++) {
                double queryWeight = queryWeights[i] / queryWeightSum * groupSize;

                queries.add(createKWQuery(i, queryWeight, numberOfKeywords, keywordSpaceMiddle, keywordSpaceSpan,
                        centroidLatitude, centroidLongitude, latitudeSpan, longitudeSpan, parameters.topkWords));
            }

            //IAggregator aggregator = AggregatorFactory.getAggregator(aggregatorName);
            AggregateSKNNQuery sgnnkQuery = new AggregateSKNNQuery(sgnnk);
            sgnnkQuery.setSGNNKQuery(queries, groupSize, subGroup, aggregator);
            sgnnkQueries.add(sgnnkQuery);

            //Query sgnnkQuery = new SGNNKQuery(queries, subGroup, aggregator);

            //sgnnkQueries.add(sgnnkQuery);
        }

        return sgnnkQueries;
    }


}

package org.ual.utils.config;

import org.junit.jupiter.api.Test;
import org.ual.spatialindex.rtree.BulkLoadMethod;
import org.ual.utils.experiment.JoinExperiment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CrossDatasetJoinConfigParsingTest {

    @Test
    void configLoaderParsesSecondaryDatasetAndIndexForStsjMultiset() throws Exception {
        String json = "{\n"
                + "  \"dataset\": { \"datasetType\": \"POSTAL_CODES\", \"usagePercentage\": 1.0 },\n"
                + "  \"index\": {\n"
                + "    \"fanout\": 10,\n"
                + "    \"fillFactor\": 0.7,\n"
                + "    \"dimension\": 2,\n"
                + "    \"betaArea\": 0.5,\n"
                + "    \"maxWord\": 4,\n"
                + "    \"numClusters\": 4,\n"
                + "    \"numMoves\": 10,\n"
                + "    \"rTreeVariant\": \"RSTAR\",\n"
                + "    \"nearMinimumOverlapFactor\": 4,\n"
                + "    \"smoothingFactor\": 0.2,\n"
                + "    \"spatialIndexType\": \"IR\",\n"
                + "    \"dataStructureType\": \"HASHMAP\",\n"
                + "    \"textualIndexType\": \"INVERTED_LIST\",\n"
                + "    \"bulkLoadMethod\": \"NONE\"\n"
                + "  },\n"
                + "  \"query\": {\n"
                + "    \"numberOfQueries\": 1,\n"
                + "    \"groupSizes\": [10],\n"
                + "    \"groupSizeDefault\": 10,\n"
                + "    \"mPercentages\": [50],\n"
                + "    \"mPercentageDefault\": 50,\n"
                + "    \"numberOfKeywords\": [2],\n"
                + "    \"numberOfKeywordsDefault\": 2,\n"
                + "    \"spaceAreaPercentages\": [0.01],\n"
                + "    \"spaceAreaPercentageDefault\": 0.01,\n"
                + "    \"keywordSpaceSizePercentages\": [3],\n"
                + "    \"keywordSpaceSizePercentageDefault\": 3,\n"
                + "    \"topKValues\": [10],\n"
                + "    \"topKDefault\": 10,\n"
                + "    \"alphaValues\": [0.5],\n"
                + "    \"alphaDefault\": 0.5,\n"
                + "    \"radiusValues\": [10.0],\n"
                + "    \"radiusDefault\": 10.0,\n"
                + "    \"spatialDistance\": [0.01],\n"
                + "    \"spatialDistanceDefault\": 0.01,\n"
                + "    \"textualSimilarity\": [0.5],\n"
                + "    \"textualSimilarityDefault\": 0.5\n"
                + "  },\n"
                + "  \"experiment\": {\n"
                + "    \"runJoinQueries\": true,\n"
                + "    \"joinExperiments\": [\n"
                + "      {\n"
                + "        \"queryTypes\": [\"STSJ_MULTISET\"],\n"
                + "        \"algorithm\": \"BEST_FIRST\",\n"
                + "        \"queryStrategy\": \"FULL_JOIN\",\n"
                + "        \"similarityType\": \"WEIGHTED_JACCARD\",\n"
                + "        \"varyParameter\": \"numberOfKeywords\",\n"
                + "        \"fixedSpatialDistance\": 0.01,\n"
                + "        \"fixedTextualSimilarity\": 0.5,\n"
                + "        \"numberOfQueries\": 1,\n"
                + "        \"joinStrategy\": \"DEFAULT\",\n"
                + "        \"thresholdPolicy\": \"STRICT\",\n"
                + "        \"secondaryDataset\": {\n"
                + "          \"name\": \"HOTEL_SET\",\n"
                + "          \"usagePercentage\": 0.8\n"
                + "        },\n"
                + "        \"secondaryIndex\": {\n"
                + "          \"fanout\": 12,\n"
                + "          \"fillFactor\": 0.7,\n"
                + "          \"dimension\": 2,\n"
                + "          \"betaArea\": 0.5,\n"
                + "          \"maxWord\": 4,\n"
                + "          \"numClusters\": 4,\n"
                + "          \"numMoves\": 10,\n"
                + "          \"rTreeVariant\": \"RSTAR\",\n"
                + "          \"nearMinimumOverlapFactor\": 4,\n"
                + "          \"smoothingFactor\": 0.2,\n"
                + "          \"spatialIndexType\": \"IR\",\n"
                + "          \"dataStructureType\": \"HASHMAP\",\n"
                + "          \"textualIndexType\": \"HASHMAP\",\n"
                + "          \"bulkLoadMethod\": \"NONE\"\n"
                + "        }\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "}";

        Path tempConfig = Files.createTempFile("cross-dataset-join", ".json");
        Files.write(tempConfig, json.getBytes(StandardCharsets.UTF_8));

        ApplicationConfig config = ConfigLoader.loadFromJson(tempConfig.toString());
        JoinExperiment experiment = config.getExperiment().getJoinExperiments().get(0);

        assertEquals("STSJ_MULTISET", experiment.getQueryTypes().get(0));
        assertNotNull(experiment.getSecondaryDataset());
        assertEquals(DatasetType.HOTELS, experiment.getSecondaryDataset().getDatasetType());
        assertEquals(0.8, experiment.getSecondaryDataset().getUsagePercentage(), 0.0);

        assertNotNull(experiment.getSecondaryIndex());
        assertEquals(TextualIndexType.INVERTED_LIST, experiment.getSecondaryIndex().getTextualIndexType());
        assertEquals(BulkLoadMethod.STR, experiment.getSecondaryIndex().getBulkLoadMethod());

        Files.deleteIfExists(tempConfig);
    }
}


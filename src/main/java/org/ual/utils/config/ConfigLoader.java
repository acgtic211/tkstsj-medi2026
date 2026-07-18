package org.ual.utils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility class to load application configuration from JSON files.
 * Handles automatic parsing of enums from string values in JSON.
 */
public class ConfigLoader {
    private static final Logger logger = LogManager.getLogger(ConfigLoader.class);

    /**
     * Loads configuration from a JSON file.
     * Enums are automatically parsed from their string names (e.g., "RSTAR" -> RTreeVariant.RSTAR)
     *
     * @param filePath Path to the JSON configuration file
     * @return ApplicationConfig object with loaded settings
     * @throws IOException if file cannot be read
     */
    public static ApplicationConfig loadFromJson(String filePath) throws IOException {
        logger.info("Loading configuration from: {}", filePath);

        if (!Files.exists(Paths.get(filePath))) {
            logger.warn("Configuration file not found: {}. Using defaults.", filePath);
            return new ApplicationConfig(); // Return default configuration
        }

        try (Reader reader = new FileReader(filePath)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonElement root = JsonParser.parseReader(reader);
            JsonObject normalizedRoot = root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();

            normalizeLegacyFields(normalizedRoot);

            ApplicationConfig config = gson.fromJson(normalizedRoot, ApplicationConfig.class);
            applyDefaults(config);
            logger.info("Configuration loaded successfully");
            return config;
        } catch (Exception e) {
            logger.error("Error loading configuration from {}: {}", filePath, e.getMessage());
            throw e;
        }
    }

    /**
     * Saves configuration to a JSON file.
     *
     * @param config ApplicationConfig object to save
     * @param filePath Path where to save the JSON file
     * @throws IOException if file cannot be written
     */
    public static void saveToJson(ApplicationConfig config, String filePath) throws IOException {
        logger.info("Saving configuration to: {}", filePath);

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        String json = gson.toJson(config);
        Files.write(Paths.get(filePath), json.getBytes());

        logger.info("Configuration saved successfully");
    }

    /**
     * Helper method to parse enum from string safely.
     * This is useful for manual parsing or validation.
     *
     * @param enumClass The enum class
     * @param value String value to parse
     * @param defaultValue Default value if parsing fails
     * @return Parsed enum value or default
     */
    public static <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid enum value '{}' for {}. Using default: {}",
                    value, enumClass.getSimpleName(), defaultValue);
            return defaultValue;
        }
    }

    private static void normalizeLegacyFields(JsonObject root) {
        JsonObject dataset = root.has("dataset") && root.get("dataset").isJsonObject()
                ? root.getAsJsonObject("dataset") : null;

        if (dataset != null && !dataset.has("datasetType") && dataset.has("name")) {
            String legacyName = dataset.get("name").getAsString();
            String normalizedName = normalizeDatasetName(legacyName);
            dataset.addProperty("datasetType", normalizedName);
            logger.info("Mapped legacy dataset.name '{}' -> datasetType '{}'.", legacyName, normalizedName);
        }

        JsonObject index = root.has("index") && root.get("index").isJsonObject()
                ? root.getAsJsonObject("index") : null;

        if (index != null && index.has("textualIndexType")) {
            String textualType = index.get("textualIndexType").getAsString();
            if ("HASHMAP".equalsIgnoreCase(textualType) || "ARRAYLIST".equalsIgnoreCase(textualType)) {
                index.addProperty("textualIndexType", "INVERTED_LIST");
                logger.info("Mapped legacy textualIndexType '{}' -> 'INVERTED_LIST'.", textualType);
            }
        }

        if (index != null && index.has("bulkLoadMethod") && "NONE".equalsIgnoreCase(index.get("bulkLoadMethod").getAsString())) {
            index.addProperty("bulkLoadMethod", "STR");
            logger.info("Mapped legacy bulkLoadMethod 'NONE' -> 'STR'.");
        }

        JsonObject experiment = root.has("experiment") && root.get("experiment").isJsonObject()
                ? root.getAsJsonObject("experiment") : null;
        if (experiment != null && experiment.has("joinExperiments") && experiment.get("joinExperiments").isJsonArray()) {
            JsonArray joinExperiments = experiment.getAsJsonArray("joinExperiments");
            for (JsonElement element : joinExperiments) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject joinExp = element.getAsJsonObject();
                if (!joinExp.has("similarityType") && joinExp.has("similarityFunction")) {
                    joinExp.add("similarityType", joinExp.get("similarityFunction"));
                    joinExp.remove("similarityFunction");
                    logger.info("Mapped legacy joinExperiments[].similarityFunction -> similarityType.");
                }
                if (!joinExp.has("queryStrategy")) {
                    joinExp.addProperty("queryStrategy", "CONSTRAINT_TEXTUAL_JOIN");
                    logger.info("Added missing joinExperiments[].queryStrategy with CONSTRAINT_TEXTUAL_JOIN.");
                } else if ("PARTIAL_JOIN".equalsIgnoreCase(joinExp.get("queryStrategy").getAsString())) {
                    joinExp.addProperty("queryStrategy", "CONSTRAINT_TEXTUAL_JOIN");
                    logger.info("Mapped legacy joinExperiments[].queryStrategy 'PARTIAL_JOIN' -> 'CONSTRAINT_TEXTUAL_JOIN'.");
                }

                JsonObject secondaryDataset = joinExp.has("secondaryDataset") && joinExp.get("secondaryDataset").isJsonObject()
                        ? joinExp.getAsJsonObject("secondaryDataset") : null;
                if (secondaryDataset != null && !secondaryDataset.has("datasetType") && secondaryDataset.has("name")) {
                    String legacyName = secondaryDataset.get("name").getAsString();
                    String normalizedName = normalizeDatasetName(legacyName);
                    secondaryDataset.addProperty("datasetType", normalizedName);
                    logger.info("Mapped legacy joinExperiments[].secondaryDataset.name '{}' -> datasetType '{}'.", legacyName, normalizedName);
                }

                JsonObject secondaryIndex = joinExp.has("secondaryIndex") && joinExp.get("secondaryIndex").isJsonObject()
                        ? joinExp.getAsJsonObject("secondaryIndex") : null;
                if (secondaryIndex != null && secondaryIndex.has("textualIndexType")) {
                    String textualType = secondaryIndex.get("textualIndexType").getAsString();
                    if ("HASHMAP".equalsIgnoreCase(textualType) || "ARRAYLIST".equalsIgnoreCase(textualType)) {
                        secondaryIndex.addProperty("textualIndexType", "INVERTED_LIST");
                        logger.info("Mapped legacy secondaryIndex.textualIndexType '{}' -> 'INVERTED_LIST'.", textualType);
                    }
                }

                if (secondaryIndex != null && secondaryIndex.has("bulkLoadMethod")
                        && "NONE".equalsIgnoreCase(secondaryIndex.get("bulkLoadMethod").getAsString())) {
                    secondaryIndex.addProperty("bulkLoadMethod", "STR");
                    logger.info("Mapped legacy joinExperiments[].secondaryIndex.bulkLoadMethod 'NONE' -> 'STR'.");
                }
            }
        }
    }

    private static String normalizeDatasetName(String datasetName) {
        if (datasetName == null) {
            return DatasetType.TEST.name();
        }

        String normalized = datasetName.trim().toUpperCase();
        if (normalized.endsWith("_SET")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }

        if ("HOTEL".equals(normalized)) {
            return DatasetType.HOTELS.name();
        }

        for (DatasetType type : DatasetType.values()) {
            if (type.name().equals(normalized)) {
                return type.name();
            }
        }

        logger.warn("Unknown legacy dataset name '{}', fallback to TEST.", datasetName);
        return DatasetType.TEST.name();
    }

    private static void applyDefaults(ApplicationConfig config) {
        if (config.getDataset() == null) {
            config.setDataset(new DatasetConfig());
        }
        if (config.getDataset().getDatasetType() == null) {
            config.getDataset().setDatasetType(DatasetType.TEST);
        }
        if (config.getDataset().getSamplingMethod() == null) {
            config.getDataset().setSamplingMethod(org.ual.utils.sampling.SamplingStrategy.SamplingMethod.RANDOMIZED);
        }
        if (config.getDataset().getSamplingStartLine() < 0) {
            config.getDataset().setSamplingStartLine(0);
        }

        if (config.getIndex() == null) {
            config.setIndex(new IndexConfig());
        }
        if (config.getIndex().getTextualIndexType() == null) {
            config.getIndex().setTextualIndexType(TextualIndexType.INVERTED_LIST);
        }

        if (config.getQuery() == null) {
            config.setQuery(new QueryConfig());
        }
        if (config.getPaths() == null) {
            config.setPaths(new PathsConfig());
        }
        if (config.getExperiment() == null) {
            config.setExperiment(new ExperimentConfig());
        }
        if (config.getCsvFormat() == null) {
            config.setCsvFormat(CsvFormatConfig.defaultConfig());
        }
    }
}

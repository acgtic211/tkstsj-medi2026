package org.ual.utils.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ConfigCatalog {
    private static final Logger logger = LogManager.getLogger(ConfigCatalog.class);

    private ConfigCatalog() {
    }

    public static List<String> listJsonConfigs(String configDirectoryPath) {
        File dir = new File(configDirectoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("Config directory not found: {}", configDirectoryPath);
            return Collections.emptyList();
        }

        String[] files = dir.list((current, name) -> name != null && name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<String> configs = new ArrayList<>(Arrays.asList(files));
        Collections.sort(configs);
        return configs;
    }

    public static String resolveConfigPath(String userInput, String configDirectoryPath, String fallbackPath) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return fallbackPath;
        }

        String trimmedInput = userInput.trim();
        File directPath = new File(trimmedInput);
        if (directPath.exists() && directPath.isFile()) {
            return directPath.getPath();
        }

        String normalizedName = trimmedInput.toLowerCase().endsWith(".json") ? trimmedInput : trimmedInput + ".json";
        File fromCatalog = new File(configDirectoryPath, normalizedName);
        if (fromCatalog.exists() && fromCatalog.isFile()) {
            return fromCatalog.getPath();
        }

        logger.warn("Could not resolve config '{}', fallback to '{}'", userInput, fallbackPath);
        return fallbackPath;
    }
}


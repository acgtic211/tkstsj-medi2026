# JSON Configuration Files - Comprehensive Guide

## Overview

**Initial instructions to create config files (missing many parameters and configurations for now)**

`AlternativeMain` is the main entry point of the project and it uses the JSON files in this directory for both interactive and autonomous execution.

This directory contains standardized JSON configuration files for running comprehensive experiments on the Spatio-Textual Index system. The configurations are organized by:

1. **Spatial Index Type**: IR, IR_BULK (with STR), DIR, CIR, CDIR
2. **Textual Index Type**: INVERTED_LIST

## File Naming Convention

Files follow the pattern:
```
{spatial-index-type}-{textual-index-type}-{scope}.json
```

### Examples:
- `ir-inverted-full.json` - IR-Tree with Inverted List (full experiments)
- `ir-str-inverted-full.json` - IR-Tree with Bulk Loading (STR) and Inverted List
- `cdir-inverted-minimal.json` - CDIR-Tree with Inverted List (minimal)

## Configuration Structure

### Dataset Section
```json
"dataset": {
  "datasetType": "POSTAL_CODES",  // POSTAL_CODES, SPORTS, PARKS, HOTELS, TEST
  "usagePercentage": 1.0,         // 0.0 - 1.0 (percentage of dataset to use)
  "samplingMethod": "RANDOMIZED", // SYSTEMATIC, RANDOMIZED, CONTIGUOUS
  "samplingRandomSeed": 42,       // Used by RANDOMIZED for reproducibility
  "samplingStartLine": 0          // Used by CONTIGUOUS, ignored otherwise
}
```

**Dataset Options:**
- `POSTAL_CODES` - 171K documents (recommended for quick tests)
- `SPORTS` - 1.75M documents
- `PARKS` - 9.96M documents
- `HOTELS` - 20K documents
- `TEST` - 10 documents (debugging only)

**Sampling Options:**
- `SYSTEMATIC` - Deterministic sequential sampling
- `RANDOMIZED` - Random sampling using `samplingRandomSeed`
- `CONTIGUOUS` - Single contiguous window starting at `samplingStartLine`

### Index Section
```json
"index": {
  "fanout": 25,                        // R-Tree internal and leaf node capacity
  "fillFactor": 0.7,                   // Percentage of fanout capacity
  "dimension": 2,                      // Spatial dimensions
  "betaArea": 0.5,                     // DIR/CDIR similarity adjustment factor
  "maxWord": 4,                        // Max words per node comparisson
  "numClusters": 8,                    // K-means clusters
  "numMoves": 300,                     // K-means iterations
  "rTreeVariant": "RSTAR",             // R-Tree variant
  "nearMinimumOverlapFactor": 8,       // R*-Tree near-minimum overlap
  "smoothingFactor": 0.2,              // IDF smoothing
  "spatialIndexType": "IR",            // IR, IR_BULK, DIR, CIR, CDIR
  "dataStructureType": "HASHMAP",      // HASHMAP, TREEMAP
  "textualIndexType": "INVERTED_LIST", // INVERTED_LIST
  "bulkLoadMethod": "STR"              // STR (IR_BULK) or "NONE".
}
```

**Spatial Index Types:**
- `IR` - Inverted R-Tree
- `IR_BULK` - Inverted R-Tree with bulk loading via STR
- `DIR` - Document aware IR-Tree
- `CIR` - Clustered IR-Tree
- `CDIR` - Clustered Document IR-Tree

**Textual Index Types:**
- `INVERTED_LIST` - Inverted index

### Query Section
```json
"query": {
  "numberOfQueries": 20,               // Queries per experiment
  "writeResults": false,               // Save query results
  
  // Parameter sweep arrays
  "groupSizes": [10, 20, 40, 60, 80],
  "mPercentages": [40, 50, 60, 70, 80],
  "numberOfKeywords": [1, 2, 4, 8, 10],
  "spaceAreaPercentages": [0.001, 0.01, 0.02, 0.03, 0.04, 0.05],
  "keywordSpaceSizePercentages": [1, 2, 3, 4, 5],
  "topKValues": [1, 10, 100, 200, 400, 600, 800, 1000],
  "alphaValues": [0.1, 0.3, 0.5, 0.7, 0.9],
  "radiusValues": [1.0, 2.0, 5.0, 10.0, 20.0],
  "spatialDistance": [0.001, 0.005, 0.01, 0.05, 0.1],
  "textualSimilarity": [0.1, 0.3, 0.5, 0.7, 0.9],
  
  // Default values for queries
  "groupSizeDefault": 10,
  "mPercentageDefault": 60,
  "numberOfKeywordsDefault": 2,
  "spaceAreaPercentageDefault": 0.01,
  "keywordSpaceSizePercentageDefault": 3,
  "topKDefault": 10,
  "alphaDefault": 0.5,
  "radiusDefault": 10.0,
  "spatialDistanceDefault": 0.01,
  "textualSimilarityDefault": 0.5,
  
  // Join parameters
  "thresholdPolicy": "STRICT",         // STRICT, COMBINED_COST (DEPRECATED)
  "joinStrategy": "PLANE_SWEEP",       // PLANE_SWEEP, DEFAULT
  "similarityFunction": "WEIGHTED_JACCARD"  // WEIGHTED_JACCARD, COSINE, WEIGHTED_SUM
}
```

### Experiment Section Examples

#### Aggregate Queries
```json
"aggregateExperiments": [
  {
    "aggregateFunctions": ["SUM"],     // SUM, MAX
    "queryTypes": ["GNNK", "SGNNK"],   // Grouped Nearest Neighbor K
    "varyParameter": "numberOfKeywords",
    "fixedAlpha": 0.5
  }
]
```

#### K-NN Queries
```json
"knnExperiments": [
  {
    "queryTypes": ["BkSK", "TkSK"],    // Boolean Spatial Keyword, Top-k Spatial Keyword
    "varyParameter": "numberOfKeywords",
    "fixedAlpha": 0.5,
    "fixedTopK": 10
  }
]
```

#### Range Queries
```json
"rangeExperiments": [
  {
    "queryTypes": ["BRSK"],            // Boolean Range Spatial Keyword
    "varyParameter": "radius",
    "fixedAlpha": 0.5
  }
]
```

#### Join Queries
```json
"joinExperiments": [
  {
    "queryTypes": ["STSJ"],            // Spatio-Textual Similarity Join (STSJ), (STSJ-EX), (TOPK_STSJ), (TOPK_STSJ_EX)
    "algorithm": "BEST_FIRST",         // BEST_FIRST, RECURSIVE
    "queryStrategy": "FULL_JOIN",     // FULL_JOIN, CONSTRAINT_TEXTUAL_JOIN, CONSTRAINT_SPATIAL_JOIN, CONSTRAINT_ALL_JOIN
    "joinStrategy": "PLANE_SWEEP",
    "thresholdPolicy": "STRICT",       // DEPRECATED
    "similarityFunction": "WEIGHTED_JACCARD",
    "varyParameter": "spatialDistance",
    "fixedTextualSimilarity": 0.5,
    "numberOfQueries": 1
  }
]
```

### Paths Section
```json
"paths": {
  "temp": "src/main/resources/temp/",
  "results": "src/main/resources/results/",
  "metrics": "src/main/resources/results/metrics/",
  "log": "src/main/resources/log/"
}
```

### CSV Format Section
```json
"csvFormat": {
  "separator": ",",                    // CSV column separator
  "decimalSymbol": ".",                // Decimal point or comma
  "includeHeaders": true,              // Include column headers
  "writeGnuplotMetadata": false        // Future: gnuplot compatibility
}
```

## Running Experiments

### Autonomous Mode (Recommended)
```bash
mvn -DskipTests exec:java -Dexec.mainClass=org.ual.AlternativeMain \
  -Dexec.args="--autonomous src/main/resources/json/ir-inverted-full.json"
```

### Quick Regression Test
```bash
mvn -DskipTests exec:java -Dexec.mainClass=org.ual.AlternativeMain \
  -Dexec.args="--autonomous src/main/resources/json/ir-inverted-minimal.json"
```

### List Available Configs
```bash
mvn -DskipTests exec:java -Dexec.mainClass=org.ual.AlternativeMain \
  -Dexec.args="--list-configs"
```

### Interactive Mode
```bash
mvn -DskipTests exec:java -Dexec.mainClass=org.ual.AlternativeMain \
  -Dexec.args="src/main/resources/json/ir-inverted-full.json"
```

## Results Output

All experiments generate metrics in:
- **Metrics:** `src/main/resources/results/metrics/`
- **Logs:** `src/main/resources/log/`
- **Raw results:** `src/main/resources/results/`

Results include:
- Query execution times
- Memory consumption
- Number of results returned
- Index construction times
- Performance statistics by parameter variation

## Customization

To create new configs:
1. Copy an existing full config template
2. Modify:
   - `spatialIndexType` (IR, DIR, CIR, CDIR)
   - `textualIndexType` (INVERTED_LIST)
   - `dataset.datasetType` (choose appropriate dataset size)
   - Query parameters and experiment types as needed
3. Save with pattern: `{name}.json`
4. Run with: `--autonomous src/main/resources/json/{name}.json`

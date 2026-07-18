package org.ual.spatiotextualindex.irtree;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.ual.algorithm.aggregator.AggregatorFactory;
import org.ual.algorithm.aggregator.IAggregator;
import org.ual.documentindex.IDocumentIndex;
import org.ual.querygeneration.AggregateSKNNQueryGenerator;
import org.ual.querygeneration.SKNNQueryGenerator;
import org.ual.querytype.AggregateSKNNQuery;
import org.ual.querytype.SKNNQuery;
import org.ual.spatialindex.parameters.Dataset;
import org.ual.spatialindex.parameters.DatasetParameters;
import org.ual.spatialindex.parameters.ParametersFactory;
import org.ual.spatialindex.spatialindex.SpatialIndex;
import org.ual.spatiotextualindex.irtreebase.AbstractIRTree;
import org.ual.utils.index.IndexLogicNEW;
import org.ual.utils.main.StatisticsLogic;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Quick performance-regression gate for the IRTree query pipeline.
 *
 * <p>Builds an IR-Tree on HOTEL_SET (20 K objects), runs a small fixed batch
 * of BkSK / TkSK / BRSK / GNNK queries and compares the average-per-query
 * wall-clock times against persisted baselines stored in
 * {@code src/test/resources/perf-baselines.properties}.
 *
 * <h3>Workflow</h3>
 * <ol>
 *   <li><b>First run (or no baseline file)</b> – records current timings and
 *       passes unconditionally, printing a reminder to re-run.</li>
 *   <li><b>Subsequent runs</b> – compares each query type against its baseline
 *       and fails if any measurement exceeds
 *       {@code baseline × (1 + tolerance)}.</li>
 *   <li><b>Deliberate re-calibration</b> – run with
 *       {@code -Dupdate.perf.baselines=true} to overwrite the file with fresh
 *       values (e.g. after a confirmed speed-up).</li>
 * </ol>
 *
 * <h3>Invocation examples</h3>
 * <pre>
 *   # Normal regression check
 *   mvn test -Dtest=PerformanceRegressionTest
 *
 *   # Re-calibrate baselines
 *   mvn test -Dtest=PerformanceRegressionTest -Dupdate.perf.baselines=true
 *
 *   # Tighten tolerance to 50 %
 *   mvn test -Dtest=PerformanceRegressionTest -Dperf.regression.tolerance=0.5
 *
 *   # Run only performance-regression-tagged tests
 *   mvn test -Dgroups="performance-regression"
 * </pre>
 *
 * <p>The test is tagged {@code performance-regression} so it can be run
 * selectively or excluded from a standard {@code mvn test} via Surefire's
 * {@code excludedGroups} configuration. If the dataset files are absent the
 * test is <em>skipped</em> rather than failed, keeping CI safe.
 */
@Tag("performance-regression")
public class PerformanceRegressionTest {

    private enum TextualVariant {
        HASHMAP,
        SIGNED_BLOCK
    }

    private static class VariantBenchmarkResult {
        private final Map<String, Double> queryAveragesMs;
        private final double buildMs;

        private VariantBenchmarkResult(Map<String, Double> queryAveragesMs, double buildMs) {
            this.queryAveragesMs = queryAveragesMs;
            this.buildMs = buildMs;
        }
    }

    // -----------------------------------------------------------------------
    // Configuration constants  (mirror defaults in config-ir-test.json)
    // -----------------------------------------------------------------------

    /** Dataset used for the regression benchmark.
     *  HOTEL_SET (≈ 20 K objects, always in the repo) gives stable timings. */
    private static final Dataset BENCHMARK_DATASET = Dataset.HOTEL_SET;

    /** Fixed random seed – ensures reproducible query sets across runs. */
    private static final int SEED = 42;

    /** Number of queries executed per type during each benchmark pass. */
    private static final int NUM_QUERIES = 30;

    // R-Tree build parameters (align with normal experiment defaults)
    private static final int    FANOUT    = 25;
    private static final float  FILL      = 0.7f;
    private static final int    DIMENSION = 2;
    private static final int    NMOF      = 8;   // nearMinimumOverlapFactor

    // Query generation parameters (defaults from config-ir-test.json)
    private static final int    NUM_KEYWORDS    = 4;
    private static final double SPACE_AREA_PCT  = 0.01;  // 0.01 %
    private static final int    KW_SPACE_PCT    = 3;     // 3 % of keyword universe
    private static final int    TOPK            = 10;
    private static final float  RADIUS          = 10.0f;
    private static final float  ALPHA           = 0.5f;
    private static final int    GROUP_SIZE      = 2;     // GNNK group size

    // -----------------------------------------------------------------------
    // Runtime control via system properties
    // -----------------------------------------------------------------------

    private static final boolean UPDATE_BASELINES =
            Boolean.getBoolean("update.perf.baselines");

    /** Fraction of baseline above which a run is flagged as a regression.
     *  Default 1.0 = 100 % – i.e. 2× slower triggers a failure. */
    private static final double TOLERANCE =
            Double.parseDouble(System.getProperty("perf.regression.tolerance", "1.0"));

    /** Path to the baselines file (relative to the Maven project root). */
    private static final String BASELINES_PATH =
            "src/test/resources/perf-baselines.properties";

    // -----------------------------------------------------------------------
    // Test method
    // -----------------------------------------------------------------------

    @Test
    void irTreeQueryPerformanceRegression() throws Exception {

        DatasetParameters params = ParametersFactory.getParameters(BENCHMARK_DATASET);

        // Skip gracefully when dataset files are absent (CI without data)
        Assumptions.assumeTrue(
                new File(params.keywordFile).exists() && new File(params.locationFile).exists(),
                "Skipping performance regression – dataset files not found at: "
                        + params.keywordFile);

        Map<String, Double> measured = new LinkedHashMap<>();
        for (TextualVariant variant : TextualVariant.values()) {
            VariantBenchmarkResult result = runVariantBenchmark(variant, params);
            for (Map.Entry<String, Double> e : result.queryAveragesMs.entrySet()) {
                measured.put(metricKey(variant, e.getKey()), e.getValue());
            }
            measured.put(buildKey(variant), result.buildMs);
            printMeasured(variant, result.queryAveragesMs, result.buildMs);
        }

        // -- Baseline comparison / recording ------------------------------
        File baselinesFile = new File(BASELINES_PATH);
        boolean hasBaselines = baselinesFile.exists() && hasAnyValue(baselinesFile);

        if (UPDATE_BASELINES || !hasBaselines) {
            recordBaselines(measured, baselinesFile);
            if (UPDATE_BASELINES) {
                System.out.println("[PerfRegression] Baselines updated (update.perf.baselines=true).");
            } else {
                System.out.println("[PerfRegression] No prior baselines – current run recorded.");
                System.out.println("                 Re-run the test to start comparing.");
            }
        } else {
            Properties baselines = loadProperties(baselinesFile);
            assertWithinBaselines(measured, baselines);
        }
    }

    private VariantBenchmarkResult runVariantBenchmark(TextualVariant variant, DatasetParameters params) {
        System.out.printf("%n[PerfRegression] Building IR-Tree on %s using %s textual index ...%n",
                BENCHMARK_DATASET, variant);
        long buildStart = System.nanoTime();

        StatisticsLogic statsLogic = new StatisticsLogic("target/perf-test-metrics/");
        IndexLogicNEW indexLogic = new IndexLogicNEW(statsLogic, params, 1.0);
        indexLogic.createHashMapDocStore(0.0f);

        if (variant == TextualVariant.SIGNED_BLOCK) {
            indexLogic.createSignedBlockTextualIndex();
        } else {
            indexLogic.createInvertedListIndex(0);
        }

        indexLogic.createIRTree(FANOUT, FILL, DIMENSION,
                SpatialIndex.RtreeVariantRstar, NMOF);

        AbstractIRTree tree = indexLogic.getAbstractIRTree();
        IDocumentIndex textualIndex = indexLogic.getTextualIndex();
        double buildMs = toMs(buildStart);

        Map<String, Double> variantMeasured = new LinkedHashMap<>();
        variantMeasured.put("BkSK", benchmarkBkSK(tree, textualIndex, params));
        variantMeasured.put("TkSK", benchmarkTkSK(tree, textualIndex, params));
        variantMeasured.put("BRSK", benchmarkBRSK(tree, textualIndex, params));
        variantMeasured.put("GNNK", benchmarkGNNK(tree, textualIndex, params));

        return new VariantBenchmarkResult(variantMeasured, buildMs);
    }

    // -----------------------------------------------------------------------
    // Individual benchmarks
    // -----------------------------------------------------------------------

    /** Boolean k-nearest-neighbour spatio-textual query. */
    private double benchmarkBkSK(AbstractIRTree tree, IDocumentIndex idx,
                                  DatasetParameters params) {
        SKNNQueryGenerator gen = new SKNNQueryGenerator(SEED, params);
        List<SKNNQuery> queries = gen.generateBooleanKNNQueries(
                NUM_QUERIES, NUM_KEYWORDS, SPACE_AREA_PCT, KW_SPACE_PCT);
        tree.setAlphaDistribution(ALPHA);
        long t = System.nanoTime();
        for (SKNNQuery q : queries) tree.booleanKnnQuery(idx, q, TOPK);
        return avgMs(t, NUM_QUERIES);
    }

    /** Top-k nearest-neighbour spatio-textual query. */
    private double benchmarkTkSK(AbstractIRTree tree, IDocumentIndex idx,
                                  DatasetParameters params) {
        SKNNQueryGenerator gen = new SKNNQueryGenerator(SEED, params);
        List<SKNNQuery> queries = gen.generateTopKNNQueries(
                NUM_QUERIES, NUM_KEYWORDS, SPACE_AREA_PCT, KW_SPACE_PCT);
        tree.setAlphaDistribution(ALPHA);
        long t = System.nanoTime();
        for (SKNNQuery q : queries) tree.topkKnnQuery(idx, q, TOPK);
        return avgMs(t, NUM_QUERIES);
    }

    /** Boolean range spatio-textual query. */
    private double benchmarkBRSK(AbstractIRTree tree, IDocumentIndex idx,
                                  DatasetParameters params) {
        SKNNQueryGenerator gen = new SKNNQueryGenerator(SEED, params);
        List<SKNNQuery> queries = gen.generateBooleanRangeQueries(
                NUM_QUERIES, NUM_KEYWORDS, SPACE_AREA_PCT, KW_SPACE_PCT);
        tree.setAlphaDistribution(ALPHA);
        long t = System.nanoTime();
        for (SKNNQuery q : queries) tree.booleanRangeQuery(idx, q, RADIUS);
        return avgMs(t, NUM_QUERIES);
    }

    /** Group nearest-neighbour query (SUM aggregator, group size 2). */
    private double benchmarkGNNK(AbstractIRTree tree, IDocumentIndex idx,
                                  DatasetParameters params) {
        IAggregator agg = AggregatorFactory.getAggregator("SUM");
        AggregateSKNNQueryGenerator gen = new AggregateSKNNQueryGenerator(SEED, params);
        List<AggregateSKNNQuery> queries = gen.generateGNNKQuery(
                NUM_QUERIES, GROUP_SIZE, NUM_KEYWORDS, SPACE_AREA_PCT, KW_SPACE_PCT, agg);
        tree.setAlphaDistribution(ALPHA);
        long t = System.nanoTime();
        for (AggregateSKNNQuery q : queries) tree.gnnk(idx, q, TOPK);
        return avgMs(t, NUM_QUERIES);
    }

    // -----------------------------------------------------------------------
    // Baseline I/O
    // -----------------------------------------------------------------------

    /** True when the file exists and contains at least one real key=value entry. */
    private boolean hasAnyValue(File file) throws IOException {
        Properties p = loadProperties(file);
        for (String key : p.stringPropertyNames()) {
            if (key.endsWith("_avgMs")) return true;
        }
        return false;
    }

    private Properties loadProperties(File file) throws IOException {
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            p.load(fis);
        }
        return p;
    }

    private void recordBaselines(Map<String, Double> measured, File file) throws IOException {
        file.getParentFile().mkdirs();
        Properties props = new Properties();
        props.setProperty("dataset", BENCHMARK_DATASET.name());
        props.setProperty("numQueries", String.valueOf(NUM_QUERIES));
        for (Map.Entry<String, Double> e : measured.entrySet()) {
            if (e.getKey().endsWith("_avgMs")) {
                props.setProperty(e.getKey(), String.format("%.6f", e.getValue()));
            } else if (e.getKey().endsWith("_build_ms")) {
                props.setProperty(e.getKey(), String.format("%.3f", e.getValue()));
            }
        }
        String header =
                "Performance baselines – IRTree / " + BENCHMARK_DATASET.name() + "\n"
                + "Re-calibrate: mvn test -Dtest=PerformanceRegressionTest -Dupdate.perf.baselines=true\n"
                + "Change tolerance: -Dperf.regression.tolerance=<fraction>  (default 1.0 = 100%)";
        try (FileOutputStream fos = new FileOutputStream(file)) {
            props.store(fos, header);
        }
    }

    // -----------------------------------------------------------------------
    // Assertion
    // -----------------------------------------------------------------------

    private void assertWithinBaselines(Map<String, Double> measured,
                                       Properties baselines) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "%n[PerfRegression] Tolerance: %.0f%% slower than baseline%n",
                TOLERANCE * 100));
        sb.append(String.format("  %-22s %14s %14s %10s %8s%n",
                "Query", "Baseline(ms)", "Measured(ms)", "Change%", "Status"));
        sb.append("  ").append(repeat('-', 72)).append(System.lineSeparator());

        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, Double> entry : measured.entrySet()) {
            String key     = entry.getKey();
            double current = entry.getValue();

            if (!key.endsWith("_avgMs")) {
                continue;
            }

            String bStr = baselines.getProperty(key);
            if (bStr == null) {
                bStr = getLegacyHashMapBaseline(baselines, key);
            }

            if (bStr == null) {
                sb.append(String.format("  %-22s %14s %14.6f %10s %8s%n",
                        key, "N/A", current, "N/A", "NEW"));
                continue;
            }

            //double baseline    = Double.parseDouble(bStr);
            double baseline    = Double.parseDouble(bStr.replace(',', '.'));
            double changeRatio = (current - baseline) / baseline;   // >0 = slower
            String status;
            if      (changeRatio >  TOLERANCE) status = "FAIL";
            else if (changeRatio >  0.20)      status = "WARN";
            else if (changeRatio < -0.10)      status = "FAST";
            else                               status = "PASS";

            sb.append(String.format("  %-22s %14.6f %14.6f %9.1f%% %8s%n",
                    key, baseline, current, changeRatio * 100, status));

            if (changeRatio > TOLERANCE) {
                failures.add(String.format(
                        "%-22s  measured %.6f ms  vs baseline %.6f ms  (+%.1f%%)",
                        key, current, baseline, changeRatio * 100));
            }
        }

        System.out.println(sb);

        if (!failures.isEmpty()) {
            String msg = "Performance regression(s) detected:\n  "
                    + String.join("\n  ", failures)
                    + "\nAccept new baselines with: "
                    + "mvn test -Dtest=PerformanceRegressionTest -Dupdate.perf.baselines=true";
            fail(msg);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Elapsed milliseconds since {@code startNano}. */
    private double toMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000.0;
    }

    /** Average milliseconds per query since {@code startNano}. */
    private double avgMs(long startNano, int count) {
        return toMs(startNano) / count;
    }

    private void printMeasured(TextualVariant variant, Map<String, Double> measured, double buildMs) {
        System.out.printf("%n[PerfRegression] Results (%d queries each, dataset=%s, textualIndex=%s):%n",
                NUM_QUERIES, BENCHMARK_DATASET, variant);
        System.out.printf("  %-8s %14s%n", "Query", "Avg/query (ms)");
        System.out.println("  " + repeat('-', 25));
        for (Map.Entry<String, Double> e : measured.entrySet()) {
            System.out.printf("  %-8s %14.6f%n", e.getKey(), e.getValue());
        }
        System.out.printf("  %-8s %14.1f  (index build)%n%n", "Build", buildMs);
    }

    private String metricKey(TextualVariant variant, String queryName) {
        return variant.name() + "_" + queryName + "_avgMs";
    }

    private String buildKey(TextualVariant variant) {
        return variant.name() + "_build_ms";
    }

    private String getLegacyHashMapBaseline(Properties baselines, String newStyleKey) {
        String prefix = TextualVariant.HASHMAP.name() + "_";
        String suffix = "_avgMs";
        if (!newStyleKey.startsWith(prefix) || !newStyleKey.endsWith(suffix)) {
            return null;
        }
        String queryType = newStyleKey.substring(prefix.length(), newStyleKey.length() - suffix.length());
        return baselines.getProperty(queryType + "_avgMs");
    }

    private String repeat(char ch, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, ch);
        return new String(chars);
    }
}


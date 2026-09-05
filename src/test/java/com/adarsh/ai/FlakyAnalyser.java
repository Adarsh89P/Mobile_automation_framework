package com.adarsh.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Ranks tests by flakiness across the last N CI runs.
 *
 * <p>Reads Allure's own accumulated history - the same {@code history/history.json} that the
 * gh-pages publishing step carries between runs - so it needs no separate database and no
 * instrumentation. History is keyed by {@code historyId}; the readable names come from joining
 * against the current run's results.</p>
 *
 * <p><b>Flakiness is measured as status flips, not failure rate.</b> A test that fails in all
 * twenty runs has a failure rate of 100% and is not flaky at all - it is simply broken, and it
 * belongs on a different list. What makes a test flaky is <em>changing its mind</em> with no
 * corresponding change in the code, so the ranking is driven by how often consecutive runs
 * disagree. Ranking by failure rate would put the consistently broken tests at the top and
 * bury the genuinely unstable ones underneath them.</p>
 *
 * <p>Unlike the other two features in this package, this one calls no model and needs no API
 * key. It is arithmetic over data the pipeline already produces, and it runs whether or not
 * {@code ai.enabled} is set - a summary Claude can then be asked to interpret.</p>
 */
public final class FlakyAnalyser {

    private static final Logger LOG = LogManager.getLogger(FlakyAnalyser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Below this, a test is not worth reporting as flaky. */
    private static final double REPORTING_THRESHOLD = 0.01;

    private FlakyAnalyser() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /**
     * One test's stability across the runs held in history.
     *
     * @param testName   readable name, or the historyId when no name could be joined
     * @param runs       how many runs this test appears in
     * @param passed     runs that passed
     * @param failed     runs that failed or broke
     * @param skipped    runs that were skipped
     * @param flips      consecutive runs whose status differed
     * @param flakiness  flips / (runs - 1), i.e. how often it changed its mind
     */
    public record TestStability(
            String testName,
            String historyId,
            int runs,
            int passed,
            int failed,
            int skipped,
            int flips,
            double flakiness,
            String lastStatus,
            String lastMessage) {

        /** Consistently red, never flipping - broken, not flaky. Reported separately. */
        public boolean isConsistentlyFailing() {
            return runs > 1 && failed == runs;
        }

        /**
         * How much of the history is the minority outcome.
         *
         * <p>Flip rate alone overstates a single blip: one failure in the middle of five green
         * runs flips twice out of four transitions, scoring 50% on a test that failed once.
         * Weighting by how balanced the outcomes actually are separates "changes its mind
         * constantly" from "hiccupped once".</p>
         */
        public double instabilityShare() {
            return runs == 0 ? 0.0 : (double) Math.min(passed, failed) / runs;
        }

        public String verdict() {
            if (isConsistentlyFailing()) {
                return "BROKEN (always fails - not flaky, just failing)";
            }
            if (flakiness >= 0.5 && instabilityShare() >= 0.4) {
                return "HIGHLY FLAKY";
            }
            if (flakiness >= 0.25) {
                return "FLAKY";
            }
            if (flakiness > 0) {
                return "OCCASIONALLY UNSTABLE";
            }
            return "STABLE";
        }
    }

    /**
     * Builds the ranked report.
     *
     * @param historyFile  Allure's {@code history/history.json}
     * @param resultsDir   the current run's {@code allure-results}, used only for test names
     */
    public static List<TestStability> analyse(Path historyFile, Path resultsDir) {
        if (!Files.exists(historyFile)) {
            LOG.warn("No Allure history at {} - a flakiness report needs at least two runs "
                    + "with history carried between them", historyFile);
            return List.of();
        }
        try {
            Map<String, String> names = readTestNames(resultsDir);
            JsonNode history = MAPPER.readTree(historyFile.toFile());

            List<TestStability> ranked = new ArrayList<>();
            history.fields().forEachRemaining(entry ->
                    toStability(entry.getKey(), entry.getValue(), names).ifPresent(ranked::add));

            ranked.sort(Comparator
                    .comparingDouble(TestStability::flakiness).reversed()
                    .thenComparing(Comparator.comparingInt(TestStability::failed).reversed())
                    .thenComparing(TestStability::testName));
            return ranked;

        } catch (IOException e) {
            LOG.warn("Could not read the Allure history: {}", e.getMessage());
            return List.of();
        }
    }

    private static Optional<TestStability> toStability(
            String historyId, JsonNode entry, Map<String, String> names) {

        JsonNode items = entry.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }

        // Allure stores newest first; reverse to read the runs chronologically so that
        // "flips" means what it sounds like.
        List<String> statuses = new ArrayList<>();
        for (JsonNode item : items) {
            statuses.add(item.path("status").asText("unknown"));
        }
        java.util.Collections.reverse(statuses);

        int passed = (int) statuses.stream().filter("passed"::equals).count();
        int failed = (int) statuses.stream()
                .filter(s -> "failed".equals(s) || "broken".equals(s)).count();
        int skipped = (int) statuses.stream().filter("skipped"::equals).count();

        int flips = 0;
        for (int i = 1; i < statuses.size(); i++) {
            if (!statuses.get(i).equals(statuses.get(i - 1))) {
                flips++;
            }
        }
        double flakiness = statuses.size() < 2 ? 0.0 : (double) flips / (statuses.size() - 1);

        JsonNode latest = items.get(0);
        return Optional.of(new TestStability(
                names.getOrDefault(historyId, historyId),
                historyId,
                statuses.size(),
                passed,
                failed,
                skipped,
                flips,
                Math.round(flakiness * 1000) / 1000.0,
                latest.path("status").asText("unknown"),
                firstLine(latest.path("statusDetails").asText(""))));
    }

    /** historyId -> readable name, from whichever results are on disk. */
    private static Map<String, String> readTestNames(Path resultsDir) {
        Map<String, String> names = new HashMap<>();
        if (resultsDir == null || !Files.isDirectory(resultsDir)) {
            return names;
        }
        try (Stream<Path> files = Files.list(resultsDir)) {
            files.filter(path -> path.getFileName().toString().endsWith("-result.json"))
                    .forEach(path -> {
                        try {
                            JsonNode result = MAPPER.readTree(path.toFile());
                            String id = result.path("historyId").asText("");
                            String full = result.path("fullName").asText("");
                            if (!id.isBlank() && !full.isBlank()) {
                                names.putIfAbsent(id, shortName(full));
                            }
                        } catch (IOException e) {
                            LOG.debug("Skipping unreadable result {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOG.debug("Could not list {}: {}", resultsDir, e.getMessage());
        }
        return names;
    }

    private static String shortName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        if (lastDot < 1) {
            return fullName;
        }
        int classDot = fullName.lastIndexOf('.', lastDot - 1);
        return classDot < 0 ? fullName : fullName.substring(classDot + 1);
    }

    private static String firstLine(String text) {
        return text == null || text.isBlank()
                ? "" : text.lines().findFirst().orElse("").trim();
    }

    /** Writes the JSON report and returns the entries that cleared the threshold. */
    public static List<TestStability> writeReport(
            Path historyFile, Path resultsDir, Path output) {

        List<TestStability> all = analyse(historyFile, resultsDir);
        List<TestStability> flaky = all.stream()
                .filter(test -> test.flakiness() > REPORTING_THRESHOLD)
                .toList();
        List<TestStability> broken = all.stream()
                .filter(TestStability::isConsistentlyFailing)
                .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("runsAnalysed", all.stream().mapToInt(TestStability::runs).max().orElse(0));
        report.put("testsAnalysed", all.size());
        report.put("method", "Ranked by status flips between consecutive runs, not by failure "
                + "rate - a test that always fails is broken, not flaky.");
        report.put("flaky", flaky);
        report.put("consistentlyFailing", broken);

        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, MAPPER.writeValueAsString(report));
            LOG.info("Flakiness report written to {} ({} flaky, {} consistently failing)",
                    output, flaky.size(), broken.size());
        } catch (IOException e) {
            LOG.warn("Could not write the flakiness report: {}", e.getMessage());
        }
        return flaky;
    }

    /** Human-readable table, for the CI log where someone will actually see it. */
    public static String renderTable(List<TestStability> tests) {
        if (tests.isEmpty()) {
            return "No flaky tests detected.";
        }
        StringBuilder table = new StringBuilder()
                .append("%-52s %5s %6s %6s %9s  %s%n"
                        .formatted("TEST", "RUNS", "PASS", "FAIL", "FLAKINESS", "VERDICT"))
                .append("-".repeat(110)).append(System.lineSeparator());
        for (TestStability test : tests) {
            table.append("%-52s %5d %6d %6d %8.0f%%  %s%n".formatted(
                    truncate(test.testName(), 52),
                    test.runs(), test.passed(), test.failed(),
                    test.flakiness() * 100, test.verdict()));
        }
        return table.toString();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    /**
     * CLI entry point, so CI can run this as a post-build step.
     *
     * <pre>
     * java -cp target/test-classes:... com.adarsh.ai.FlakyAnalyser \
     *      allure-report/history/history.json allure-results target/flakiness-report.json
     * </pre>
     */
    public static void main(String[] args) {
        Path history = Paths.get(args.length > 0 ? args[0] : "allure-report/history/history.json");
        Path results = Paths.get(args.length > 1 ? args[1] : "allure-results");
        Path output = Paths.get(args.length > 2 ? args[2] : "target/flakiness-report.json");

        List<TestStability> flaky = writeReport(history, results, output);
        System.out.println(renderTable(flaky));
        // Always exits 0: a flakiness report is information, not a build gate. Failing the
        // build here would make the pipeline red for something nobody can fix in that run.
    }
}

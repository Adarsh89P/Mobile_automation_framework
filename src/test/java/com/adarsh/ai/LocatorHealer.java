package com.adarsh.ai;

import com.adarsh.config.ConfigReader;
import com.adarsh.core.DriverManager;
import com.adarsh.utils.AppUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.appium.java_client.AppiumBy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Suggests a replacement when a locator stops matching.
 *
 * <p>Locators rot. A developer renames a {@code testID}, forty tests fail, and someone spends
 * an afternoon in the inspector rediscovering what the new one is. The page source at the
 * moment of failure already contains the answer; this asks the model to find it and writes the
 * suggestion to {@code healing-report.json} so the fix is a five-minute edit instead.</p>
 *
 * <p><b>It suggests; it does not decide.</b> By default the suggestion is recorded and the
 * original failure is rethrown, so the test still fails and the report still says why. Setting
 * {@code ai.healing.apply=true} additionally retries once with the suggested locator - useful
 * when you want to know whether the suggestion actually works, but it lets the AI layer change
 * an outcome, so it stays off in CI. Self-healing that silently keeps a suite green is how a
 * team stops noticing that its locators no longer describe the product.</p>
 */
public final class LocatorHealer {

    private static final Logger LOG = LogManager.getLogger(LocatorHealer.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Accumulated for the run and flushed once, so parallel threads do not fight over a file. */
    private static final List<Map<String, Object>> SUGGESTIONS = new CopyOnWriteArrayList<>();

    private static final String SYSTEM_PROMPT = """
            You are an Appium locator expert. An element was not found. Using the page source,
            identify the element the automation was most likely looking for and return a
            replacement locator.

            Strategy preference, best first:
              1. accessibility id  (content-desc on Android, name on iOS)
              2. id                (resource-id)
              3. xpath             (last resort only)

            Rules:
            - The locator must match an element that is actually present in the page source.
            - Return NONE if no plausible candidate exists. Do not invent an element.
            - Prefer a stable attribute over a positional path. Never return an index-based
              xpath such as (//android.widget.TextView)[3].

            Reply with JSON only, no prose and no code fences:
            {"strategy":"accessibility|id|xpath|none","value":"<locator or empty>",
             "confidence":<0-100>,"reasoning":"<one sentence>"}
            """;

    private LocatorHealer() {
        throw new AssertionError("Utility class - not instantiable");
    }

    public static boolean isEnabled() {
        return ConfigReader.config().aiHealingEnabled() && ClaudeClient.isAvailable();
    }

    /**
     * Asks for a replacement locator and records the suggestion.
     *
     * @param original    the locator that failed
     * @param description what the automation was trying to reach, in words
     * @return the suggested locator, or empty when the layer is off or nothing plausible exists
     */
    public static Optional<By> suggest(By original, String description) {
        if (!isEnabled() || !DriverManager.hasDriver()) {
            return Optional.empty();
        }
        try {
            String pageSource = ClaudeClient.trim(
                    AppUtils.pageSource(), ConfigReader.config().aiPageSourceLimit());

            String prompt = """
                    ELEMENT DESCRIPTION
                    %s

                    LOCATOR THAT FAILED
                    %s

                    PAGE SOURCE
                    %s
                    """.formatted(description, original, pageSource);

            Optional<String> response = ClaudeClient.ask(SYSTEM_PROMPT, prompt, "locator healing");
            if (response.isEmpty()) {
                return Optional.empty();
            }

            Suggestion suggestion = parse(response.get());
            record(original, description, suggestion);

            if (suggestion == null || suggestion.isNone()) {
                LOG.info("No locator suggestion for {}", description);
                return Optional.empty();
            }

            LOG.warn("SUGGESTED LOCATOR for {}: {}={} ({}% confidence) - {}",
                    description, suggestion.strategy, suggestion.value,
                    suggestion.confidence, suggestion.reasoning);
            return suggestion.toBy();

        } catch (RuntimeException e) {
            LOG.warn("Locator healing skipped: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Attempts one retry with a suggested locator, if that is switched on.
     *
     * <p>Returns empty when {@code ai.healing.apply} is false, which is the default - the
     * suggestion is still recorded, but the caller rethrows and the test still fails.</p>
     */
    public static Optional<WebElement> healAndRetry(By original, String description) {
        Optional<By> suggested = suggest(original, description);
        if (suggested.isEmpty()) {
            return Optional.empty();
        }
        if (!ConfigReader.config().aiHealingApply()) {
            LOG.warn("ai.healing.apply=false - the suggestion was recorded but not retried, "
                    + "so this test still fails on its own merits");
            return Optional.empty();
        }
        try {
            WebElement healed = DriverManager.getDriver().findElement(suggested.get());
            LOG.warn("HEALED: {} was found with the suggested locator {}. This test passed with "
                    + "an AI-supplied locator - fix the page object before trusting the result.",
                    description, suggested.get());
            return Optional.of(healed);
        } catch (RuntimeException e) {
            LOG.info("The suggested locator did not match either: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void record(By original, String description, Suggestion suggestion) {
        SUGGESTIONS.add(new java.util.LinkedHashMap<>(Map.of(
                "timestamp", Instant.now().toString(),
                "thread", Thread.currentThread().getName(),
                "element", description,
                "failedLocator", String.valueOf(original),
                "suggestedStrategy", suggestion == null ? "none" : suggestion.strategy,
                "suggestedValue", suggestion == null ? "" : suggestion.value,
                "confidence", suggestion == null ? 0 : suggestion.confidence,
                "reasoning", suggestion == null ? "no suggestion returned" : suggestion.reasoning,
                "applied", ConfigReader.config().aiHealingApply())));
    }

    /** Writes the report. Called once at the end of the run by the test listener. */
    public static void flushReport() {
        if (SUGGESTIONS.isEmpty()) {
            return;
        }
        Path target = Paths.get(ConfigReader.config().aiHealingReport());
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Map<String, Object> report = new java.util.LinkedHashMap<>();
            report.put("generatedAt", Instant.now().toString());
            report.put("note", "Suggestions only. Review each one before changing a page object.");
            report.put("appliedDuringRun", ConfigReader.config().aiHealingApply());
            report.put("count", SUGGESTIONS.size());
            report.put("suggestions", SUGGESTIONS);

            Files.writeString(target, MAPPER.writeValueAsString(report));
            LOG.warn("Wrote {} locator suggestion(s) to {}", SUGGESTIONS.size(), target);
        } catch (IOException e) {
            LOG.warn("Could not write the healing report: {}", e.getMessage());
        }
    }

    static void clear() {
        SUGGESTIONS.clear();
    }

    public static int suggestionCount() {
        return SUGGESTIONS.size();
    }

    // ------------------------------------------------------------------ response shape

    private static Suggestion parse(String response) {
        try {
            return MAPPER.readValue(FailureTriage.stripFences(response), Suggestion.class);
        } catch (Exception e) {
            LOG.warn("Could not parse the locator suggestion: {}", e.getMessage());
            return null;
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    static final class Suggestion {
        public String strategy = "none";
        public String value = "";
        public int confidence;
        public String reasoning = "";

        boolean isNone() {
            return value == null || value.isBlank()
                    || "none".equalsIgnoreCase(strategy == null ? "" : strategy.trim());
        }

        Optional<By> toBy() {
            if (isNone()) {
                return Optional.empty();
            }
            return switch (strategy.trim().toLowerCase(Locale.ROOT)) {
                case "accessibility", "accessibility id", "accessibilityid" ->
                        Optional.of(AppiumBy.accessibilityId(value));
                case "id", "resource-id" -> Optional.of(By.id(value));
                case "xpath" -> Optional.of(By.xpath(value));
                default -> {
                    LOG.warn("Unknown suggested strategy '{}' - ignoring", strategy);
                    yield Optional.empty();
                }
            };
        }
    }
}

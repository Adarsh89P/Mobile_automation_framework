package com.adarsh.ai;

import com.adarsh.config.ConfigReader;
import com.adarsh.core.DriverManager;
import com.adarsh.utils.AppUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Classifies a failure so a human knows who should look at it.
 *
 * <p>Triage is the slowest part of owning a mobile suite: a red run is a stack trace, a
 * screenshot and a question - <em>is this us or is this them?</em> This produces a first
 * opinion on that question and attaches it to the report. It is explicitly advisory; the test
 * has already passed or failed by the time this runs, and nothing here can change that.</p>
 */
public final class FailureTriage {

    private static final Logger LOG = LogManager.getLogger(FailureTriage.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * The instruction is deliberately strict about two things: the exact category vocabulary,
     * and the requirement to say UNKNOWN rather than guess. A triage bot that always produces
     * a confident answer is worse than one that admits when the evidence is thin - the whole
     * value is in trusting the verdicts that do come back.
     */
    private static final String SYSTEM_PROMPT = """
            You are a senior mobile QA engineer triaging one failed Appium test.

            Classify the failure into exactly one category:
              PRODUCT_BUG  - the application behaved incorrectly
              TEST_BUG     - the test's expectation, locator or flow is wrong
              ENVIRONMENT  - device, emulator, driver, build or network problem
              FLAKY        - timing or race; the app is most likely correct
              UNKNOWN      - the evidence does not support a confident classification

            Rules:
            - Prefer UNKNOWN over a guess. A wrong confident verdict costs more than no verdict.
            - An element that is absent from the page source is usually TEST_BUG or PRODUCT_BUG,
              not FLAKY. Only call it FLAKY if there is positive evidence of a timing issue.
            - A session that never started is ENVIRONMENT.
            - Judge only from the evidence given; do not invent app behaviour.

            Reply with JSON only, no prose and no code fences:
            {"category":"...","confidence":<0-100>,"reasoning":"<max 2 sentences>",
             "nextStep":"<the single most useful next action>"}
            """;

    private FailureTriage() {
        throw new AssertionError("Utility class - not instantiable");
    }

    public static boolean isEnabled() {
        return ConfigReader.config().aiTriageEnabled() && ClaudeClient.isAvailable();
    }

    /**
     * Produces a verdict for a failed test.
     *
     * @param testName the failing test
     * @param lastStep the last action the framework logged before failing, for context
     * @param error    the failure
     * @return the verdict, or empty when the layer is off or the model gave nothing usable
     */
    public static Optional<TriageVerdict> triage(String testName, String lastStep, Throwable error) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            String prompt = buildPrompt(testName, lastStep, error);
            return ClaudeClient.ask(SYSTEM_PROMPT, prompt, "failure triage")
                    .flatMap(FailureTriage::parse);
        } catch (RuntimeException e) {
            LOG.warn("Failure triage skipped: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String buildPrompt(String testName, String lastStep, Throwable error) {
        // Page source is only available while a session is alive. Its absence is itself a
        // signal - it usually means the driver never started - so the prompt says so.
        String pageSource = DriverManager.hasDriver()
                ? ClaudeClient.trim(AppUtils.pageSource(), ConfigReader.config().aiPageSourceLimit())
                : "(no live session - the driver was not available when the test failed)";

        String context = DriverManager.hasDriver()
                ? "Current package: %s%nCurrent activity: %s"
                        .formatted(AppUtils.currentPackage(), AppUtils.currentActivity())
                : "(no live session)";

        return """
                TEST
                %s

                LAST STEP BEFORE FAILURE
                %s

                ERROR
                %s

                DEVICE CONTEXT
                %s

                PAGE SOURCE AT FAILURE
                %s
                """.formatted(
                testName,
                lastStep == null || lastStep.isBlank() ? "(not recorded)" : lastStep,
                describe(error),
                context,
                pageSource);
    }

    /** Type, message and the frames in our own packages - the rest is framework noise. */
    private static String describe(Throwable error) {
        if (error == null) {
            return "(no throwable recorded)";
        }
        StringBuilder text = new StringBuilder()
                .append(error.getClass().getName())
                .append(": ")
                .append(error.getMessage());

        java.util.Arrays.stream(error.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("com.adarsh"))
                .limit(8)
                .forEach(frame -> text.append(System.lineSeparator()).append("    at ").append(frame));

        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            text.append(System.lineSeparator())
                    .append("Caused by: ").append(cause.getClass().getSimpleName())
                    .append(": ").append(cause.getMessage());
        }
        return text.toString();
    }

    /**
     * Parses the model's JSON, tolerating the code fences models sometimes add anyway.
     *
     * <p>A malformed response yields an empty Optional, not an exception: the report simply
     * carries no verdict, which is the correct degradation.</p>
     */
    private static Optional<TriageVerdict> parse(String response) {
        try {
            String json = stripFences(response);
            TriageVerdict verdict = MAPPER.readValue(json, TriageVerdict.class);
            LOG.info("AI triage verdict: {} ({}% confidence)",
                    verdict.asCategory().label(), verdict.confidence());
            return Optional.of(verdict);
        } catch (Exception e) {
            LOG.warn("Could not parse the triage response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static String stripFences(String response) {
        String text = response.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > -1) {
                text = text.substring(firstNewline + 1);
            }
            int fence = text.lastIndexOf("```");
            if (fence > -1) {
                text = text.substring(0, fence);
            }
        }
        // Some responses wrap the object in a sentence; take the outermost JSON object.
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start > -1 && end > start ? text.substring(start, end + 1) : text.trim();
    }
}

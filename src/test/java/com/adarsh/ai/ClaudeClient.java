package com.adarsh.ai;

import com.adarsh.config.ConfigReader;
import com.adarsh.config.FrameworkConfig;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Thin wrapper over the Anthropic Messages API, with one rule above all others:
 * <b>it can never fail a test</b>.
 *
 * <p>Every path returns {@link Optional} rather than throwing. The AI layer is an aid to the
 * human reading the report, not part of the assertion chain - so a missing key, an expired
 * key, a rate limit, a network partition or a malformed response must all degrade to "no
 * verdict available" and nothing else. A framework where an unrelated outage turns the suite
 * red is worse than one with no AI at all.</p>
 *
 * <p><b>Credentials.</b> The key is read from {@code ANTHROPIC_API_KEY} in the environment and
 * from nowhere else - never a properties file, never a constant, never a CI variable echoed
 * into a log. If it is absent the layer switches itself off.</p>
 */
public final class ClaudeClient {

    private static final Logger LOG = LogManager.getLogger(ClaudeClient.class);

    private static final String API_KEY_ENV = "ANTHROPIC_API_KEY";

    /** Lazily built once; null when the layer is switched off or unusable. */
    private static volatile AnthropicClient client;

    private static volatile boolean initialised;

    private ClaudeClient() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /**
     * Whether AI features should run at all.
     *
     * <p>Checked before any prompt is built, so that with the flag off the framework does not
     * even pay for assembling a page source.</p>
     */
    public static boolean isAvailable() {
        return ConfigReader.config().aiEnabled() && apiKey().isPresent();
    }

    private static Optional<String> apiKey() {
        String key = System.getenv(API_KEY_ENV);
        return key == null || key.isBlank() ? Optional.empty() : Optional.of(key.trim());
    }

    /**
     * Sends one prompt and returns the model's text response.
     *
     * @param system  the role instruction - what kind of answer is wanted
     * @param user    the request itself, including the evidence
     * @param purpose short label used in logs, so a slow or failing call is attributable
     * @return the response text, or empty if the layer is off or anything at all went wrong
     */
    public static Optional<String> ask(String system, String user, String purpose) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        AnthropicClient anthropic = clientOrNull();
        if (anthropic == null) {
            return Optional.empty();
        }

        FrameworkConfig config = ConfigReader.config();
        long start = System.currentTimeMillis();
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(config.aiModel())
                    .maxTokens(config.aiMaxTokens())
                    .system(system)
                    .outputConfig(OutputConfig.builder()
                            .effort(effort(config.aiEffort()))
                            .build())
                    .addUserMessage(user)
                    .build();

            Message response = anthropic.messages().create(params);
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(block -> block.text())
                    .reduce("", (a, b) -> a + b)
                    .trim();

            LOG.info("AI {} completed in {}ms ({} input / {} output tokens)",
                    purpose,
                    System.currentTimeMillis() - start,
                    response.usage().inputTokens(),
                    response.usage().outputTokens());
            return text.isBlank() ? Optional.empty() : Optional.of(text);

        } catch (RuntimeException e) {
            // Deliberately broad. Rate limits, auth failures, timeouts, SDK changes - none of
            // them are a reason to change the outcome of a mobile test.
            LOG.warn("AI {} unavailable after {}ms: {}",
                    purpose, System.currentTimeMillis() - start, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code Effort} is an open value type in the SDK, not a Java enum - {@code of()} accepts
     * any string so new levels do not break the client. That means an unknown value would be
     * sent to the API and rejected there, so it is validated locally first.
     */
    private static OutputConfig.Effort effort(String configured) {
        String value = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "low" -> OutputConfig.Effort.LOW;
            case "medium" -> OutputConfig.Effort.MEDIUM;
            case "high" -> OutputConfig.Effort.HIGH;
            case "xhigh" -> OutputConfig.Effort.XHIGH;
            case "max" -> OutputConfig.Effort.MAX;
            default -> {
                LOG.warn("Unknown ai.effort '{}' - using low", configured);
                yield OutputConfig.Effort.LOW;
            }
        };
    }

    /** Builds the client on first use; a construction failure disables the layer for the run. */
    private static AnthropicClient clientOrNull() {
        if (initialised) {
            return client;
        }
        synchronized (ClaudeClient.class) {
            if (initialised) {
                return client;
            }
            initialised = true;
            try {
                client = AnthropicOkHttpClient.builder()
                        .apiKey(apiKey().orElseThrow())
                        .timeout(Duration.ofSeconds(ConfigReader.config().aiTimeoutSeconds()))
                        .build();
                LOG.info("AI layer enabled (model: {})", ConfigReader.config().aiModel());
            } catch (RuntimeException e) {
                LOG.warn("AI layer could not start, continuing without it: {}", e.getMessage());
                client = null;
            }
            return client;
        }
    }

    /**
     * Trims oversized evidence before it is sent.
     *
     * <p>A mobile page source runs to hundreds of kilobytes. Sending it whole is slow, costly
     * and counter-productive - the useful signal is near the top of the hierarchy. Keeping the
     * head and marking the cut is more honest than silently sending a truncated document.</p>
     */
    public static String trim(String content, int limit) {
        if (content == null) {
            return "";
        }
        if (content.length() <= limit) {
            return content;
        }
        return content.substring(0, limit)
                + System.lineSeparator()
                + "... [truncated " + (content.length() - limit) + " more characters]";
    }

    /** Test seam: forces the next call to rebuild the client. */
    static void reset() {
        synchronized (ClaudeClient.class) {
            client = null;
            initialised = false;
        }
    }
}

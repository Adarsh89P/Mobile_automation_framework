package com.adarsh.listeners;

import com.adarsh.config.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retries a failed test up to {@code retry.count} times (default 2).
 *
 * <p><b>Retrying is a diagnosis aid, not a cure.</b> A test that only passes on the third
 * attempt is still broken; the retry buys a green pipeline today and a flakiness report to act
 * on tomorrow. That is why every retry is logged loudly and surfaced in the report by
 * {@link TestListener} rather than being quietly absorbed — a framework that hides retries is
 * a framework that lets real product bugs through as "just flaky".</p>
 *
 * <p>Attempts are counted per <em>invocation</em>, not per method: a data-driven test runs the
 * same method several times with different data, and one bad data row must not exhaust the
 * retry budget of the others. The key therefore includes the parameters.</p>
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LogManager.getLogger(RetryAnalyzer.class);

    /** Shared across threads: parallel {@code <test>} blocks retry independently. */
    private static final Map<String, AtomicInteger> ATTEMPTS = new ConcurrentHashMap<>();

    private final int maxRetries = ConfigReader.config().retryCount();

    @Override
    public boolean retry(ITestResult result) {
        String key = invocationKey(result);
        AtomicInteger attempts = ATTEMPTS.computeIfAbsent(key, ignored -> new AtomicInteger());

        if (attempts.get() >= maxRetries) {
            LOG.error("{} failed after {} attempt(s) - giving up", key, attempts.get() + 1);
            return false;
        }

        int attempt = attempts.incrementAndGet();
        // Flag the attempt that is being abandoned. TestNG records it as a skip, and an
        // unflagged skip is indistinguishable from a test that was never meant to run -
        // which is exactly how retries end up hiding intermittent failures.
        AllureAttachmentListener.markFlaky(
                "Attempt %d failed and is being retried".formatted(attempt));
        LOG.warn("{} failed ({}), retrying: attempt {} of {}",
                key,
                rootCause(result),
                attempt + 1,
                maxRetries + 1);
        return true;
    }

    /** How many times this invocation was re-run. Read by the listener when reporting. */
    public static int attemptsFor(ITestResult result) {
        AtomicInteger attempts = ATTEMPTS.get(invocationKey(result));
        return attempts == null ? 0 : attempts.get();
    }

    public static boolean wasRetried(ITestResult result) {
        return attemptsFor(result) > 0;
    }

    /** Cleared between suites so a long-lived JVM does not carry counts across runs. */
    public static void reset() {
        ATTEMPTS.clear();
    }

    private static String invocationKey(ITestResult result) {
        String method = result.getTestClass().getName() + "." + result.getMethod().getMethodName();
        Object[] parameters = result.getParameters();
        return parameters.length == 0 ? method : method + Arrays.toString(parameters);
    }

    private static String rootCause(ITestResult result) {
        Throwable error = result.getThrowable();
        if (error == null) {
            return "no throwable recorded";
        }
        String message = error.getMessage();
        String summary = error.getClass().getSimpleName()
                + (message == null ? "" : ": " + message.lines().findFirst().orElse(""));
        return summary.length() > 200 ? summary.substring(0, 200) + "..." : summary;
    }
}

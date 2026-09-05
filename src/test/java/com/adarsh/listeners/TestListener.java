package com.adarsh.listeners;

import com.adarsh.ai.FailureTriage;
import com.adarsh.ai.LocatorHealer;
import com.adarsh.ai.TriageVerdict;
import com.adarsh.config.ConfigReader;
import com.adarsh.core.DriverManager;
import com.adarsh.utils.AppUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IConfigurationListener;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The reporting spine: evidence on failure, visible retries, and a run summary.
 *
 * <p>Registered through {@code META-INF/services}, so it applies to every suite automatically
 * and cannot be forgotten when a new suite file is added.</p>
 */
public class TestListener
        implements ITestListener, ISuiteListener, IConfigurationListener, IInvokedMethodListener {

    private static final Logger LOG = LogManager.getLogger(TestListener.class);

    /** Base64 recordings, keyed per invocation, kept only until the test finishes. */
    private static final Map<String, String> RECORDINGS = new ConcurrentHashMap<>();

    private final AtomicInteger retriedTests = new AtomicInteger();

    // ------------------------------------------------------------------ suite

    @Override
    public void onStart(ISuite suite) {
        RetryAnalyzer.reset();
        LOG.info("### Suite '{}' starting | platform={} execution={} app={} ###",
                suite.getName(),
                ConfigReader.config().platform(),
                ConfigReader.config().execution(),
                ConfigReader.config().app());
    }

    @Override
    public void onFinish(ISuite suite) {
        // Written once per run rather than per failure, so parallel threads never contend
        // for the file. No-op when nothing was suggested.
        LocatorHealer.flushReport();

        if (retriedTests.get() > 0) {
            // Surfaced at the end of the console output as well as in the report: a retry that
            // nobody notices is a flaky test that nobody fixes.
            LOG.warn("### Suite '{}' finished with {} retried test(s) - see the 'Retried' "
                    + "label in Allure ###", suite.getName(), retriedTests.get());
        }
    }

    // ------------------------------------------------------------------ invocation

    /**
     * Marks retried invocations as flaky, and attaches evidence, while the Allure test case is
     * still open.
     *
     * <p>This is deliberately not done from {@code onTestSuccess}/{@code onTestFailure}. Allure's
     * own TestNG listener finalises and writes the result during those callbacks, and listener
     * order between two {@code ITestListener}s is not guaranteed - so an update made there is
     * frequently applied to a result that has already been written, and silently vanishes.
     * TestNG calls {@code afterInvocation} before any of that, which is the last point where
     * the test case is reliably still mutable.</p>
     */
    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult result) {
        if (!method.isTestMethod() || !RetryAnalyzer.wasRetried(result)) {
            return;
        }
        int retries = RetryAnalyzer.attemptsFor(result);
        AllureAttachmentListener.markFlaky(result.isSuccess()
                ? "Passed after " + retries + " retry(ies)"
                : "Attempt failed and was retried (" + retries + " so far)");
    }

    // ------------------------------------------------------------------ test lifecycle

    @Override
    public void onTestStart(ITestResult result) {
        if (ConfigReader.config().recordVideoOnFailure() && AppUtils.startScreenRecording()) {
            RECORDINGS.put(key(result), "");
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        // A passing test's recording is discarded, but it still has to be stopped - a device
        // left encoding video will fail the next session's recording attempt.
        discardRecording(result);

        if (RetryAnalyzer.wasRetried(result)) {
            int attempts = RetryAnalyzer.attemptsFor(result) + 1;
            retriedTests.incrementAndGet();
            String note = """
                    This test PASSED, but only on attempt %d of %d.

                    It is flaky, not healthy. The pipeline is green because the retry policy
                    made it green; the underlying instability is still there and will surface
                    again - usually on someone else's pull request.
                    """.formatted(attempts, ConfigReader.config().retryCount() + 1);

            LOG.warn("FLAKY: {} passed on attempt {}", name(result), attempts);
            AllureAttachmentListener.attachNote("Flaky - passed on retry", note);
        }
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String testName = name(result);
        LOG.error("FAILED: {} - {}", testName, message(result));

        AllureAttachmentListener.attachFailureEvidence(testName);
        attachRecording(result, testName);
        triage(result, testName);

        if (RetryAnalyzer.wasRetried(result)) {
            retriedTests.incrementAndGet();
            int attempts = RetryAnalyzer.attemptsFor(result) + 1;
            AllureAttachmentListener.attachNote("Retry history",
                    "Retried %d time(s) and failed every time. This is a consistent failure, "
                            + "not flakiness.".formatted(attempts - 1));
        }
    }

    /**
     * TestNG reports a failed-and-retried attempt as skipped.
     *
     * <p>Left alone, that is how retries hide bugs: the report shows a tidy skip where an
     * intermittent failure actually happened. So the intermediate attempt keeps its evidence
     * and is labelled, rather than being swallowed.</p>
     */
    @Override
    public void onTestSkipped(ITestResult result) {
        if (result.getThrowable() != null && RetryAnalyzer.wasRetried(result)) {
            LOG.warn("RETRYING: {} - attempt {} failed with {}",
                    name(result), RetryAnalyzer.attemptsFor(result), message(result));
            AllureAttachmentListener.attachFailureEvidence(name(result) + " (failed attempt)");
        } else {
            LOG.info("SKIPPED: {} - {}", name(result), message(result));
        }
        discardRecording(result);
    }

    /**
     * A failure in {@code @BeforeMethod} - typically the driver refusing to start.
     *
     * <p>TestNG treats a configuration failure as a different kind of event: it does not call
     * {@code onTestFailure}, and retry analyzers do not apply to it. Without this hook the run
     * reports a pile of skipped tests and no reason for any of them, which is the least useful
     * possible outcome. Here the cause is logged once, loudly, with whatever the device can
     * still tell us.</p>
     */
    @Override
    public void onConfigurationFailure(ITestResult result) {
        LOG.error("SETUP FAILED: {} - {}", name(result), message(result));
        AllureAttachmentListener.attachNote("Setup failure",
                "%s failed before the test body ran.%n%nCause: %s%n%nNo test steps were "
                        + "executed, so the tests that follow are skipped rather than failed."
                        .formatted(name(result), message(result)));
        AllureAttachmentListener.attachFailureEvidence(name(result) + " (setup)");
    }

    @Override
    public void onConfigurationSkip(ITestResult result) {
        LOG.warn("SETUP SKIPPED: {}", name(result));
    }

    @Override
    public void onFinish(ITestContext context) {
        LOG.info("### {} finished: {} passed, {} failed, {} skipped ###",
                context.getName(),
                context.getPassedTests().size(),
                context.getFailedTests().size(),
                context.getSkippedTests().size());
    }

    /**
     * Asks the AI layer to classify the failure, if it is switched on.
     *
     * <p>Runs after the evidence is attached and after the result is already decided, so it
     * cannot influence the outcome. Wrapped defensively on top of the client's own guards -
     * a reporting nicety must never be able to break teardown.</p>
     */
    private void triage(ITestResult result, String testName) {
        if (!FailureTriage.isEnabled()) {
            return;
        }
        try {
            FailureTriage.triage(testName, lastStepOf(result), result.getThrowable())
                    .map(TriageVerdict::render)
                    .ifPresent(verdict ->
                            AllureAttachmentListener.attachNote("AI triage verdict", verdict));
        } catch (RuntimeException e) {
            LOG.warn("AI triage skipped for {}: {}", testName, e.getMessage());
        }
    }

    /** Best available description of what the test was doing when it failed. */
    private static String lastStepOf(ITestResult result) {
        String description = result.getMethod().getDescription();
        return description == null || description.isBlank()
                ? result.getMethod().getMethodName() : description;
    }

    // ------------------------------------------------------------------ internals

    private void attachRecording(ITestResult result, String testName) {
        if (!RECORDINGS.containsKey(key(result))) {
            return;
        }
        String video = DriverManager.hasDriver() ? AppUtils.stopScreenRecording() : "";
        RECORDINGS.remove(key(result));
        AllureAttachmentListener.attachVideo(testName, video);
    }

    private void discardRecording(ITestResult result) {
        if (RECORDINGS.remove(key(result)) != null && DriverManager.hasDriver()) {
            AppUtils.stopScreenRecording();
        }
    }

    private static String key(ITestResult result) {
        return result.getTestClass().getName() + "." + result.getMethod().getMethodName()
                + java.util.Arrays.toString(result.getParameters());
    }

    private static String name(ITestResult result) {
        return result.getTestClass().getRealClass().getSimpleName()
                + "." + result.getMethod().getMethodName();
    }

    private static String message(ITestResult result) {
        Throwable error = result.getThrowable();
        if (error == null) {
            return "no error recorded";
        }
        String text = error.getMessage();
        return text == null ? error.getClass().getSimpleName()
                : text.lines().findFirst().orElse(error.getClass().getSimpleName());
    }
}

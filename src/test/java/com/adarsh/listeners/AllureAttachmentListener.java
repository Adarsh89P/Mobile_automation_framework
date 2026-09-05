package com.adarsh.listeners;

import com.adarsh.config.ConfigReader;
import com.adarsh.core.DriverManager;
import com.adarsh.utils.AppUtils;
import com.adarsh.utils.ScreenshotUtils;
import io.qameta.allure.Allure;
import io.qameta.allure.model.StatusDetails;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Collects the evidence bundle attached to a failed test.
 *
 * <p>The set is chosen to answer the four questions triage always asks, in order: <em>what did
 * the screen look like</em> (screenshot), <em>what did the app think was on it</em> (page
 * source), <em>where in the app were we</em> (current activity and package), and <em>what did
 * the device say</em> (logcat). A failure with all four is usually diagnosable from the report
 * alone, which is the difference between a five-minute triage and a re-run on a borrowed
 * device.</p>
 *
 * <p>Every capture is individually guarded. Evidence gathering runs when something has already
 * gone wrong — often with a half-dead session — and one unavailable artefact must not cost you
 * the other three.</p>
 */
public final class AllureAttachmentListener {

    private static final Logger LOG = LogManager.getLogger(AllureAttachmentListener.class);

    /** Enough logcat to hold a stack trace, little enough that someone will actually read it. */
    private static final int LOGCAT_LINES = 300;

    private static final Path VIDEO_DIR = Paths.get("target", "videos");

    private AllureAttachmentListener() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /** Attaches everything worth having about the current failure. */
    public static void attachFailureEvidence(String testName) {
        if (!DriverManager.hasDriver()) {
            LOG.warn("No live session for '{}' - the failure happened before the driver "
                    + "was created, so there is nothing on the device to capture", testName);
            return;
        }
        attachScreenshot(testName);
        attachContext();
        attachPageSource();
        attachLogcat();
    }

    public static void attachScreenshot(String testName) {
        guarded("screenshot", () -> {
            byte[] image = ScreenshotUtils.capture();
            if (image.length > 0) {
                Allure.addAttachment(
                        "Screenshot - " + testName, "image/png",
                        new ByteArrayInputStream(image), ".png");
            }
            // Also written to disk: CI uploads the folder even when report generation fails.
            ScreenshotUtils.captureToFile(testName);
        });
    }

    /**
     * The page source as the driver saw it at the moment of failure.
     *
     * <p>Attached as XML so Allure renders it foldable. This is what turns "element not found"
     * into "the element was there, but under a different accessibility id".</p>
     */
    public static void attachPageSource() {
        guarded("page source", () ->
                Allure.addAttachment("Page source", "text/xml", AppUtils.pageSource(), ".xml"));
    }

    public static void attachLogcat() {
        guarded("device logs", () -> {
            String logs = AppUtils.logcatExcerpt(LOGCAT_LINES);
            if (!logs.isBlank()) {
                Allure.addAttachment(
                        "Device log (last %d lines)".formatted(LOGCAT_LINES), "text/plain", logs);
            }
        });
    }

    /** Where the app actually was - frequently the whole answer on its own. */
    public static void attachContext() {
        guarded("device context", () -> {
            String context = """
                    Platform         : %s
                    Execution target : %s
                    App profile      : %s
                    Current package  : %s
                    Current activity : %s
                    Thread           : %s
                    """.formatted(
                    ConfigReader.config().platform(),
                    ConfigReader.config().execution(),
                    ConfigReader.config().app(),
                    AppUtils.currentPackage(),
                    AppUtils.currentActivity(),
                    Thread.currentThread().getName());
            Allure.addAttachment("Device context", "text/plain", context);
        });
    }

    /**
     * Attaches a screen recording, if one was running, and writes it to disk.
     *
     * <p>Written to {@code target/videos} as well as into the report for the same reason as
     * screenshots: CI uploads the directory as a raw artefact, so the recording survives even
     * when report generation fails - which is exactly the run you most want to watch.</p>
     */
    public static void attachVideo(String testName, String base64Video) {
        if (base64Video == null || base64Video.isBlank()) {
            return;
        }
        guarded("screen recording", () -> {
            byte[] video = Base64.getDecoder().decode(base64Video);
            Allure.addAttachment("Recording - " + testName, "video/mp4",
                    new ByteArrayInputStream(video), ".mp4");
            writeVideoToDisk(testName, video);
        });
    }

    private static void writeVideoToDisk(String testName, byte[] video) {
        try {
            Files.createDirectories(VIDEO_DIR);
            Path target = VIDEO_DIR.resolve("%s_%s.mp4".formatted(
                    testName.replaceAll("[^A-Za-z0-9._-]", "_"),
                    System.currentTimeMillis()));
            Files.write(target, video);
            LOG.info("Screen recording saved to {}", target);
        } catch (IOException e) {
            LOG.warn("Could not write the screen recording to disk: {}", e.getMessage());
        }
    }

    /**
     * Marks the Allure test case currently in flight as flaky, with a reason.
     *
     * <p>Allure's flaky flag drives the report's flakiness view and the trend charts, so a
     * retried test is visible as unstable instead of blending in with the healthy ones.</p>
     *
     * <p>Only ever effective while a test case is open, which is why it is called from the
     * retry decision and from {@code afterInvocation} rather than from the final result
     * callbacks - by then Allure has written the result and an update is a silent no-op.</p>
     */
    public static void markFlaky(String reason) {
        try {
            Allure.getLifecycle().updateTestCase(testCase -> {
                StatusDetails details = testCase.getStatusDetails();
                if (details == null) {
                    details = new StatusDetails();
                    testCase.setStatusDetails(details);
                }
                details.setFlaky(true);
                String existing = testCase.getDescription() == null
                        ? "" : testCase.getDescription();
                testCase.setDescription(existing + System.lineSeparator() + "[RETRIED] " + reason);
            });
            Allure.label("retried", reason);
        } catch (RuntimeException e) {
            LOG.debug("Could not mark the Allure result as flaky: {}", e.getMessage());
        }
    }

    /** Free-text note, used by the retry reporting and the AI triage layer. */
    public static void attachNote(String title, String body) {
        guarded(title, () -> Allure.addAttachment(title, "text/plain",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), ".txt"));
    }

    private static void guarded(String what, Runnable capture) {
        try {
            capture.run();
        } catch (RuntimeException e) {
            LOG.warn("Could not attach {} to the report: {}", what, e.getMessage());
        }
    }
}

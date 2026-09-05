package com.adarsh.utils;

import com.adarsh.core.DriverManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Captures screenshots for the report and for the filesystem.
 *
 * <p>Every method here returns something usable even when capture fails. A screenshot is taken
 * because a test has <em>already</em> failed; throwing from the capture would replace the real
 * assertion failure with a screenshot error, which is exactly the information you did not want
 * to lose. So failures here are logged and swallowed, and callers get an empty array.</p>
 */
public final class ScreenshotUtils {

    private static final Logger LOG = LogManager.getLogger(ScreenshotUtils.class);

    private static final Path SCREENSHOT_DIR = Paths.get("target", "screenshots");

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);

    private ScreenshotUtils() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /** PNG bytes of the whole screen, or an empty array if the session is already gone. */
    public static byte[] capture() {
        try {
            return ((TakesScreenshot) DriverManager.getDriver()).getScreenshotAs(OutputType.BYTES);
        } catch (WebDriverException | IllegalStateException e) {
            LOG.warn("Could not capture a screenshot: {}", e.getMessage());
            return new byte[0];
        }
    }

    /** PNG bytes of a single element, for a focused "this is the wrong value" attachment. */
    public static byte[] capture(WebElement element) {
        try {
            return element.getScreenshotAs(OutputType.BYTES);
        } catch (WebDriverException e) {
            LOG.warn("Could not capture an element screenshot: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Also writes the screenshot to {@code target/screenshots}.
     *
     * <p>Allure is the primary destination, but CI jobs upload the directory as a raw artifact
     * too — when a report fails to generate, the images are still there.</p>
     *
     * @return the file written, or null if nothing was captured
     */
    public static Path captureToFile(String testName) {
        byte[] image = capture();
        if (image.length == 0) {
            return null;
        }
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            String fileName = "%s_%s.png".formatted(sanitise(testName), LocalDateTime.now().format(TIMESTAMP));
            Path target = SCREENSHOT_DIR.resolve(fileName);
            Files.write(target, image);
            LOG.info("Screenshot saved to {}", target);
            return target;
        } catch (IOException e) {
            LOG.warn("Could not write the screenshot to disk: {}", e.getMessage());
            return null;
        }
    }

    /** Keeps generated file names safe on every filesystem, including Windows. */
    private static String sanitise(String value) {
        String cleaned = (value == null ? "screenshot" : value)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }
}

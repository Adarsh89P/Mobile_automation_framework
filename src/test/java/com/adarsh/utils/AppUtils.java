package com.adarsh.utils;

import com.adarsh.core.DriverManager;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.remote.SupportsRotation;
import io.appium.java_client.screenrecording.CanRecordScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.WebDriverException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Device- and app-level operations that belong to no single screen.
 *
 * <p>Backgrounding, rotation, connectivity and deep links are properties of the <em>device</em>,
 * not of a page, so they live here rather than being bolted onto an arbitrary page class.</p>
 *
 * <p>Where Appium 2 offers both a client method and a {@code mobile:} script, this prefers the
 * script: the endpoints are implemented by the UiAutomator2 driver itself and are the ones
 * still being maintained, while several client-side equivalents are deprecated shims.</p>
 */
public final class AppUtils {

    private static final Logger LOG = LogManager.getLogger(AppUtils.class);

    private AppUtils() {
        throw new AssertionError("Utility class - not instantiable");
    }

    private static AppiumDriver driver() {
        return DriverManager.getDriver();
    }

    /**
     * Narrows the driver to one of Appium's capability interfaces.
     *
     * <p>{@link AppiumDriver} itself declares almost nothing: app lifecycle lives on
     * {@link InteractsWithApps} and rotation on {@link SupportsRotation}, both implemented by
     * the platform drivers. Casting through this helper means an unsupported operation fails
     * with a sentence explaining which capability is missing, rather than a bare
     * {@code ClassCastException} from the middle of a test.</p>
     */
    private static <T> T capability(Class<T> capability, String operation) {
        AppiumDriver driver = driver();
        if (capability.isInstance(driver)) {
            return capability.cast(driver);
        }
        throw new UnsupportedOperationException(
                "%s is not supported: the current session (%s) does not implement %s"
                        .formatted(operation, driver.getClass().getSimpleName(),
                                capability.getSimpleName()));
    }

    private static InteractsWithApps apps(String operation) {
        return capability(InteractsWithApps.class, operation);
    }

    private static SupportsRotation rotation(String operation) {
        return capability(SupportsRotation.class, operation);
    }

    // ------------------------------------------------------------------ app lifecycle

    /**
     * Sends the app to the background for {@code duration}, then brings it back.
     *
     * <p>This is not a sleep in disguise: the app is genuinely backgrounded, so the OS may pause,
     * trim or kill it. That is exactly the behaviour under test — whether state survives it.</p>
     */
    public static void runInBackground(Duration duration) {
        LOG.info("Backgrounding the app for {}s", duration.toSeconds());
        apps("backgrounding the app").runAppInBackground(duration);
        LOG.info("App restored to the foreground");
    }

    public static void activateApp(String appId) {
        apps("activating an app").activateApp(appId);
    }

    public static void terminateApp(String appId) {
        apps("terminating an app").terminateApp(appId);
    }

    public static boolean isAppInstalled(String appId) {
        return apps("checking whether an app is installed").isAppInstalled(appId);
    }

    /** Android activity currently in the foreground; a marker string on iOS. */
    public static String currentActivity() {
        try {
            return driver() instanceof AndroidDriver android ? android.currentActivity() : "n/a (iOS)";
        } catch (WebDriverException e) {
            return "unknown";
        }
    }

    public static String currentPackage() {
        try {
            return driver() instanceof AndroidDriver android ? android.getCurrentPackage() : "n/a (iOS)";
        } catch (WebDriverException e) {
            return "unknown";
        }
    }

    // ------------------------------------------------------------------ orientation

    public static ScreenOrientation orientation() {
        return rotation("reading the orientation").getOrientation();
    }

    /**
     * Rotates the device and waits until the driver reports the new orientation.
     *
     * <p>Rotation is asynchronous — the call returns before the relayout finishes. Waiting for
     * the reported orientation to change is what makes the next assertion meaningful instead of
     * a race, and is why no test needs a sleep after rotating.</p>
     */
    public static void rotate(ScreenOrientation target) {
        if (orientation() == target) {
            return;
        }
        LOG.info("Rotating the device to {}", target);
        rotation("rotating the device").rotate(target);
        WaitUtils.waitUntil(() -> orientation() == target,
                "the device to report " + target + " orientation");
    }

    public static void rotateToLandscape() {
        rotate(ScreenOrientation.LANDSCAPE);
    }

    public static void rotateToPortrait() {
        rotate(ScreenOrientation.PORTRAIT);
    }

    // ------------------------------------------------------------------ connectivity

    /**
     * Turns the radios on or off, i.e. airplane mode.
     *
     * <p>Uses {@code mobile: setConnectivity}, which needs the {@code adb_shell} insecure
     * feature the Appium server is started with. On an emulator running Android 11+ this is the
     * only reliable way to change connectivity; the older {@code setConnection} capability is a
     * no-op on many images.</p>
     */
    public static void setAirplaneMode(boolean enabled) {
        LOG.info("Setting airplane mode to {}", enabled);
        driver().executeScript("mobile: setConnectivity", Map.of(
                "wifi", !enabled,
                "data", !enabled,
                "airplaneMode", enabled));
        WaitUtils.waitUntil(() -> isAirplaneModeOn() == enabled,
                "airplane mode to become " + enabled);
    }

    public static boolean isAirplaneModeOn() {
        Object result = driver().executeScript("mobile: getConnectivity");
        if (result instanceof Map<?, ?> state) {
            return Boolean.TRUE.equals(state.get("airplaneMode"));
        }
        return false;
    }

    /** Restores connectivity. Safe to call even if it was never disabled. */
    public static void restoreConnectivity() {
        try {
            setAirplaneMode(false);
        } catch (WebDriverException e) {
            // Cleanup must never mask the assertion failure that brought us here.
            LOG.warn("Could not restore connectivity: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ deep links

    /**
     * Opens a deep link.
     *
     * <p>{@code driver.get(url)} is the documented W3C route and is what this uses. The
     * {@code mobile: deepLink} script is the alternative when the link must be delivered to a
     * specific package rather than resolved by the system chooser.</p>
     */
    public static void openDeepLink(String url) {
        LOG.info("Opening deep link {}", url);
        driver().get(url);
    }

    public static void openDeepLink(String url, String packageName) {
        LOG.info("Opening deep link {} in {}", url, packageName);
        driver().executeScript("mobile: deepLink", Map.of(
                "url", url,
                "package", packageName));
    }

    // ------------------------------------------------------------------ native dialogs

    /**
     * Accepts a native alert if one is showing.
     *
     * @return true if an alert was present and accepted
     */
    public static boolean acceptAlertIfPresent() {
        try {
            String text = driver().switchTo().alert().getText();
            driver().switchTo().alert().accept();
            LOG.info("Accepted the native alert: '{}'", text);
            return true;
        } catch (WebDriverException e) {
            return false;
        }
    }

    public static boolean dismissAlertIfPresent() {
        try {
            driver().switchTo().alert().dismiss();
            LOG.info("Dismissed the native alert");
            return true;
        } catch (WebDriverException e) {
            return false;
        }
    }

    /** Alert text, or an empty string when no alert is showing. */
    public static String alertText() {
        try {
            return driver().switchTo().alert().getText();
        } catch (WebDriverException e) {
            return "";
        }
    }

    public static boolean isAlertPresent() {
        return !alertText().isEmpty();
    }

    // ------------------------------------------------------------------ screen recording

    /**
     * Starts recording the screen.
     *
     * <p>Recording is opt-in ({@code record.video.on.failure}) because it is not free: the
     * device encodes video for the whole test, and on a shared CI machine that shows up as
     * slower, flakier runs. It earns its cost only when you are chasing a failure you cannot
     * reproduce from a screenshot.</p>
     *
     * @return true if recording actually started
     */
    public static boolean startScreenRecording() {
        try {
            capability(CanRecordScreen.class, "screen recording").startRecordingScreen();
            LOG.debug("Started screen recording");
            return true;
        } catch (WebDriverException | UnsupportedOperationException e) {
            LOG.warn("Could not start screen recording: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Stops recording and returns the video as base64.
     *
     * <p>Never throws. This runs during teardown of an already-failing test, and losing the
     * real failure behind a recording error would be a poor trade.</p>
     *
     * @return base64 mp4, or an empty string if nothing was recorded
     */
    public static String stopScreenRecording() {
        try {
            return capability(CanRecordScreen.class, "screen recording").stopRecordingScreen();
        } catch (WebDriverException | UnsupportedOperationException e) {
            LOG.warn("Could not stop screen recording: {}", e.getMessage());
            return "";
        }
    }

    // ------------------------------------------------------------------ diagnostics

    /**
     * The tail of logcat, for attaching to a failed test.
     *
     * <p>Bounded on purpose. A full logcat buffer is megabytes of noise that nobody opens; the
     * last few hundred lines are where the crash actually is.</p>
     */
    public static String logcatExcerpt(int maxLines) {
        try {
            List<String> lines = driver().manage().logs().get("logcat").getAll().stream()
                    .map(entry -> entry.getLevel() + " " + entry.getMessage())
                    .toList();
            int from = Math.max(0, lines.size() - maxLines);
            return String.join(System.lineSeparator(), lines.subList(from, lines.size()));
        } catch (WebDriverException e) {
            return "Device logs unavailable: " + e.getMessage();
        }
    }

    /** Full page source, used by the failure listener and the AI triage layer. */
    public static String pageSource() {
        try {
            return driver().getPageSource();
        } catch (WebDriverException e) {
            return "Page source unavailable: " + e.getMessage();
        }
    }
}

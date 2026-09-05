package com.adarsh.core;

import com.adarsh.config.AppProfile;
import com.adarsh.config.ConfigReader;
import com.adarsh.config.FrameworkConfig;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;
import io.appium.java_client.remote.options.BaseOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the {@link AppiumDriver} for the current thread and binds it to {@link DriverManager}.
 *
 * <p>Capabilities are assembled through the typed {@code UiAutomator2Options} /
 * {@code XCUITestOptions} builders rather than the removed {@code DesiredCapabilities} —
 * the typed builders apply the {@code appium:} vendor prefix required by W3C and catch
 * misspelled capability names at compile time instead of at session-creation time.</p>
 *
 * <p>Everything that varies per parallel session arrives in the {@link DeviceConfig} argument;
 * everything else comes from {@link FrameworkConfig}. There is no static driver field here —
 * the created session is handed straight to the thread-local in {@link DriverManager}.</p>
 */
public final class DriverFactory {

    private static final Logger LOG = LogManager.getLogger(DriverFactory.class);

    /** Pre-uploaded cloud app identifiers and remote URLs are passed through untouched. */
    private static final String[] REMOTE_APP_PREFIXES = {"bs://", "lt://", "http://", "https://"};

    private DriverFactory() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /** Creates a session for the configured device and the default app profile. */
    public static AppiumDriver createDriver() {
        return createDriver(DeviceConfig.fromConfig(), AppProfile.fromConfig());
    }

    /** Creates a session for the configured device and a named app profile. */
    public static AppiumDriver createDriver(AppProfile app) {
        return createDriver(DeviceConfig.fromConfig(), app);
    }

    /**
     * Creates a session for {@code device} running {@code app}, binds it to the calling thread
     * and returns it.
     *
     * @throws IllegalStateException if the Appium server or the app binary cannot be reached
     */
    public static AppiumDriver createDriver(DeviceConfig device, AppProfile app) {
        FrameworkConfig config = ConfigReader.config();
        PlatformType platform = ConfigReader.platform();
        ExecutionTarget target = ConfigReader.executionTarget();

        URL endpoint = resolveEndpoint(config, target);
        LOG.info("Creating a {} session for {} running '{}' against {} [{}]",
                platform.key(), device.label(), app.displayName(), endpoint, target.key());

        BaseOptions<?> options = switch (platform) {
            case ANDROID -> androidOptions(config, device, app, target);
            case IOS -> iosOptions(config, device, app, target);
        };
        if (target == ExecutionTarget.CLOUD) {
            applyCloudOptions(options, config);
        }

        AppiumDriver driver = switch (platform) {
            case ANDROID -> new AndroidDriver(endpoint, options);
            case IOS -> new IOSDriver(endpoint, options);
        };

        // Explicit waits only. A non-zero implicit wait silently changes the meaning of every
        // explicit wait it overlaps with and makes negative assertions crawl.
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);

        DriverManager.setDriver(driver);
        LOG.info("Session {} created on {}", driver.getSessionId(), device.label());
        return driver;
    }

    // ------------------------------------------------------------------ endpoint

    private static URL resolveEndpoint(FrameworkConfig config, ExecutionTarget target) {
        return switch (target) {
            case LOCAL -> AppiumServerManager.startIfNeeded();
            case GRID -> toUrl(config.gridUrl());
            case CLOUD -> cloudHubUrl(config);
        };
    }

    /**
     * Builds the vendor hub URL with credentials taken from the environment only.
     *
     * <p>The variable names below are the documented defaults for each vendor.</p>
     */
    private static URL cloudHubUrl(FrameworkConfig config) {
        CloudProvider provider = CloudProvider.from(config.cloudProvider());
        // TODO: confirm these variable names against your own cloud account / CI secrets.
        String userVar = provider == CloudProvider.BROWSERSTACK
                ? "BROWSERSTACK_USERNAME" : "LT_USERNAME";
        String keyVar = provider == CloudProvider.BROWSERSTACK
                ? "BROWSERSTACK_ACCESS_KEY" : "LT_ACCESS_KEY";

        String user = requiredEnv(userVar);
        String key = requiredEnv(keyVar);
        return toUrl("https://" + user + ":" + key + "@" + provider.hubHost());
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable " + name + " is not set. Cloud credentials are read "
                            + "from the environment only and are never committed.");
        }
        return value;
    }

    // ------------------------------------------------------------------ android caps

    private static UiAutomator2Options androidOptions(
            FrameworkConfig config, DeviceConfig device, AppProfile app, ExecutionTarget target) {

        // The automationName capability is implied by the options class itself.
        UiAutomator2Options options = new UiAutomator2Options()
                .setDeviceName(device.deviceName())
                .setAutoGrantPermissions(config.autoGrantPermissions())
                .setNoReset(config.noReset())
                .setFullReset(config.fullReset())
                .setNewCommandTimeout(Duration.ofSeconds(config.newCommandTimeoutSeconds()))
                .setAdbExecTimeout(Duration.ofMillis(config.adbExecTimeoutMs()))
                .setUiautomator2ServerInstallTimeout(
                        Duration.ofMillis(config.uiautomator2ServerInstallTimeoutMs()))
                .setUiautomator2ServerLaunchTimeout(
                        Duration.ofMillis(config.uiautomator2ServerLaunchTimeoutMs()))
                // Without a unique systemPort, two parallel Android sessions fight over the
                // same UiAutomator2 server socket and both fail intermittently.
                .setSystemPort(device.systemPort())
                // Emulator animations are a real source of stale-element flakiness.
                .setDisableWindowAnimation(true);

        // Left unset when blank so the launch component comes from the APK manifest — the
        // capability is only worth sending when the app has several entry points.
        if (app.hasAppPackage()) {
            options.setAppPackage(app.appPackage());
        }
        if (app.hasAppActivity()) {
            options.setAppActivity(app.appActivity());
            options.setAppWaitActivity(app.waitActivity());
        }
        if (device.hasPlatformVersion()) {
            options.setPlatformVersion(device.platformVersion());
        }
        if (device.hasUdid()) {
            options.setUdid(device.udid());
        }
        options.setApp(resolveApp(app.path(), target));
        return options;
    }

    // ------------------------------------------------------------------ ios caps

    /**
     * iOS capabilities are wired but never exercised in this repo — there is no macOS runner.
     * They exist so the driver layer is genuinely platform-agnostic rather than Android-shaped.
     */
    private static XCUITestOptions iosOptions(
            FrameworkConfig config, DeviceConfig device, AppProfile app, ExecutionTarget target) {

        XCUITestOptions options = new XCUITestOptions()
                .setDeviceName(device.deviceName())
                .setNoReset(config.noReset())
                .setFullReset(config.fullReset())
                .setAutoAcceptAlerts(config.autoAcceptAlerts())
                .setNewCommandTimeout(Duration.ofSeconds(config.newCommandTimeoutSeconds()))
                .setWdaLaunchTimeout(Duration.ofSeconds(config.wdaLaunchTimeoutSeconds()))
                // Same reasoning as systemPort on Android: one WDA port per parallel session.
                .setWdaLocalPort(device.wdaLocalPort())
                // Reusing an installed WDA keeps re-runs fast; CI cold-builds it once.
                .setUseNewWDA(false);

        if (device.hasPlatformVersion()) {
            options.setPlatformVersion(device.platformVersion());
        }
        if (device.hasUdid()) {
            options.setUdid(device.udid());
        }
        if (app.hasBundleId()) {
            options.setBundleId(app.bundleId());
        }
        options.setApp(resolveApp(app.path(), target));
        return options;
    }

    // ------------------------------------------------------------------ cloud caps

    private static void applyCloudOptions(BaseOptions<?> options, FrameworkConfig config) {
        CloudProvider provider = CloudProvider.from(config.cloudProvider());
        Map<String, Object> vendor = new LinkedHashMap<>();
        vendor.put("projectName", config.cloudProjectName());
        vendor.put("buildName", config.cloudBuildName());
        vendor.put("sessionName", Thread.currentThread().getName());
        vendor.put("debug", true);
        vendor.put("networkLogs", true);
        if (config.cloudTunnelEnabled()) {
            // TODO: set the tunnel/local identifier your CI job starts the tunnel binary with.
            vendor.put("local", true);
        }

        switch (provider) {
            case BROWSERSTACK -> options.setCapability("bstack:options", vendor);
            case LAMBDATEST -> {
                vendor.put("w3c", true);
                vendor.put("isRealMobile", true);
                options.setCapability("lt:options", vendor);
            }
        }
        LOG.info("Applied {} cloud capabilities (build: {})",
                provider.key(), config.cloudBuildName());
    }

    // ------------------------------------------------------------------ app resolution

    /**
     * Turns {@code app.path} into something the server can actually install.
     *
     * <p>Cloud ids and URLs pass straight through. Everything else must resolve to a file that
     * exists — failing here with the attempted path is far more useful than an opaque
     * "unable to find app" from the server thirty seconds later.</p>
     */
    private static String resolveApp(String appPath, ExecutionTarget target) {
        if (appPath == null || appPath.isBlank()) {
            throw new IllegalStateException("app.path is not configured");
        }
        String trimmed = appPath.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String prefix : REMOTE_APP_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return trimmed;
            }
        }
        if (target == ExecutionTarget.CLOUD) {
            throw new IllegalStateException(
                    "Cloud runs need an uploaded app id (bs://... or lt://...), but app.path is '"
                            + trimmed + "'. Upload the binary to the vendor and pass the id with "
                            + "-Dapp.path=bs://<hash>.");
        }

        URL resource = Thread.currentThread().getContextClassLoader().getResource(trimmed);
        if (resource != null) {
            try {
                return Paths.get(resource.toURI()).toAbsolutePath().toString();
            } catch (URISyntaxException e) {
                throw new UncheckedIOException(new IOException("Bad app resource URI: " + resource, e));
            }
        }

        Path asFile = Paths.get(trimmed).toAbsolutePath();
        if (Files.exists(asFile)) {
            return asFile.toString();
        }

        throw new IllegalStateException(
                "Could not find the app under test. Tried classpath resource '" + trimmed
                        + "' and file '" + asFile + "'. Run scripts/fetch-apps.sh to download "
                        + "the binaries that are too large to commit, or pass an absolute "
                        + "-Dapp.path.");
    }

    private static URL toUrl(String value) {
        try {
            return URI.create(value).toURL();
        } catch (IOException | IllegalArgumentException e) {
            throw new UncheckedIOException(new IOException("Invalid server URL: " + value, e));
        }
    }

    /** Kept package-visible for tests that assert on path resolution without a device. */
    static String resolveAppForTesting(String appPath) {
        return resolveApp(appPath, ExecutionTarget.LOCAL);
    }
}

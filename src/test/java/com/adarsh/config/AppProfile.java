package com.adarsh.config;

import com.adarsh.core.PlatformType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Identity of one application under test, for one platform.
 *
 * <p>This framework drives two apps — ApiDemos for the device-capability suite and Sauce Labs
 * My Demo App for the business flows — and a suite may switch between them per {@code <test>}
 * block. That rules out putting the app in {@link FrameworkConfig}, which Owner resolves once
 * per JVM. So each app lives in its own {@code config/apps/<name>.properties} file and is
 * loaded on demand into this record.</p>
 *
 * <p>Profiles are cached per {@code name + platform}: the file never changes mid-run, and the
 * cache keeps parallel threads from re-reading it for every session.</p>
 *
 * @param name         profile id, i.e. the properties file name ({@code apidemos}, {@code mydemo})
 * @param displayName  human label used in logs and Allure
 * @param path         classpath-relative binary, absolute path, or {@code bs://}/{@code lt://} id
 * @param appPackage   Android package; blank means "let the manifest decide"
 * @param appActivity  Android launch activity; blank means "let the manifest decide"
 * @param waitActivity Android {@code appWaitActivity}; only sent alongside a non-blank activity
 * @param bundleId     iOS bundle identifier
 */
public record AppProfile(
        String name,
        String displayName,
        String path,
        String appPackage,
        String appActivity,
        String waitActivity,
        String bundleId) {

    private static final Logger LOG = LogManager.getLogger(AppProfile.class);

    private static final String RESOURCE_PATTERN = "config/apps/%s.properties";

    private static final Map<String, AppProfile> CACHE = new ConcurrentHashMap<>();

    public AppProfile {
        name = blankIfNull(name);
        displayName = blankIfNull(displayName);
        path = blankIfNull(path);
        appPackage = blankIfNull(appPackage);
        appActivity = blankIfNull(appActivity);
        waitActivity = blankIfNull(waitActivity);
        bundleId = blankIfNull(bundleId);
    }

    /** The profile named by {@code -Dapp=...}, for the platform named by {@code -Dplatform=...}. */
    public static AppProfile fromConfig() {
        return load(ConfigReader.config().app(), ConfigReader.platform());
    }

    /** The named profile for the current platform. Used by suites that switch apps per test. */
    public static AppProfile named(String name) {
        return load(name, ConfigReader.platform());
    }

    public static AppProfile load(String name, PlatformType platform) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("App profile name must not be blank");
        }
        String key = name.trim().toLowerCase(Locale.ROOT) + "@" + platform.key();
        return CACHE.computeIfAbsent(key, ignored -> read(name.trim(), platform));
    }

    private static AppProfile read(String name, PlatformType platform) {
        String resource = RESOURCE_PATTERN.formatted(name);
        Properties properties = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException(
                        "No app profile '" + name + "' - expected src/test/resources/" + resource
                                + ". Available profiles are the files in config/apps/.");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read app profile " + resource, e);
        }

        String prefix = platform.key() + ".";
        AppProfile profile = new AppProfile(
                name,
                value(properties, "app.display.name", name),
                // A -D flag beats the file, so a one-off build can point at a fresh binary
                // without editing (and accidentally committing) a properties file.
                override("app.path", value(properties, prefix + "app.path", "")),
                override("app.package", value(properties, prefix + "app.package", "")),
                override("app.activity", value(properties, prefix + "app.activity", "")),
                override("app.wait.activity", value(properties, prefix + "app.wait.activity", "*")),
                override("app.bundle.id", value(properties, prefix + "app.bundle.id", "")));

        if (profile.path().isBlank()) {
            throw new IllegalStateException(
                    "App profile '" + name + "' has no " + prefix + "app.path - it has no "
                            + platform.key() + " build. Pick a different profile or supply "
                            + "-Dapp.path=<binary>.");
        }
        LOG.debug("Loaded app profile {} for {}: {}", name, platform.key(), profile.path());
        return profile;
    }

    private static String override(String systemProperty, String fromFile) {
        String supplied = System.getProperty(systemProperty);
        return supplied == null || supplied.isBlank() ? fromFile : supplied.trim();
    }

    private static String value(Properties properties, String key, String fallback) {
        String found = properties.getProperty(key);
        return found == null || found.isBlank() ? fallback : found.trim();
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean hasAppPackage() {
        return !appPackage.isBlank();
    }

    public boolean hasAppActivity() {
        return !appActivity.isBlank();
    }

    public boolean hasBundleId() {
        return !bundleId.isBlank();
    }
}

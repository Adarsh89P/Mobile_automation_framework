package com.adarsh.core;

import com.adarsh.config.AppProfile;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertTrue;

/**
 * Device-free tests for the resolution layer: app paths, device parameters and the enums that
 * turn a {@code -D} flag into a typed decision.
 *
 * <p><b>Why this class exists.</b> Every other test in this repo needs an emulator, so the
 * fastest possible feedback on a mistake in the configuration layer was previously a fifteen
 * second session creation - and on CI, a four minute emulator boot. The logic tested here
 * decides <em>which binary is installed</em> and <em>which device is driven</em>; getting it
 * wrong wastes an entire pipeline run to produce an error message. None of it needs a device,
 * so none of it should wait for one.</p>
 *
 * <p><b>What it is not.</b> These are unit tests over pure functions, not a substitute for the
 * suites that drive a real app. They assert on decisions the framework makes before the first
 * W3C command is sent, and nothing beyond that.</p>
 *
 * <p>The class lives in {@code com.adarsh.core} rather than {@code com.adarsh.tests} because
 * {@link DriverFactory#resolveAppForTesting(String)} is deliberately package-private: path
 * resolution is an implementation detail that deserves a test, not a public API.</p>
 *
 * <p>It does not extend {@code BaseTest} - there is no {@code @BeforeMethod} creating a session,
 * so these run on any machine, including a CI job with no Android SDK installed at all.</p>
 *
 * <p>TODO: {@code RetryTransformer} attaches {@code RetryAnalyzer} to every test method in the
 *       suite, including these. Retrying a deterministic unit test can only ever produce the
 *       same result twice, so the transformer should skip the {@code unit} group. That is a
 *       separate change - this comment marks the coupling rather than hiding it.</p>
 */
public class ConfigResolutionTest {

    /** The APK that is committed to the repo, so this assertion holds on a fresh clone. */
    private static final String COMMITTED_APK = "apps/ApiDemos-debug.apk";

    // ------------------------------------------------------------------ app resolution

    /**
     * The committed APK resolves through the classpath to a file that exists.
     *
     * <p>Absolute, because Appium installs the binary on behalf of a server process whose working
     * directory is not this one - a relative path that happens to work locally is a path that
     * fails the moment the server is started from somewhere else.</p>
     */
    @Test(groups = {"unit"},
            description = "A committed app binary resolves from the classpath to an absolute file")
    public void committedApkResolvesToAnExistingAbsoluteFile() {
        skipIfAppPathOverridden();

        String resolved = DriverFactory.resolveAppForTesting(COMMITTED_APK);
        Path path = Paths.get(resolved);

        assertTrue(path.isAbsolute(), "Resolved app path must be absolute, was: " + resolved);
        assertTrue(Files.exists(path), "Resolved app path does not exist: " + resolved);
        assertEquals(path.getFileName().toString(), "ApiDemos-debug.apk");
    }

    /**
     * Cloud identifiers and URLs are passed through untouched.
     *
     * <p>A {@code bs://} id names a binary already uploaded to the vendor. Treating it as a local
     * path would send the run looking for a file that is not supposed to exist.</p>
     */
    @Test(groups = {"unit"},
            description = "Pre-uploaded cloud ids and remote URLs are not treated as local paths")
    public void remoteAppIdentifiersArePassedThrough() {
        skipIfAppPathOverridden();

        assertEquals(DriverFactory.resolveAppForTesting("bs://0123456789abcdef"),
                "bs://0123456789abcdef");
        assertEquals(DriverFactory.resolveAppForTesting("lt://APP123"), "lt://APP123");
        assertEquals(DriverFactory.resolveAppForTesting("https://example.test/build.apk"),
                "https://example.test/build.apk");
    }

    /** Surrounding whitespace comes from properties files and suite XML, and must not survive. */
    @Test(groups = {"unit"}, description = "A padded app identifier is trimmed, not rejected")
    public void remoteAppIdentifierIsTrimmed() {
        skipIfAppPathOverridden();

        assertEquals(DriverFactory.resolveAppForTesting("  bs://padded  "), "bs://padded");
    }

    /**
     * A missing binary fails here, with the fix in the message.
     *
     * <p>This is the most common first-run failure in the repo - the 32 MB app is not committed -
     * so the assertion is on the remedy being named, not merely on the throw. An exception that
     * says what to run is worth more than one that says only what went wrong.</p>
     */
    @Test(groups = {"unit"},
            description = "A missing binary fails with the fetch command in the message")
    public void missingAppFailsWithAnActionableMessage() {
        skipIfAppPathOverridden();

        IllegalStateException error = expectThrows(IllegalStateException.class,
                () -> DriverFactory.resolveAppForTesting("apps/no-such-build.apk"));

        assertTrue(error.getMessage().contains("scripts/fetch-apps.sh"),
                "The message should name the script that fixes this, was: " + error.getMessage());
        assertTrue(error.getMessage().contains("apps/no-such-build.apk"),
                "The message should quote the path that was tried, was: " + error.getMessage());
    }

    @Test(groups = {"unit"}, description = "A blank app path is rejected before any session work")
    public void blankAppPathIsRejected() {
        skipIfAppPathOverridden();

        assertThrows(IllegalStateException.class, () -> DriverFactory.resolveAppForTesting("   "));
    }

    // ------------------------------------------------------------------ app profiles

    /** The profile a fresh clone runs on, read from {@code config/apps/apidemos.properties}. */
    @Test(groups = {"unit"},
            description = "The ApiDemos profile carries its Android launch identity")
    public void apiDemosProfileDescribesTheCommittedApk() {
        skipIfAppPathOverridden();

        AppProfile profile = AppProfile.load("apidemos", PlatformType.ANDROID);

        assertEquals(profile.path(), COMMITTED_APK);
        assertTrue(profile.hasAppPackage());
        assertEquals(profile.appPackage(), "io.appium.android.apis");
        assertTrue(profile.hasAppActivity());
    }

    /**
     * ApiDemos has no iOS build, and asking for one fails immediately rather than at the device.
     *
     * <p>This pins a real limitation rather than papering over it: the device-capability suite is
     * Android-only by construction, and the failure says so in a sentence instead of surfacing
     * thirty seconds later as an opaque install error.</p>
     */
    @Test(groups = {"unit"},
            description = "Requesting an iOS build of an Android-only profile fails with the reason")
    public void androidOnlyProfileHasNoIosBuild() {
        skipIfAppPathOverridden();

        IllegalStateException error = expectThrows(IllegalStateException.class,
                () -> AppProfile.load("apidemos", PlatformType.IOS));

        assertTrue(error.getMessage().contains("ios.app.path"),
                "The message should name the missing key, was: " + error.getMessage());
    }

    @Test(groups = {"unit"}, description = "An unknown profile name names the directory to look in")
    public void unknownProfileIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AppProfile.load("not-a-real-app", PlatformType.ANDROID));
    }

    // ------------------------------------------------------------------ device parameters

    /**
     * Suite XML parameters arrive as raw strings, and padded values are routine in hand-edited
     * XML. A device name with a trailing space produces a capability that matches no device.
     */
    @Test(groups = {"unit"}, description = "Device parameters are trimmed and null-safe")
    public void deviceConfigNormalisesItsInputs() {
        DeviceConfig device = new DeviceConfig("  Pixel 7 API 34  ", null, "   ", 8201, 8101);

        assertEquals(device.deviceName(), "Pixel 7 API 34");
        assertEquals(device.platformVersion(), "");
        assertEquals(device.udid(), "");
        assertFalse(device.hasUdid(), "A blank udid must not be sent as a capability");
        assertFalse(device.hasPlatformVersion());
        assertEquals(device.label(), "Pixel 7 API 34");
    }

    /** With several devices attached, the udid is the only thing that identifies the session. */
    @Test(groups = {"unit"}, description = "A udid is carried into the log label")
    public void deviceLabelIncludesTheUdidWhenPresent() {
        DeviceConfig device = new DeviceConfig("Pixel 7", "14.0", "emulator-5554", 8201, 8101);

        assertTrue(device.hasUdid());
        assertTrue(device.hasPlatformVersion());
        assertEquals(device.label(), "Pixel 7 (emulator-5554)");
    }

    /** The ports are what make {@code parallel="tests"} safe; they must survive unchanged. */
    @Test(groups = {"unit"}, description = "Per-session driver ports are preserved verbatim")
    public void driverPortsArePreserved() {
        DeviceConfig device = new DeviceConfig("Pixel 7", "14.0", "emulator-5554", 8202, 8102);

        assertEquals(device.systemPort(), 8202);
        assertEquals(device.wdaLocalPort(), 8102);
    }

    // ------------------------------------------------------------------ typed flags

    /**
     * {@code -D} flags are typed by hand on a command line, so case and stray whitespace are
     * expected input, not misuse. An unknown value fails here rather than as a capability error.
     */
    @Test(groups = {"unit"},
            description = "Platform, execution target and provider parse leniently")
    public void typedFlagsAcceptRealWorldInput() {
        assertEquals(PlatformType.from("  Android "), PlatformType.ANDROID);
        assertEquals(PlatformType.from("IOS"), PlatformType.IOS);
        assertEquals(ExecutionTarget.from("CLOUD"), ExecutionTarget.CLOUD);
        assertEquals(CloudProvider.from(" LambdaTest "), CloudProvider.LAMBDATEST);
    }

    @Test(groups = {"unit"}, description = "An unknown flag value lists what was expected")
    public void unknownFlagValuesListTheValidOnes() {
        IllegalArgumentException platform = expectThrows(IllegalArgumentException.class,
                () -> PlatformType.from("windows-phone"));
        assertTrue(platform.getMessage().contains("android"),
                "The message should list the valid platforms, was: " + platform.getMessage());

        assertThrows(IllegalArgumentException.class, () -> ExecutionTarget.from("remote"));
        assertThrows(IllegalArgumentException.class, () -> CloudProvider.from("saucelabs"));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * {@code -Dapp.path} deliberately overrides the value in every app profile, which is exactly
     * what these assertions read. Skipping is honest about that: a passing assertion that only
     * held because nobody used the override would be worse than no assertion at all.
     */
    private static void skipIfAppPathOverridden() {
        String override = System.getProperty("app.path");
        if (override != null && !override.isBlank()) {
            throw new SkipException(
                    "-Dapp.path=" + override + " overrides the profile values under test");
        }
    }
}

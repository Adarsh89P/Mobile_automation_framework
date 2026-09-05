package com.adarsh.tests;

import com.adarsh.config.AppProfile;
import com.adarsh.config.ConfigReader;
import com.adarsh.core.AppiumServerManager;
import com.adarsh.core.DeviceConfig;
import com.adarsh.core.DriverFactory;
import com.adarsh.core.DriverManager;
import com.adarsh.models.CheckoutProfile;
import com.adarsh.models.LoginScenario;
import com.adarsh.models.Product;
import com.adarsh.models.User;
import com.adarsh.pages.LoginPage;
import com.adarsh.pages.ProductListPage;
import com.adarsh.utils.FakerUtils;
import com.adarsh.utils.JsonDataReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import java.lang.reflect.Method;

/**
 * Driver lifecycle and shared fixtures for every test.
 *
 * <p><b>One session per test method.</b> Not per class, and not per suite. A shared session
 * makes tests order-dependent — the second test starts wherever the first one left the app, and
 * a failure in one silently poisons the rest. A fresh session costs perhaps fifteen seconds and
 * buys tests that can be run individually, in any order, and in parallel.</p>
 *
 * <p><b>Device parameters come from the suite file.</b> Each {@code <test>} block passes its own
 * {@code deviceName}, {@code udid}, {@code systemPort} and {@code wdaLocalPort}, which is what
 * makes {@code parallel="tests"} safe: two sessions never share a UiAutomator2 port. They are
 * all {@code @Optional}, so a single-device run needs no parameters at all.</p>
 */
public abstract class BaseTest {

    protected static final Logger LOG = LogManager.getLogger(BaseTest.class);

    /**
     * Which app this test class drives.
     *
     * <p>Overridden by the shop tests and the device tests; the suite file can override it
     * again per {@code <test>} block. Defaults to whatever {@code -Dapp} says.</p>
     */
    protected String appProfileName() {
        return ConfigReader.config().app();
    }

    // ------------------------------------------------------------------ lifecycle

    @Parameters({"deviceName", "platformVersion", "udid", "systemPort", "wdaLocalPort", "app"})
    @BeforeMethod(alwaysRun = true)
    public void setUp(
            @Optional String deviceName,
            @Optional String platformVersion,
            @Optional String udid,
            @Optional String systemPort,
            @Optional String wdaLocalPort,
            @Optional String app,
            Method method) {

        DeviceConfig fallback = DeviceConfig.fromConfig();
        DeviceConfig device = new DeviceConfig(
                orDefault(deviceName, fallback.deviceName()),
                orDefault(platformVersion, fallback.platformVersion()),
                orDefault(udid, fallback.udid()),
                orDefault(systemPort, fallback.systemPort()),
                orDefault(wdaLocalPort, fallback.wdaLocalPort()));

        // Stamps every log line from this thread with the test and device, so a parallel run's
        // interleaved output can still be read one device at a time.
        ThreadContext.put("testName", method.getName());
        ThreadContext.put("device", device.deviceName());

        AppProfile profile = AppProfile.named(orDefault(app, appProfileName()));
        LOG.info("=== Starting {} on {} [{}] ===",
                method.getName(), device.label(), profile.displayName());

        DriverFactory.createDriver(device, profile);
    }

    /**
     * Quits the session after every test, pass or fail.
     *
     * <p>{@code alwaysRun = true} matters: without it a failure in a configuration method skips
     * teardown, and the orphaned session holds its device port until the Appium server times it
     * out — which then breaks the <em>next</em> run rather than this one.</p>
     */
    @AfterMethod(alwaysRun = true)
    public void tearDown(Method method) {
        LOG.info("=== Finished {} ===", method.getName());
        DriverManager.quitDriver();
        FakerUtils.reset();
        ThreadContext.clearAll();
    }

    /** Stops the server only if this JVM started it; an adopted server is left running. */
    @AfterSuite(alwaysRun = true)
    public void stopAppiumServer() {
        AppiumServerManager.stop();
    }

    // ------------------------------------------------------------------ fixtures

    protected static User standardUser() {
        return JsonDataReader.findByKey("users.json", User.class, User::id, "standard");
    }

    protected static User lockedUser() {
        return JsonDataReader.findByKey("users.json", User.class, User::id, "locked");
    }

    protected static Product product(String id) {
        return JsonDataReader.findByKey("products.json", Product.class, Product::id, id);
    }

    protected static CheckoutProfile checkoutProfile() {
        return JsonDataReader.read("checkout.json", CheckoutProfile.class);
    }

    protected static LoginScenario scenario(String id) {
        return JsonDataReader.findByKey(
                "login-scenarios.json", LoginScenario.class, LoginScenario::id, id);
    }

    // ------------------------------------------------------------------ shared flows

    /**
     * Logs in and returns the catalog.
     *
     * <p>Deliberately not a {@code @BeforeMethod}: a test that needs a logged-in user says so on
     * its first line, so anyone reading it can see its starting state without checking the
     * superclass. Hidden setup is how a suite becomes unreadable.</p>
     */
    protected ProductListPage loginAsStandardUser() {
        LoginPage login = openLoginScreen();
        return login.loginAs(standardUser());
    }

    /**
     * Navigates to the login screen from wherever the app launched.
     *
     * <p>My Demo App opens on the catalog, and login lives behind the drawer.</p>
     */
    protected LoginPage openLoginScreen() {
        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();
        return catalog.openMenu().openLogin();
    }

    // ------------------------------------------------------------------ helpers

    private static String orDefault(String supplied, String fallback) {
        return supplied == null || supplied.isBlank() ? fallback : supplied.trim();
    }

    private static int orDefault(String supplied, int fallback) {
        if (supplied == null || supplied.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(supplied.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Suite parameter must be a number, was '" + supplied + "'", e);
        }
    }
}

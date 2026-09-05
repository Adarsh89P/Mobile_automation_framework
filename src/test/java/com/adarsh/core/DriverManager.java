package com.adarsh.core;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Owns the driver instance for the current thread.
 *
 * <p>There is no static {@code AppiumDriver} field anywhere in this framework. TestNG runs
 * {@code <test>} blocks in parallel on separate threads, and a shared driver would have
 * two threads issuing commands down one session — the classic source of "random" mobile
 * flakiness. A {@link ThreadLocal} gives each thread its own session, and
 * {@link #quitDriver()} removes the entry so the thread-pool thread does not carry a dead
 * driver into the next test.</p>
 */
public final class DriverManager {

    private static final Logger LOG = LogManager.getLogger(DriverManager.class);

    private static final ThreadLocal<AppiumDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /**
     * @throws IllegalStateException if called before the driver was created — a clear message
     *         beats the {@code NullPointerException} a bare {@code get()} would throw.
     */
    public static AppiumDriver getDriver() {
        AppiumDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException(
                    "No driver bound to thread '" + Thread.currentThread().getName()
                            + "'. DriverFactory.createDriver(...) must run in @BeforeMethod "
                            + "on the same thread as the test.");
        }
        return driver;
    }

    public static void setDriver(AppiumDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("Refusing to bind a null driver");
        }
        DRIVER.set(driver);
        LOG.debug("Bound {} to thread '{}'",
                driver.getClass().getSimpleName(), Thread.currentThread().getName());
    }

    public static boolean hasDriver() {
        return DRIVER.get() != null;
    }

    /**
     * Quits the session and clears the thread binding.
     *
     * <p>Never throws: teardown runs after a test has possibly already failed, and losing the
     * real failure behind a quit error makes triage much harder. The remove() is in a finally
     * block so a hung quit still cannot leak the ThreadLocal.</p>
     */
    public static void quitDriver() {
        AppiumDriver driver = DRIVER.get();
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
            LOG.debug("Quit session on thread '{}'", Thread.currentThread().getName());
        } catch (RuntimeException e) {
            LOG.warn("Ignoring error while quitting the driver: {}", e.getMessage());
        } finally {
            DRIVER.remove();
        }
    }

    /** @throws IllegalStateException when the current session is not an Android one. */
    public static AndroidDriver asAndroid() {
        AppiumDriver driver = getDriver();
        if (driver instanceof AndroidDriver android) {
            return android;
        }
        throw new IllegalStateException(
                "Android-only operation requested but the session is "
                        + driver.getClass().getSimpleName());
    }

    /** @throws IllegalStateException when the current session is not an iOS one. */
    public static IOSDriver asIos() {
        AppiumDriver driver = getDriver();
        if (driver instanceof IOSDriver ios) {
            return ios;
        }
        throw new IllegalStateException(
                "iOS-only operation requested but the session is "
                        + driver.getClass().getSimpleName());
    }
}

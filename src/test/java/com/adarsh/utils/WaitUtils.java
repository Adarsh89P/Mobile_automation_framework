package com.adarsh.utils;

import com.adarsh.config.ConfigReader;
import com.adarsh.core.DriverManager;
import io.appium.java_client.AppiumDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Every wait in the framework goes through here.
 *
 * <p>This class is the reason there is no {@code Thread.sleep} in the repo. A sleep is either
 * too short (flaky) or too long (slow), and it is always both across a suite. A condition-based
 * wait returns the moment the app is ready and fails with a description of what never happened.</p>
 *
 * <p>Every wait ignores {@link StaleElementReferenceException}. On a React Native app the view
 * hierarchy is rebuilt on each render, so an element found a moment ago is routinely replaced
 * mid-wait; treating that as a retry rather than an error removes a whole class of flakiness.</p>
 */
public final class WaitUtils {

    private WaitUtils() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /** The default budget from {@code timeout.explicit.seconds}. */
    public static Duration defaultTimeout() {
        return Duration.ofSeconds(ConfigReader.config().explicitTimeoutSeconds());
    }

    private static Duration pollingInterval() {
        return Duration.ofMillis(ConfigReader.config().pollingIntervalMs());
    }

    /** A wait bound to this thread's driver — never a shared one. */
    public static Wait<AppiumDriver> waiter(Duration timeout) {
        return new FluentWait<>(DriverManager.getDriver())
                .withTimeout(timeout)
                .pollingEvery(pollingInterval())
                .ignoring(NoSuchElementException.class)
                .ignoring(StaleElementReferenceException.class);
    }

    // ------------------------------------------------------------------ presence

    public static WebElement waitForVisible(WebElement element) {
        return waitForVisible(element, defaultTimeout());
    }

    public static WebElement waitForVisible(WebElement element, Duration timeout) {
        return until(ExpectedConditions.visibilityOf(element), timeout,
                "element to become visible");
    }

    public static WebElement waitForVisible(By locator) {
        return waitForVisible(locator, defaultTimeout());
    }

    public static WebElement waitForVisible(By locator, Duration timeout) {
        return until(ExpectedConditions.visibilityOfElementLocated(locator), timeout,
                "an element matching " + locator + " to become visible");
    }

    public static WebElement waitForClickable(WebElement element) {
        return waitForClickable(element, defaultTimeout());
    }

    public static WebElement waitForClickable(WebElement element, Duration timeout) {
        return until(ExpectedConditions.elementToBeClickable(element), timeout,
                "element to become clickable");
    }

    public static boolean waitForInvisible(WebElement element) {
        return waitForInvisible(element, defaultTimeout());
    }

    public static boolean waitForInvisible(WebElement element, Duration timeout) {
        return until(ExpectedConditions.invisibilityOf(element), timeout,
                "element to disappear");
    }

    public static boolean waitForInvisible(By locator, Duration timeout) {
        return until(ExpectedConditions.invisibilityOfElementLocated(locator), timeout,
                "elements matching " + locator + " to disappear");
    }

    public static List<WebElement> waitForAtLeast(By locator, int count, Duration timeout) {
        return until(ExpectedConditions.numberOfElementsToBeMoreThan(locator, count - 1), timeout,
                "at least " + count + " elements matching " + locator);
    }

    // ------------------------------------------------------------------ content

    public static boolean waitForText(WebElement element, String expected, Duration timeout) {
        return until(ExpectedConditions.textToBePresentInElement(element, expected), timeout,
                "element text to contain '" + expected + "'");
    }

    public static boolean waitForAttribute(
            WebElement element, String attribute, String value, Duration timeout) {
        return until(ExpectedConditions.attributeContains(element, attribute, value), timeout,
                "attribute '" + attribute + "' to contain '" + value + "'");
    }

    // ------------------------------------------------------------------ generic

    /**
     * Waits for an arbitrary condition — the escape hatch that keeps sleeps out of page classes.
     *
     * <p>Example: {@code WaitUtils.waitUntil(() -> cart.badgeCount() == 2, "cart badge to read 2")}.</p>
     */
    public static void waitUntil(BooleanSupplier condition, String description) {
        waitUntil(condition, description, defaultTimeout());
    }

    public static void waitUntil(BooleanSupplier condition, String description, Duration timeout) {
        until(driver -> condition.getAsBoolean() ? Boolean.TRUE : null, timeout, description);
    }

    /**
     * Runs a condition and rewrites the timeout message to say what was being waited for.
     *
     * <p>Selenium's default message quotes the raw condition, which in a mobile stack is a long
     * unreadable proxy string. Saying "waited 20s for the cart badge to read 2" is the
     * difference between triaging from the report and re-running locally to find out.</p>
     */
    private static <T> T until(ExpectedCondition<T> condition, Duration timeout, String what) {
        try {
            return waiter(timeout).until(condition);
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "Timed out after %ds waiting for %s".formatted(timeout.toSeconds(), what), e);
        }
    }
}

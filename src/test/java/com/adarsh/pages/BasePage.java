package com.adarsh.pages;

import com.adarsh.ai.LocatorHealer;
import com.adarsh.config.ConfigReader;
import com.adarsh.core.DriverManager;
import com.adarsh.core.PlatformType;
import com.adarsh.utils.GestureUtils;
import com.adarsh.utils.WaitUtils;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;
import io.qameta.allure.Step;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;

import java.time.Duration;
import java.util.List;

/**
 * Base class for every screen. Owns <em>all</em> interaction with the driver.
 *
 * <p>Page subclasses declare locators and express user intent; they never call
 * {@code element.click()} or touch the driver themselves. Centralising it here means the
 * waiting, logging, keyboard handling and scroll-into-view behaviour is written once and every
 * screen gets it — and when a new stabilisation trick is needed, it lands in one file rather
 * than in forty.</p>
 *
 * <p>Fields are decorated with {@link AppiumFieldDecorator} carrying an explicit timeout, so a
 * lookup driven by {@code @AndroidFindBy} waits rather than failing on the first miss. That is
 * lazy: constructing a page performs no queries, so a page object can be created before its
 * screen has finished rendering.</p>
 */
public abstract class BasePage {

    private static final Logger LOG = LogManager.getLogger(BasePage.class);

    protected final AppiumDriver driver;

    protected BasePage() {
        this.driver = DriverManager.getDriver();
        PageFactory.initElements(
                new AppiumFieldDecorator(driver, WaitUtils.defaultTimeout()), this);
    }

    /**
     * An element that must be present for this screen to count as loaded.
     *
     * <p>Every page names one, so {@link #isLoaded()} and {@link #waitUntilLoaded()} work the
     * same everywhere and no test has to invent its own "am I there yet" check.</p>
     */
    protected abstract WebElement pageMarker();

    /** Human name used in logs, Allure steps and assertion messages. */
    public abstract String screenName();

    // ------------------------------------------------------------------ screen state

    @Step("Wait until the {this.screenName} screen is loaded")
    public void waitUntilLoaded() {
        try {
            WaitUtils.waitForVisible(pageMarker());
            LOG.info("On the {} screen", screenName());
        } catch (TimeoutException e) {
            throw new TimeoutException(
                    "The %s screen never appeared. Current activity: %s"
                            .formatted(screenName(), currentActivity()), e);
        }
    }

    public boolean isLoaded() {
        return isDisplayed(pageMarker());
    }

    // ------------------------------------------------------------------ interaction

    @Step("Tap {description}")
    protected void click(WebElement element, String description) {
        WaitUtils.waitForClickable(element).click();
        LOG.info("Tapped {}", description);
    }

    /**
     * Types into a field, clearing whatever was there first.
     *
     * <p>The keyboard is dismissed afterwards because on Android it covers the lower third of
     * the screen — including, routinely, the button the next step needs to tap.</p>
     */
    @Step("Type '{value}' into {description}")
    protected void type(WebElement element, String value, String description) {
        WebElement field = WaitUtils.waitForVisible(element);
        field.clear();
        field.sendKeys(value);
        LOG.info("Typed '{}' into {}", value, description);
        hideKeyboard();
    }

    /** Types without logging the value — for anything that must not appear in a report. */
    protected void typeSecret(WebElement element, String value, String description) {
        WebElement field = WaitUtils.waitForVisible(element);
        field.clear();
        field.sendKeys(value);
        LOG.info("Typed a hidden value into {}", description);
        hideKeyboard();
    }

    protected String getText(WebElement element) {
        WebElement visible = WaitUtils.waitForVisible(element);
        String text = visible.getText();
        if (text != null && !text.isBlank()) {
            return text.trim();
        }
        // React Native often renders the readable value into the accessibility label rather
        // than the text attribute, so fall back before concluding the element is empty.
        String label = visible.getDomAttribute(
                ConfigReader.platform() == PlatformType.ANDROID ? "content-desc" : "label");
        return label == null ? "" : label.trim();
    }

    /**
     * Non-throwing presence check, for assertions that expect something to be absent.
     *
     * <p>Deliberately does not wait: a test asserting "the error is gone" would otherwise pay
     * the full timeout on the happy path.</p>
     */
    protected boolean isDisplayed(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (NoSuchElementException | org.openqa.selenium.StaleElementReferenceException e) {
            return false;
        }
    }

    /** Waits for an element the test expects to appear shortly. */
    protected boolean isDisplayedWithin(WebElement element, Duration timeout) {
        try {
            WaitUtils.waitForVisible(element, timeout);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    protected boolean isEnabled(WebElement element) {
        return WaitUtils.waitForVisible(element).isEnabled();
    }

    // ------------------------------------------------------------------ scrolling

    /**
     * Scrolls a native Android list until text is on screen, using UiScrollable.
     *
     * <p>UiScrollable runs inside the UiAutomator2 server on the device, so it scrolls in one
     * round trip and knows when it has hit the end of the list. Repeated swipe-and-look from
     * the client side needs a round trip per step and cannot tell "not yet" from "not there".</p>
     *
     * @throws NoSuchElementException if the end of the list is reached without a match
     */
    @Step("Scroll to '{text}'")
    protected WebElement scrollIntoView(String text) {
        if (ConfigReader.platform() != PlatformType.ANDROID) {
            return scrollIntoViewByGesture(text);
        }
        String uiSelector = "new UiScrollable(new UiSelector().scrollable(true).instance(0))"
                + ".scrollIntoView(new UiSelector().textContains(\"" + text + "\").instance(0))";
        try {
            return driver.findElement(AppiumBy.androidUIAutomator(uiSelector));
        } catch (org.openqa.selenium.WebDriverException e) {
            throw new NoSuchElementException(
                    "Scrolled to the end of the list without finding text '" + text + "'", e);
        }
    }

    /**
     * Platform-neutral fallback: swipe up a bounded number of times, checking after each.
     *
     * <p>The bound matters. An unbounded scroll-until-found loops forever on a screen that
     * never contains the target, and the test then dies on the suite timeout with no useful
     * message instead of failing here with one.</p>
     */
    protected WebElement scrollIntoViewByGesture(String text) {
        By locator = By.xpath("//*[@name='" + text + "' or @label='" + text
                + "' or contains(@text,'" + text + "')]");
        int maxSwipes = 10;
        for (int attempt = 0; attempt < maxSwipes; attempt++) {
            List<WebElement> found = driver.findElements(locator);
            if (!found.isEmpty() && found.get(0).isDisplayed()) {
                return found.get(0);
            }
            GestureUtils.swipeUp();
        }
        throw new NoSuchElementException(
                "'" + text + "' was not on screen after " + maxSwipes + " swipes");
    }

    /** Scrolls until a specific element becomes visible, e.g. a button below the fold. */
    protected WebElement scrollIntoView(WebElement element, String description) {
        int maxSwipes = 10;
        for (int attempt = 0; attempt < maxSwipes; attempt++) {
            if (isDisplayed(element)) {
                return element;
            }
            GestureUtils.swipeUp();
        }
        throw new NoSuchElementException(
                description + " was not on screen after " + maxSwipes + " swipes");
    }

    protected void swipeUp() {
        GestureUtils.swipeUp();
    }

    protected void swipeDown() {
        GestureUtils.swipeDown();
    }

    protected void longPress(WebElement element) {
        GestureUtils.longPress(element);
    }

    protected void dragAndDrop(WebElement source, WebElement target) {
        GestureUtils.dragAndDrop(source, target);
    }

    // ------------------------------------------------------------------ device

    /**
     * Dismisses the soft keyboard if one is up.
     *
     * <p>Never throws: whether a keyboard is showing is a race on a real device, and a test
     * must not fail because it tried to close a keyboard that had already closed.</p>
     */
    protected void hideKeyboard() {
        try {
            if (driver instanceof AndroidDriver android && android.isKeyboardShown()) {
                android.hideKeyboard();
            }
        } catch (org.openqa.selenium.WebDriverException e) {
            LOG.debug("Could not hide the keyboard, continuing: {}", e.getMessage());
        }
    }

    /** Android back button. On iOS, callers use the screen's own back control instead. */
    protected void pressBack() {
        driver.navigate().back();
    }

    /** Current activity, or a placeholder on iOS — used to make timeout messages diagnosable. */
    protected String currentActivity() {
        try {
            return driver instanceof AndroidDriver android
                    ? android.currentActivity() : "n/a (iOS)";
        } catch (org.openqa.selenium.WebDriverException e) {
            return "unknown";
        }
    }

    protected List<WebElement> findAll(By locator) {
        return driver.findElements(locator);
    }

    /**
     * Finds an element, and on failure asks the AI layer for a replacement locator.
     *
     * <p>With the AI layer off - the default, and the state CI runs in - this is exactly
     * {@code driver.findElement(locator)} and costs nothing. With it on, the suggestion is
     * written to the healing report and the original exception is still thrown, unless
     * {@code ai.healing.apply=true} explicitly allows one retry.</p>
     *
     * <p>The original exception is rethrown rather than a new one, so the report shows the
     * real locator that failed rather than a wrapper that hides it.</p>
     */
    protected WebElement findElement(By locator, String description) {
        try {
            return driver.findElement(locator);
        } catch (NoSuchElementException original) {
            if (!LocatorHealer.isEnabled()) {
                throw original;
            }
            LOG.warn("{} not found with {} - asking for a locator suggestion", description, locator);
            return LocatorHealer.healAndRetry(locator, description).orElseThrow(() -> original);
        }
    }
}

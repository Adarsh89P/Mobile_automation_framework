package com.adarsh.pages;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Landing screen of ApiDemos — the Android sample app used for the device-capability suite.
 *
 * <p>ApiDemos is deliberately the target for rotation, alerts, backgrounding and drag-and-drop.
 * Those tests are about the <em>device</em>, and running them against a demo shop would tie a
 * platform behaviour to a vendor's app; here they exercise stock Android widgets and cannot
 * fail because a shop redesigned a screen.</p>
 *
 * <p><b>Locator note.</b> ApiDemos predates accessibility ids and sets none, so the usual
 * "accessibility id first" rule has nothing to bind to. The fallback is
 * {@code UiSelector().text(...)}, not XPath: it is evaluated on the device by UiAutomator2 in a
 * single round trip, while an XPath is evaluated by walking a serialised copy of the whole view
 * tree, which on a long list is both slower and more brittle.</p>
 */
public class ApiDemosHomePage extends BasePage {

    /** The activity title, present on every ApiDemos screen. */
    @AndroidFindBy(id = "android:id/action_bar")
    private WebElement actionBar;

    @AndroidFindBy(id = "android:id/list")
    private WebElement demoList;

    @AndroidFindBy(className = "android.widget.TextView")
    private List<WebElement> listEntries;

    @Override
    protected WebElement pageMarker() {
        return demoList;
    }

    @Override
    public String screenName() {
        return "ApiDemos home";
    }

    // ------------------------------------------------------------------ queries

    /** Top-level categories, in display order — the assertion target for the launch test. */
    public List<String> categories() {
        return listEntries.stream()
                .map(WebElement::getText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    public boolean hasCategory(String name) {
        return categories().contains(name);
    }

    // ------------------------------------------------------------------ navigation

    /**
     * Taps a list entry by its visible text, scrolling to it first if necessary.
     *
     * <p>Returns {@code this} rather than a new page type: ApiDemos is a tree of near-identical
     * list screens, and inventing a class per node would add forty files that all say the same
     * thing. Screens with genuinely different behaviour get their own page class.</p>
     */
    @Step("Open '{entry}'")
    public ApiDemosHomePage open(String entry) {
        WebElement target = scrollIntoView(entry);
        click(target, "the '" + entry + "' list entry");
        return this;
    }

    /** Walks a path of list entries, e.g. {@code navigateTo("Views", "Drag and Drop")}. */
    @Step("Navigate to {path}")
    public ApiDemosHomePage navigateTo(String... path) {
        for (String entry : path) {
            open(entry);
        }
        return this;
    }

    @Step("Go back to the previous ApiDemos screen")
    public ApiDemosHomePage back() {
        pressBack();
        return this;
    }

    /**
     * Finds a widget by its visible text on the current ApiDemos screen.
     *
     * <p>Protected on purpose: a public method handing a {@link WebElement} to a test would put
     * driver calls back in the test layer, which is the thing page objects exist to prevent.</p>
     */
    protected WebElement widgetWithText(String text) {
        return findElement(AppiumBy.androidUIAutomator(
                "new UiSelector().text(\"" + text + "\")"), "the widget with text '" + text + "'");
    }

    /** Finds a widget by resource id, for the demo screens that expose one. */
    protected WebElement widgetWithId(String resourceId) {
        return findElement(AppiumBy.id(resourceId), "the widget " + resourceId);
    }

    // ------------------------------------------------------------------ gestures

    @Step("Drag '{sourceId}' onto '{targetId}'")
    public ApiDemosHomePage dragBetween(String sourceId, String targetId) {
        dragAndDrop(widgetWithId(sourceId), widgetWithId(targetId));
        return this;
    }

    @Step("Long-press the entry '{entry}'")
    public ApiDemosHomePage longPressEntry(String entry) {
        longPress(widgetWithText(entry));
        return this;
    }

    @Step("Tap the widget '{resourceId}'")
    public ApiDemosHomePage tapWidget(String resourceId) {
        click(widgetWithId(resourceId), "the widget " + resourceId);
        return this;
    }

    /** Visible text of a widget, so tests assert on data rather than on elements. */
    public String textOf(String resourceId) {
        return getText(widgetWithId(resourceId));
    }

    /** Whether a widget with the given resource id is currently on screen. */
    public boolean isWidgetDisplayed(String resourceId) {
        return !findAll(AppiumBy.id(resourceId)).isEmpty();
    }
}

package com.adarsh.utils;

import com.adarsh.core.DriverManager;
import io.appium.java_client.AppiumDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;

import java.time.Duration;
import java.util.List;

/**
 * Touch gestures, built on the W3C Actions API.
 *
 * <p>{@code TouchAction} — the API most Appium tutorials still show — was removed in Appium 2.
 * Everything here is expressed as {@link Sequence}s of {@link PointerInput} events, which is
 * the protocol-level gesture model both UiAutomator2 and XCUITest implement, so the same code
 * drives both platforms.</p>
 *
 * <p>Swipes are defined as a fraction of the screen rather than in pixels: a 300px swipe is a
 * flick on a phone and barely a nudge on a tablet, and hard-coded coordinates are the reason
 * gesture tests break when the device changes.</p>
 *
 * <p>Each gesture ends with a short pause before lifting the finger. Without it the driver
 * releases mid-flight and the app reads a fling instead of a controlled drag — the usual cause
 * of a "swipe did nothing" failure.</p>
 */
public final class GestureUtils {

    private static final Logger LOG = LogManager.getLogger(GestureUtils.class);

    private static final String FINGER = "finger";

    /** Slow enough that the app's gesture recogniser reliably classifies it as a drag. */
    private static final Duration DEFAULT_SWIPE_DURATION = Duration.ofMillis(600);

    private static final Duration DEFAULT_LONG_PRESS = Duration.ofMillis(1500);

    /** Keeps the gesture clear of the status bar, the nav bar and edge-swipe zones. */
    private static final double SAFE_EDGE_MARGIN = 0.15;

    private GestureUtils() {
        throw new AssertionError("Utility class - not instantiable");
    }

    // ------------------------------------------------------------------ primitives

    /** Single tap at a point. Prefer clicking an element; this is for canvas-style surfaces. */
    public static void tap(Point point) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, FINGER);
        Sequence tap = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), point.x, point.y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, Duration.ofMillis(100)))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        perform(tap);
    }

    public static void swipe(Point start, Point end) {
        swipe(start, end, DEFAULT_SWIPE_DURATION);
    }

    public static void swipe(Point start, Point end, Duration duration) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, FINGER);
        Sequence swipe = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), start.x, start.y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                // A brief hold before moving makes the app treat this as a drag, not a fling.
                .addAction(new Pause(finger, Duration.ofMillis(100)))
                .addAction(finger.createPointerMove(duration,
                        PointerInput.Origin.viewport(), end.x, end.y))
                .addAction(new Pause(finger, Duration.ofMillis(100)))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        perform(swipe);
        LOG.debug("Swiped from {} to {} over {}ms", start, end, duration.toMillis());
    }

    // ------------------------------------------------------------------ directional swipes

    /** Scrolls the content up, i.e. moves the finger upward to reveal what is below. */
    public static void swipeUp() {
        swipeVertically(1 - SAFE_EDGE_MARGIN, SAFE_EDGE_MARGIN);
    }

    public static void swipeDown() {
        swipeVertically(SAFE_EDGE_MARGIN, 1 - SAFE_EDGE_MARGIN);
    }

    public static void swipeLeft() {
        swipeHorizontally(1 - SAFE_EDGE_MARGIN, SAFE_EDGE_MARGIN);
    }

    public static void swipeRight() {
        swipeHorizontally(SAFE_EDGE_MARGIN, 1 - SAFE_EDGE_MARGIN);
    }

    private static void swipeVertically(double fromRatio, double toRatio) {
        Dimension size = screenSize();
        int x = size.width / 2;
        swipe(new Point(x, (int) (size.height * fromRatio)),
                new Point(x, (int) (size.height * toRatio)));
    }

    private static void swipeHorizontally(double fromRatio, double toRatio) {
        Dimension size = screenSize();
        int y = size.height / 2;
        swipe(new Point((int) (size.width * fromRatio), y),
                new Point((int) (size.width * toRatio), y));
    }

    /** Swipes within one element's bounds — for a carousel that sits inside a scrolling page. */
    public static void swipeLeftOn(WebElement element) {
        Rectangle bounds = element.getRect();
        int y = bounds.getY() + bounds.getHeight() / 2;
        swipe(new Point(bounds.getX() + (int) (bounds.getWidth() * 0.9), y),
                new Point(bounds.getX() + (int) (bounds.getWidth() * 0.1), y));
    }

    public static void swipeRightOn(WebElement element) {
        Rectangle bounds = element.getRect();
        int y = bounds.getY() + bounds.getHeight() / 2;
        swipe(new Point(bounds.getX() + (int) (bounds.getWidth() * 0.1), y),
                new Point(bounds.getX() + (int) (bounds.getWidth() * 0.9), y));
    }

    // ------------------------------------------------------------------ press & drag

    public static void longPress(WebElement element) {
        longPress(element, DEFAULT_LONG_PRESS);
    }

    public static void longPress(WebElement element, Duration duration) {
        Point centre = centreOf(element);
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, FINGER);
        Sequence press = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), centre.x, centre.y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, duration))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        perform(press);
        LOG.debug("Long-pressed at {} for {}ms", centre, duration.toMillis());
    }

    /**
     * Drags {@code source} onto {@code target}.
     *
     * <p>The move is split into two steps with a hold between them: most drag-and-drop
     * implementations only arm after the press is recognised, and a single continuous move
     * from down to up is usually discarded as a swipe.</p>
     */
    public static void dragAndDrop(WebElement source, WebElement target) {
        Point from = centreOf(source);
        Point to = centreOf(target);
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, FINGER);
        Sequence drag = new Sequence(finger, 0)
                .addAction(finger.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), from.x, from.y))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, Duration.ofMillis(800)))
                .addAction(finger.createPointerMove(Duration.ofMillis(1000),
                        PointerInput.Origin.viewport(), to.x, to.y))
                .addAction(new Pause(finger, Duration.ofMillis(300)))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        perform(drag);
        LOG.debug("Dragged {} onto {}", from, to);
    }

    // ------------------------------------------------------------------ multi-touch

    /** Pinch inwards (zoom out) using two fingers moving towards the element centre. */
    public static void pinch(WebElement element) {
        multiTouch(element, true);
    }

    /** Spread outwards (zoom in). */
    public static void zoom(WebElement element) {
        multiTouch(element, false);
    }

    /**
     * Two pointers moving in opposite directions in a single {@code perform} call.
     *
     * <p>Both sequences must be dispatched together — that simultaneity is exactly what makes
     * it a pinch rather than two unrelated drags, and it is the reason multi-touch cannot be
     * expressed with the removed single-pointer TouchAction API.</p>
     */
    private static void multiTouch(WebElement element, boolean inwards) {
        Rectangle bounds = element.getRect();
        int centreX = bounds.getX() + bounds.getWidth() / 2;
        int centreY = bounds.getY() + bounds.getHeight() / 2;
        int reach = Math.min(bounds.getWidth(), bounds.getHeight()) / 3;

        Point nearTop = new Point(centreX, centreY - reach / 3);
        Point farTop = new Point(centreX, centreY - reach);
        Point nearBottom = new Point(centreX, centreY + reach / 3);
        Point farBottom = new Point(centreX, centreY + reach);

        PointerInput first = new PointerInput(PointerInput.Kind.TOUCH, "finger-1");
        PointerInput second = new PointerInput(PointerInput.Kind.TOUCH, "finger-2");

        Sequence up = dragSequence(first, inwards ? farTop : nearTop, inwards ? nearTop : farTop);
        Sequence down = dragSequence(second,
                inwards ? farBottom : nearBottom, inwards ? nearBottom : farBottom);

        DriverManager.getDriver().perform(List.of(up, down));
        LOG.debug("Performed {} on element at {}", inwards ? "pinch" : "zoom", bounds);
    }

    private static Sequence dragSequence(PointerInput pointer, Point from, Point to) {
        return new Sequence(pointer, 0)
                .addAction(pointer.createPointerMove(Duration.ZERO,
                        PointerInput.Origin.viewport(), from.x, from.y))
                .addAction(pointer.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(pointer, Duration.ofMillis(100)))
                .addAction(pointer.createPointerMove(Duration.ofMillis(700),
                        PointerInput.Origin.viewport(), to.x, to.y))
                .addAction(pointer.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
    }

    // ------------------------------------------------------------------ helpers

    public static Point centreOf(WebElement element) {
        Rectangle bounds = element.getRect();
        return new Point(bounds.getX() + bounds.getWidth() / 2,
                bounds.getY() + bounds.getHeight() / 2);
    }

    public static Dimension screenSize() {
        return DriverManager.getDriver().manage().window().getSize();
    }

    private static void perform(Sequence sequence) {
        AppiumDriver driver = DriverManager.getDriver();
        driver.perform(List.of(sequence));
    }
}

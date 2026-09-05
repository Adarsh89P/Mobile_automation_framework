package com.adarsh.tests;

import com.adarsh.pages.ApiDemosHomePage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * W3C touch gestures against ApiDemos.
 *
 * <p>ApiDemos ships a drag-and-drop demo that reports the outcome in a text view, which makes it
 * one of the few places a drag can be asserted on rather than merely performed. A gesture test
 * that only checks "no exception was thrown" proves nothing: the driver will happily perform a
 * drag the app never registers, and the test would pass anyway.</p>
 */
@Epic("Mobile platform")
@Feature("Touch gestures")
public class GestureTest extends DeviceTest {

    // TODO: confirm these ids with Appium Inspector. They are the ApiDemos drag-and-drop demo's
    //       documented view ids; the result text view is what makes the outcome assertable.
    private static final String DRAG_DOT_1 = "io.appium.android.apis:id/drag_dot_1";
    private static final String DRAG_DOT_2 = "io.appium.android.apis:id/drag_dot_2";
    private static final String DRAG_RESULT = "io.appium.android.apis:id/drag_result_text";

    @Test(groups = {"mobile-native"},
            description = "A W3C drag-and-drop gesture is registered by the app")
    @Story("Drag and drop")
    @Severity(SeverityLevel.NORMAL)
    public void dragAndDropIsRegisteredByTheApp() {
        ApiDemosHomePage home = new ApiDemosHomePage();
        home.waitUntilLoaded();

        home.navigateTo("Views", "Drag and Drop");

        assertTrue(home.isWidgetDisplayed(DRAG_DOT_1),
                "The drag-and-drop demo did not open");
        String resultBefore = home.textOf(DRAG_RESULT);

        home.dragBetween(DRAG_DOT_1, DRAG_DOT_2);

        String resultAfter = home.textOf(DRAG_RESULT);
        assertNotEquals(resultAfter, resultBefore,
                "The app did not register the drag - the result text is unchanged ('"
                        + resultBefore + "'). The gesture was performed but never landed.");
        assertFalse(resultAfter.isBlank(), "The drag produced an empty result message");
        LOG.info("Drag-and-drop result: {}", resultAfter);
    }

    @Test(groups = {"mobile-native"},
            description = "A long press opens the context menu on a list entry")
    @Story("Long press")
    @Severity(SeverityLevel.MINOR)
    public void longPressOpensTheContextMenu() {
        ApiDemosHomePage home = new ApiDemosHomePage();
        home.waitUntilLoaded();

        home.navigateTo("Views", "Expandable Lists", "1. Custom Adapter");
        home.longPressEntry("People Names");

        // A context menu is a separate window; its title is the proof the press was recognised
        // as a long press rather than as a tap.
        assertTrue(home.isWidgetDisplayed("android:id/title"),
                "A long press did not open the context menu");
    }
}

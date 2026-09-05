package com.adarsh.tests;

import com.adarsh.pages.ApiDemosHomePage;
import com.adarsh.utils.AppUtils;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.openqa.selenium.ScreenOrientation;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Device behaviours exercised against ApiDemos: rotation and native dialogs.
 *
 * <p>Run against stock Android widgets on purpose. These assertions are about the platform and
 * the driver, so binding them to a vendor's demo shop would mean a shop redesign could fail a
 * test that has nothing to do with the shop.</p>
 */
@Epic("Mobile platform")
@Feature("Device interaction")
public class DeviceInteractionTest extends DeviceTest {

    /**
     * Rotating must not lose content.
     *
     * <p>Comparing the list contents before and after — rather than just checking the screen is
     * still there — is what catches the real rotation bug: an activity that recreates itself and
     * comes back empty.</p>
     */
    @Test(groups = {"mobile-native"},
            description = "Rotating to landscape and back leaves the layout intact")
    @Story("Screen rotation")
    @Severity(SeverityLevel.NORMAL)
    public void rotationPreservesTheLayout() {
        ApiDemosHomePage home = new ApiDemosHomePage();
        home.waitUntilLoaded();

        List<String> portraitEntries = home.categories();
        assertFalse(portraitEntries.isEmpty(), "Setup failed - the demo list rendered nothing");

        AppUtils.rotateToLandscape();
        assertEquals(AppUtils.orientation(), ScreenOrientation.LANDSCAPE,
                "The device did not rotate to landscape");
        assertTrue(home.isLoaded(), "The screen did not survive rotation to landscape");
        assertEquals(home.categories(), portraitEntries,
                "The demo list lost or reordered entries in landscape");

        AppUtils.rotateToPortrait();
        assertEquals(AppUtils.orientation(), ScreenOrientation.PORTRAIT,
                "The device did not rotate back to portrait");
        assertEquals(home.categories(), portraitEntries,
                "The demo list did not come back intact after rotating to portrait");
    }

    /**
     * A native dialog must be reachable, readable and dismissible.
     *
     * <p>ApiDemos builds a real {@code AlertDialog}, so this exercises the same code path as a
     * runtime-permission prompt: content the app's own view hierarchy does not own.</p>
     */
    // TODO: confirm the entry label and the dialog ids with Appium Inspector. The navigation
    //       path is the documented ApiDemos structure, and android:id/button1 is the Android
    //       framework's own id for a dialog's positive button.
    @Test(groups = {"mobile-native"},
            description = "A native alert dialog is displayed, read and dismissed")
    @Story("Native dialogs")
    @Severity(SeverityLevel.CRITICAL)
    public void nativeAlertDialogIsHandled() {
        ApiDemosHomePage home = new ApiDemosHomePage();
        home.waitUntilLoaded();

        home.navigateTo("App", "Alert Dialogs", "OK Cancel dialog with a message");

        assertTrue(home.isWidgetDisplayed("android:id/alertTitle")
                        || home.isWidgetDisplayed("android:id/message"),
                "No native alert dialog appeared");

        String message = home.textOf("android:id/message");
        assertFalse(message.isBlank(), "The alert dialog rendered an empty message");
        LOG.info("Alert dialog says: {}", message);

        home.tapWidget("android:id/button1");

        assertFalse(home.isWidgetDisplayed("android:id/message"),
                "The alert dialog is still on screen after tapping OK");
    }

    /** Rotation is device state and outlives the session, so it is always put back. */
    @AfterMethod(alwaysRun = true)
    public void restoreOrientation() {
        try {
            AppUtils.rotateToPortrait();
        } catch (RuntimeException e) {
            LOG.warn("Could not restore portrait orientation: {}", e.getMessage());
        }
    }
}

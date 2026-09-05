package com.adarsh.tests;

import com.adarsh.config.AppProfile;
import com.adarsh.models.Product;
import com.adarsh.pages.ProductListPage;
import com.adarsh.utils.AppUtils;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Behaviours that belong to the operating system rather than to any screen: backgrounding,
 * deep links and loss of connectivity.
 *
 * <p>These are the tests a web-only suite cannot have, and they are where mobile apps actually
 * break — a cart that survives a tap-through but not a phone call is still a broken cart.</p>
 */
@Epic("Mobile platform")
@Feature("Application lifecycle")
public class AppLifecycleTest extends ShopTest {

    private static final String DEEP_LINK_SCHEME = "mydemoapprn://";

    /**
     * State must survive the app being sent to the background.
     *
     * <p>Five seconds is chosen because it is long enough for the OS to actually suspend the
     * process, and short enough that it will not usually be killed outright — which is the
     * window where real state-restoration bugs live.</p>
     */
    @Test(groups = {"mobile-native"},
            description = "Cart state survives the app being backgrounded for 5 seconds")
    @Story("Backgrounding and restoring")
    @Severity(SeverityLevel.CRITICAL)
    public void cartStateSurvivesBackgrounding() {
        Product expected = product("backpack");

        ProductListPage catalog = loginAsStandardUser();
        catalog.openProduct(expected.name()).addToCart();

        ProductListPage beforeBackground = new ProductListPage();
        beforeBackground.waitForCartBadge(1);
        int badgeBefore = beforeBackground.cartBadgeCount();

        AppUtils.runInBackground(Duration.ofSeconds(5));

        ProductListPage afterRestore = new ProductListPage();
        afterRestore.waitUntilLoaded();

        assertEquals(afterRestore.cartBadgeCount(), badgeBefore,
                "The cart badge changed while the app was backgrounded");
        assertTrue(afterRestore.openCart().contains(expected.name()),
                "The cart lost its contents while the app was backgrounded");
    }

    /** The session, not just the screen, has to survive a trip to the background. */
    @Test(groups = {"mobile-native"},
            description = "The signed-in session survives backgrounding")
    @Story("Backgrounding and restoring")
    @Severity(SeverityLevel.NORMAL)
    public void sessionSurvivesBackgrounding() {
        loginAsStandardUser();

        AppUtils.runInBackground(Duration.ofSeconds(5));

        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();
        assertTrue(catalog.openMenu().isLoggedIn(),
                "The user was signed out by backgrounding the app");
    }

    /**
     * A deep link must bring the app to the foreground on a usable screen.
     *
     * <p>The scheme is read from the app's own manifest. The path is not asserted: the manifest
     * declares no path restriction, so which screen a given path opens is a product decision
     * this test has no way to verify from the outside.</p>
     */
    // TODO: once the app's routing table is confirmed, extend this to open a specific product
    //       (e.g. mydemoapprn://product/1) and assert the product screen for that item.
    @Test(groups = {"mobile-native"},
            description = "A deep link launches the app on a usable screen")
    @Story("Deep links")
    @Severity(SeverityLevel.NORMAL)
    public void deepLinkOpensTheApp() {
        AppProfile app = AppProfile.named(appProfileName());

        AppUtils.openDeepLink(DEEP_LINK_SCHEME);

        assertEquals(AppUtils.currentPackage(), app.appPackage(),
                "The deep link did not bring the app under test to the foreground");

        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();
        assertTrue(catalog.visibleProductCount() > 0,
                "The deep link opened the app but the catalog did not render");
    }

    /**
     * The app must not fall over when the network disappears.
     *
     * <p>Asserting that airplane mode really engaged is part of the test. Without it, a silently
     * failing toggle turns this into a test that always passes while checking nothing — the
     * worst kind of green.</p>
     */
    @Test(groups = {"mobile-native"},
            description = "The app stays usable when connectivity is lost and restored")
    @Story("Offline behaviour")
    @Severity(SeverityLevel.NORMAL)
    public void appSurvivesLossOfConnectivity() {
        ProductListPage catalog = loginAsStandardUser();
        int productsOnline = catalog.visibleProductCount();

        AppUtils.setAirplaneMode(true);
        assertTrue(AppUtils.isAirplaneModeOn(),
                "Airplane mode did not engage - the rest of this test would prove nothing");

        ProductListPage offline = new ProductListPage();
        assertTrue(offline.isLoaded(),
                "The app left the catalog screen when connectivity was lost");
        assertEquals(offline.visibleProductCount(), productsOnline,
                "The catalog lost products when the network went away");

        AppUtils.setAirplaneMode(false);
        assertTrue(new ProductListPage().isLoaded(),
                "The app did not recover after connectivity was restored");
    }

    /**
     * Connectivity is device state, not app state, so quitting the driver does not undo it.
     * Left unrestored it would break every later test on the same emulator.
     *
     * <p>TestNG runs a subclass {@code @AfterMethod} before the superclass one, so this still
     * has a live driver — {@code BaseTest.tearDown} quits the session afterwards.</p>
     */
    @AfterMethod(alwaysRun = true)
    public void restoreConnectivity() {
        AppUtils.restoreConnectivity();
    }
}

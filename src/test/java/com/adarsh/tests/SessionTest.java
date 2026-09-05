package com.adarsh.tests;

import com.adarsh.pages.LoginPage;
import com.adarsh.pages.MenuPage;
import com.adarsh.pages.ProductListPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Epic("My Demo App")
@Feature("Authentication")
public class SessionTest extends ShopTest {

    /**
     * Logging out must end the session, not merely navigate away from it.
     *
     * <p>The second assertion is the one that matters. Plenty of apps return to the login screen
     * while leaving the token in storage, so this re-opens the drawer afterwards and checks the
     * app is genuinely signed out rather than trusting the screen it landed on.</p>
     */
    @Test(groups = {"regression"},
            description = "Logout returns to the login screen and leaves no session behind")
    @Story("Signing out")
    @Severity(SeverityLevel.CRITICAL)
    public void logoutEndsTheSession() {
        ProductListPage catalog = loginAsStandardUser();

        MenuPage menu = catalog.openMenu();
        assertTrue(menu.isLoggedIn(), "Setup failed - the user is not signed in");

        LoginPage login = menu.logout();
        assertTrue(login.isLoaded(), "Logout did not return to the login screen");

        MenuPage afterLogout = login.openMenu();
        assertFalse(afterLogout.isLoggedIn(),
                "The session survived logout - the menu still offers 'Log out'");
    }

    /** A cart that outlives the session leaks one user's basket into the next user's app. */
    @Test(groups = {"regression"},
            description = "The cart is emptied when the user signs out")
    @Story("Signing out")
    @Severity(SeverityLevel.NORMAL)
    public void logoutClearsTheCart() {
        ProductListPage catalog = loginAsStandardUser();
        catalog.openProduct(product("backpack").name()).addToCart();

        ProductListPage afterAdd = new ProductListPage();
        afterAdd.waitForCartBadge(1);

        LoginPage login = afterAdd.openMenu().logout();
        ProductListPage catalogAgain = login.openMenu().openCatalog();

        assertEquals(catalogAgain.cartBadgeCount(), 0,
                "The cart survived logout and is still showing a badge");
    }
}

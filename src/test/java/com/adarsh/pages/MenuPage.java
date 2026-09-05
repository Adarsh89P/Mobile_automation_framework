package com.adarsh.pages;

import com.adarsh.utils.WaitUtils;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

/**
 * Side navigation drawer: catalog, cart, log in / log out, and the API/webview demos.
 *
 * <p>The drawer is where logout lives, so this class is what the session-persistence test
 * drives. Its ids ({@code menu item log in}, {@code menu item log out}, {@code menu item
 * catalog}) were read from the app bundle and are exact.</p>
 */
public class MenuPage extends BasePage {

    @AndroidFindBy(accessibility = "menu item catalog")
    @iOSXCUITFindBy(accessibility = "menu item catalog")
    private WebElement catalogItem;

    @AndroidFindBy(accessibility = "menu item log in")
    @iOSXCUITFindBy(accessibility = "menu item log in")
    private WebElement loginItem;

    @AndroidFindBy(accessibility = "menu item log out")
    @iOSXCUITFindBy(accessibility = "menu item log out")
    private WebElement logoutItem;

    @AndroidFindBy(accessibility = "menu item webview")
    @iOSXCUITFindBy(accessibility = "menu item webview")
    private WebElement webviewItem;

    @AndroidFindBy(accessibility = "menu item api")
    @iOSXCUITFindBy(accessibility = "menu item api")
    private WebElement apiCallsItem;

    /** Confirmation dialog shown after tapping log out. */
    // TODO: confirm with Appium Inspector — expected to be "Logout button".
    @AndroidFindBy(accessibility = "Logout button")
    @iOSXCUITFindBy(accessibility = "Logout button")
    private WebElement confirmLogoutButton;

    @Override
    protected WebElement pageMarker() {
        return catalogItem;
    }

    @Override
    public String screenName() {
        return "Navigation menu";
    }

    // ------------------------------------------------------------------ queries

    /**
     * Whether a user is signed in, judged by which entry the drawer offers.
     *
     * <p>The drawer shows "Log out" only to a signed-in user, which makes this the cheapest
     * honest session check available — cheaper and less brittle than reading a profile screen.</p>
     */
    public boolean isLoggedIn() {
        return isDisplayed(logoutItem);
    }

    // ------------------------------------------------------------------ actions

    @Step("Open the catalog from the menu")
    public ProductListPage openCatalog() {
        click(catalogItem, "the catalog menu item");
        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();
        return catalog;
    }

    @Step("Open the login screen from the menu")
    public LoginPage openLogin() {
        click(loginItem, "the log in menu item");
        LoginPage login = new LoginPage();
        login.waitUntilLoaded();
        return login;
    }

    /**
     * Logs out and lands back on the login screen.
     *
     * <p>The confirmation dialog is treated as optional: it is present in current builds, but a
     * logout that skips straight to the login screen is still a correct logout, and failing the
     * test over a missing dialog would be asserting on the wrong thing.</p>
     */
    @Step("Log out")
    public LoginPage logout() {
        click(logoutItem, "the log out menu item");
        if (isDisplayedWithin(confirmLogoutButton, java.time.Duration.ofSeconds(3))) {
            click(confirmLogoutButton, "the logout confirmation button");
        }
        LoginPage login = new LoginPage();
        login.waitUntilLoaded();
        return login;
    }

    /** Waits for the drawer to close again, e.g. after tapping outside it. */
    @Step("Close the menu")
    public void close() {
        pressBack();
        WaitUtils.waitForInvisible(catalogItem);
    }
}

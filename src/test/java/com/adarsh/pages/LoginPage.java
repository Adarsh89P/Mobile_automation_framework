package com.adarsh.pages;

import com.adarsh.models.User;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.time.Duration;

/**
 * Login screen of Sauce Labs My Demo App.
 *
 * <p><b>Locator strategy.</b> Accessibility id first, everywhere it exists. The app is React
 * Native and sets a {@code testID} on each control, which surfaces as an accessibility id on
 * both platforms — so one annotation pair covers Android and iOS, the ids are stable across
 * releases in a way generated resource ids are not, and using them keeps the tests aligned
 * with what a screen reader sees.</p>
 *
 * <p>The ids below were read out of the app's own JS bundle rather than guessed. The app
 * composes input-field ids as {@code "<Label> input field"}, which is why they read as prose.</p>
 */
public class LoginPage extends BasePage {

    @AndroidFindBy(accessibility = "Username input field")
    @iOSXCUITFindBy(accessibility = "Username input field")
    private WebElement usernameField;

    @AndroidFindBy(accessibility = "Password input field")
    @iOSXCUITFindBy(accessibility = "Password input field")
    private WebElement passwordField;

    // TODO: confirm with Appium Inspector — the label-composed ids above were verified in the
    //       app bundle, but the login button's id could not be isolated from the minified
    //       bundle. Expected to be "Login button"; correct here if the inspector disagrees.
    @AndroidFindBy(accessibility = "Login button")
    @iOSXCUITFindBy(accessibility = "Login button")
    private WebElement loginButton;

    /**
     * Server-side rejection: wrong password, unknown user, locked account.
     * Rendered once, below the form.
     */
    @AndroidFindBy(accessibility = "generic-error-message")
    @iOSXCUITFindBy(accessibility = "generic-error-message")
    private WebElement genericErrorMessage;

    /** Client-side validation, rendered under the offending field before any request. */
    @AndroidFindBy(accessibility = "Username-error-message")
    @iOSXCUITFindBy(accessibility = "Username-error-message")
    private WebElement usernameErrorMessage;

    @AndroidFindBy(accessibility = "Password-error-message")
    @iOSXCUITFindBy(accessibility = "Password-error-message")
    private WebElement passwordErrorMessage;

    /** The drawer is reachable from the login screen too, which is how a signed-out
     *  session is verified after logging out. */
    @AndroidFindBy(accessibility = "open menu")
    @iOSXCUITFindBy(accessibility = "open menu")
    private WebElement menuButton;

    @Override
    protected WebElement pageMarker() {
        return usernameField;
    }

    @Override
    public String screenName() {
        return "Login";
    }

    // ------------------------------------------------------------------ actions

    @Step("Enter username '{username}'")
    public LoginPage enterUsername(String username) {
        type(usernameField, username, "the username field");
        return this;
    }

    @Step("Enter the password")
    public LoginPage enterPassword(String password) {
        // Kept out of the log and the Allure step: a password in a report is a password leaked,
        // even a demo one — the habit is what matters.
        typeSecret(passwordField, password, "the password field");
        return this;
    }

    @Step("Submit the login form")
    public void submit() {
        click(loginButton, "the Login button");
    }

    /**
     * Logs in and lands on the catalog.
     *
     * <p>Returns the next page object, so a caller cannot keep driving a screen that is no
     * longer on the device — the compiler enforces the flow.</p>
     */
    @Step("Log in as {user.username}")
    public ProductListPage loginAs(User user) {
        enterUsername(user.username());
        enterPassword(user.password());
        submit();
        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();
        return catalog;
    }

    /**
     * Submits credentials that are expected to fail, and stays on this screen.
     *
     * <p>Separate from {@link #loginAs} on purpose: a method that returns the catalog would be
     * lying about where a failed login leaves you, and the test would then fail somewhere
     * other than where the problem is.</p>
     */
    @Step("Attempt login with '{username}'")
    public LoginPage loginExpectingFailure(String username, String password) {
        if (!username.isEmpty()) {
            enterUsername(username);
        }
        if (!password.isEmpty()) {
            enterPassword(password);
        }
        submit();
        return this;
    }

    // ------------------------------------------------------------------ queries

    /**
     * The error the app is currently showing, wherever it rendered it.
     *
     * <p>Field-level validation and server rejection use different elements, but a test asserting
     * "the right message was shown" should not have to know which — so this returns whichever
     * one appeared.</p>
     */
    public String errorMessage() {
        Duration shortWait = Duration.ofSeconds(5);
        if (isDisplayedWithin(genericErrorMessage, shortWait)) {
            return getText(genericErrorMessage);
        }
        if (isDisplayed(usernameErrorMessage)) {
            return getText(usernameErrorMessage);
        }
        if (isDisplayed(passwordErrorMessage)) {
            return getText(passwordErrorMessage);
        }
        return "";
    }

    public boolean hasError() {
        return !errorMessage().isBlank();
    }

    @Step("Open the navigation menu")
    public MenuPage openMenu() {
        click(menuButton, "the menu button");
        MenuPage menu = new MenuPage();
        menu.waitUntilLoaded();
        return menu;
    }
}

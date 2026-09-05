package com.adarsh.tests;

import com.adarsh.models.LoginScenario;
import com.adarsh.models.User;
import com.adarsh.pages.LoginPage;
import com.adarsh.pages.ProductListPage;
import com.adarsh.utils.JsonDataReader;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Epic("My Demo App")
@Feature("Authentication")
public class LoginTest extends ShopTest {

    /**
     * The negative cases come from {@code login-scenarios.json}, one test invocation per entry.
     *
     * <p>Data-driven rather than one method per case: the cases differ only in their inputs and
     * expected message, so five copies of the same method would be five places to update when
     * the copy changes. Adding a sixth case is now a data edit, not a code change.</p>
     */
    @DataProvider(name = "invalidCredentials")
    public static Object[][] invalidCredentials() {
        return JsonDataReader.toDataProvider("login-scenarios.json", LoginScenario.class);
    }

    @Test(groups = {"smoke"},
            description = "Valid credentials sign the user in and land on the product catalog")
    @Story("A registered user can sign in")
    @Severity(SeverityLevel.BLOCKER)
    @Description("The single most important path in the app: if this fails, nothing else matters.")
    public void validLoginLandsOnCatalog() {
        User user = standardUser();

        LoginPage login = openLoginScreen();
        ProductListPage catalog = login.loginAs(user);

        assertTrue(catalog.isLoaded(),
                "A valid login did not land on the product catalog");
        assertTrue(catalog.visibleProductCount() > 0,
                "The catalog loaded but rendered no products");
        assertTrue(catalog.openMenu().isLoggedIn(),
                "The session was not established - the menu still offers 'Log in'");
    }

    @Test(groups = {"smoke"},
            dataProvider = "invalidCredentials",
            description = "Invalid credentials are rejected with the correct message")
    @Story("Invalid credentials are rejected")
    @Severity(SeverityLevel.CRITICAL)
    public void invalidCredentialsShowExpectedError(LoginScenario scenario) {
        LOG.info("Scenario '{}': {}", scenario.id(), scenario.description());

        LoginPage login = openLoginScreen();
        login.loginExpectingFailure(scenario.username(), scenario.password());

        assertEquals(login.errorMessage(), scenario.expectedError(),
                "Wrong error message for scenario '" + scenario.id() + "'");

        // The real risk with a failed login is not a missing message — it is being let in
        // anyway. Asserting we are still on the login screen is what catches that.
        assertTrue(login.isLoaded(),
                "Scenario '" + scenario.id() + "' left the login screen despite failing");
    }

    @Test(groups = {"regression"},
            description = "A locked-out account is refused even with the correct password")
    @Story("Invalid credentials are rejected")
    @Severity(SeverityLevel.CRITICAL)
    public void lockedOutUserCannotSignIn() {
        User locked = lockedUser();

        LoginPage login = openLoginScreen();
        login.loginExpectingFailure(locked.username(), locked.password());

        assertEquals(login.errorMessage(), scenario("locked-out-user").expectedError(),
                "A locked-out account showed the wrong message");
        assertFalse(new ProductListPage().isLoaded(),
                "A locked-out account reached the catalog");
    }
}

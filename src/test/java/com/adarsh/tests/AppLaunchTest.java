package com.adarsh.tests;

import com.adarsh.models.Product;
import com.adarsh.pages.ProductListPage;
import com.adarsh.utils.JsonDataReader;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Epic("My Demo App")
@Feature("Application launch")
public class AppLaunchTest extends ShopTest {

    /**
     * The first thing worth knowing about a build: does it start, and does it start
     * <em>where it should</em>.
     *
     * <p>Asserting the catalog's actual contents rather than merely "a screen appeared" is what
     * makes this a real smoke test. An app that launches to an empty catalog is broken, and a
     * check for the screen marker alone would call that a pass.</p>
     */
    @Test(groups = {"smoke"},
            description = "App launches and lands on the product catalog with the full inventory")
    @Story("The app launches to the product catalog")
    @Severity(SeverityLevel.BLOCKER)
    public void appLaunchesOnProductCatalog() {
        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();

        assertTrue(catalog.isLoaded(),
                "The app did not land on the product catalog after launch");

        List<Product> expected = JsonDataReader.readList("products.json", Product.class);
        List<String> visible = catalog.visibleProductNames();

        assertEquals(catalog.visibleProductCount(), expected.size(),
                "The catalog rendered a different number of products than the fixture expects");

        SoftAssert soft = new SoftAssert();
        for (Product product : expected) {
            // Soft, so one missing product does not hide the other five.
            soft.assertTrue(visible.contains(product.name()),
                    "Catalog is missing '" + product.name() + "'. Visible: " + visible);
        }
        soft.assertAll();
    }

    /** A launch that starts signed out is part of "landed on the expected screen". */
    @Test(groups = {"smoke"},
            description = "A freshly installed app starts with an empty cart and no session")
    @Story("The app launches to the product catalog")
    @Severity(SeverityLevel.NORMAL)
    public void appLaunchesSignedOutWithEmptyCart() {
        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();

        assertEquals(catalog.cartBadgeCount(), 0,
                "A freshly launched app should show no cart badge");
        assertFalse(catalog.openMenu().isLoggedIn(),
                "A freshly launched app should not have a signed-in session");
    }
}

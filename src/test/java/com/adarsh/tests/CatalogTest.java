package com.adarsh.tests;

import com.adarsh.models.Product;
import com.adarsh.pages.ProductDetailsPage;
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
import static org.testng.Assert.assertTrue;

@Epic("My Demo App")
@Feature("Product catalog")
public class CatalogTest extends ShopTest {

    @Test(groups = {"regression"},
            description = "The catalog loads every product and can scroll to the last one")
    @Story("Browsing the catalog")
    @Severity(SeverityLevel.CRITICAL)
    public void catalogLoadsAllProductsAndScrollsToTheLast() {
        List<Product> expected = JsonDataReader.readList("products.json", Product.class);
        ProductListPage catalog = loginAsStandardUser();

        assertEquals(catalog.visibleProductCount(), expected.size(),
                "The catalog did not render the expected number of products");

        // The last fixture entry is below the fold on every phone-sized screen, so reaching it
        // exercises the scroll path rather than just reading what happened to be on screen.
        Product last = expected.get(expected.size() - 1);
        catalog.scrollToProduct(last.name());

        assertTrue(catalog.isProductVisible(last.name()),
                "Scrolling did not bring '" + last.name() + "' into view");
    }

    @Test(groups = {"regression"},
            description = "Product details match the expected title, price and description")
    @Story("Viewing a product")
    @Severity(SeverityLevel.CRITICAL)
    public void productDetailsMatchTestData() {
        Product expected = product("backpack");
        ProductListPage catalog = loginAsStandardUser();

        ProductDetailsPage details = catalog.openProduct(expected.name());

        SoftAssert soft = new SoftAssert();
        soft.assertEquals(details.title(), expected.name(), "Wrong product title");
        soft.assertEquals(details.description(), expected.description(),
                "Wrong product description");
        if (expected.hasPrice()) {
            soft.assertEquals(details.price(), expected.price(), 0.001,
                    "Wrong product price");
        } else {
            // The fixture declines to assert a backend-served price rather than asserting a
            // guess; see the _todo note in products.json.
            LOG.warn("No expected price in the fixture for '{}' - price assertion skipped",
                    expected.id());
        }
        soft.assertAll();
    }

    @Test(groups = {"mobile-native"},
            description = "The product image carousel responds to a swipe gesture")
    @Story("Viewing a product")
    @Severity(SeverityLevel.MINOR)
    public void productImageCarouselRespondsToSwipe() {
        Product expected = product("backpack");
        ProductDetailsPage details = loginAsStandardUser().openProduct(expected.name());

        int imagesBefore = details.imageCount();
        assertTrue(imagesBefore > 0, "The product screen rendered no images to swipe");

        details.swipeImageCarousel();

        // The carousel must still be intact and on the same product — a swipe that navigated
        // away or emptied the carousel is a failure, not a pass.
        assertEquals(details.title(), expected.name(),
                "Swiping the carousel navigated away from the product");
        assertTrue(details.imageCount() > 0,
                "The carousel lost its images after the swipe");
    }
}

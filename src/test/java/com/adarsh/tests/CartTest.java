package com.adarsh.tests;

import com.adarsh.models.Product;
import com.adarsh.pages.CartPage;
import com.adarsh.pages.ProductDetailsPage;
import com.adarsh.pages.ProductListPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Epic("My Demo App")
@Feature("Shopping cart")
public class CartTest extends ShopTest {

    private static final double PENNY = 0.001;

    @Test(groups = {"regression"},
            description = "Adding a product increments the badge and puts the right item in the cart")
    @Story("Adding products to the cart")
    @Severity(SeverityLevel.BLOCKER)
    public void addingToCartUpdatesBadgeAndCartContents() {
        Product expected = product("backpack");
        ProductListPage catalog = loginAsStandardUser();

        assertEquals(catalog.cartBadgeCount(), 0,
                "The cart was not empty at the start of the test");

        catalog.openProduct(expected.name()).addToCart();

        // The badge is the user-visible signal that the tap worked; the cart contents are the
        // truth behind it. Asserting only one of the two lets a real bug through.
        ProductListPage afterAdd = new ProductListPage();
        CartPage cart = afterAdd.openCart();

        SoftAssert soft = new SoftAssert();
        soft.assertEquals(cart.lineItemCount(), 1, "Wrong number of line items in the cart");
        soft.assertTrue(cart.contains(expected.name()),
                "The cart does not contain '" + expected.name() + "'. It has: "
                        + cart.productNames());
        soft.assertEquals(cart.quantityOf(expected.name()), 1, "Wrong quantity for the added item");
        if (expected.hasPrice()) {
            soft.assertEquals(cart.totalPrice(), expected.price(), PENNY,
                    "The cart total does not match the product price");
        }
        soft.assertAll();
    }

    @Test(groups = {"regression"},
            description = "Incrementing and decrementing quantity recalculates the cart total")
    @Story("Changing quantities")
    @Severity(SeverityLevel.CRITICAL)
    public void changingQuantityRecalculatesTheTotal() {
        Product expected = product("backpack");
        if (!expected.hasPrice()) {
            throw new org.testng.SkipException(
                    "No expected price in the fixture for '" + expected.id()
                            + "' - a total cannot be asserted. Fill in the price in "
                            + "products.json once confirmed against a real device.");
        }

        ProductDetailsPage details = loginAsStandardUser().openProduct(expected.name());
        details.addToCart();
        CartPage cart = details.openCart();

        assertEquals(cart.totalPrice(), expected.lineTotal(1), PENNY,
                "Wrong total for a single item");

        cart.increaseQuantity(expected.name());
        assertEquals(cart.quantityOf(expected.name()), 2, "Quantity did not increment");
        assertEquals(cart.totalPrice(), expected.lineTotal(2), PENNY,
                "The total did not recalculate after incrementing");

        cart.increaseQuantity(expected.name());
        assertEquals(cart.totalPrice(), expected.lineTotal(3), PENNY,
                "The total did not recalculate after a second increment");

        cart.decreaseQuantity(expected.name());
        assertEquals(cart.quantityOf(expected.name()), 2, "Quantity did not decrement");
        assertEquals(cart.totalPrice(), expected.lineTotal(2), PENNY,
                "The total did not recalculate after decrementing");
    }

    @Test(groups = {"regression"},
            description = "A cart holding two different products totals both line items")
    @Story("Adding products to the cart")
    @Severity(SeverityLevel.NORMAL)
    public void cartTotalsMultipleDistinctProducts() {
        Product first = product("backpack");
        Product second = product("bike-light");
        if (!first.hasPrice() || !second.hasPrice()) {
            throw new org.testng.SkipException(
                    "Fixture prices are needed to assert a multi-item total");
        }

        ProductListPage catalog = loginAsStandardUser();
        catalog.openProduct(first.name()).addToCart().goBack();
        catalog.openProduct(second.name()).addToCart();

        CartPage cart = new ProductListPage().openCart();

        assertEquals(cart.lineItemCount(), 2, "Both products should appear as separate lines");
        assertTrue(cart.contains(first.name()) && cart.contains(second.name()),
                "The cart is missing one of the added products: " + cart.productNames());
        assertEquals(cart.totalPrice(), first.price() + second.price(), PENNY,
                "The cart total is not the sum of both line items");
    }

    @Test(groups = {"regression"},
            description = "Removing the only item empties the cart")
    @Story("Removing products from the cart")
    @Severity(SeverityLevel.NORMAL)
    public void removingTheLastItemEmptiesTheCart() {
        Product expected = product("backpack");

        ProductDetailsPage details = loginAsStandardUser().openProduct(expected.name());
        details.addToCart();
        CartPage cart = details.openCart();

        assertEquals(cart.lineItemCount(), 1, "Setup failed - the item was not added");

        cart.removeItem(expected.name());

        assertTrue(cart.isEmpty(),
                "The cart still holds items after removing the only one: " + cart.productNames());
    }
}

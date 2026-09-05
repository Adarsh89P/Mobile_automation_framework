package com.adarsh.pages;

import com.adarsh.utils.WaitUtils;
import io.appium.java_client.AppiumBy;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Cart screen: line items, per-item quantity stepper, total, checkout.
 *
 * <p>Quantity changes are asynchronous — the row re-renders and the total recalculates after
 * the tap. Every mutator here therefore waits for the resulting state rather than returning
 * immediately, so a test can assert on the next line without a sleep and without a race.</p>
 */
public class CartPage extends BasePage {

    private static final By CART_ITEM = AppiumBy.accessibilityId("store item");
    private static final By ITEM_TITLE = AppiumBy.accessibilityId("store item text");
    private static final By ITEM_PRICE = AppiumBy.accessibilityId("product price");
    private static final By REMOVE_ITEM = AppiumBy.accessibilityId("remove item");
    private static final By PLUS_BUTTON = AppiumBy.accessibilityId("counter plus button");
    private static final By MINUS_BUTTON = AppiumBy.accessibilityId("counter minus button");
    private static final By COUNTER_AMOUNT = AppiumBy.accessibilityId("counter amount");

    @AndroidFindBy(accessibility = "cart screen")
    @iOSXCUITFindBy(accessibility = "cart screen")
    private WebElement cartScreen;

    @AndroidFindBy(accessibility = "store item")
    @iOSXCUITFindBy(accessibility = "store item")
    private List<WebElement> cartItems;

    @AndroidFindBy(accessibility = "total number")
    @iOSXCUITFindBy(accessibility = "total number")
    private WebElement totalItemCount;

    @AndroidFindBy(accessibility = "total price")
    @iOSXCUITFindBy(accessibility = "total price")
    private WebElement totalPrice;

    // TODO: confirm with Appium Inspector — expected to be "Proceed To Checkout button".
    @AndroidFindBy(accessibility = "Proceed To Checkout button")
    @iOSXCUITFindBy(accessibility = "Proceed To Checkout button")
    private WebElement checkoutButton;

    /** Shown instead of the item list when the cart is empty. */
    @AndroidFindBy(accessibility = "empty cart")
    @iOSXCUITFindBy(accessibility = "empty cart")
    private WebElement emptyCartMessage;

    @Override
    protected WebElement pageMarker() {
        return cartScreen;
    }

    @Override
    public String screenName() {
        return "Cart";
    }

    // ------------------------------------------------------------------ queries

    public boolean isEmpty() {
        return isDisplayed(emptyCartMessage) || cartItems.isEmpty();
    }

    public int lineItemCount() {
        return cartItems.size();
    }

    public List<String> productNames() {
        return cartItems.stream()
                .map(item -> item.findElement(ITEM_TITLE).getText().trim())
                .toList();
    }

    public boolean contains(String productName) {
        return productNames().contains(productName.trim());
    }

    /** Total unit count across all lines, as the app reports it. */
    public int totalItemCount() {
        String value = getText(totalItemCount).replaceAll("[^0-9]", "");
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    /** Order total as a number, for comparison against a computed expectation. */
    public double totalPrice() {
        String digits = getText(totalPrice).replaceAll("[^0-9.]", "");
        if (digits.isBlank()) {
            throw new IllegalStateException("No total price rendered on the cart screen");
        }
        return Double.parseDouble(digits);
    }

    public int quantityOf(String productName) {
        String value = row(productName).findElement(COUNTER_AMOUNT).getText().replaceAll("[^0-9]", "");
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    /** Unit price shown on the line, e.g. {@code $29.99}. */
    public String unitPriceOf(String productName) {
        return row(productName).findElement(ITEM_PRICE).getText().trim();
    }

    // ------------------------------------------------------------------ actions

    /**
     * Increments a line and waits for the new quantity to render.
     *
     * <p>Waiting for the value — rather than assuming the tap landed — is what lets the test
     * assert the recalculated total on the very next line.</p>
     */
    @Step("Increase the quantity of '{productName}'")
    public CartPage increaseQuantity(String productName) {
        int before = quantityOf(productName);
        click(row(productName).findElement(PLUS_BUTTON), "the increase button for " + productName);
        WaitUtils.waitUntil(() -> quantityOf(productName) == before + 1,
                "the quantity of '" + productName + "' to become " + (before + 1));
        return this;
    }

    @Step("Decrease the quantity of '{productName}'")
    public CartPage decreaseQuantity(String productName) {
        int before = quantityOf(productName);
        if (before <= 1) {
            throw new IllegalStateException(
                    "Cannot decrease '" + productName + "' below 1 - it is at " + before
                            + ". Use removeItem() to take it out of the cart.");
        }
        click(row(productName).findElement(MINUS_BUTTON), "the decrease button for " + productName);
        WaitUtils.waitUntil(() -> quantityOf(productName) == before - 1,
                "the quantity of '" + productName + "' to become " + (before - 1));
        return this;
    }

    @Step("Remove '{productName}' from the cart")
    public CartPage removeItem(String productName) {
        click(row(productName).findElement(REMOVE_ITEM), "the remove button for " + productName);
        WaitUtils.waitUntil(() -> !contains(productName),
                "'" + productName + "' to leave the cart");
        return this;
    }

    @Step("Proceed to checkout")
    public CheckoutPage proceedToCheckout() {
        scrollIntoView(checkoutButton, "the Proceed To Checkout button");
        click(checkoutButton, "the Proceed To Checkout button");
        CheckoutPage checkout = new CheckoutPage();
        checkout.waitUntilLoaded();
        return checkout;
    }

    // ------------------------------------------------------------------ internals

    private WebElement row(String productName) {
        WaitUtils.waitForAtLeast(CART_ITEM, 1, WaitUtils.defaultTimeout());
        return cartItems.stream()
                .filter(item -> item.findElement(ITEM_TITLE).getText().trim()
                        .equals(productName.trim()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "'" + productName + "' is not in the cart. Present: " + productNames()));
    }
}

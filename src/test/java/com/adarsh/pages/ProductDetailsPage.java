package com.adarsh.pages;

import com.adarsh.models.Product;
import com.adarsh.utils.GestureUtils;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Product details screen: title, price, description, quantity stepper, add-to-cart.
 *
 * <p>Getters return plain strings and numbers rather than {@link WebElement}s. A page object
 * that hands a WebElement back to a test leaks the locator layer into the test — the test then
 * knows about the DOM, and every markup change becomes a test change.</p>
 */
public class ProductDetailsPage extends BasePage {

    @AndroidFindBy(accessibility = "product screen")
    @iOSXCUITFindBy(accessibility = "product screen")
    private WebElement productScreen;

    @AndroidFindBy(accessibility = "product label")
    @iOSXCUITFindBy(accessibility = "product label")
    private WebElement productTitle;

    @AndroidFindBy(accessibility = "product price")
    @iOSXCUITFindBy(accessibility = "product price")
    private WebElement productPrice;

    // TODO: confirm with Appium Inspector. The description element's id could not be isolated
    //       from the minified bundle; the app does render the exact text asserted in
    //       testdata/products.json, which was extracted from that bundle.
    @AndroidFindBy(accessibility = "product description")
    @iOSXCUITFindBy(accessibility = "product description")
    private WebElement productDescription;

    @AndroidFindBy(accessibility = "counter plus button")
    @iOSXCUITFindBy(accessibility = "counter plus button")
    private WebElement increaseQuantityButton;

    @AndroidFindBy(accessibility = "counter minus button")
    @iOSXCUITFindBy(accessibility = "counter minus button")
    private WebElement decreaseQuantityButton;

    @AndroidFindBy(accessibility = "counter amount")
    @iOSXCUITFindBy(accessibility = "counter amount")
    private WebElement quantityAmount;

    // TODO: confirm with Appium Inspector — expected to be "Add To Cart button".
    @AndroidFindBy(accessibility = "Add To Cart button")
    @iOSXCUITFindBy(accessibility = "Add To Cart button")
    private WebElement addToCartButton;

    @AndroidFindBy(accessibility = "tab bar option cart")
    @iOSXCUITFindBy(accessibility = "tab bar option cart")
    private WebElement cartTab;

    @AndroidFindBy(accessibility = "navigation back button")
    @iOSXCUITFindBy(accessibility = "navigation back button")
    private WebElement backButton;

    /** The image carousel; several images per product, swipeable. */
    @AndroidFindBy(accessibility = "product image")
    @iOSXCUITFindBy(accessibility = "product image")
    private List<WebElement> productImages;

    @Override
    protected WebElement pageMarker() {
        return productScreen;
    }

    @Override
    public String screenName() {
        return "Product details";
    }

    // ------------------------------------------------------------------ queries

    public String title() {
        return getText(productTitle);
    }

    /** Price as rendered, e.g. {@code $29.99}. */
    public String priceText() {
        return getText(productPrice);
    }

    /** Price as a number, for arithmetic against fixture data. */
    public double price() {
        String digits = priceText().replaceAll("[^0-9.]", "");
        if (digits.isBlank()) {
            throw new IllegalStateException("No price rendered on the product screen");
        }
        return Double.parseDouble(digits);
    }

    public String description() {
        // The description sits below the fold on smaller screens.
        scrollIntoView(productDescription, "the product description");
        return getText(productDescription);
    }

    public int quantity() {
        String value = getText(quantityAmount).replaceAll("[^0-9]", "");
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    public int imageCount() {
        return productImages.size();
    }

    /** True when the screen matches the fixture. Price is skipped if the fixture has none. */
    public boolean matches(Product expected) {
        boolean sameTitle = title().equals(expected.name());
        boolean sameDescription = description().equals(expected.description());
        boolean samePrice = !expected.hasPrice() || price() == expected.price();
        return sameTitle && sameDescription && samePrice;
    }

    // ------------------------------------------------------------------ actions

    @Step("Increase the quantity to {target}")
    public ProductDetailsPage setQuantity(int target) {
        if (target < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1, was " + target);
        }
        while (quantity() < target) {
            click(increaseQuantityButton, "the increase-quantity button");
        }
        while (quantity() > target) {
            click(decreaseQuantityButton, "the decrease-quantity button");
        }
        return this;
    }

    @Step("Add the product to the cart")
    public ProductDetailsPage addToCart() {
        scrollIntoView(addToCartButton, "the Add To Cart button");
        click(addToCartButton, "the Add To Cart button");
        return this;
    }

    @Step("Swipe the product image carousel")
    public ProductDetailsPage swipeImageCarousel() {
        if (productImages.isEmpty()) {
            throw new IllegalStateException("The product screen rendered no images to swipe");
        }
        GestureUtils.swipeLeftOn(productImages.get(0));
        return this;
    }

    @Step("Open the cart")
    public CartPage openCart() {
        click(cartTab, "the cart tab");
        CartPage cart = new CartPage();
        cart.waitUntilLoaded();
        return cart;
    }

    @Step("Go back to the catalog")
    public ProductListPage goBack() {
        click(backButton, "the back button");
        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();
        return catalog;
    }
}

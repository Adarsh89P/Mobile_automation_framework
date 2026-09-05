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
 * Product catalog — the screen a successful login lands on.
 *
 * <p>Every row shares one accessibility id ({@code "store item"}), which is normal for a list:
 * the id identifies the <em>kind</em> of row, not a particular product. So rows are located as
 * a collection and matched on their visible title, which is also how a user finds a product.
 * Indexing into the list by position would couple the test to catalog ordering and break the
 * moment the sort default changes.</p>
 */
public class ProductListPage extends BasePage {

    /** Ids below were extracted from the app bundle and are exact. */
    private static final By STORE_ITEM = AppiumBy.accessibilityId("store item");
    private static final By STORE_ITEM_TITLE = AppiumBy.accessibilityId("store item text");
    private static final By STORE_ITEM_PRICE = AppiumBy.accessibilityId("store item price");

    @AndroidFindBy(accessibility = "products screen")
    @iOSXCUITFindBy(accessibility = "products screen")
    private WebElement productsScreen;

    @AndroidFindBy(accessibility = "store item")
    @iOSXCUITFindBy(accessibility = "store item")
    private List<WebElement> productRows;

    @AndroidFindBy(accessibility = "sort button")
    @iOSXCUITFindBy(accessibility = "sort button")
    private WebElement sortButton;

    @AndroidFindBy(accessibility = "tab bar option cart")
    @iOSXCUITFindBy(accessibility = "tab bar option cart")
    private WebElement cartTab;

    @AndroidFindBy(accessibility = "open menu")
    @iOSXCUITFindBy(accessibility = "open menu")
    private WebElement menuButton;

    @AndroidFindBy(accessibility = "cart badge")
    @iOSXCUITFindBy(accessibility = "cart badge")
    private WebElement cartBadge;

    @Override
    protected WebElement pageMarker() {
        return productsScreen;
    }

    @Override
    public String screenName() {
        return "Product catalog";
    }

    // ------------------------------------------------------------------ queries

    /** Number of rows currently rendered. */
    public int visibleProductCount() {
        WaitUtils.waitForAtLeast(STORE_ITEM, 1, WaitUtils.defaultTimeout());
        return productRows.size();
    }

    /** Titles of the rows currently rendered, in display order. */
    public List<String> visibleProductNames() {
        WaitUtils.waitForAtLeast(STORE_ITEM, 1, WaitUtils.defaultTimeout());
        return productRows.stream()
                .map(row -> row.findElement(STORE_ITEM_TITLE).getText().trim())
                .toList();
    }

    /**
     * Items in the cart according to the badge, or 0 when no badge is drawn.
     *
     * <p>An absent badge means an empty cart, which is a valid state, not a failure — so this
     * returns 0 rather than throwing.</p>
     */
    public int cartBadgeCount() {
        if (!isDisplayed(cartBadge)) {
            return 0;
        }
        String text = getText(cartBadge).replaceAll("[^0-9]", "");
        return text.isBlank() ? 0 : Integer.parseInt(text);
    }

    // ------------------------------------------------------------------ actions

    /**
     * Scrolls until the named product is on screen and returns its row.
     *
     * <p>Delegates to UiScrollable on Android, so the device does the scrolling and reports
     * honestly when it has reached the end of the list.</p>
     */
    @Step("Scroll to the product '{productName}'")
    public ProductListPage scrollToProduct(String productName) {
        scrollIntoView(productName);
        // Proves the row really is on screen, not merely present in the hierarchy.
        findRowByName(productName);
        return this;
    }

    /** Whether a product row is currently rendered, without scrolling to find it. */
    public boolean isProductVisible(String productName) {
        return visibleProductNames().contains(productName.trim());
    }

    @Step("Open the product '{productName}'")
    public ProductDetailsPage openProduct(String productName) {
        WebElement row = findRowByName(productName);
        click(row, "the product '" + productName + "'");
        ProductDetailsPage details = new ProductDetailsPage();
        details.waitUntilLoaded();
        return details;
    }

    /** Opens the last row in the catalog, scrolling there first. */
    @Step("Open the last product in the catalog")
    public ProductDetailsPage openLastProduct() {
        List<String> names = visibleProductNames();
        if (names.isEmpty()) {
            throw new IllegalStateException("The catalog rendered no products");
        }
        return openProduct(names.get(names.size() - 1));
    }

    @Step("Open the cart")
    public CartPage openCart() {
        click(cartTab, "the cart tab");
        CartPage cart = new CartPage();
        cart.waitUntilLoaded();
        return cart;
    }

    @Step("Open the navigation menu")
    public MenuPage openMenu() {
        click(menuButton, "the menu button");
        MenuPage menu = new MenuPage();
        menu.waitUntilLoaded();
        return menu;
    }

    @Step("Open the sort options")
    public ProductListPage openSortOptions() {
        click(sortButton, "the sort button");
        return this;
    }

    /** Waits until the badge reads a specific number — the cart updates asynchronously. */
    @Step("Wait for the cart badge to read {expected}")
    public ProductListPage waitForCartBadge(int expected) {
        WaitUtils.waitUntil(() -> cartBadgeCount() == expected,
                "the cart badge to read " + expected + " (currently " + cartBadgeCount() + ")");
        return this;
    }

    // ------------------------------------------------------------------ internals

    /**
     * Finds a row by its title.
     *
     * <p>Matching on the price element of the same row is what makes {@link #priceOf} safe:
     * the price is read from within the matched row, never from "the first price on screen".</p>
     */
    private WebElement findRowByName(String productName) {
        WaitUtils.waitForAtLeast(STORE_ITEM, 1, WaitUtils.defaultTimeout());
        return productRows.stream()
                .filter(row -> row.findElement(STORE_ITEM_TITLE).getText().trim()
                        .equals(productName.trim()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No product named '" + productName + "' in the catalog. Visible: "
                                + visibleProductNames()));
    }

    /** Price as shown on the catalog row, e.g. {@code $29.99}. */
    public String priceOf(String productName) {
        return findRowByName(productName).findElement(STORE_ITEM_PRICE).getText().trim();
    }
}

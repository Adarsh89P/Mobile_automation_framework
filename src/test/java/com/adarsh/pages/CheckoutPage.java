package com.adarsh.pages;

import com.adarsh.models.CheckoutProfile;
import com.adarsh.models.PaymentCard;
import com.adarsh.models.ShippingAddress;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import io.qameta.allure.Step;
import org.openqa.selenium.WebElement;

/**
 * The checkout flow: shipping address → payment → review → confirmation.
 *
 * <p>Modelled as one page class rather than four. The steps are a single wizard the user cannot
 * enter halfway, they share a header and a footer button, and there is no state a test could
 * meaningfully assert between them that this class does not expose. Four classes would mean
 * four constructors, four markers and a lot of ceremony for no isolation.</p>
 *
 * <p>Each step still exposes its own entry point, so a test can drive one step at a time, and
 * {@link #completeCheckout} composes them for the flows that only care about the outcome.</p>
 *
 * <p><b>Note on locators:</b> the address and payment field ids follow the app's
 * {@code "<Label> input field"} convention — verified as a scheme in the app bundle, but the
 * individual labels below could not be isolated from the minified bundle and carry TODOs.</p>
 */
public class CheckoutPage extends BasePage {

    // ---------------------------------------------------------------- shipping address

    @AndroidFindBy(accessibility = "address screen")
    @iOSXCUITFindBy(accessibility = "address screen")
    private WebElement addressScreen;

    // TODO: confirm every field id below with Appium Inspector against the running app.
    @AndroidFindBy(accessibility = "Full Name* input field")
    @iOSXCUITFindBy(accessibility = "Full Name* input field")
    private WebElement fullNameField;

    @AndroidFindBy(accessibility = "Address Line 1* input field")
    @iOSXCUITFindBy(accessibility = "Address Line 1* input field")
    private WebElement addressLine1Field;

    @AndroidFindBy(accessibility = "Address Line 2 input field")
    @iOSXCUITFindBy(accessibility = "Address Line 2 input field")
    private WebElement addressLine2Field;

    @AndroidFindBy(accessibility = "City* input field")
    @iOSXCUITFindBy(accessibility = "City* input field")
    private WebElement cityField;

    @AndroidFindBy(accessibility = "State/Region input field")
    @iOSXCUITFindBy(accessibility = "State/Region input field")
    private WebElement stateRegionField;

    @AndroidFindBy(accessibility = "Zip Code* input field")
    @iOSXCUITFindBy(accessibility = "Zip Code* input field")
    private WebElement zipCodeField;

    @AndroidFindBy(accessibility = "Country* input field")
    @iOSXCUITFindBy(accessibility = "Country* input field")
    private WebElement countryField;

    @AndroidFindBy(accessibility = "To Payment button")
    @iOSXCUITFindBy(accessibility = "To Payment button")
    private WebElement toPaymentButton;

    // ---------------------------------------------------------------- payment

    @AndroidFindBy(accessibility = "payment screen")
    @iOSXCUITFindBy(accessibility = "payment screen")
    private WebElement paymentScreen;

    @AndroidFindBy(accessibility = "Full Name* input field")
    @iOSXCUITFindBy(accessibility = "Full Name* input field")
    private WebElement nameOnCardField;

    @AndroidFindBy(accessibility = "Card Number* input field")
    @iOSXCUITFindBy(accessibility = "Card Number* input field")
    private WebElement cardNumberField;

    @AndroidFindBy(accessibility = "Expiration Date* input field")
    @iOSXCUITFindBy(accessibility = "Expiration Date* input field")
    private WebElement expiryDateField;

    @AndroidFindBy(accessibility = "Security Code* input field")
    @iOSXCUITFindBy(accessibility = "Security Code* input field")
    private WebElement securityCodeField;

    @AndroidFindBy(accessibility = "Billing address checkbox")
    @iOSXCUITFindBy(accessibility = "Billing address checkbox")
    private WebElement sameAsShippingCheckbox;

    @AndroidFindBy(accessibility = "Review Order button")
    @iOSXCUITFindBy(accessibility = "Review Order button")
    private WebElement reviewOrderButton;

    // ---------------------------------------------------------------- review & confirmation

    @AndroidFindBy(accessibility = "checkout review order screen")
    @iOSXCUITFindBy(accessibility = "checkout review order screen")
    private WebElement reviewScreen;

    @AndroidFindBy(accessibility = "delivery address")
    @iOSXCUITFindBy(accessibility = "delivery address")
    private WebElement deliveryAddressSummary;

    @AndroidFindBy(accessibility = "payment info")
    @iOSXCUITFindBy(accessibility = "payment info")
    private WebElement paymentInfoSummary;

    @AndroidFindBy(accessibility = "total price")
    @iOSXCUITFindBy(accessibility = "total price")
    private WebElement orderTotal;

    @AndroidFindBy(accessibility = "Place Order button")
    @iOSXCUITFindBy(accessibility = "Place Order button")
    private WebElement placeOrderButton;

    @AndroidFindBy(accessibility = "checkout complete screen")
    @iOSXCUITFindBy(accessibility = "checkout complete screen")
    private WebElement confirmationScreen;

    @AndroidFindBy(accessibility = "Continue Shopping button")
    @iOSXCUITFindBy(accessibility = "Continue Shopping button")
    private WebElement continueShoppingButton;

    @Override
    protected WebElement pageMarker() {
        return addressScreen;
    }

    @Override
    public String screenName() {
        return "Checkout";
    }

    // ------------------------------------------------------------------ step 1: address

    @Step("Fill in the shipping address")
    public CheckoutPage enterShippingAddress(ShippingAddress address) {
        type(fullNameField, address.fullName(), "the full name field");
        type(addressLine1Field, address.addressLine1(), "address line 1");
        if (address.addressLine2() != null && !address.addressLine2().isBlank()) {
            type(addressLine2Field, address.addressLine2(), "address line 2");
        }
        type(cityField, address.city(), "the city field");
        type(stateRegionField, address.stateRegion(), "the state/region field");
        type(zipCodeField, address.zipCode(), "the zip code field");
        type(countryField, address.country(), "the country field");
        return this;
    }

    @Step("Continue to payment")
    public CheckoutPage continueToPayment() {
        scrollIntoView(toPaymentButton, "the To Payment button");
        click(toPaymentButton, "the To Payment button");
        waitForStep(paymentScreen, "payment");
        return this;
    }

    // ------------------------------------------------------------------ step 2: payment

    @Step("Fill in the payment details")
    public CheckoutPage enterPaymentDetails(PaymentCard card) {
        type(nameOnCardField, card.nameOnCard(), "the name on card field");
        // The card number never reaches the log or the report, even though this one is a
        // published test value — the rule is about the habit, not this particular card.
        typeSecret(cardNumberField, card.cardNumber(), "the card number field");
        type(expiryDateField, card.expiryDate(), "the expiry date field");
        typeSecret(securityCodeField, card.securityCode(), "the security code field");
        return this;
    }

    @Step("Set 'billing address same as shipping' to {same}")
    public CheckoutPage useSameAddressForBilling(boolean same) {
        boolean currentlyTicked = sameAsShippingCheckbox.isSelected();
        if (currentlyTicked != same) {
            click(sameAsShippingCheckbox, "the billing address checkbox");
        }
        return this;
    }

    @Step("Continue to the order review")
    public CheckoutPage continueToReview() {
        scrollIntoView(reviewOrderButton, "the Review Order button");
        click(reviewOrderButton, "the Review Order button");
        waitForStep(reviewScreen, "review");
        return this;
    }

    // ------------------------------------------------------------------ step 3: review

    public String deliveryAddressSummary() {
        return getText(deliveryAddressSummary);
    }

    public String paymentSummary() {
        return getText(paymentInfoSummary);
    }

    public double orderTotal() {
        String digits = getText(orderTotal).replaceAll("[^0-9.]", "");
        if (digits.isBlank()) {
            throw new IllegalStateException("No order total rendered on the review screen");
        }
        return Double.parseDouble(digits);
    }

    @Step("Place the order")
    public CheckoutPage placeOrder() {
        scrollIntoView(placeOrderButton, "the Place Order button");
        click(placeOrderButton, "the Place Order button");
        waitForStep(confirmationScreen, "order confirmation");
        return this;
    }

    // ------------------------------------------------------------------ step 4: confirmation

    public boolean isOrderConfirmed() {
        return isDisplayed(confirmationScreen);
    }

    @Step("Return to the catalog from the confirmation screen")
    public ProductListPage continueShopping() {
        click(continueShoppingButton, "the Continue Shopping button");
        ProductListPage catalog = new ProductListPage();
        catalog.waitUntilLoaded();
        return catalog;
    }

    // ------------------------------------------------------------------ whole flow

    /**
     * Runs the entire wizard for tests whose subject is the outcome, not the individual steps.
     *
     * <p>Tests that assert on an intermediate screen call the step methods directly instead —
     * this exists to keep those tests from re-typing the same seven form fills.</p>
     */
    @Step("Complete checkout")
    public CheckoutPage completeCheckout(CheckoutProfile profile) {
        enterShippingAddress(profile.shippingAddress());
        continueToPayment();
        enterPaymentDetails(profile.paymentCard());
        useSameAddressForBilling(profile.useSameAddress());
        continueToReview();
        placeOrder();
        return this;
    }

    private void waitForStep(WebElement marker, String stepName) {
        try {
            com.adarsh.utils.WaitUtils.waitForVisible(marker);
        } catch (org.openqa.selenium.TimeoutException e) {
            throw new org.openqa.selenium.TimeoutException(
                    "The checkout '%s' step never appeared. Current activity: %s"
                            .formatted(stepName, currentActivity()), e);
        }
    }
}

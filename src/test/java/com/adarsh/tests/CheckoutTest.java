package com.adarsh.tests;

import com.adarsh.models.CheckoutProfile;
import com.adarsh.models.Product;
import com.adarsh.pages.CartPage;
import com.adarsh.pages.CheckoutPage;
import com.adarsh.pages.ProductDetailsPage;
import com.adarsh.utils.FakerUtils;
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
@Feature("Checkout")
public class CheckoutTest extends ShopTest {

    private static final double PENNY = 0.001;

    /**
     * The end-to-end revenue path: cart → address → payment → review → confirmation.
     *
     * <p>The customer name is generated per run. Re-running with a fixed name would keep
     * submitting the same order, and a duplicate-order bug — the kind that only appears on the
     * second attempt — would never be seen by a test that always looks identical to the backend.</p>
     */
    @Test(groups = {"regression"},
            description = "A full checkout ends on the order confirmation screen")
    @Story("Placing an order")
    @Severity(SeverityLevel.BLOCKER)
    public void completeCheckoutReachesOrderConfirmation() {
        Product expected = product("backpack");
        String customerName = FakerUtils.fullName();
        CheckoutProfile profile = checkoutProfile().withCustomerName(customerName);

        ProductDetailsPage details = loginAsStandardUser().openProduct(expected.name());
        details.addToCart();
        CartPage cart = details.openCart();

        assertTrue(cart.contains(expected.name()), "Setup failed - the cart is missing the product");
        double cartTotal = expected.hasPrice() ? cart.totalPrice() : 0;

        CheckoutPage checkout = cart.proceedToCheckout();
        checkout.enterShippingAddress(profile.shippingAddress());
        checkout.continueToPayment();
        checkout.enterPaymentDetails(profile.paymentCard());
        checkout.useSameAddressForBilling(profile.useSameAddress());
        checkout.continueToReview();

        // Asserting the review screen before placing the order is what turns a "did it finish"
        // check into one that catches data being dropped between wizard steps.
        SoftAssert soft = new SoftAssert();
        soft.assertTrue(checkout.deliveryAddressSummary().contains(customerName),
                "The review screen lost the customer name. Showed: "
                        + checkout.deliveryAddressSummary());
        soft.assertTrue(checkout.deliveryAddressSummary()
                        .contains(profile.shippingAddress().city()),
                "The review screen lost the shipping city");
        if (expected.hasPrice()) {
            soft.assertEquals(checkout.orderTotal(), cartTotal, PENNY,
                    "The order total changed between the cart and the review screen");
        }
        soft.assertAll();

        checkout.placeOrder();

        assertTrue(checkout.isOrderConfirmed(),
                "The order was placed but no confirmation screen appeared");
    }

    /** After an order, the cart must be empty — otherwise the next order double-charges. */
    @Test(groups = {"regression"},
            description = "Placing an order clears the cart")
    @Story("Placing an order")
    @Severity(SeverityLevel.CRITICAL)
    public void placingAnOrderClearsTheCart() {
        Product expected = product("backpack");
        CheckoutProfile profile = checkoutProfile().withCustomerName(FakerUtils.fullName());

        ProductDetailsPage details = loginAsStandardUser().openProduct(expected.name());
        details.addToCart();

        CheckoutPage checkout = details.openCart().proceedToCheckout();
        checkout.completeCheckout(profile);
        assertTrue(checkout.isOrderConfirmed(), "The order did not complete");

        CartPage cart = checkout.continueShopping().openCart();

        assertTrue(cart.isEmpty(),
                "The cart still holds items after a completed order: " + cart.productNames());
    }
}

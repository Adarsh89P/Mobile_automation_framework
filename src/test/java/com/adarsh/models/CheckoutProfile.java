package com.adarsh.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Everything the checkout flow needs, in one fixture object.
 *
 * @param shippingAddress address form values
 * @param paymentCard     payment form values
 * @param useSameAddress  whether the billing-address checkbox should be left ticked
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutProfile(
        ShippingAddress shippingAddress,
        PaymentCard paymentCard,
        boolean useSameAddress) {

    /** Applies one generated identity across both forms, so the order is internally consistent. */
    public CheckoutProfile withCustomerName(String fullName) {
        return new CheckoutProfile(
                shippingAddress.withFullName(fullName),
                paymentCard.withNameOnCard(fullName),
                useSameAddress);
    }
}

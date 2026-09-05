package com.adarsh.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Shipping details typed into the checkout address form.
 *
 * <p>Loaded from {@code testdata/checkout.json} as a template; the checkout test replaces the
 * name with a Faker-generated one so repeated runs do not create identical orders.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShippingAddress(
        String fullName,
        String addressLine1,
        String addressLine2,
        String city,
        String stateRegion,
        String zipCode,
        String country) {

    /** Returns a copy under a different name — records are immutable, so this is a new value. */
    public ShippingAddress withFullName(String newName) {
        return new ShippingAddress(
                newName, addressLine1, addressLine2, city, stateRegion, zipCode, country);
    }
}

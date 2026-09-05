package com.adarsh.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A catalog item from {@code testdata/products.json}, used as the expected value when
 * asserting product-detail and cart contents.
 *
 * <p>{@code price} is boxed deliberately: My Demo App serves prices from a backend, so a
 * fixture may legitimately decline to assert one. A null price means "do not assert",
 * which is honest, where {@code 0.0} would quietly assert something false.</p>
 *
 * @param id          lookup key used by tests
 * @param name        exact title shown in the catalog and on the details screen
 * @param price       expected unit price, or null to skip the price assertion
 * @param description exact description shown on the details screen
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Product(String id, String name, Double price, String description) {

    public boolean hasPrice() {
        return price != null;
    }

    /** Formatted the way the app renders it, for message-level comparisons. */
    public String formattedPrice() {
        return hasPrice() ? "$%.2f".formatted(price) : "";
    }

    /** Line total for a given quantity — the value the cart screen must recalculate to. */
    public double lineTotal(int quantity) {
        if (!hasPrice()) {
            throw new IllegalStateException(
                    "Product '" + id + "' has no price in the fixture, so no total can be expected");
        }
        return price * quantity;
    }
}

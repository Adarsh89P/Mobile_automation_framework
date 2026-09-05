package com.adarsh.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Card details typed into the checkout payment form.
 *
 * <p>This is a demo-app test card, and the number below is a well-known Visa test value that
 * no payment network will authorise. A real card number would never live in a fixture — it
 * would be read from the environment, like every other secret in this framework.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCard(
        String nameOnCard,
        String cardNumber,
        String expiryDate,
        String securityCode) {

    public PaymentCard withNameOnCard(String newName) {
        return new PaymentCard(newName, cardNumber, expiryDate, securityCode);
    }

    /** Masked form, safe to write into a log or an Allure step. */
    public String maskedNumber() {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("\s", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "**** **** **** " + digits.substring(digits.length() - 4);
    }
}

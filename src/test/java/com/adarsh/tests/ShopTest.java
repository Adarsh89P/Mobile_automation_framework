package com.adarsh.tests;

/**
 * Base for tests that drive Sauce Labs My Demo App — the business flows.
 *
 * <p>Exists so the app choice is declared once per suite area rather than repeated in every
 * class, and so a {@code <test>} block can still override it with an {@code app} parameter.</p>
 */
public abstract class ShopTest extends BaseTest {

    @Override
    protected String appProfileName() {
        return "mydemo";
    }
}

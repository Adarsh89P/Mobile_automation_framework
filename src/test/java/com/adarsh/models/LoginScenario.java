package com.adarsh.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One negative-login case from {@code testdata/login-scenarios.json}.
 *
 * <p>Feeds the data-driven invalid-credentials test. Keeping the expected message in the
 * fixture rather than the test body means a copy change in the app is a one-line data edit,
 * and the scenario list is readable by someone who does not read Java.</p>
 *
 * @param id            stable identifier, also used as the Allure test name suffix
 * @param description   why this case exists, in words
 * @param username      value typed into the username field ({@code ""} to leave it empty)
 * @param password      value typed into the password field ({@code ""} to leave it empty)
 * @param expectedError exact error text the app must display
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginScenario(
        String id,
        String description,
        String username,
        String password,
        String expectedError) {

    @Override
    public String toString() {
        return id;
    }
}

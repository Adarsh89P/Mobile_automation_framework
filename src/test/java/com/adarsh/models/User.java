package com.adarsh.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A login identity from {@code testdata/users.json}.
 *
 * <p>These are the demo app's own public accounts, not secrets — they are committed on
 * purpose so the suite runs after a clone. Anything genuinely sensitive is read from the
 * environment instead and never appears in a fixture.</p>
 *
 * @param id       key tests look the user up by, e.g. {@code standard}
 * @param username login name (an email address in My Demo App)
 * @param password login password
 * @param locked   {@code true} for an account the backend refuses on purpose
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record User(String id, String username, String password, boolean locked) {

    public String describe() {
        return id + " <" + username + ">";
    }
}

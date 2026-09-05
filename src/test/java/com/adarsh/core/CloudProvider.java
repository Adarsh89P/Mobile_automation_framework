package com.adarsh.core;

import java.util.Arrays;
import java.util.Locale;

/**
 * Device-cloud vendors supported by the {@link ExecutionTarget#CLOUD} branch.
 *
 * <p>Each constant carries only the hub host; the user and access key are read from
 * environment variables at session-creation time and are never stored in this repo.</p>
 */
public enum CloudProvider {

    BROWSERSTACK("browserstack", "hub.browserstack.com/wd/hub"),
    LAMBDATEST("lambdatest", "mobile-hub.lambdatest.com/wd/hub");

    private final String key;
    private final String hubHost;

    CloudProvider(String key, String hubHost) {
        this.key = key;
        this.hubHost = hubHost;
    }

    public String key() {
        return key;
    }

    public String hubHost() {
        return hubHost;
    }

    public static CloudProvider from(String value) {
        String normalised = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(p -> p.key.equals(normalised))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported cloud provider '" + value
                                + "'. Expected one of: browserstack, lambdatest"));
    }
}

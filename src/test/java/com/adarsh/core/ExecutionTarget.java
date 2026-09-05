package com.adarsh.core;

import java.util.Arrays;
import java.util.Locale;

/**
 * Where the Appium session is created, selected with {@code -Dexecution=local|grid|cloud}.
 *
 * <ul>
 *   <li>{@code LOCAL} — an Appium server on this machine, started by
 *       {@link AppiumServerManager} or reused if one is already listening.</li>
 *   <li>{@code GRID} — a Selenium Grid / Appium hub endpoint from {@code grid.url}.</li>
 *   <li>{@code CLOUD} — BrowserStack / LambdaTest; credentials come from env vars only.</li>
 * </ul>
 */
public enum ExecutionTarget {

    LOCAL("local"),
    GRID("grid"),
    CLOUD("cloud");

    private final String key;

    ExecutionTarget(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ExecutionTarget from(String value) {
        String normalised = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(t -> t.key.equals(normalised))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported execution target '" + value
                                + "'. Expected one of: local, grid, cloud"));
    }
}

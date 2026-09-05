package com.adarsh.core;

import java.util.Arrays;
import java.util.Locale;

/**
 * The mobile platforms the framework can target.
 *
 * <p>Selected with {@code -Dplatform=android|ios}; also decides which
 * {@code config/<platform>.properties} file Owner loads.</p>
 */
public enum PlatformType {

    ANDROID("android"),
    IOS("ios");

    private final String key;

    PlatformType(String key) {
        this.key = key;
    }

    /** Lower-case token used in {@code -Dplatform} and in resource file names. */
    public String key() {
        return key;
    }

    public boolean isAndroid() {
        return this == ANDROID;
    }

    public boolean isIos() {
        return this == IOS;
    }

    /**
     * @throws IllegalArgumentException on an unknown value — failing here is far cheaper
     *         than failing later with a confusing capability error from the Appium server.
     */
    public static PlatformType from(String value) {
        String normalised = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(p -> p.key.equals(normalised))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported platform '" + value + "'. Expected one of: android, ios"));
    }
}

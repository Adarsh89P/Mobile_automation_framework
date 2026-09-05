package com.adarsh.config;

import com.adarsh.core.ExecutionTarget;
import com.adarsh.core.PlatformType;
import org.aeonbits.owner.ConfigFactory;

import java.util.Properties;

/**
 * Single entry point to the framework's configuration.
 *
 * <p>Builds one immutable {@link FrameworkConfig} proxy for the JVM. The instance is shared
 * across threads deliberately — it is read-only, and every value that <em>must</em> differ
 * between parallel sessions lives in {@code DeviceConfig} instead, not here.</p>
 *
 * <p>{@code System.getProperties()} is imported so that {@code ${platform}} inside
 * {@code @Config.Sources} expands before Owner resolves the classpath file. Without the
 * import the placeholder would not resolve and every run would silently fall back to the
 * {@code @DefaultValue}s.</p>
 */
public final class ConfigReader {

    private static final String PLATFORM_PROPERTY = "platform";

    /** Holder idiom: the JVM guarantees this initialises exactly once, lazily, without locking. */
    private static final class Holder {
        private static final FrameworkConfig INSTANCE = build();

        private static FrameworkConfig build() {
            Properties overrides = new Properties();
            overrides.putAll(System.getProperties());
            // Guarantees ${platform} always expands, even when -Dplatform was not supplied.
            overrides.putIfAbsent(PLATFORM_PROPERTY, PlatformType.ANDROID.key());
            return ConfigFactory.create(FrameworkConfig.class, overrides);
        }
    }

    private ConfigReader() {
        throw new AssertionError("Utility class - not instantiable");
    }

    public static FrameworkConfig config() {
        return Holder.INSTANCE;
    }

    /** Parsed and validated once, so a bad {@code -Dplatform} fails before any device work. */
    public static PlatformType platform() {
        return PlatformType.from(config().platform());
    }

    public static ExecutionTarget executionTarget() {
        return ExecutionTarget.from(config().execution());
    }
}

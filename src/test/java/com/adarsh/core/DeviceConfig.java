package com.adarsh.core;

import com.adarsh.config.ConfigReader;
import com.adarsh.config.FrameworkConfig;

/**
 * The per-device slice of a session's capabilities.
 *
 * <p>Parallel execution runs one TestNG {@code <test>} block per device, and each block
 * supplies its own device name, udid and driver ports. Everything a second concurrent
 * session would otherwise collide on lives in this record, so it is passed explicitly to
 * {@link DriverFactory} rather than read from shared global config.</p>
 *
 * @param deviceName      capability {@code appium:deviceName}
 * @param platformVersion capability {@code appium:platformVersion}
 * @param udid            device/emulator id; blank lets Appium pick the only attached device
 * @param systemPort      Android — port for this thread's UiAutomator2 server; must be unique
 * @param wdaLocalPort    iOS — port for this thread's WebDriverAgent; must be unique
 */
public record DeviceConfig(
        String deviceName,
        String platformVersion,
        String udid,
        int systemPort,
        int wdaLocalPort) {

    public DeviceConfig {
        deviceName = deviceName == null ? "" : deviceName.trim();
        platformVersion = platformVersion == null ? "" : platformVersion.trim();
        udid = udid == null ? "" : udid.trim();
    }

    /** Falls back to the single-device values in the platform properties file. */
    public static DeviceConfig fromConfig() {
        FrameworkConfig config = ConfigReader.config();
        return new DeviceConfig(
                config.deviceName(),
                config.platformVersion(),
                config.udid(),
                config.systemPort(),
                config.wdaLocalPort());
    }

    public boolean hasUdid() {
        return !udid.isBlank();
    }

    public boolean hasPlatformVersion() {
        return !platformVersion.isBlank();
    }

    /** Short label used in logs and Allure attachments. */
    public String label() {
        return hasUdid() ? deviceName + " (" + udid + ")" : deviceName;
    }
}

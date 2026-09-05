package com.adarsh.core;

import com.adarsh.config.ConfigReader;
import com.adarsh.config.FrameworkConfig;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Starts and stops a local Appium 2 server for the run.
 *
 * <p>One server serves every parallel session — Appium multiplexes sessions itself, and each
 * Android session is isolated by its own {@code systemPort}. So this is a JVM-level singleton
 * guarded by a monitor, started once by whichever thread gets there first.</p>
 *
 * <p>With {@code appium.server.reuse=true} (or an already-listening server on the configured
 * URL) nothing is spawned and the existing endpoint is used. That keeps the local
 * edit-run-debug loop fast and lets CI point at a server started by a separate workflow step,
 * while the default of {@code false} makes a clean run fully self-contained.</p>
 */
public final class AppiumServerManager {

    private static final Logger LOG = LogManager.getLogger(AppiumServerManager.class);

    private static final Duration STATUS_PROBE_TIMEOUT = Duration.ofSeconds(3);

    private static final Object LOCK = new Object();

    /** Non-null only when this JVM actually spawned the server, i.e. only when we may stop it. */
    private static AppiumDriverLocalService managedService;

    /** The endpoint sessions should talk to, whether we started it or merely found it. */
    private static URL serverUrl;

    private AppiumServerManager() {
        throw new AssertionError("Utility class - not instantiable");
    }

    /**
     * Returns the local server endpoint, starting a server if one is needed and not present.
     * Safe to call from every thread; only the first call does any work.
     */
    public static URL startIfNeeded() {
        synchronized (LOCK) {
            if (serverUrl != null) {
                return serverUrl;
            }
            FrameworkConfig config = ConfigReader.config();
            URL configured = toUrl(config.appiumServerUrl());

            if (config.reuseAppiumServer()) {
                if (!isRunning(configured)) {
                    throw new IllegalStateException(
                            "appium.server.reuse=true but no Appium server responded at "
                                    + configured + ". Start one with: appium --port "
                                    + configured.getPort()
                                    + " - or set appium.server.reuse=false to let the framework "
                                    + "start its own.");
                }
                LOG.info("Reusing the Appium server already listening at {}", configured);
                serverUrl = configured;
                return serverUrl;
            }

            if (isRunning(configured)) {
                // Spawning a second server on a taken port would just fail; adopting the running
                // one is what an engineer with `appium` already open actually wants.
                LOG.info("An Appium server is already listening at {} - adopting it instead of "
                        + "starting a second one", configured);
                serverUrl = configured;
                return serverUrl;
            }

            managedService = buildService(config);
            managedService.start();
            serverUrl = managedService.getUrl();
            LOG.info("Started a managed Appium server at {} (log: {})",
                    serverUrl, config.appiumServerLogFile());
            Runtime.getRuntime().addShutdownHook(
                    new Thread(AppiumServerManager::stop, "appium-server-shutdown"));
            return serverUrl;
        }
    }

    /** Stops the server only if this JVM started it. An adopted server is left running. */
    public static void stop() {
        synchronized (LOCK) {
            if (managedService == null) {
                serverUrl = null;
                return;
            }
            try {
                if (managedService.isRunning()) {
                    managedService.stop();
                    LOG.info("Stopped the managed Appium server");
                }
            } catch (RuntimeException e) {
                LOG.warn("Ignoring error while stopping the Appium server: {}", e.getMessage());
            } finally {
                managedService = null;
                serverUrl = null;
            }
        }
    }

    public static boolean isManagedByThisJvm() {
        synchronized (LOCK) {
            return managedService != null;
        }
    }

    private static AppiumDriverLocalService buildService(FrameworkConfig config) {
        File logFile = new File(config.appiumServerLogFile());
        File logDir = logFile.getParentFile();
        if (logDir != null && !logDir.exists() && !logDir.mkdirs()) {
            LOG.warn("Could not create the Appium log directory {} - continuing anyway", logDir);
        }

        AppiumServiceBuilder builder = new AppiumServiceBuilder()
                .withIPAddress(config.appiumServerIp())
                .withArgument(GeneralServerFlag.SESSION_OVERRIDE)
                .withArgument(GeneralServerFlag.LOG_LEVEL, config.appiumServerLogLevel())
                .withTimeout(Duration.ofSeconds(config.appiumServerStartupTimeoutSeconds()))
                .withLogFile(logFile);

        // Port 0 means "any free port" — the right setting when several builds share a machine.
        if (config.appiumServerPort() > 0) {
            builder.usingPort(config.appiumServerPort());
        } else {
            builder.usingAnyFreePort();
        }

        String insecure = config.appiumAllowInsecure();
        if (insecure != null && !insecure.isBlank()) {
            // adb_shell is what lets the mobile-native suite read logcat and toggle radios.
            builder.withArgument(() -> "--allow-insecure", insecure);
        }

        // TODO: on machines where appium is not on PATH, export APPIUM_MAIN_JS (path to
        //       appium/index.js) and NODE_BINARY_PATH (path to the node executable).
        String appiumJs = System.getenv("APPIUM_MAIN_JS");
        if (appiumJs != null && !appiumJs.isBlank()) {
            builder.withAppiumJS(new File(appiumJs));
        }
        String nodeBinary = System.getenv("NODE_BINARY_PATH");
        if (nodeBinary != null && !nodeBinary.isBlank()) {
            builder.usingDriverExecutable(new File(nodeBinary));
        }

        AppiumDriverLocalService service = AppiumDriverLocalService.buildService(builder);
        // Server chatter goes to the log file only; stdout stays readable as a test log.
        service.clearOutPutStreams();
        return service;
    }

    /** Probes /status — the endpoint every healthy Appium 2 server answers. */
    private static boolean isRunning(URL url) {
        // HttpClient only became AutoCloseable in Java 21; on the 17 baseline it is left to GC.
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(STATUS_PROBE_TIMEOUT)
                .build();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(stripTrailingSlashes(url.toString()) + "/status"))
                    .timeout(STATUS_PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response =
                    client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static URL toUrl(String value) {
        try {
            return URI.create(value).toURL();
        } catch (IOException | IllegalArgumentException e) {
            throw new UncheckedIOException(
                    new IOException("Invalid appium.server.url: " + value, e));
        }
    }
}

package com.adarsh.config;

import org.aeonbits.owner.Config;

/**
 * Typed view over every tunable in the framework.
 *
 * <p><b>Resolution order (first match wins, {@link Config.LoadType#MERGE}):</b></p>
 * <ol>
 *   <li>{@code system:properties} — anything passed as {@code -Dkey=value}</li>
 *   <li>{@code classpath:config/${platform}.properties} — the platform defaults file</li>
 *   <li>{@code system:env} — environment variables, for CI-injected values</li>
 *   <li>{@code @DefaultValue} on the method — the last-resort fallback</li>
 * </ol>
 *
 * <p>MERGE (rather than Owner's default FIRST) is deliberate: FIRST would make the whole
 * properties file invisible as soon as a single {@code -D} flag was present. With MERGE the
 * override is per-key, which is what "system properties override file values" actually means.</p>
 *
 * <p>Secrets have no defaults and no entry in any committed properties file — they resolve
 * from the environment or not at all.</p>
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "classpath:config/${platform}.properties",
        "system:env"
})
public interface FrameworkConfig extends Config {

    // ---------------------------------------------------------------- run selection

    @Key("platform")
    @DefaultValue("android")
    String platform();

    @Key("execution")
    @DefaultValue("local")
    String execution();

    // ---------------------------------------------------------------- appium server

    /** Used verbatim for grid/cloud, and for local runs when the server is reused. */
    @Key("appium.server.url")
    @DefaultValue("http://127.0.0.1:4723")
    String appiumServerUrl();

    /** {@code true} attaches to an already-running server instead of spawning one. */
    @Key("appium.server.reuse")
    @DefaultValue("false")
    boolean reuseAppiumServer();

    @Key("appium.server.ip")
    @DefaultValue("127.0.0.1")
    String appiumServerIp();

    /** {@code 0} asks the builder for any free port — the safe choice under parallelism. */
    @Key("appium.server.port")
    @DefaultValue("4723")
    int appiumServerPort();

    @Key("appium.server.log.level")
    @DefaultValue("info")
    String appiumServerLogLevel();

    @Key("appium.server.log.file")
    @DefaultValue("target/logs/appium-server.log")
    String appiumServerLogFile();

    @Key("appium.server.startup.timeout.seconds")
    @DefaultValue("60")
    int appiumServerStartupTimeoutSeconds();

    /** Comma-separated Appium 2 insecure features, e.g. {@code adb_shell}. */
    @Key("appium.server.allow.insecure")
    @DefaultValue("adb_shell")
    String appiumAllowInsecure();

    // ---------------------------------------------------------------- device

    @Key("device.name")
    @DefaultValue("Android Emulator")
    String deviceName();

    @Key("platform.version")
    @DefaultValue("")
    String platformVersion();

    @Key("udid")
    @DefaultValue("")
    String udid();

    /** Android: per-thread UiAutomator2 server port. Overridden per {@code <test>} block. */
    @Key("system.port")
    @DefaultValue("8200")
    int systemPort();

    /** iOS: per-thread WebDriverAgent port. Overridden per {@code <test>} block. */
    @Key("wda.local.port")
    @DefaultValue("8100")
    int wdaLocalPort();

    // ---------------------------------------------------------------- application

    /**
     * Name of the default app profile, i.e. a file in {@code config/apps/}.
     *
     * <p>The binary, package and activity live in {@link AppProfile} rather than here, because
     * a suite can switch apps per {@code <test>} block while Owner resolves this interface once
     * per JVM.</p>
     */
    @Key("app")
    @DefaultValue("apidemos")
    String app();

    @Key("app.no.reset")
    @DefaultValue("false")
    boolean noReset();

    @Key("app.full.reset")
    @DefaultValue("false")
    boolean fullReset();

    @Key("auto.grant.permissions")
    @DefaultValue("true")
    boolean autoGrantPermissions();

    /** iOS counterpart to autoGrantPermissions for system alerts. */
    @Key("auto.accept.alerts")
    @DefaultValue("true")
    boolean autoAcceptAlerts();

    // ---------------------------------------------------------------- test data

    /** Classpath folder holding the JSON fixtures read by {@code JsonDataReader}. */
    @Key("testdata.dir")
    @DefaultValue("testdata")
    String testDataDir();

    /** Locale driving JavaFaker, so generated data matches the app under test. */
    @Key("faker.locale")
    @DefaultValue("en-US")
    String fakerLocale();

    /**
     * Fixes the Faker seed for a reproducible run. Blank (the default) means a fresh random
     * seed per run, which is what "unique data" needs; set it only when reproducing a failure.
     */
    @Key("faker.seed")
    @DefaultValue("")
    String fakerSeed();

    // ---------------------------------------------------------------- timeouts

    @Key("timeout.new.command.seconds")
    @DefaultValue("120")
    int newCommandTimeoutSeconds();

    @Key("timeout.adb.exec.ms")
    @DefaultValue("60000")
    int adbExecTimeoutMs();

    @Key("timeout.uiautomator2.install.ms")
    @DefaultValue("120000")
    int uiautomator2ServerInstallTimeoutMs();

    @Key("timeout.uiautomator2.launch.ms")
    @DefaultValue("60000")
    int uiautomator2ServerLaunchTimeoutMs();

    /** iOS: WebDriverAgent build/launch budget — a cold build is genuinely slow. */
    @Key("timeout.wda.launch.seconds")
    @DefaultValue("180")
    int wdaLaunchTimeoutSeconds();

    /** Default explicit-wait budget for BasePage and the PageFactory decorator. */
    @Key("timeout.explicit.seconds")
    @DefaultValue("20")
    int explicitTimeoutSeconds();

    @Key("timeout.polling.ms")
    @DefaultValue("300")
    int pollingIntervalMs();

    // ---------------------------------------------------------------- grid / cloud

    @Key("grid.url")
    @DefaultValue("http://127.0.0.1:4444/wd/hub")
    String gridUrl();

    @Key("cloud.provider")
    @DefaultValue("browserstack")
    String cloudProvider();

    @Key("cloud.project.name")
    @DefaultValue("mobile-automation-framework")
    String cloudProjectName();

    @Key("cloud.build.name")
    @DefaultValue("local-build")
    String cloudBuildName();

    /** BrowserStack/LambdaTest tunnel for hitting a private staging backend. */
    @Key("cloud.tunnel.enabled")
    @DefaultValue("false")
    boolean cloudTunnelEnabled();

    // ---------------------------------------------------------------- optional features

    /**
     * Master switch for the AI layer. Off by default, and off is the only state CI relies on:
     * nothing under {@code com.adarsh.ai} may change whether a test passes.
     */
    @Key("ai.enabled")
    @DefaultValue("false")
    boolean aiEnabled();

    @Key("ai.model")
    @DefaultValue("claude-opus-5")
    String aiModel();

    /** Effort level for the model. Triage and locator suggestion are small classification
     *  tasks, so the low end is the right default; raise it if verdicts look shallow. */
    @Key("ai.effort")
    @DefaultValue("low")
    String aiEffort();

    @Key("ai.max.tokens")
    @DefaultValue("2048")
    int aiMaxTokens();

    /**
     * Hard ceiling on a single AI call.
     *
     * <p>Deliberately short. This runs inside a test teardown, and a slow model call must never
     * be the reason a suite overruns its CI budget - a missing verdict is a far smaller loss
     * than a timed-out pipeline.</p>
     */
    @Key("ai.timeout.seconds")
    @DefaultValue("45")
    int aiTimeoutSeconds();

    @Key("ai.triage.enabled")
    @DefaultValue("true")
    boolean aiTriageEnabled();

    @Key("ai.healing.enabled")
    @DefaultValue("true")
    boolean aiHealingEnabled();

    /**
     * Whether a healed locator is actually retried, as opposed to only being written to the
     * healing report.
     *
     * <p>Default false, and that default is load-bearing. Retrying with a model-suggested
     * locator can turn a failing test green, which is precisely the influence over pass/fail
     * the AI layer is not allowed to have. Turn it on for an exploratory run when you want to
     * know whether the suggestion would have worked; leave it off in CI.</p>
     */
    @Key("ai.healing.apply")
    @DefaultValue("false")
    boolean aiHealingApply();

    @Key("ai.healing.report")
    @DefaultValue("target/healing-report.json")
    String aiHealingReport();

    /** Page source is truncated before being sent - a full mobile hierarchy is enormous. */
    @Key("ai.page.source.limit")
    @DefaultValue("12000")
    int aiPageSourceLimit();

    @Key("record.video.on.failure")
    @DefaultValue("false")
    boolean recordVideoOnFailure();

    @Key("retry.count")
    @DefaultValue("2")
    int retryCount();
}

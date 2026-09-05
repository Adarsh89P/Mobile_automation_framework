package com.adarsh.tests;

/**
 * Base for tests that drive ApiDemos — the device-capability suite.
 *
 * <p>Rotation, native dialogs and drag-and-drop are Android behaviours, not shop behaviours.
 * Running them against stock widgets means they cannot fail because a vendor redesigned a
 * screen, which is what makes them useful as a platform regression signal.</p>
 */
public abstract class DeviceTest extends BaseTest {

    /** Package of the app under test, for foreground assertions. */
    protected static final String API_DEMOS_PACKAGE = "io.appium.android.apis";

    @Override
    protected String appProfileName() {
        return "apidemos";
    }
}

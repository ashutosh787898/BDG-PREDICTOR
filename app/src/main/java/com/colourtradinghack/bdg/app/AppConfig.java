package com.colourtradinghack.bdg.app;

/** Settings chosen in Site2App. Edit a value and rebuild to change the app. */
final class AppConfig {
    private AppConfig() {}

    static final String START_URL = "https://bdg.colourtradinghack.com/";

    // Behaviour
    static final boolean ZOOM = false;
    static final boolean KEEP_SCREEN_ON = true;
    static final boolean FULLSCREEN = false;
    static final boolean PULL_TO_REFRESH = false;
    static final boolean FIREBASE = false;

    // Branding
    static final int BRAND_COLOR = 0xFFD5C034;
    static final boolean SPLASH_ENABLED = true;
    static final int SPLASH_COLOR = 0xFFFFFFFF;
    static final int SPLASH_MIN_MS = 1200;

    // Link handling: "internal" opens inside the app, "external" opens the browser or another app.
    // Rules run top to bottom and the first match wins. Types: domain, startswith, contains, regex.
    static final boolean SAME_DOMAIN_INTERNAL = true;
    static final String DEFAULT_LINK_ACTION = "internal";
    static final String[][] LINK_RULES = new String[][] {
        {"startswith", "allapi.in", "internal"},
        {"startswith", "wa.me", "external"},
        {"startswith", "t.me", "external"},
        {"contains", "youtu.be", "external"},
    };
}

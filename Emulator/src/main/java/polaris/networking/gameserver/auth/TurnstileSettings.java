package polaris.networking.gameserver.auth;

import polaris.Emulator;

/** Turnstile's settings, read without starting the verifier's HTTP client. */
final class TurnstileSettings {
    private TurnstileSettings() {}

    static boolean enabled() {
        return Emulator.getConfig() != null && Emulator.getConfig().getBoolean("login.turnstile.enabled", false);
    }

    /** The public site key the login page's widget needs; the secret key never leaves the server. */
    static String siteKey() {
        return Emulator.getConfig() == null ? "" : Emulator.getConfig().getValue("login.turnstile.sitekey", "");
    }
}

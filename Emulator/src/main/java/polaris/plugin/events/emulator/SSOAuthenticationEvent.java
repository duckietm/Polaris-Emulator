package polaris.plugin.events.emulator;

import polaris.plugin.Event;

public class SSOAuthenticationEvent extends Event {
    public final String sso;

    public SSOAuthenticationEvent(String sso) {
        this.sso = sso;
    }
}

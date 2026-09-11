package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official {@code class_2799} / {@code class_2694} (official header 2524, ours 9471):
 * {@code short target, string reason, int banExpirySeconds, string localizedReason}.
 * {@code HabboAlertDialogManager.handleBanInfoMessage} turns it into the ban alert built from
 * {@code login.banned.until} / {@code login.banned.reason}; a non-empty localized reason wins and
 * gets its {@code {expiryDate}} placeholder filled in. A negative expiry means "permanent".
 */
public class BanInfoComposer extends MessageComposer {
    private final int target;
    private final String reason;
    private final int banExpirySeconds;
    private final String localizedReason;

    public BanInfoComposer(int target, String reason, int banExpirySeconds, String localizedReason) {
        this.target = target;
        this.reason = reason == null ? "" : reason;
        this.banExpirySeconds = banExpirySeconds;
        this.localizedReason = localizedReason == null ? "" : localizedReason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BanInfoComposer);
        this.response.appendShort(this.target);
        this.response.appendString(this.reason);
        this.response.appendInt(this.banExpirySeconds);
        this.response.appendString(this.localizedReason);
        return this.response;
    }
}

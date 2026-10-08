package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.messages.incoming.MessageHandler;

/**
 * Every housekeeping handler extends this and starts its {@code handle()} with
 * {@code if (!this.allowed()) return;} (HousekeepingHandlerContractTest keeps it so). That one
 * call is the gate: panel access, the emergency lockdown and, when the handler names one, the
 * area permission; a refusal is answered here, so the client never waits for a timeout.
 * The packet reads stay in each handler's own handle(), where the packet contract reads them.
 */
public abstract class HousekeepingHandler extends MessageHandler {
    /** A permission this handler needs on top of panel access, or null for none. */
    protected String requiredPermission() {
        return null;
    }

    protected final boolean allowed() {
        return HousekeepingAccess.check(this.client, this.requiredPermission());
    }
}

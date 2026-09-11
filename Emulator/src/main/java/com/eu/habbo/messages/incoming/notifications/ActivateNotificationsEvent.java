package com.eu.habbo.messages.incoming.notifications;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.handshake.EnableNotificationsComposer;

/**
 * Official {@code HabboNotifications.activate()} (header 3235): the client says its notification
 * feed is up. The official client enables its own feed view before sending; the server answers
 * with the feed flag so a client that activates later than the login burst still gets it.
 */
public class ActivateNotificationsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() == null) {
            return;
        }

        this.client.sendResponse(
                new EnableNotificationsComposer(Emulator.getConfig().getBoolean("bubblealerts.enabled", true)));
    }
}

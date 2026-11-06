package polaris.messages.incoming.notifications;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.handshake.EnableNotificationsComposer;

/**
 * Official {@code HabboNotifications.activate()} (header 3235): the client says its notification
 * feed is up. The official client enables its own feed view before sending; the server answers
 * with the feed flag so a client that activates later than the login burst still gets it.
 */
public class ActivateNotificationsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser() == null) {
            return;
        }

        this.client.sendResponse(
                new EnableNotificationsComposer(Emulator.getConfig().getBoolean("bubblealerts.enabled", true)));
    }
}

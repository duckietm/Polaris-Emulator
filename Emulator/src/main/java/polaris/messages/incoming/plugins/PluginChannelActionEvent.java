package polaris.messages.incoming.plugins;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.PluginChannel;
import polaris.plugin.events.users.UserPluginChannelEvent;

/**
 * A client panel answering its plugin on a plugin channel. A message with a bad channel name or an oversized payload
 * is dropped; anything else becomes a {@link UserPluginChannelEvent} for the plugins.
 */
public class PluginChannelActionEvent extends MessageHandler {
    // Panels send on clicks; this keeps a script from flooding the plugins with events.
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        String channel = this.packet.readString();
        String payload = this.packet.readString();

        if (this.client.getUser() == null
                || !PluginChannel.isValidChannel(channel)
                || !PluginChannel.isValidPayload(payload)) {
            return;
        }

        Emulator.getPluginManager().fireEvent(new UserPluginChannelEvent(this.client.getUser(), channel, payload));
    }
}

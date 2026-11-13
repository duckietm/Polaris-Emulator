package polaris.messages.outgoing.plugins;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * A message a plugin sends to its own client panel: a channel name and a payload, usually JSON. The client carries it
 * to whatever listens on the channel. Plugins send it through {@link polaris.plugin.PluginChannel#send}, which checks
 * the channel name and the payload size first.
 */
public class PluginChannelMessageComposer extends MessageComposer {
    private final String channel;
    private final String payload;

    public PluginChannelMessageComposer(String channel, String payload) {
        this.channel = channel;
        this.payload = payload;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PluginChannelMessageComposer);
        this.response.appendString(this.channel);
        this.response.appendString(this.payload);
        return this.response;
    }
}

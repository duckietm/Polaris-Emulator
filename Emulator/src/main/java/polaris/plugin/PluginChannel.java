package polaris.plugin;

import java.util.regex.Pattern;
import polaris.hotel.users.User;
import polaris.messages.outgoing.plugins.PluginChannelMessageComposer;

/**
 * The generic plugin channel: a plugin and its client panel exchange JSON on a channel of their own. Send with
 * {@link #send}; listen for what the panel sends back with {@link polaris.plugin.events.users.UserPluginChannelEvent}.
 * Name channels after the plugin ({@code rp.job}, {@code casino.table}) so two plugins never share one.
 */
public final class PluginChannel {
    /** Lower case letters, digits, dots, dashes and underscores; starts with a letter or digit; 1 to 64 characters. */
    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    /** The longest payload either side may send, in characters. */
    public static final int MAX_PAYLOAD = 16_384;

    private PluginChannel() {}

    public static boolean isValidChannel(String channel) {
        return channel != null && CHANNEL.matcher(channel).matches();
    }

    public static boolean isValidPayload(String payload) {
        return payload != null && payload.length() <= MAX_PAYLOAD;
    }

    /**
     * Sends {@code payload} to {@code user}'s client on {@code channel}. Returns false, sending nothing, when the user is
     * offline or the channel or payload is not valid.
     */
    public static boolean send(User user, String channel, String payload) {
        if (user == null || user.getClient() == null || !isValidChannel(channel) || !isValidPayload(payload)) {
            return false;
        }

        user.getClient().sendResponse(new PluginChannelMessageComposer(channel, payload));
        return true;
    }
}

package polaris.plugin.events.users;

import polaris.hotel.users.User;

/**
 * A client panel sent something on a plugin channel. The emulator does nothing with it: the plugin that owns
 * {@link #channel} reads {@link #payload} (usually JSON) and answers with {@link polaris.plugin.PluginChannel#send}.
 * Channel and payload are already checked for name and size; their content is the plugin's to validate.
 */
public class UserPluginChannelEvent extends UserEvent {
    public final String channel;
    public final String payload;

    public UserPluginChannelEvent(User user, String channel, String payload) {
        super(user);
        this.channel = channel;
        this.payload = payload;
    }
}

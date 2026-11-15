package polaris.hotel.messenger;

import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.messages.ServerMessage;

/**
 * A chat channel shown in the console as a friend with a negative id, such as Staff Chat. Whatever a player writes
 * to it is handed to {@link #onMessageReceived}, which decides who reads it; it is never stored as a conversation,
 * takes no friend slot and takes plain text only. A plugin gives a player its own channel with
 * {@link Messenger#addChannel(User, ChannelBuddy)}.
 */
public abstract class ChannelBuddy extends MessengerBuddy {
    protected ChannelBuddy(int id, String name, String look, int userOne) {
        super(id, name, look, (short) 0, userOne);

        if (id >= 0) {
            throw new IllegalArgumentException("A channel id is negative: " + id);
        }

        this.setOnline(true);
    }

    /** Called with every line a player sends to this channel; it delivers the line to whoever should read it. */
    @Override
    public abstract void onMessageReceived(User from, String message);

    @Override
    public void serialize(ServerMessage message) {
        message.appendInt(this.getId());
        message.appendString(this.getUsername());
        message.appendInt(this.getGender().equals(UserGender.M) ? 0 : 1);
        message.appendBoolean(true); // online
        message.appendBoolean(false); // not in room
        message.appendString(this.getLook());
        message.appendInt(0); // category
        message.appendString(""); // motto
        message.appendString(""); // last seen
        message.appendString(""); // realname
        message.appendBoolean(true); // offline messaging supported
        message.appendBoolean(false);
        message.appendBoolean(false);
        message.appendShort(0); // relation
    }
}

package polaris.hotel.messenger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.messenger.history.MessengerHistoryServices;
import polaris.hotel.modtool.WordFilter;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.friends.FriendChatMessageComposer;

public class MessengerBuddy implements Runnable, ISerialize {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessengerBuddy.class);

    private int id;
    private String username;
    private UserGender gender = UserGender.M;
    private int online = 0;
    private String look = "";
    private String motto = "";
    private short relation;
    private int categoryId = 0;
    private boolean inRoom;
    private int userOne = 0;

    public MessengerBuddy(ResultSet set) {
        try {
            this.id = set.getInt("id");
            this.username = set.getString("username");
            this.gender = UserGender.valueOf(set.getString("gender"));
            this.online = set.getInt("online");
            this.motto = set.getString("motto");
            this.look = set.getString("look");
            this.relation = (short) set.getInt("relation");
            this.categoryId = set.getInt("category");
            this.userOne = set.getInt("user_one_id");
            this.inRoom = false;
            if (this.online == 1) {
                User user = Emulator.getGameServer().getGameClientManager().getUser(this.username);

                if (user != null) {
                    this.inRoom = user.getUserInfo().getCurrentRoom() != null;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public MessengerBuddy(ResultSet set, boolean value) {
        try {
            this.id = set.getInt("id");
            this.username = set.getString("username");
            this.look = set.getString("look");
            this.relation = 0;
            this.userOne = 0;
            this.online = set.getInt("online");
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public MessengerBuddy(int id, String username, String look, Short relation, int userOne) {
        this.id = id;
        this.username = username;
        this.gender = UserGender.M;
        this.online = 0;
        this.motto = "";
        this.look = look;
        this.relation = relation;
        this.userOne = userOne;
    }

    public MessengerBuddy(User user, int userOne) {
        this.id = user.getUserInfo().getId();
        this.username = user.getUserInfo().getUsername();
        this.gender = user.getUserInfo().getGender();
        this.online = user.getUserInfo().isOnline() ? 1 : 0;
        this.motto = user.getUserInfo().getMotto();
        this.look = user.getUserInfo().getLook();
        this.relation = 0;
        this.userOne = userOne;
        this.inRoom = user.getUserInfo().getCurrentRoom() != null;
    }

    public int getId() {
        return this.id;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UserGender getGender() {
        return this.gender;
    }

    public void setGender(UserGender gender) {
        this.gender = gender;
    }

    public int getOnline() {
        User user = Emulator.getGameServer().getGameClientManager().getUser(this.id);

        return user != null && user.getUserInfo().isOnline() ? 1 : this.online;
    }

    public void setOnline(boolean value) {
        this.online = (value ? 1 : 0);
    }

    public boolean isVisibleOnline() {
        User user = Emulator.getGameServer().getGameClientManager().getUser(this.id);

        return this.getOnline() == 1 && (user == null || !user.getUserStats().hideOnline);
    }

    public String getLook() {
        return this.look;
    }

    public void setLook(String look) {
        this.look = look;
    }

    public String getMotto() {
        return this.motto;
    }

    public short getRelation() {
        return this.relation;
    }

    public void setRelation(int relation) {
        this.relation = (short) relation;
        Emulator.getThreading().run(this);
    }

    public int getCategoryId() {
        return this.categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public boolean inRoom() {
        User user = Emulator.getGameServer().getGameClientManager().getUser(this.id);

        if (user != null) {
            return user.getUserInfo().getCurrentRoom() != null;
        }

        return this.inRoom;
    }

    public void inRoom(boolean value) {
        this.inRoom = value;
    }

    public boolean isVisibleInRoom() {
        return this.isVisibleOnline() && this.inRoom();
    }

    @Override
    public void run() {

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE messenger_friendships SET relation = ? WHERE user_one_id = ? AND user_two_id = ?")) {
            statement.setInt(1, this.relation);
            statement.setInt(2, this.userOne);
            statement.setInt(3, this.id);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void onMessageReceived(User from, String message) {
        this.onMessageReceivedWithDeliveryStatus(from, message);
    }

    public boolean onMessageReceivedWithDeliveryStatus(User from, String message) {
        User user = Emulator.getGameServer().getGameClientManager().getUser(this.id);

        Message chatMessage = new Message(from.getUserInfo().getId(), this.id, message);

        if (WordFilter.ENABLED_FRIENDCHAT) {
            chatMessage.setMessage(
                    Emulator.getGameEnvironment().getWordFilter().filter(chatMessage.getMessage(), from));
        }

        // The console text goes into the conversation history too, as the messenger packet stores it: text that only
        // reached chatlogs_private disappeared from the history once the conversation held a Habbicon, and its read
        // receipts never worked. A failure here must not stop the delivery.
        try {
            MessengerHistoryServices.create()
                    .sendMessage(0, from.getUserInfo().getId(), this.id, 0, chatMessage.getMessage(), null);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not store console message history", exception);
        }

        Emulator.getThreading().run(chatMessage);

        if (user == null
                || !user.isOnline()
                || user.getClient() == null
                || !user.getClient().getChannel().isOpen()) {
            return this.storeOfflineMessage(chatMessage);
        }

        user.getClient().sendResponse(new FriendChatMessageComposer(chatMessage));
        return !this.isVisibleOnline();
    }

    private boolean storeOfflineMessage(Message message) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO messenger_offline (user_id, user_from_id, message, sended_on) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, message.getToId());
            statement.setInt(2, message.getFromId());
            statement.setString(3, message.getMessage());
            statement.setInt(4, message.getTimestamp());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Could not store offline messenger message", e);
            return false;
        }
    }

    @Override
    public void serialize(ServerMessage message) {
        boolean visibleOnline = this.isVisibleOnline();
        boolean visibleInRoom = this.isVisibleInRoom();

        message.appendInt(this.id);
        message.appendString(this.username);
        message.appendInt(this.gender.equals(UserGender.M) ? 0 : 1);
        message.appendBoolean(visibleOnline);
        message.appendBoolean(visibleOnline && visibleInRoom); // IN ROOM
        message.appendString(this.look);
        message.appendInt(this.categoryId); // Friends category ID
        message.appendString(this.motto);
        message.appendString(""); // Last seen as DATETIMESTRING
        message.appendString(""); // Realname or Facebookame as String
        message.appendBoolean(true); // Offline messaging.
        message.appendBoolean(false);
        message.appendBoolean(false);
        message.appendShort(this.relation);
    }
}

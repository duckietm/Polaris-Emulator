package polaris.hotel.messenger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.friends.FriendChatMessageComposer;
import polaris.messages.outgoing.friends.UpdateFriendComposer;
import polaris.plugin.events.users.friends.UserAcceptFriendRequestEvent;

public class Messenger {

    private static final Logger LOGGER = LoggerFactory.getLogger(Messenger.class);

    // Configuration. Loaded from database & updated accordingly.
    public static volatile boolean SAVE_PRIVATE_CHATS = false;
    public static volatile int MAXIMUM_FRIENDS = 200;
    public static volatile int MAXIMUM_FRIENDS_HC = 500;

    private final ConcurrentHashMap<Integer, MessengerBuddy> friends;
    private final Set<FriendRequest> friendRequests;

    public Messenger() {
        this.friends = new ConcurrentHashMap<>();
        this.friendRequests = new HashSet<>();
    }

    public static void unfriend(int userOne, int userTwo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM messenger_friendships WHERE (user_one_id = ? AND user_two_id = ?) OR (user_one_id = ? AND user_two_id = ?)")) {
            statement.setInt(1, userOne);
            statement.setInt(2, userTwo);
            statement.setInt(3, userTwo);
            statement.setInt(4, userOne);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static Set<MessengerBuddy> searchUsers(String username) {
        Set<MessengerBuddy> users = new HashSet<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM users WHERE username LIKE ? ORDER BY username ASC LIMIT "
                                + Emulator.getConfig().getInt("hotel.messenger.search.maxresults"))) {
            statement.setString(1, polaris.util.SqlLikeEscaper.escape(username) + "%");
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    users.add(new MessengerBuddy(set, false));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return users;
    }

    /**
     * @deprecated
     * This method is no longer used and is only kept to avoid breaking any plugins
     */
    @Deprecated
    public static boolean canFriendRequest(User requester, String friend) {
        User user = Emulator.getGameEnvironment().getUserManager().getUser(friend);
        UserInfo friendInfo;

        if (user != null) {
            friendInfo = user.getUserInfo();
        } else {
            friendInfo = UserManager.getOfflineUserInfo(friend);
        }

        if (friendInfo == null) {
            return false;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM messenger_friendrequests WHERE (user_to_id = ? AND user_from_id = ?) OR (user_to_id = ? AND user_from_id = ?) LIMIT 1")) {
            statement.setInt(1, requester.getUserInfo().getId());
            statement.setInt(2, friendInfo.getId());
            statement.setInt(3, friendInfo.getId());
            statement.setInt(4, requester.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }

    /** True when {@code userFrom} has a pending friend request to {@code userTo}; note the argument order. */
    public static boolean friendRequested(int userTo, int userFrom) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM messenger_friendrequests WHERE user_to_id = ? AND user_from_id = ? LIMIT 1")) {
            statement.setInt(1, userTo);
            statement.setInt(2, userFrom);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }

    public static void makeFriendRequest(int userFrom, int userTo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT IGNORE INTO messenger_friendrequests (user_to_id, user_from_id) VALUES (?, ?)")) {
            statement.setInt(1, userTo);
            statement.setInt(2, userFrom);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static int getFriendCount(int userId) {
        User user = Emulator.getGameServer().getGameClientManager().getUser(userId);

        if (user != null) return user.getMessenger().getFriends().size();

        int count = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(id) as count FROM messenger_friendships WHERE user_one_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) count = set.getInt("count");
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return count;
    }

    public static Map<Integer, Set<MessengerBuddy>> getFriends(int userId) {
        Map<Integer, Set<MessengerBuddy>> map = new HashMap<>();
        map.put(1, new HashSet<>());
        map.put(2, new HashSet<>());
        map.put(3, new HashSet<>());

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT users.id, users.look, users.username, messenger_friendships.relation FROM messenger_friendships INNER JOIN users ON users.id = messenger_friendships.user_two_id WHERE user_one_id = ? ORDER BY RAND() LIMIT 50")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (set.getInt("relation") == 0) continue;

                    MessengerBuddy buddy = new MessengerBuddy(
                            set.getInt("id"),
                            set.getString("username"),
                            set.getString("look"),
                            (short) set.getInt("relation"),
                            userId);
                    map.get((int) buddy.getRelation()).add(buddy);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return map;
    }

    public static void checkFriendSizeProgress(User user) {
        int progress = user.getUserStats()
                .getAchievementProgress(
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("FriendListSize"));

        int toProgress = 1;

        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("FriendListSize");

        if (achievement == null) return;

        if (progress > 0) {
            toProgress = user.getMessenger().getFriends().size() - progress;

            if (toProgress < 0) {
                return;
            }
        }

        AchievementManager.progressAchievement(user, achievement, toProgress);
    }

    public void loadFriends(User user) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + "users.id, "
                                + "users.username, "
                                + "users.gender, "
                                + "users.online, "
                                + "users.look, "
                                + "users.motto, "
                                + "messenger_friendships.* FROM messenger_friendships INNER JOIN users ON messenger_friendships.user_two_id = users.id WHERE user_one_id = ?")) {
            statement.setInt(1, user.getUserInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    MessengerBuddy buddy = new MessengerBuddy(set);

                    if (buddy.getId() == user.getUserInfo().getId()) {
                        continue;
                    }

                    this.friends.putIfAbsent(set.getInt("id"), buddy);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        if (user.hasPermission(StaffChatBuddy.PERMISSION_KEY)) {
            this.friends.putIfAbsent(
                    StaffChatBuddy.BUDDY_ID,
                    new StaffChatBuddy(user.getUserInfo().getId()));
        }
    }

    public MessengerBuddy loadFriend(User user, int userId) {
        MessengerBuddy buddy = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + "users.id, "
                                + "users.username, "
                                + "users.gender, "
                                + "users.online, "
                                + "users.look, "
                                + "users.motto, "
                                + "messenger_friendships.* FROM messenger_friendships INNER JOIN users ON messenger_friendships.user_two_id = users.id WHERE user_one_id = ? AND user_two_id = ? LIMIT 1")) {
            statement.setInt(1, user.getUserInfo().getId());
            statement.setInt(2, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    buddy = new MessengerBuddy(set);
                    this.friends.putIfAbsent(set.getInt("id"), buddy);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return buddy;
    }

    /**
     * The pending requests as the database has them now. The set was only filled at login, so answered requests came
     * back and new ones were missing whenever the client asked for the list again.
     */
    public void reloadFriendRequests(User user) {
        synchronized (this.friendRequests) {
            this.friendRequests.clear();
            this.loadFriendRequests(user);
        }
    }

    /** Friends that count toward the friend limit: the Staff Chat entry (id -1) takes no slot. */
    public int countedFriends() {
        int staffChat = this.friends.containsKey(StaffChatBuddy.BUDDY_ID) ? 1 : 0;

        return this.friends.size() - staffChat;
    }

    public void loadFriendRequests(User user) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT users.id, users.username, users.look FROM messenger_friendrequests INNER JOIN users ON user_from_id = users.id WHERE user_to_id = ?")) {
            statement.setInt(1, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.friendRequests.add(new FriendRequest(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void removeBuddy(int id) {
        this.friends.remove(id);
    }

    public void removeBuddy(User user) {
        this.friends.remove(user.getUserInfo().getId());
    }

    public void addBuddy(MessengerBuddy buddy) {
        this.friends.put(buddy.getId(), buddy);
    }

    public Set<MessengerBuddy> getFriends(String username) {
        Set<MessengerBuddy> users = new HashSet<>();

        for (Map.Entry<Integer, MessengerBuddy> map : this.friends.entrySet()) {
            if (Strings.CI.contains(map.getValue().getUsername(), username)) {
                users.add(map.getValue());
            }
        }

        return users;
    }

    public void connectionChanged(User owner, boolean online, boolean inRoom) {
        if (owner != null) {
            boolean visibleOnline = online && !owner.getUserStats().hideOnline;
            boolean visibleInRoom = visibleOnline && inRoom;

            for (Map.Entry<Integer, MessengerBuddy> map : this.getFriends().entrySet()) {
                if (map.getValue().getOnline() == 0) continue;

                User user = Emulator.getGameServer().getGameClientManager().getUser(map.getKey());

                if (user != null) {
                    if (user.getMessenger() != null) {
                        MessengerBuddy buddy = user.getMessenger()
                                .getFriend(owner.getUserInfo().getId());

                        if (buddy != null) {
                            buddy.setOnline(visibleOnline);
                            buddy.inRoom(visibleInRoom);
                            buddy.setLook(owner.getUserInfo().getLook());
                            buddy.setGender(owner.getUserInfo().getGender());
                            buddy.setUsername(owner.getUserInfo().getUsername());
                            user.getClient().sendResponse(new UpdateFriendComposer(user, buddy, 0));
                        }
                    }
                }
            }
        }
    }

    public void deleteAllFriendRequests(int userTo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM messenger_friendrequests WHERE user_to_id = ?")) {
            statement.setInt(1, userTo);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int deleteFriendRequests(int userFrom, int userTo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM messenger_friendrequests WHERE (user_to_id = ? AND user_from_id = ?) OR (user_to_id = ? AND user_from_id = ?)")) {
            statement.setInt(1, userTo);
            statement.setInt(2, userFrom);
            statement.setInt(4, userTo);
            statement.setInt(3, userFrom);
            return statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return 0;
    }

    // TODO Needs redesign. userFrom is redundant.
    /**
     * userTo accepts the request userFrom sent. Only that incoming request counts: deleting in both directions let a
     * user "accept" their own outgoing request and become friends with someone who never agreed. A reverse request
     * (both asked each other) goes too, once the incoming one is found.
     */
    public void acceptFriendRequest(int userFrom, int userTo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement incoming = connection.prepareStatement(
                    "DELETE FROM messenger_friendrequests WHERE user_to_id = ? AND user_from_id = ?")) {
                incoming.setInt(1, userTo);
                incoming.setInt(2, userFrom);

                if (incoming.executeUpdate() <= 0) return;
            }

            try (PreparedStatement reverse = connection.prepareStatement(
                    "DELETE FROM messenger_friendrequests WHERE user_to_id = ? AND user_from_id = ?")) {
                reverse.setInt(1, userFrom);
                reverse.setInt(2, userTo);
                reverse.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT IGNORE INTO messenger_friendships (user_one_id, user_two_id, friends_since) VALUES (?, ?, ?)")) {
                statement.setInt(1, userFrom);
                statement.setInt(2, userTo);
                statement.setInt(3, Emulator.getIntUnixTimestamp());
                statement.execute();

                statement.setInt(1, userTo);
                statement.setInt(2, userFrom);
                statement.setInt(3, Emulator.getIntUnixTimestamp());
                statement.execute();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return;
        }

        User recipient = Emulator.getGameServer().getGameClientManager().getUser(userTo);
        User sender = Emulator.getGameServer().getGameClientManager().getUser(userFrom);

        if (recipient != null && sender != null) {
            MessengerBuddy to =
                    new MessengerBuddy(sender, recipient.getUserInfo().getId());
            MessengerBuddy from =
                    new MessengerBuddy(recipient, sender.getUserInfo().getId());

            recipient.getMessenger().friends.putIfAbsent(sender.getUserInfo().getId(), to);
            sender.getMessenger().friends.putIfAbsent(recipient.getUserInfo().getId(), from);

            if (Emulator.getPluginManager()
                    .fireEvent(new UserAcceptFriendRequestEvent(recipient, from))
                    .isCancelled()) {
                // Undo the friendship on both sides (this removed the accepting user's own id before).
                recipient.getMessenger().removeBuddy(userFrom);
                sender.getMessenger().removeBuddy(userTo);
                unfriend(userFrom, userTo);
                return;
            }

            recipient.getClient().sendResponse(new UpdateFriendComposer(recipient, to, 1));
            sender.getClient().sendResponse(new UpdateFriendComposer(sender, from, 1));
        } else if (recipient != null) {
            recipient
                    .getClient()
                    .sendResponse(new UpdateFriendComposer(recipient, this.loadFriend(recipient, userFrom), 1));
        } else if (sender != null) {
            sender.getClient().sendResponse(new UpdateFriendComposer(sender, this.loadFriend(sender, userTo), 1));
        }
    }

    public ConcurrentHashMap<Integer, MessengerBuddy> getFriends() {
        return this.friends;
    }

    public Set<FriendRequest> getFriendRequests() {
        synchronized (this.friendRequests) {
            return this.friendRequests;
        }
    }

    public FriendRequest findFriendRequest(String username) {
        synchronized (this.friendRequests) {
            for (FriendRequest friendRequest : this.friendRequests) {
                if (friendRequest.getUsername().equalsIgnoreCase(username)) {
                    return friendRequest;
                }
            }
        }

        return null;
    }

    public MessengerBuddy getFriend(int id) {
        return this.friends.get(id);
    }

    public void deliverOfflineMessages(User recipient) {
        List<Integer> messageIds = new ArrayList<>();
        List<Message> messages = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, user_from_id, message, sended_on FROM messenger_offline WHERE user_id = ? ORDER BY id ASC LIMIT 500")) {
            statement.setInt(1, recipient.getUserInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    messageIds.add(set.getInt("id"));
                    messages.add(new Message(
                            set.getInt("user_from_id"),
                            recipient.getUserInfo().getId(),
                            set.getString("message"),
                            set.getInt("sended_on")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not load offline messenger messages", e);
            return;
        }

        if (messageIds.isEmpty()) {
            return;
        }

        List<Integer> deliveredIds = new ArrayList<>();

        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);

            if (this.friends.containsKey(message.getFromId())) {
                recipient
                        .getClient()
                        .sendResponse(new FriendChatMessageComposer(
                                message, message.getFromId(), message.getFromId(), "offline"));
            }

            deliveredIds.add(messageIds.get(index));
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM messenger_offline WHERE id = ? AND user_id = ?")) {
            for (int messageId : deliveredIds) {
                statement.setInt(1, messageId);
                statement.setInt(2, recipient.getUserInfo().getId());
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Could not delete delivered offline messenger messages", e);
        }
    }

    public void dispose() {
        synchronized (this.friends) {
            this.friends.clear();
        }

        synchronized (this.friendRequests) {
            this.friendRequests.clear();
        }
    }
}

package com.eu.habbo.habbohotel.messenger;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.friends.UpdateFriendComposer;
import com.eu.habbo.plugin.events.users.friends.UserAcceptFriendRequestEvent;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Messenger {

    private static final Logger LOGGER = LoggerFactory.getLogger(Messenger.class);

    //Configuration. Loaded from database & updated accordingly.
    public static boolean SAVE_PRIVATE_CHATS = false;
    public static int MAXIMUM_FRIENDS = 200;
    public static int MAXIMUM_FRIENDS_HC = 500;

    private final ConcurrentHashMap<Integer, MessengerBuddy> friends;
    private final THashSet<FriendRequest> friendRequests;

    public Messenger() {
        this.friends = new ConcurrentHashMap<>();
        this.friendRequests = new THashSet<>();
    }

    public static void unfriend(int userOne, int userTwo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM messenger_friendships WHERE (user_one_id = ? AND user_two_id = ?) OR (user_one_id = ? AND user_two_id = ?)")) {
            statement.setInt(1, userOne);
            statement.setInt(2, userTwo);
            statement.setInt(3, userTwo);
            statement.setInt(4, userOne);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static THashSet<MessengerBuddy> searchUsers(String username) {
        THashSet<MessengerBuddy> users = new THashSet<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE username LIKE ? ORDER BY username ASC LIMIT " + Emulator.getConfig().getInt("hotel.messenger.search.maxresults"))) {
            statement.setString(1, com.eu.habbo.util.SqlLikeEscaper.escape(username) + "%");
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
    public static boolean canFriendRequest(Habbo habbo, String friend) {
        Habbo user = Emulator.getGameEnvironment().getHabboManager().getHabbo(friend);
        HabboInfo habboInfo;

        if (user != null) {
            habboInfo = user.getHabboInfo();
        } else {
            habboInfo = HabboManager.getOfflineHabboInfo(friend);
        }

        if (habboInfo == null) {
            return false;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM messenger_friendrequests WHERE (user_to_id = ? AND user_from_id = ?) OR (user_to_id = ? AND user_from_id = ?) LIMIT 1")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setInt(2, habboInfo.getId());
            statement.setInt(3, habboInfo.getId());
            statement.setInt(4, habbo.getHabboInfo().getId());
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

    public static boolean friendRequested(int userFrom, int userTo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM messenger_friendrequests WHERE user_to_id = ? AND user_from_id = ? LIMIT 1")) {
            statement.setInt(1, userFrom);
            statement.setInt(2, userTo);

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
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO messenger_friendrequests (user_to_id, user_from_id) VALUES (?, ?)")) {
            statement.setInt(1, userTo);
            statement.setInt(2, userFrom);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static int getFriendCount(int userId) {
        Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(userId);

        if (habbo != null)
            return habbo.getMessenger().getFriends().size();

        int count = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT count(id) as count FROM messenger_friendships WHERE user_one_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next())
                    count = set.getInt("count");
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return count;
    }

    public static THashMap<Integer, THashSet<MessengerBuddy>> getFriends(int userId) {
        THashMap<Integer, THashSet<MessengerBuddy>> map = new THashMap<>();
        map.put(1, new THashSet<>());
        map.put(2, new THashSet<>());
        map.put(3, new THashSet<>());

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT users.id, users.look, users.username, messenger_friendships.relation FROM messenger_friendships INNER JOIN users ON users.id = messenger_friendships.user_two_id WHERE user_one_id = ? ORDER BY RAND() LIMIT 50")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (set.getInt("relation") == 0)
                        continue;

                    MessengerBuddy buddy = new MessengerBuddy(set.getInt("id"), set.getString("username"), set.getString("look"), (short) set.getInt("relation"), userId);
                    map.get((int) buddy.getRelation()).add(buddy);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return map;
    }

    public static void checkFriendSizeProgress(Habbo habbo) {
        int progress = habbo.getHabboStats().getAchievementProgress(Emulator.getGameEnvironment().getAchievementManager().getAchievement("FriendListSize"));

        int toProgress = 1;

        Achievement achievement = Emulator.getGameEnvironment().getAchievementManager().getAchievement("FriendListSize");

        if (achievement == null)
            return;

        if (progress > 0) {
            toProgress = habbo.getMessenger().getFriends().size() - progress;

            if (toProgress < 0) {
                return;
            }
        }

        AchievementManager.progressAchievement(habbo, achievement, toProgress);
    }

    public void loadFriends(Habbo habbo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " +
                     "users.id, " +
                     "users.username, " +
                     "users.gender, " +
                     "users.online, " +
                     "users.look, " +
                     "users.motto, " +
                     "messenger_friendships.* FROM messenger_friendships INNER JOIN users ON messenger_friendships.user_two_id = users.id WHERE user_one_id = ?")) {
            statement.setInt(1, habbo.getHabboInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    MessengerBuddy buddy = new MessengerBuddy(set);

                    if (buddy.getId() == habbo.getHabboInfo().getId()) {
                        continue;
                    }

                    this.friends.putIfAbsent(set.getInt("id"), buddy);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        if (habbo.hasPermission(StaffChatBuddy.PERMISSION_KEY)) {
            this.friends.putIfAbsent(StaffChatBuddy.BUDDY_ID, new StaffChatBuddy(habbo.getHabboInfo().getId()));
        }
    }

    public MessengerBuddy loadFriend(Habbo habbo, int userId) {
        MessengerBuddy buddy = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT " +
                "users.id, " +
                "users.username, " +
                "users.gender, " +
                "users.online, " +
                "users.look, " +
                "users.motto, " +
                "messenger_friendships.* FROM messenger_friendships INNER JOIN users ON messenger_friendships.user_two_id = users.id WHERE user_one_id = ? AND user_two_id = ? LIMIT 1")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
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

    public void loadFriendRequests(Habbo habbo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT users.id, users.username, users.look FROM messenger_friendrequests INNER JOIN users ON user_from_id = users.id WHERE user_to_id = ?")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
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

    public void removeBuddy(Habbo habbo) {
        this.friends.remove(habbo.getHabboInfo().getId());
    }

    public void addBuddy(MessengerBuddy buddy) {
        this.friends.put(buddy.getId(), buddy);
    }

    public THashSet<MessengerBuddy> getFriends(String username) {
        THashSet<MessengerBuddy> users = new THashSet<>();

        for (Map.Entry<Integer, MessengerBuddy> map : this.friends.entrySet()) {
            if (StringUtils.containsIgnoreCase(map.getValue().getUsername(), username)) {
                users.add(map.getValue());
            }
        }

        return users;
    }

    public void connectionChanged(Habbo owner, boolean online, boolean inRoom) {
        if (owner != null) {
            for (Map.Entry<Integer, MessengerBuddy> map : this.getFriends().entrySet()) {
                if (map.getValue().getOnline() == 0)
                    continue;

                Habbo habbo = Emulator.getGameServer().getGameClientManager().getHabbo(map.getKey());

                if (habbo != null) {
                    if (habbo.getMessenger() != null) {
                        MessengerBuddy buddy = habbo.getMessenger().getFriend(owner.getHabboInfo().getId());

                        if (buddy != null) {
                            buddy.setOnline(online);
                            buddy.inRoom(inRoom);
                            buddy.setLook(owner.getHabboInfo().getLook());
                            buddy.setGender(owner.getHabboInfo().getGender());
                            buddy.setUsername(owner.getHabboInfo().getUsername());
                            habbo.getClient().sendResponse(new UpdateFriendComposer(habbo, buddy, 0));
                        }
                    }
                }
            }
        }
    }

    public void deleteAllFriendRequests(int userTo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM messenger_friendrequests WHERE user_to_id = ?")) {
            statement.setInt(1, userTo);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int deleteFriendRequests(int userFrom, int userTo) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM messenger_friendrequests WHERE (user_to_id = ? AND user_from_id = ?) OR (user_to_id = ? AND user_from_id = ?)")) {
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

    //TODO Needs redesign. userFrom is redundant.
    public void acceptFriendRequest(int userFrom, int userTo) {
        int count = this.deleteFriendRequests(userFrom, userTo);

        if (count > 0) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO messenger_friendships (user_one_id, user_two_id, friends_since) VALUES (?, ?, ?)")) {
                statement.setInt(1, userFrom);
                statement.setInt(2, userTo);
                statement.setInt(3, Emulator.getIntUnixTimestamp());
                statement.execute();

                statement.setInt(1, userTo);
                statement.setInt(2, userFrom);
                statement.setInt(3, Emulator.getIntUnixTimestamp());
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            Habbo habboTo = Emulator.getGameServer().getGameClientManager().getHabbo(userTo);
            Habbo habboFrom = Emulator.getGameServer().getGameClientManager().getHabbo(userFrom);

            if (habboTo != null && habboFrom != null) {
                MessengerBuddy to = new MessengerBuddy(habboFrom, habboTo.getHabboInfo().getId());
                MessengerBuddy from = new MessengerBuddy(habboTo, habboFrom.getHabboInfo().getId());


                habboTo.getMessenger().friends.putIfAbsent(habboFrom.getHabboInfo().getId(), to);
                habboFrom.getMessenger().friends.putIfAbsent(habboTo.getHabboInfo().getId(), from);

                if (Emulator.getPluginManager().fireEvent(new UserAcceptFriendRequestEvent(habboTo, from)).isCancelled()) {
                    this.removeBuddy(userTo);
                    return;
                }

                habboTo.getClient().sendResponse(new UpdateFriendComposer(habboTo, to, 1));
                habboFrom.getClient().sendResponse(new UpdateFriendComposer(habboFrom, from, 1));
            } else if (habboTo != null) {
                habboTo.getClient().sendResponse(new UpdateFriendComposer(habboTo, this.loadFriend(habboTo, userFrom), 1));
            } else if (habboFrom != null) {
                habboFrom.getClient().sendResponse(new UpdateFriendComposer(habboFrom, this.loadFriend(habboFrom, userTo), 1));
            }
        }
    }

    public ConcurrentHashMap<Integer, MessengerBuddy> getFriends() {
        return this.friends;
    }

    public THashSet<FriendRequest> getFriendRequests() {
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

    public void dispose() {
        synchronized (this.friends) {
            this.friends.clear();
        }

        synchronized (this.friendRequests) {
            this.friendRequests.clear();
        }
    }
}

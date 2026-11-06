package polaris.messages.incoming.friends;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.permissions.RankLimits;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.FriendRequestComposer;
import polaris.messages.outgoing.friends.FriendRequestErrorComposer;
import polaris.plugin.events.users.friends.UserRequestFriendshipEvent;

public class FriendRequestEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendRequestEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String username = FriendInputGuard.normalizeUsername(this.packet.readString());

        if (this.client == null || !FriendInputGuard.isValidUsername(username)) return;

        // TargetHabbo can be null if the Habbo is not online or when the Habbo doesn't exist
        User targetUser = Emulator.getGameServer().getGameClientManager().getUser(username);

        // If the Habbo is null, we try to get the Habbo from the database.
        // If the Habbo is still null, the Habbo doesn't exist.
        if (targetUser == null) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                // Building a Habbo loads the whole account (inventory, friends, requests): the cheap refusals are
                // checked on a small row first, so a repeated request to an offline user no longer loads it each time.
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT users.id, users_settings.block_friendrequests FROM users INNER JOIN users_settings ON users.id = users_settings.user_id WHERE username = ? LIMIT 1")) {
                    statement.setString(1, username);
                    try (ResultSet set = statement.executeQuery()) {
                        if (!set.next()) {
                            this.client.sendResponse(
                                    new FriendRequestErrorComposer(FriendRequestErrorComposer.TARGET_NOT_FOUND));
                            return;
                        }
                        if (this.refusedBeforeLoading(set.getInt("id"), set.getBoolean("block_friendrequests"))) {
                            return;
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT users.*, users_settings.block_friendrequests FROM users INNER JOIN users_settings ON users.id = users_settings.user_id WHERE username = ? LIMIT 1")) {
                    statement.setString(1, username);
                    try (ResultSet set = statement.executeQuery()) {
                        while (set.next()) {
                            targetUser = new User(set);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                return;
            }
        }

        if (targetUser == null) {
            this.client.sendResponse(new FriendRequestErrorComposer(FriendRequestErrorComposer.TARGET_NOT_FOUND));
            return;
        }

        int targetId = targetUser.getUserInfo().getId();
        boolean targetBlocksFriendRequests = targetUser.getUserStats().blockFriendRequests;

        // Making friends with yourself would be very pathetic, we try to avoid that
        if (targetId == this.client.getUser().getUserInfo().getId()) return;

        if (this.client.getUser().getMessenger().getFriends().containsKey(targetId)) return;

        if (Messenger.friendRequested(
                        targetId, this.client.getUser().getUserInfo().getId())
                || Messenger.friendRequested(this.client.getUser().getUserInfo().getId(), targetId)) return;

        // Target Habbo exists
        // Check if Habbo is accepting friend requests
        if (targetBlocksFriendRequests) {
            this.client.sendResponse(
                    new FriendRequestErrorComposer(FriendRequestErrorComposer.TARGET_NOT_ACCEPTING_REQUESTS));
            return;
        }

        // You can only have x friends
        if (this.client.getUser().getMessenger().countedFriends() >= RankLimits.friends(this.client.getUser())
                && !this.client.getUser().hasPermission("acc_infinite_friends")) {
            this.client.sendResponse(new FriendRequestErrorComposer(FriendRequestErrorComposer.FRIEND_LIST_OWN_FULL));
            return;
        }

        // Check if targets friendlist is full
        if (targetUser.getMessenger().countedFriends() >= RankLimits.friends(targetUser)
                && !targetUser.hasPermission("acc_infinite_friends")) {
            this.client.sendResponse(
                    new FriendRequestErrorComposer(FriendRequestErrorComposer.FRIEND_LIST_TARGET_FULL));
            return;
        }

        // Allow plugins to cancel the request
        if (Emulator.getPluginManager()
                .fireEvent(new UserRequestFriendshipEvent(this.client.getUser(), username, targetUser))
                .isCancelled()) {
            this.client.sendResponse(new FriendRequestErrorComposer(2));
            return;
        }

        // A user read from the database with a stale online flag has no client.
        if (targetUser.isOnline() && targetUser.getClient() != null) {
            targetUser.getClient().sendResponse(new FriendRequestComposer(this.client.getUser()));
        }

        Messenger.makeFriendRequest(this.client.getUser().getUserInfo().getId(), targetId);
        polaris.hotel.quests.QuestProgressEvents.progress(
                this.client.getUser(), polaris.hotel.quests.QuestGoalType.REQUEST_FRIEND, 1);
    }

    /** The refusals that need only the target's id and setting: themselves, a friend, a pending request, blocked. */
    private boolean refusedBeforeLoading(int targetId, boolean targetBlocksFriendRequests) {
        int userId = this.client.getUser().getUserInfo().getId();
        if (targetId == userId) return true;
        if (this.client.getUser().getMessenger().getFriends().containsKey(targetId)) return true;
        if (Messenger.friendRequested(targetId, userId) || Messenger.friendRequested(userId, targetId)) return true;

        if (targetBlocksFriendRequests) {
            this.client.sendResponse(
                    new FriendRequestErrorComposer(FriendRequestErrorComposer.TARGET_NOT_ACCEPTING_REQUESTS));
            return true;
        }
        return false;
    }
}

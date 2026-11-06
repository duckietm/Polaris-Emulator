package polaris.messages.incoming.friends;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.permissions.Rank;
import polaris.hotel.permissions.RankLimits;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.friends.FriendRequestErrorComposer;
import polaris.messages.outgoing.friends.LoadFriendRequestsComposer;

public class AcceptFriendRequestEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcceptFriendRequestEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    /** The friend count and limit of an accepted user who is offline, read without loading their whole account. */
    private record OfflineTarget(int friends, int limit, boolean infinite) {}

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();
        if (count <= 0 || count > 100) return;

        User user = this.client.getUser();
        GameEnvironment environment = Emulator.getGameEnvironment();
        // A request skipped for a full list stays pending, but the client has already hidden it: send the list again.
        boolean refused = false;
        int userId;

        for (int i = 0; i < count; i++) {
            userId = this.packet.readInt();

            if (userId <= 0) continue;

            if (user.getMessenger().getFriends().containsKey(userId)) {
                user.getMessenger()
                        .deleteFriendRequests(userId, user.getUserInfo().getId());
                continue;
            }

            if (user.getMessenger().countedFriends() >= RankLimits.friends(user)
                    && !user.hasPermission("acc_infinite_friends")) {
                this.client.sendResponse(
                        new FriendRequestErrorComposer(FriendRequestErrorComposer.FRIEND_LIST_OWN_FULL));
                refused = true;
                break;
            }

            User target = environment.getUserManager().getUser(userId);
            OfflineTarget offline = null;

            if (target == null) {
                // One small query instead of building a Habbo from the row, which loaded the whole inventory, pets,
                // bots, badges,
                // friends and requests of every offline user in a bulk accept of up to 100.
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "SELECT users.`rank`, users_settings.max_friends, (SELECT COUNT(*) FROM messenger_friendships WHERE user_one_id = users.id) AS friend_count FROM users INNER JOIN users_settings ON users.id = users_settings.user_id WHERE users.id = ? LIMIT 1")) {
                    statement.setInt(1, userId);
                    try (ResultSet set = statement.executeQuery()) {
                        if (set.next()) {
                            Rank rank = environment.getPermissionsManager().getRank(set.getInt("rank"));
                            offline = new OfflineTarget(
                                    set.getInt("friend_count"),
                                    RankLimits.friends(set.getInt("max_friends"), rank),
                                    rank != null && rank.hasPermission("acc_infinite_friends", false));
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                    return;
                }
            }

            if (target == null && offline == null) {
                this.client.sendResponse(new FriendRequestErrorComposer(FriendRequestErrorComposer.TARGET_NOT_FOUND));
                user.getMessenger()
                        .deleteFriendRequests(userId, user.getUserInfo().getId());
                continue;
            }

            boolean targetFull = target != null
                    ? target.getMessenger().countedFriends() >= RankLimits.friends(target)
                            && !target.hasPermission("acc_infinite_friends")
                    : offline.friends() >= offline.limit() && !offline.infinite();

            if (targetFull) {
                this.client.sendResponse(
                        new FriendRequestErrorComposer(FriendRequestErrorComposer.FRIEND_LIST_TARGET_FULL));
                refused = true;
                continue;
            }

            user.getMessenger().acceptFriendRequest(userId, user.getUserInfo().getId());

            Messenger.checkFriendSizeProgress(user);
            if (target != null) Messenger.checkFriendSizeProgress(target);
        }

        if (refused) {
            user.getMessenger().reloadFriendRequests(user);
            this.client.sendResponse(new LoadFriendRequestsComposer(user));
        }
    }
}

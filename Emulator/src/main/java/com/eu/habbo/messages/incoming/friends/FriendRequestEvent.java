package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.FriendRequestComposer;
import com.eu.habbo.messages.outgoing.friends.FriendRequestErrorComposer;
import com.eu.habbo.plugin.events.users.friends.UserRequestFriendshipEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class FriendRequestEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendRequestEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String username = FriendInputGuard.normalizeUsername(this.packet.readString());

        if (this.client == null || !FriendInputGuard.isValidUsername(username))
            return;

        // TargetHabbo can be null if the Habbo is not online or when the Habbo doesn't exist
        Habbo targetHabbo = Emulator.getGameServer().getGameClientManager().getHabbo(username);

        // If the Habbo is null, we try to get the Habbo from the database.
        // If the Habbo is still null, the Habbo doesn't exist.
        if (targetHabbo == null) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT users.*, users_settings.block_friendrequests FROM users INNER JOIN users_settings ON users.id = users_settings.user_id WHERE username = ? LIMIT 1")) {
                statement.setString(1, username);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        targetHabbo = new Habbo(set);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                return;
            }
        }

        if (targetHabbo == null) {
            this.client.sendResponse(new FriendRequestErrorComposer(FriendRequestErrorComposer.TARGET_NOT_FOUND));
            return;
        }

        int targetId = targetHabbo.getHabboInfo().getId();
        boolean targetBlocksFriendRequests = targetHabbo.getHabboStats().blockFriendRequests;

        // Making friends with yourself would be very pathetic, we try to avoid that
        if (targetId == this.client.getHabbo().getHabboInfo().getId())
            return;

        if (this.client.getHabbo().getMessenger().getFriends().containsKey(targetId))
            return;

        if (Messenger.friendRequested(targetId, this.client.getHabbo().getHabboInfo().getId()) || Messenger.friendRequested(this.client.getHabbo().getHabboInfo().getId(), targetId))
            return;

        // Target Habbo exists
        // Check if Habbo is accepting friend requests
        if (targetBlocksFriendRequests) {
            this.client.sendResponse(new FriendRequestErrorComposer(FriendRequestErrorComposer.TARGET_NOT_ACCEPTING_REQUESTS));
            return;
        }

        // You can only have x friends
        if (this.client.getHabbo().getMessenger().getFriends().size() >= this.client.getHabbo().getHabboStats().maxFriends && !this.client.getHabbo().hasPermission("acc_infinite_friends")) {
            this.client.sendResponse(new FriendRequestErrorComposer(FriendRequestErrorComposer.FRIEND_LIST_OWN_FULL));
            return;
        }

        // Check if targets friendlist is full
        if (targetHabbo.getMessenger().getFriends().size() >= targetHabbo.getHabboStats().maxFriends && !targetHabbo.hasPermission("acc_infinite_friends")) {
            this.client.sendResponse(new FriendRequestErrorComposer(FriendRequestErrorComposer.FRIEND_LIST_TARGET_FULL));
            return;
        }

        // Allow plugins to cancel the request
        if (Emulator.getPluginManager().fireEvent(new UserRequestFriendshipEvent(this.client.getHabbo(), username, targetHabbo)).isCancelled()) {
            this.client.sendResponse(new FriendRequestErrorComposer(2));
            return;
        }

        if(targetHabbo.isOnline()) {
            targetHabbo.getClient().sendResponse(new FriendRequestComposer(this.client.getHabbo()));
        }

        Messenger.makeFriendRequest(this.client.getHabbo().getHabboInfo().getId(), targetId);
    }
}

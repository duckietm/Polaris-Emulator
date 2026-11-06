package polaris.messages.incoming.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.ChangeNameUpdatedComposer;
import polaris.messages.outgoing.rooms.users.RoomUserNameChangedComposer;
import polaris.messages.outgoing.users.ChangeNameCheckResultComposer;
import polaris.messages.outgoing.users.UserDataComposer;
import polaris.networking.gameserver.auth.AvailabilityCache;
import polaris.plugin.events.users.UserNameChangedEvent;

public class ConfirmChangeNameEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmChangeNameEvent.class);

    /** Names being claimed right now, lowercase, as the availability check reads them. */
    public static final List<String> changingUsernames = new ArrayList<>(2);

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().getUserStats().allowNameChange) return;

        String name = this.packet.readString();

        if (name.equalsIgnoreCase(this.client.getUser().getUserInfo().getUsername())) {
            this.client.getUser().getUserStats().allowNameChange = false;
            this.client.sendResponse(new ChangeNameUpdatedComposer(this.client.getUser()));
            this.client.sendResponse(new RoomUserNameChangedComposer(this.client.getUser()).compose());
            this.client.sendResponse(new UserDataComposer(this.client.getUser()));
            return;
        }

        if (name.equals(this.client.getUser().getUserStats().changeNameChecked)) {
            UserInfo userInfo = UserManager.getOfflineUserInfo(name);

            if (userInfo == null) {
                String key = name.toLowerCase(Locale.ROOT);

                synchronized (changingUsernames) {
                    if (changingUsernames.contains(key)) return;

                    changingUsernames.add(key);
                }

                // The name is claimed in the database before anything else changes: two users confirming "Bob" and
                // "bob" together both passed the lookup, and the loser kept a duplicate name in memory, with rooms,
                // groups and the log rewritten, while every later save of its profile failed.
                boolean claimed;
                try {
                    claimed = claimUsername(this.client.getUser().getUserInfo().getId(), name);
                } finally {
                    synchronized (changingUsernames) {
                        changingUsernames.remove(key);
                    }
                }

                if (!claimed) {
                    this.client.sendResponse(new ChangeNameCheckResultComposer(
                            ChangeNameCheckResultComposer.TAKEN_WITH_SUGGESTIONS, name, new ArrayList<>()));
                    return;
                }

                String oldName = this.client.getUser().getUserInfo().getUsername();
                this.client.getUser().getUserStats().allowNameChange = false;
                this.client.getUser().getUserInfo().setUsername(name);
                this.client.getUser().getUserInfo().run();

                // The registration form caches whether a name is free: the old name is free now and the new one
                // is taken, and both answers were kept until they expired.
                AvailabilityCache.invalidateUsername(oldName);
                AvailabilityCache.invalidateUsername(name);

                Emulator.getPluginManager().fireEvent(new UserNameChangedEvent(this.client.getUser(), oldName));
                for (Room room :
                        Emulator.getGameEnvironment().getRoomManager().getRoomsForUser(this.client.getUser())) {
                    room.setOwnerName(name);
                    room.setNeedsUpdate(true);
                    room.save();
                }

                for (Guild guild : Emulator.getGameEnvironment()
                        .getGuildManager()
                        .getOwnedGuilds(this.client.getUser().getUserInfo().getId())) {
                    guild.setOwnerName(name);
                }

                this.client.sendResponse(new ChangeNameUpdatedComposer(this.client.getUser()));

                if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
                    this.client
                            .getUser()
                            .getUserInfo()
                            .getCurrentRoom()
                            .sendComposer(new RoomUserNameChangedComposer(this.client.getUser()).compose());
                } else {
                    this.client.sendResponse(new RoomUserNameChangedComposer(this.client.getUser()).compose());
                }

                this.client
                        .getUser()
                        .getMessenger()
                        .connectionChanged(
                                this.client.getUser(),
                                true,
                                this.client.getUser().getUserInfo().getCurrentRoom() != null);
                this.client.getUser().getClient().sendResponse(new UserDataComposer(this.client.getUser()));

                try (Connection connection = connection();
                        PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO namechange_log (user_id, old_name, new_name, timestamp) VALUES (?, ?, ?, ?) ")) {
                    statement.setInt(1, this.client.getUser().getUserInfo().getId());
                    statement.setString(2, oldName);
                    statement.setString(3, name);
                    statement.setInt(4, Emulator.getIntUnixTimestamp());
                    statement.execute();
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            } else {
                this.client.sendResponse(new ChangeNameCheckResultComposer(
                        ChangeNameCheckResultComposer.TAKEN_WITH_SUGGESTIONS, name, new ArrayList<>()));
            }
        }
    }

    /** Writes the new name for this user alone; false when another user holds it (the unique key on username). */
    static boolean claimUsername(int userId, String name) {
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE users SET username = ? WHERE id = ? LIMIT 1")) {
            statement.setString(1, name);
            statement.setInt(2, userId);

            return statement.executeUpdate() == 1;
        } catch (SQLIntegrityConstraintViolationException e) {
            return false;
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) return false;

            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }
}

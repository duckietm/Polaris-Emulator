package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.ChangeNameUpdatedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserNameChangedComposer;
import com.eu.habbo.messages.outgoing.users.ChangeNameCheckResultComposer;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.eu.habbo.plugin.events.users.UserNameChangedEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfirmChangeNameEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmChangeNameEvent.class);

    /** Names being claimed right now, lowercase, as the availability check reads them. */
    public static final List<String> changingUsernames = new ArrayList<>(2);

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().getHabboStats().allowNameChange) return;

        String name = this.packet.readString();

        if (name.equalsIgnoreCase(this.client.getHabbo().getHabboInfo().getUsername())) {
            this.client.getHabbo().getHabboStats().allowNameChange = false;
            this.client.sendResponse(new ChangeNameUpdatedComposer(this.client.getHabbo()));
            this.client.sendResponse(new RoomUserNameChangedComposer(this.client.getHabbo()).compose());
            this.client.sendResponse(new UserDataComposer(this.client.getHabbo()));
            return;
        }

        if (name.equals(this.client.getHabbo().getHabboStats().changeNameChecked)) {
            HabboInfo habboInfo = HabboManager.getOfflineHabboInfo(name);

            if (habboInfo == null) {
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
                    claimed =
                            claimUsername(this.client.getHabbo().getHabboInfo().getId(), name);
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

                String oldName = this.client.getHabbo().getHabboInfo().getUsername();
                this.client.getHabbo().getHabboStats().allowNameChange = false;
                this.client.getHabbo().getHabboInfo().setUsername(name);
                this.client.getHabbo().getHabboInfo().run();

                Emulator.getPluginManager().fireEvent(new UserNameChangedEvent(this.client.getHabbo(), oldName));
                for (Room room :
                        Emulator.getGameEnvironment().getRoomManager().getRoomsForHabbo(this.client.getHabbo())) {
                    room.setOwnerName(name);
                    room.setNeedsUpdate(true);
                    room.save();
                }

                for (Guild guild : Emulator.getGameEnvironment()
                        .getGuildManager()
                        .getOwnedGuilds(this.client.getHabbo().getHabboInfo().getId())) {
                    guild.setOwnerName(name);
                }

                this.client.sendResponse(new ChangeNameUpdatedComposer(this.client.getHabbo()));

                if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                    this.client
                            .getHabbo()
                            .getHabboInfo()
                            .getCurrentRoom()
                            .sendComposer(new RoomUserNameChangedComposer(this.client.getHabbo()).compose());
                } else {
                    this.client.sendResponse(new RoomUserNameChangedComposer(this.client.getHabbo()).compose());
                }

                this.client
                        .getHabbo()
                        .getMessenger()
                        .connectionChanged(
                                this.client.getHabbo(),
                                true,
                                this.client.getHabbo().getHabboInfo().getCurrentRoom() != null);
                this.client.getHabbo().getClient().sendResponse(new UserDataComposer(this.client.getHabbo()));

                try (Connection connection = connection();
                        PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO namechange_log (user_id, old_name, new_name, timestamp) VALUES (?, ?, ?, ?) ")) {
                    statement.setInt(1, this.client.getHabbo().getHabboInfo().getId());
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

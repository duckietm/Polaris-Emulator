package com.eu.habbo.habbohotel.rooms.competition;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Room competitions: during the submission window an owner enters one of their rooms, during the
 * voting window everybody else votes for the rooms they visit. Both windows and the entry rules come
 * from room_competitions; the entries and the votes are rows, so the tally survives a restart and a
 * crafted packet cannot vote twice (the primary key refuses it).
 *
 * <p>Only one competition is ever live, and it is cached for a minute: room entry asks for it, and
 * that would otherwise be a query per visit. A single final instance keeps the class free of mutable
 * statics, like the snowwar manager next door.
 */
public class RoomCompetitionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomCompetitionManager.class);

    private static final int CACHE_SECONDS = 60;

    private static final int DAY_SECONDS = 86400;

    private static final RoomCompetitionManager INSTANCE = new RoomCompetitionManager();

    private final AtomicReference<CachedCompetition> cache = new AtomicReference<>(null);

    private RoomCompetitionManager() {}

    public static RoomCompetitionManager getInstance() {
        return INSTANCE;
    }

    private record CachedCompetition(RoomCompetition competition, int loadedAt) {}

    /** The competition whose submission or voting window is open right now, or null. */
    public RoomCompetition active() {
        int now = Emulator.getIntUnixTimestamp();
        CachedCompetition cached = this.cache.get();
        RoomCompetition competition;

        if (cached != null && now - cached.loadedAt() < CACHE_SECONDS) {
            competition = cached.competition();
        } else {
            competition = this.loadActive(now);
            this.cache.set(new CachedCompetition(competition, now));
        }

        if (competition == null) {
            return null;
        }

        return competition.submissionOpen(now) || competition.votingOpen(now) ? competition : null;
    }

    /** Drops the cached competition; the next caller reads the table again. */
    public void invalidate() {
        this.cache.set(null);
    }

    private RoomCompetition loadActive(int now) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, code, name, required_furni, votes_per_user, submit_starts, submit_ends,"
                                + " vote_starts, vote_ends, rooms_created_after FROM room_competitions WHERE enabled = 1"
                                + " AND submit_starts <= ? AND vote_ends > ? ORDER BY submit_starts DESC LIMIT 1")) {
            statement.setInt(1, now);
            statement.setInt(2, now);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return null;
                }

                return new RoomCompetition(
                        set.getInt("id"),
                        set.getString("code"),
                        set.getString("name"),
                        splitFurni(set.getString("required_furni")),
                        set.getInt("votes_per_user"),
                        set.getInt("submit_starts"),
                        set.getInt("submit_ends"),
                        set.getInt("vote_starts"),
                        set.getInt("vote_ends"),
                        set.getInt("rooms_created_after"));
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return null;
        }
    }

    static List<String> splitFurni(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    /**
     * What the submit window should say about this room. The order matters: a room that is already
     * in says so, and only a room that could still enter is checked against the door and the
     * furniture.
     */
    public RoomCompetitionSubmitState submitState(Habbo habbo, Room room, RoomCompetition competition) {
        // Somebody else's room, and a second room of somebody who already entered, have no window at
        // all: the official codes are all about a room that could still enter.
        if (habbo == null || room == null || competition == null) {
            return RoomCompetitionSubmitState.of(RoomCompetitionResult.NOTHING);
        }

        if (room.getOwnerId() != habbo.getHabboInfo().getId()) {
            return RoomCompetitionSubmitState.of(RoomCompetitionResult.NOTHING);
        }

        int entered = this.entryRoomId(habbo.getHabboInfo().getId(), competition);

        if (entered == room.getId()) {
            return RoomCompetitionSubmitState.of(RoomCompetitionResult.SUBMITTED);
        }

        if (entered > 0) {
            return RoomCompetitionSubmitState.of(RoomCompetitionResult.NOTHING);
        }

        if (room.getState() != RoomState.OPEN) {
            return RoomCompetitionSubmitState.of(RoomCompetitionResult.DOOR_CLOSED);
        }

        if (!competition.acceptsRoomCreatedAt(this.roomCreatedAt(room.getId()))) {
            return RoomCompetitionSubmitState.of(RoomCompetitionResult.ROOM_NOT_ELIGIBLE);
        }

        List<String> missing = this.missingFurni(room, competition);

        if (!missing.isEmpty()) {
            return new RoomCompetitionSubmitState(
                    RoomCompetitionResult.MISSING_FURNI, competition.requiredFurni(), missing);
        }

        return new RoomCompetitionSubmitState(RoomCompetitionResult.READY, competition.requiredFurni(), List.of());
    }

    List<String> missingFurni(Room room, RoomCompetition competition) {
        if (competition.requiredFurni().isEmpty()) {
            return List.of();
        }

        Set<String> present = new HashSet<>();
        for (HabboItem item : room.getFloorItems()) {
            if (item != null && item.getBaseItem() != null) {
                present.add(item.getBaseItem().getName().toLowerCase());
            }
        }

        List<String> missing = new ArrayList<>();
        for (String required : competition.requiredFurni()) {
            if (!present.contains(required.toLowerCase())) {
                missing.add(required);
            }
        }

        return missing;
    }

    /** The room this user entered in the competition, or zero. */
    public int entryRoomId(int userId, RoomCompetition competition) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT room_id FROM room_competition_entries WHERE competition_id = ? AND user_id = ?"
                                + " LIMIT 1")) {
            statement.setInt(1, competition.id());
            statement.setInt(2, userId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt("room_id") : 0;
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return 0;
        }
    }

    public boolean isPartOf(int userId, RoomCompetition competition) {
        return this.entryRoomId(userId, competition) > 0;
    }

    /** The entry id of a room in this competition, or zero when the room is not entered. */
    public int entryId(int roomId, RoomCompetition competition) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM room_competition_entries WHERE competition_id = ? AND room_id = ? LIMIT 1")) {
            statement.setInt(1, competition.id());
            statement.setInt(2, roomId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt("id") : 0;
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return 0;
        }
    }

    /** Enters the room. False when somebody raced us to it: the keys decide, not a check. */
    public boolean submit(Habbo habbo, Room room, RoomCompetition competition) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO room_competition_entries (competition_id, room_id, user_id, submitted_at)"
                                + " VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, competition.id());
            statement.setInt(2, room.getId());
            statement.setInt(3, habbo.getHabboInfo().getId());
            statement.setInt(4, Emulator.getIntUnixTimestamp());
            statement.execute();
            return true;
        } catch (SQLException refused) {
            LOGGER.warn(
                    "Room {} could not enter competition {}: {}",
                    room.getId(),
                    competition.code(),
                    refused.getMessage());
            return false;
        }
    }

    /** How many votes this user has left today. */
    public int votesLeft(int userId, RoomCompetition competition) {
        int since = Emulator.getIntUnixTimestamp() - DAY_SECONDS;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) AS used FROM room_competition_votes WHERE competition_id = ?"
                                + " AND user_id = ? AND voted_at > ?")) {
            statement.setInt(1, competition.id());
            statement.setInt(2, userId);
            statement.setInt(3, since);

            try (ResultSet set = statement.executeQuery()) {
                int used = set.next() ? set.getInt("used") : 0;
                return Math.max(0, competition.votesPerUser() - used);
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return 0;
        }
    }

    /**
     * Counts one vote for the room. The primary key of room_competition_votes is what stops a second
     * vote for the same room, so a race ends as a refused insert and not as a double count.
     */
    public boolean vote(int userId, int entryId, RoomCompetition competition) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO room_competition_votes (competition_id, entry_id, user_id, voted_at)"
                            + " VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, competition.id());
                statement.setInt(2, entryId);
                statement.setInt(3, userId);
                statement.setInt(4, Emulator.getIntUnixTimestamp());
                statement.execute();
            } catch (SQLException alreadyVoted) {
                return false;
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("UPDATE room_competition_entries SET votes = votes + 1 WHERE id = ?")) {
                statement.setInt(1, entryId);
                statement.execute();
            }

            return true;
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return false;
        }
    }

    /** A room taking part, other than the one given. Used by the "show me another one" buttons. */
    public int randomEntryRoom(RoomCompetition competition, int excludeRoomId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT room_id FROM room_competition_entries WHERE competition_id = ? AND room_id <> ?"
                                + " ORDER BY RAND() LIMIT 1")) {
            statement.setInt(1, competition.id());
            statement.setInt(2, excludeRoomId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt("room_id") : 0;
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return 0;
        }
    }

    /** Every room taking part, most voted first. */
    public List<Integer> entryRooms(RoomCompetition competition, int limit) {
        List<Integer> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT room_id FROM room_competition_entries WHERE competition_id = ?"
                                + " ORDER BY votes DESC, submitted_at ASC LIMIT ?")) {
            statement.setInt(1, competition.id());
            statement.setInt(2, Math.max(1, limit));

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    rooms.add(set.getInt("room_id"));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
        }

        return rooms;
    }

    /**
     * When the room was made. Rooms that existed before the hotel started recording it answer zero,
     * which the competition reads as an age it cannot vouch for.
     */
    int roomCreatedAt(int roomId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT date_created FROM rooms WHERE id = ? LIMIT 1")) {
            statement.setInt(1, roomId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt("date_created") : 0;
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return 0;
        }
    }
}

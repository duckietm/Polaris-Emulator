package com.eu.habbo.habbohotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

final class RoomPersistence {

    private static final String SAVE_ROOM_SQL = "UPDATE rooms SET name = ?, description = ?, password = ?, "
            + "state = ?, users_max = ?, category = ?, score = ?, "
            + "paper_floor = ?, paper_wall = ?, paper_landscape = ?, "
            + "thickness_wall = ?, wall_height = ?, thickness_floor = ?, "
            + "moodlight_data = ?, tags = ?, allow_other_pets = ?, "
            + "allow_other_pets_eat = ?, allow_walkthrough = ?, "
            + "allow_hidewall = ?, chat_mode = ?, chat_weight = ?, "
            + "chat_speed = ?, chat_hearing_distance = ?, "
            + "chat_protection =?, who_can_mute = ?, who_can_kick = ?, "
            + "who_can_ban = ?, poll_id = ?, guild_id = ?, "
            + "roller_speed = ?, override_model = ?, "
            + "is_staff_picked = ?, promoted = ?, trade_mode = ?, "
            + "move_diagonally = ?, owner_id = ?, owner_name = ?, "
            + "jukebox_active = ?, hidewired = ?, allow_underpass = ?, "
            + "youtube_enabled = ?, builders_club_trial_locked = ?, "
            + "builders_club_original_state = ?, mute_all_pets = ?, "
            + "leave_on_door_tile = ?, idle_sleep_enabled = ?, "
            + "idle_sleep_timeout_seconds = ?, idle_autokick_enabled = ?, "
            + "idle_autokick_timeout_seconds = ? WHERE id = ?";
    private final RoomDependencies.ConnectionProvider database;

    RoomPersistence(RoomDependencies.ConnectionProvider database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    void save(State state) throws SQLException {
        try (Connection connection = this.database.openConnection();
                PreparedStatement statement = connection.prepareStatement(SAVE_ROOM_SQL)) {
            statement.setString(1, state.name());
            statement.setString(2, state.description());
            statement.setString(3, state.password());
            statement.setString(4, state.roomState().name().toLowerCase());
            statement.setInt(5, state.usersMax());
            statement.setInt(6, state.category());
            statement.setInt(7, state.score());
            statement.setString(8, state.floorPaint());
            statement.setString(9, state.wallPaint());
            statement.setString(10, state.backgroundPaint());
            statement.setInt(11, state.wallSize());
            statement.setInt(12, state.wallHeight());
            statement.setInt(13, state.floorSize());
            statement.setString(14, serializeMoodlight(state.moodlightData()));
            statement.setString(15, state.tags());
            statement.setString(16, databaseBoolean(state.allowPets()));
            statement.setString(17, databaseBoolean(state.allowPetsEat()));
            statement.setString(18, databaseBoolean(state.allowWalkthrough()));
            statement.setString(19, databaseBoolean(state.hideWall()));
            statement.setInt(20, state.chatMode());
            statement.setInt(21, state.chatWeight());
            statement.setInt(22, state.chatSpeed());
            statement.setInt(23, state.chatDistance());
            statement.setInt(24, state.chatProtection());
            statement.setInt(25, state.muteOption());
            statement.setInt(26, state.kickOption());
            statement.setInt(27, state.banOption());
            statement.setInt(28, state.pollId());
            statement.setInt(29, state.guild());
            statement.setInt(30, state.rollerSpeed());
            statement.setString(31, databaseBoolean(state.overrideModel()));
            statement.setString(32, databaseBoolean(state.staffPromotedRoom()));
            statement.setString(33, databaseBoolean(state.promoted()));
            statement.setInt(34, state.tradeMode());
            statement.setString(35, databaseBoolean(state.moveDiagonally()));
            statement.setInt(36, state.ownerId());
            statement.setString(37, state.ownerName());
            statement.setString(38, databaseBoolean(state.jukeboxActive()));
            statement.setString(39, databaseBoolean(state.hideWired()));
            statement.setString(40, databaseBoolean(state.allowUnderpass()));
            statement.setString(41, databaseBoolean(state.youtubeEnabled()));
            statement.setString(42, databaseBoolean(state.buildersClubTrialLocked()));
            statement.setString(
                    43,
                    Objects.requireNonNullElse(state.buildersClubOriginalState(), RoomState.OPEN)
                            .name()
                            .toLowerCase());
            statement.setString(44, databaseBoolean(state.muteAllPets()));
            statement.setString(45, databaseBoolean(state.leaveOnDoorTileEnabled()));
            statement.setString(46, databaseBoolean(state.idleSleepEnabled()));
            statement.setInt(47, state.idleSleepTimeoutSeconds());
            statement.setString(48, databaseBoolean(state.idleAutokickEnabled()));
            statement.setInt(49, state.idleAutokickTimeoutSeconds());
            statement.setInt(50, state.id());
            statement.executeUpdate();
        }
    }

    private static String databaseBoolean(boolean value) {
        return value ? "1" : "0";
    }

    private static String serializeMoodlight(List<RoomMoodlightData> moodlightData) {
        StringBuilder serialized = new StringBuilder();
        int id = 1;

        for (RoomMoodlightData data : moodlightData) {
            data.setId(id);
            serialized.append(data).append(';');
            id++;
        }

        return serialized.toString();
    }

    record State(
            int id,
            int ownerId,
            String ownerName,
            String name,
            String description,
            String password,
            RoomState roomState,
            int usersMax,
            int category,
            int score,
            String floorPaint,
            String wallPaint,
            String backgroundPaint,
            int wallSize,
            int wallHeight,
            int floorSize,
            List<RoomMoodlightData> moodlightData,
            String tags,
            boolean allowPets,
            boolean allowPetsEat,
            boolean allowWalkthrough,
            boolean hideWall,
            int chatMode,
            int chatWeight,
            int chatSpeed,
            int chatDistance,
            int chatProtection,
            int muteOption,
            int kickOption,
            int banOption,
            int pollId,
            int guild,
            int rollerSpeed,
            boolean overrideModel,
            boolean staffPromotedRoom,
            boolean promoted,
            int tradeMode,
            boolean moveDiagonally,
            boolean jukeboxActive,
            boolean hideWired,
            boolean allowUnderpass,
            boolean youtubeEnabled,
            boolean buildersClubTrialLocked,
            RoomState buildersClubOriginalState,
            boolean muteAllPets,
            boolean leaveOnDoorTileEnabled,
            boolean idleSleepEnabled,
            int idleSleepTimeoutSeconds,
            boolean idleAutokickEnabled,
            int idleAutokickTimeoutSeconds) {

        State {
            moodlightData = List.copyOf(moodlightData);
        }
    }
}

package com.eu.habbo.habbohotel.rooms;

import java.sql.ResultSet;
import java.sql.SQLException;

record RoomSnapshot(
        Initial initial,
        PostBanLoad postBanLoad) {

    static Initial readInitial(ResultSet set) throws SQLException {
        return new Initial(
                set.getInt("id"),
                set.getInt("owner_id"),
                set.getString("owner_name"),
                set.getString("name"),
                set.getString("description"),
                set.getString("password"),
                RoomState.valueOf(set.getString("state").toUpperCase()),
                set.getInt("users_max"),
                set.getInt("score"),
                set.getInt("category"),
                paint(set, "paper_floor"),
                paint(set, "paper_wall"),
                paint(set, "paper_landscape"),
                set.getInt("thickness_wall"),
                set.getInt("wall_height"),
                set.getInt("thickness_floor"),
                set.getString("tags"),
                set.getBoolean("is_public"),
                set.getBoolean("is_staff_picked"),
                set.getBoolean("allow_other_pets"),
                set.getBoolean("allow_other_pets_eat"),
                set.getBoolean("allow_walkthrough"),
                set.getBoolean("allow_hidewall"),
                optionalBoolean(set, "youtube_enabled"),
                optionalBoolean(set, "soundboard_enabled"),
                set.getInt("chat_mode"),
                set.getInt("chat_weight"),
                set.getInt("chat_speed"),
                set.getInt("chat_hearing_distance"),
                set.getInt("chat_protection"),
                set.getInt("who_can_mute"),
                set.getInt("who_can_kick"),
                set.getInt("who_can_ban"),
                set.getInt("poll_id"),
                set.getInt("guild_id"),
                set.getInt("roller_speed"),
                set.getString("override_model").equals("1"),
                set.getString("model"),
                set.getString("promoted").equals("1"),
                set.getString("jukebox_active").equals("1"),
                set.getString("hidewired").equals("1"),
                set.getBoolean("builders_club_trial_locked"),
                buildersClubOriginalState(set.getString("builders_club_original_state")));
    }

    static RoomSnapshot complete(Initial initial, ResultSet set) throws SQLException {
        return new RoomSnapshot(
                initial,
                new PostBanLoad(
                        set.getInt("trade_mode"),
                        set.getString("move_diagonally").equals("1"),
                        set.getString("allow_underpass").equals("1"),
                        set.getBoolean("mute_all_pets"),
                        set.getBoolean("leave_on_door_tile"),
                        set.getBoolean("idle_sleep_enabled"),
                        set.getInt("idle_sleep_timeout_seconds"),
                        set.getBoolean("idle_autokick_enabled"),
                        set.getInt("idle_autokick_timeout_seconds"),
                        set.getString("moodlight_data")));
    }

    private static boolean optionalBoolean(ResultSet set, String column) {
        try {
            return set.getBoolean(column);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String paint(ResultSet set, String column) throws SQLException {
        return set.getString(column) == null ? "0.0" : set.getString(column);
    }

    private static RoomState buildersClubOriginalState(String value) {
        if (value == null || value.isEmpty()) {
            return RoomState.OPEN;
        }

        try {
            return RoomState.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return RoomState.OPEN;
        }
    }

    record Initial(
            int id,
            int ownerId,
            String ownerName,
            String name,
            String description,
            String password,
            RoomState state,
            int usersMax,
            int score,
            int category,
            String floorPaint,
            String wallPaint,
            String backgroundPaint,
            int wallSize,
            int wallHeight,
            int floorSize,
            String tags,
            boolean publicRoom,
            boolean staffPromotedRoom,
            boolean allowPets,
            boolean allowPetsEat,
            boolean allowWalkthrough,
            boolean hideWall,
            boolean youtubeEnabled,
            boolean soundboardEnabled,
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
            String layoutName,
            boolean promoted,
            boolean jukeboxActive,
            boolean hideWired,
            boolean buildersClubTrialLocked,
            RoomState buildersClubOriginalState) {
    }

    record PostBanLoad(
            int tradeMode,
            boolean moveDiagonally,
            boolean allowUnderpass,
            boolean muteAllPets,
            boolean leaveOnDoorTileEnabled,
            boolean idleSleepEnabled,
            int idleSleepTimeoutSeconds,
            boolean idleAutokickEnabled,
            int idleAutokickTimeoutSeconds,
            String moodlightData) {
    }
}

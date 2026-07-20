package com.eu.habbo.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HotQueryProjectionCoverageTest {
    private static final Set<String> MARKETPLACE_OFFER_COLUMNS = Set.of(
            "item_id", "user_id", "price", "state");
    private static final Set<String> ITEM_COLUMNS = Set.of(
            "id", "user_id", "room_id", "item_id", "wall_pos", "x", "y",
            "z", "rot", "extra_data", "wired_data", "limited_data", "guild_id");
    private static final Set<String> BASE_ITEM_COLUMNS = Set.of(
            "id", "sprite_id", "public_name", "item_name", "type", "width",
            "length", "stack_height", "allow_stack", "allow_sit", "allow_lay",
            "allow_walk", "allow_gift", "allow_trade", "allow_recycle",
            "allow_marketplace_sell", "allow_inventory_stack",
            "interaction_type", "interaction_modes_count", "vending_ids",
            "multiheight", "customparams", "effect_id_male",
            "effect_id_female", "clothing_on_walk");
    private static final Set<String> ROOM_COLUMNS = Set.of(
            "id", "owner_id", "owner_name", "name", "description", "password",
            "state", "users_max", "score", "category", "paper_floor",
            "paper_wall", "paper_landscape", "thickness_wall", "wall_height",
            "thickness_floor", "tags", "is_public", "is_staff_picked",
            "allow_other_pets", "allow_other_pets_eat", "allow_walkthrough",
            "allow_hidewall", "youtube_enabled", "soundboard_enabled",
            "chat_mode", "chat_weight", "chat_speed", "chat_hearing_distance",
            "chat_protection", "who_can_mute", "who_can_kick", "who_can_ban",
            "poll_id", "guild_id", "roller_speed", "override_model", "model",
            "promoted", "jukebox_active", "hidewired",
            "builders_club_trial_locked", "builders_club_original_state",
            "trade_mode", "move_diagonally", "allow_underpass",
            "moodlight_data");

    @Test
    void marketplacePurchaseQueriesCoverEveryConsumedColumn() throws Exception {
        String source = source(
                "habbohotel/catalog/marketplace/MarketPlace.java");

        assertProjectionCovers(
                source,
                "marketplace_items",
                "WHERE id = \\? LIMIT 1",
                MARKETPLACE_OFFER_COLUMNS);
        assertProjectionCovers(
                source,
                "items",
                "WHERE id = \\? LIMIT 1",
                ITEM_COLUMNS);
    }

    @Test
    void baseItemPreloadCoversEveryItemMapperColumn() throws Exception {
        assertProjectionCovers(
                source("habbohotel/items/ItemManager.java"),
                "items_base",
                "ORDER BY id DESC",
                BASE_ITEM_COLUMNS);
    }

    @Test
    void roomQueriesCoverEveryRoomSnapshotColumn() throws Exception {
        String source = source("habbohotel/rooms/RoomManager.java");

        assertProjectionCovers(
                source,
                "rooms",
                "WHERE is_public = \\? OR is_staff_picked = \\? ORDER BY id DESC",
                ROOM_COLUMNS);
        assertProjectionCovers(
                source,
                "rooms",
                "WHERE owner_name = \\? ORDER BY id DESC LIMIT 25",
                Set.of("id"));
        assertProjectionCovers(
                source,
                "rooms",
                "WHERE owner_id = \\?",
                ROOM_COLUMNS);
    }

    private static void assertProjectionCovers(
            String source,
            String table,
            String suffix,
            Set<String> required) {
        Pattern query = Pattern.compile(
                "SELECT\\s+([^\"\\n]+?)\\s+FROM\\s+"
                        + Pattern.quote(table)
                        + "\\s+"
                        + suffix,
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = query.matcher(source);
        assertTrue(matcher.find(), "Missing hot query for " + table);

        String projection = matcher.group(1).trim();
        if (projection.equals("*")) {
            return;
        }

        Set<String> selected = Arrays.stream(projection.split(","))
                .map(String::trim)
                .map(column -> column.replace("`", ""))
                .map(column -> column.contains(".")
                        ? column.substring(column.lastIndexOf('.') + 1)
                        : column)
                .collect(Collectors.toSet());
        assertTrue(
                selected.containsAll(required),
                () -> "Projection for " + table + " is missing "
                        + difference(required, selected));
    }

    private static Set<String> difference(
            Set<String> required,
            Set<String> selected) {
        return required.stream()
                .filter(column -> !selected.contains(column))
                .collect(Collectors.toSet());
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/eu/habbo",
                relativePath));
    }
}

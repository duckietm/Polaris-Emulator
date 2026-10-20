package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredInternalVariableRegistryCompatibilityTest {

    @Test
    void everyCurrentCapabilitySetIsFrozenExactly() {
        assertEquals(
                Set.of(
                        "@position_x",
                        "@position_y",
                        "@position",
                        "@direction",
                        "@altitude",
                        "@effect_id",
                        "@handitem_id",
                        "@team_score",
                        "@player_score",
                        "@habbicon"),
                keys(WiredInternalVariableRegistry.Capability.USER_DESTINATION));
        assertEquals(
                Set.of("@team_red_score", "@team_green_score", "@team_blue_score", "@team_yellow_score"),
                keys(WiredInternalVariableRegistry.Capability.ROOM_DESTINATION));
        assertEquals(
                Set.of(
                        "@state",
                        "@position_x",
                        "@position_y",
                        "@position",
                        "@occupation",
                        "@rotation",
                        "@altitude",
                        "@gravity",
                        "@opacity",
                        "~recolorable_furni.color.rgb",
                        "~recolorable_furni.color.rgb.red",
                        "~recolorable_furni.color.rgb.green",
                        "~recolorable_furni.color.rgb.blue",
                        "~clock.pulse_count"),
                keys(WiredInternalVariableRegistry.Capability.FURNI_DESTINATION));
        assertEquals(
                Set.of(
                        "@index",
                        "@type",
                        "@gender",
                        "@level",
                        "@achievement_score",
                        "@is_hc",
                        "@has_rights",
                        "@is_group_admin",
                        "@is_owner",
                        "@is_muted",
                        "@is_trading",
                        "@is_frozen",
                        "@effect_id",
                        "@team_score",
                        "@player_score",
                        "@team_color",
                        "@team_type",
                        "@sign",
                        "@dance",
                        "@is_idle",
                        "@handitem_id",
                        "@position_x",
                        "@position_y",
                        "@position",
                        "@direction",
                        "@altitude",
                        "@favourite_group_id",
                        "@room_entry.method",
                        "@room_entry.teleport_id",
                        "@user_id",
                        "@bot_id",
                        "@pet_id",
                        "@pet_owner_id",
                        "@habbicon",
                        "@transaction.in_trade",
                        "@transaction.start_time",
                        "@transaction.contract_id",
                        "@transaction.state",
                        "@transaction.can_accept",
                        "@transaction.current_multiplier",
                        "~pet.level",
                        "~pet.experience",
                        "~pet.energy",
                        "~pet.happiness",
                        "~pet.max_level",
                        "~pet.experience_required",
                        "~pet.max_energy",
                        "~pet.max_happiness",
                        "~pet.scratches",
                        "~pet.owner_id",
                        "~pet.creation_time",
                        "~horse.has_saddle",
                        "~horse.is_riding",
                        "~horse.controller_user_id",
                        "~plant.rarity_level",
                        "~plant.shape",
                        "~plant.color",
                        "~plant.can_breed",
                        "~plant.can_harvest",
                        "~plant.can_revive",
                        "~plant.is_dead",
                        "~plant.is_growing",
                        "~plant.remaining_wellbeing_seconds",
                        "~plant.remaining_growing_seconds",
                        "~plant.max_wellbeing_seconds"),
                keys(WiredInternalVariableRegistry.Capability.USER_REFERENCE));
        assertEquals(
                Set.of(
                        "~teleport.target_id",
                        "~recolorable_furni.color.rgb",
                        "~recolorable_furni.color.rgb.red",
                        "~recolorable_furni.color.rgb.green",
                        "~recolorable_furni.color.rgb.blue",
                        "~background_color.hue",
                        "~background_color.saturation",
                        "~background_color.lightness",
                        "~walk_helper.is_multi_walk",
                        "~area_hide.root_x",
                        "~area_hide.root_y",
                        "~area_hide.width",
                        "~area_hide.length",
                        "~area_hide.is_invisible_furni",
                        "~area_hide.hiding_wallitems",
                        "~area_hide.inverted",
                        "~chest.available_amount",
                        "~chest.locked",
                        "~chest.is_auto_lock",
                        "~chest.capacity",
                        "~chest.is_open",
                        "~chest.is_donatable",
                        "~clock.state",
                        "~clock.pulse_count",
                        "~clock.is_game_aware",
                        "@id",
                        "@class_id",
                        "@height",
                        "@state",
                        "@position_x",
                        "@position_y",
                        "@position",
                        "@occupation",
                        "@rotation",
                        "@altitude",
                        "@is_invisible",
                        "@type",
                        "@is_stackable",
                        "@can_stand_on",
                        "@can_sit_on",
                        "@can_lay_on",
                        "@owner_id",
                        "@wallitem_offset",
                        "@dimensions.x",
                        "@dimensions.y",
                        "@projectile.animation.position.x",
                        "@projectile.animation.position.y",
                        "@projectile.animation.position.altitude",
                        "@projectile.animation.is_travelling",
                        "@projectile.animation.tiles_travelled",
                        "@projectile.animation.furni_collisions",
                        "@projectile.animation.user_collisions",
                        "@gravity",
                        "@opacity"),
                keys(WiredInternalVariableRegistry.Capability.FURNI_REFERENCE));
        assertEquals(
                Set.of(
                        "@furni_count",
                        "@user_count",
                        "@wired_timer",
                        "@team_red_score",
                        "@team_green_score",
                        "@team_blue_score",
                        "@team_yellow_score",
                        "@team_red_size",
                        "@team_green_size",
                        "@team_blue_size",
                        "@team_yellow_size",
                        "@room_id",
                        "@group_id",
                        "@timezone_server",
                        "@timezone_client",
                        "@current_time",
                        "@current_time.milliseconds_of_seconds",
                        "@current_time.seconds_of_minute",
                        "@current_time.minute_of_hour",
                        "@current_time.hour_of_day",
                        "@current_time.day_of_week",
                        "@current_time.day_of_month",
                        "@current_time.day_of_year",
                        "@current_time.week_of_year",
                        "@current_time.month_of_year",
                        "@current_time.year"),
                keys(WiredInternalVariableRegistry.Capability.ROOM_REFERENCE));
        assertEquals(
                Set.of(
                        "@selector_furni_count",
                        "@selector_user_count",
                        "@signal_furni_count",
                        "@signal_user_count",
                        "@antenna_id",
                        "@chat_type",
                        "@chat_style",
                        "@event.link.source_room_id",
                        "@event.transaction_failed.reason",
                        "@event.habbicon",
                        "@event.transaction_complete.multiplier",
                        "@event.transaction_complete.deposit.furni_count",
                        "@event.transaction_complete.deposit.coins_count",
                        "@event.transaction_complete.withdrawal.furni_count",
                        "@event.transaction_complete.withdrawal.coins_count",
                        "@event.variable_update.box_id",
                        "@event.variable_update.change_type",
                        "@event.variable_update.old_value",
                        "@event.variable_update.new_value",
                        "@event.variable_update.difference",
                        "@event.variable_update.change_origin",
                        "@array.change_type",
                        "@array.index",
                        "@array.source_index",
                        "@array.destination_index",
                        "@array.field_index",
                        "@array.old_value",
                        "@array.new_value",
                        "@array.old_length",
                        "@array.new_length"),
                keys(WiredInternalVariableRegistry.Capability.CONTEXT_REFERENCE));
    }

    @Test
    void everyLegacyAliasRetainsExactCaseSensitiveNormalization() {
        assertEquals(
                Map.ofEntries(
                        Map.entry("@current_time.millisecond_of_second", "@current_time.milliseconds_of_seconds"),
                        Map.entry("~recolorable_furni.color.rgb.r", "~recolorable_furni.color.rgb.red"),
                        Map.entry("~recolorable_furni.color.rgb.g", "~recolorable_furni.color.rgb.green"),
                        Map.entry("~recolorable_furni.color.rgb.b", "~recolorable_furni.color.rgb.blue"),
                        Map.entry("@projectile.animation.is_traveling", "@projectile.animation.is_travelling"),
                        Map.entry("@projectile.animation.tiles_traveled", "@projectile.animation.tiles_travelled"),
                        Map.entry("@position.x", "@position_x"),
                        Map.entry("@position.y", "@position_y"),
                        Map.entry("@effect", "@effect_id"),
                        Map.entry("@handitems", "@handitem_id"),
                        Map.entry("@handitem", "@handitem_id"),
                        Map.entry("@team.score", "@team_score"),
                        Map.entry("@player.score", "@player_score"),
                        Map.entry("@team.color", "@team_color"),
                        Map.entry("@team.type", "@team_type"),
                        Map.entry("@event.chat.type", "@chat_type"),
                        Map.entry("@event.chat.style", "@chat_style"),
                        Map.entry("@event.signal.antenna_id", "@antenna_id"),
                        Map.entry("@is_mute", "@is_muted"),
                        Map.entry("@teams.red.score", "@team_red_score"),
                        Map.entry("@teams.green.score", "@team_green_score"),
                        Map.entry("@teams.blue.score", "@team_blue_score"),
                        Map.entry("@teams.yellow.score", "@team_yellow_score"),
                        Map.entry("@teams.red.size", "@team_red_size"),
                        Map.entry("@teams.green.size", "@team_green_size"),
                        Map.entry("@teams.blue.size", "@team_blue_size"),
                        Map.entry("@teams.yellow.size", "@team_yellow_size")),
                WiredInternalVariableRegistry.DEFAULT.aliases());
        assertEquals("@position_x", WiredInternalVariableSupport.normalizeKey("  @position.x  "));
        assertEquals("@POSITION.X", WiredInternalVariableSupport.normalizeKey("@POSITION.X"));
        assertEquals("", WiredInternalVariableSupport.normalizeKey(null));
    }

    @Test
    void publicCapabilityMethodsExposeLegacyAndNewWritableValues() {
        assertTrue(WiredInternalVariableSupport.canUseUserReference("@effect"));
        assertTrue(WiredInternalVariableSupport.canUseFurniDestination(" @altitude "));
        assertTrue(WiredInternalVariableSupport.canUseRoomReference("@teams.red.score"));
        assertTrue(WiredInternalVariableSupport.canUseContextReference("@chat_style"));
        assertTrue(WiredInternalVariableSupport.canUseUserDestination("@altitude"));
        assertTrue(WiredInternalVariableSupport.canUseFurniReference("@gravity"));
        assertFalse(WiredInternalVariableSupport.canUseRoomReference(""));
    }

    @Test
    void builderRejectsAliasCollisionsAndUnknownTargets() {
        WiredInternalVariableRegistry.Builder conflicting = new WiredInternalVariableRegistry.Builder()
                .register(WiredInternalVariableRegistry.Capability.FURNI_REFERENCE, "@state")
                .alias("@legacy", "@state");
        assertThrows(IllegalArgumentException.class, () -> conflicting.alias("@legacy", "@other"));

        WiredInternalVariableRegistry.Builder unknown = new WiredInternalVariableRegistry.Builder()
                .register(WiredInternalVariableRegistry.Capability.FURNI_REFERENCE, "@state")
                .alias("@legacy", "@missing");
        assertThrows(IllegalArgumentException.class, unknown::build);
    }

    private static Set<String> keys(WiredInternalVariableRegistry.Capability capability) {
        return WiredInternalVariableRegistry.DEFAULT.keys(capability);
    }
}

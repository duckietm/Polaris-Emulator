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
                Set.of("@position_x", "@position_y", "@direction"),
                keys(WiredInternalVariableRegistry.Capability.USER_DESTINATION));
        assertEquals(
                Set.of("@state", "@position_x", "@position_y", "@rotation", "@altitude", "@gravity"),
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
                        "@team_color",
                        "@team_type",
                        "@sign",
                        "@dance",
                        "@is_idle",
                        "@handitem_id",
                        "@position_x",
                        "@position_y",
                        "@direction",
                        "@altitude",
                        "@favourite_group_id",
                        "@room_entry.method",
                        "@room_entry.teleport_id",
                        "@user_id",
                        "@bot_id",
                        "@pet_id",
                        "@pet_owner_id"),
                keys(WiredInternalVariableRegistry.Capability.USER_REFERENCE));
        assertEquals(
                Set.of(
                        "~teleport.target_id",
                        "@id",
                        "@class_id",
                        "@height",
                        "@state",
                        "@position_x",
                        "@position_y",
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
                        "@gravity"),
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
                        "@current_time.millisecond_of_second",
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
                        "@chat_style"),
                keys(WiredInternalVariableRegistry.Capability.CONTEXT_REFERENCE));
    }

    @Test
    void everyLegacyAliasRetainsExactCaseSensitiveNormalization() {
        assertEquals(
                Map.ofEntries(
                        Map.entry("@position.x", "@position_x"),
                        Map.entry("@position.y", "@position_y"),
                        Map.entry("@effect", "@effect_id"),
                        Map.entry("@handitems", "@handitem_id"),
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
    void currentPublicCapabilityMethodsDelegateWithoutBehaviorChange() {
        assertTrue(WiredInternalVariableSupport.canUseUserReference("@effect"));
        assertTrue(WiredInternalVariableSupport.canUseFurniDestination(" @altitude "));
        assertTrue(WiredInternalVariableSupport.canUseRoomReference("@teams.red.score"));
        assertTrue(WiredInternalVariableSupport.canUseContextReference("@chat_style"));
        assertFalse(WiredInternalVariableSupport.canUseUserDestination("@altitude"));
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

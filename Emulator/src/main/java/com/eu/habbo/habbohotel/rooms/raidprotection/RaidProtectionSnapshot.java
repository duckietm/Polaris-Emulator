package com.eu.habbo.habbohotel.rooms.raidprotection;

/**
 * Everything AIR's RaidProtectionSettingsSnapshot carries: the settings, whether a raid is going on
 * and when the last one started (unix seconds, 0 for never).
 */
public record RaidProtectionSnapshot(RaidProtectionSettings settings, boolean incidentActive, int lastRaidAt) {}

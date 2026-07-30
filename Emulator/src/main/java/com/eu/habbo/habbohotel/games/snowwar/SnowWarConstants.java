package com.eu.habbo.habbohotel.games.snowwar;

/**
 * Game constants for the SnowWar (SnowStorm) simulation.
 * Values mirror the official 2026 AIR SnowWar implementation. The client runs
 * the same deterministic simulation and verifies it through checksums.
 */
public final class SnowWarConstants {

    // Timing
    public static final int SUBTURNS_PER_TICK = 3;
    public static final int SERVER_TICK_MS = 150;
    public static final int SUBTURN_MS = 50;

    // Movement
    public static final int SUBTURN_MOVEMENT = 534;
    public static final int TILE_SIZE = 3200;
    public static final int VELOCITY_DIVISOR = 255;
    public static final int QUICK_THROW_PLANAR_VELOCITY = 2000;

    // Collision
    public static final int SNOWBALL_RADIUS = 400;
    public static final int AVATAR_RADIUS = 1600;
    public static final int AVATAR_COLLISION_HEIGHT = 5000;
    public static final int TREE_RADIUS = TILE_SIZE - SNOWBALL_RADIUS - 1;
    public static final int TREE_COLLISION_HEIGHT = TILE_SIZE;
    public static final int TREE_MAX_HITS = 3;
    public static final int MACHINE_RADIUS = 1200;

    // Health and snowballs
    public static final int INITIAL_HEALTH = 5;
    public static final int MAX_SNOWBALLS = 5;

    // Activity timers (in subturns/frames, NOT milliseconds)
    public static final int STUNNED_TIMER = 100;
    public static final int INVINCIBILITY_TIMER = 60;
    public static final int CREATING_TIMER = 20;

    // Scoring
    public static final int HIT_SCORE = 1;
    public static final int STUN_SCORE = 5;

    // Cooldowns (real milliseconds)
    public static final int THROW_COOLDOWN_MS = 300;
    // Custom Domexx-era ray gun extension (not in official AIR SnowStorm):
    // one burst per gun per window so arrival-triggered bursts cannot be spammed.
    public static final int RAY_GUN_COOLDOWN_MS = 3000;
    // Anti-flood: minimum spacing between in-game chat messages and between
    // full-game-status requests from the same player. Chat is broadcast to
    // everyone and a status request forces a full state serialization, so both
    // are amplification vectors without a cooldown.
    public static final int CHAT_COOLDOWN_MS = 750;
    public static final int STATUS_REQUEST_COOLDOWN_MS = 1000;

    // Editor save hardening: hard caps on the counts a (permitted) editor may
    // publish, so a malformed or malicious save packet can't bloat the arena
    // definition or spin the read loop.
    public static final int EDITOR_MAX_ITEMS = 2048;
    public static final int EDITOR_MAX_SPAWNS = 256;
    public static final int EDITOR_MAX_ROWS = 256;
    public static final int EDITOR_MAX_COLUMNS = 256;
    public static final int EDITOR_MIN_WALKABLE_TILES = 2;
    public static final int EDITOR_MAX_ITEM_NAME_LENGTH = 100;
    public static final int EDITOR_MAX_IMAGE_URL_LENGTH = 2048;

    // Generic per-user flood cap across ALL SnowWar packets: catches mixed
    // packet floods that slip between the per-action cooldowns (e.g. spamming
    // walk + create + throw in a loop). Generous enough for normal play.
    public static final int PACKET_FLOOD_WINDOW_MS = 1000;
    public static final int PACKET_FLOOD_MAX_PER_WINDOW = 40;

    // Official AIR projectile limits, in world units. The fourth trajectory is
    // the original adaptive throw: quick through 42,000 units, short lob
    // through 60,000, and long lob beyond that (capped at 100,000).
    public static final int TRAJECTORY_QUICK = 0;
    public static final int TRAJECTORY_SHORT_LOB = 1;
    public static final int TRAJECTORY_LONG_LOB = 2;
    public static final int TRAJECTORY_DEFAULT = 3;
    public static final int QUICK_THROW_MAX_RANGE = 20_000;
    public static final int SHORT_LOB_MAX_RANGE = 60_000;
    public static final int LONG_LOB_MAX_RANGE = 100_000;
    public static final int DEFAULT_THROW_TO_LOB_CUTOFF_RANGE = 42_000;

    // AIR machine cadence: 100 frames to generate, 20 between auto-pickups.
    public static final int MACHINE_SNOWBALL_GENERATOR_TIME = 100;
    public static final int SNOWBALL_SOURCE_PICKUP_TIME = 20;
    public static final int MACHINE_MAX_SNOWBALL_CAPACITY = 5;
    public static final int PILE_MAX_SNOWBALL_CAPACITY = 12;

    // Object type ids (FullGameStatus + checksums)
    public static final int OBJECT_TYPE_AVATAR = 1;
    public static final int OBJECT_TYPE_SNOWBALL = 2;
    public static final int OBJECT_TYPE_MACHINE = 3;
    public static final int OBJECT_TYPE_TREE = 4;
    public static final int OBJECT_TYPE_PILE = 5;

    // Generic error codes (SnowStormGenericErrorComposer)
    public static final int ERROR_QUEUE_FULL = 1;
    public static final int ERROR_ALREADY_IN_GAME = 2;
    public static final int ERROR_NOT_ENOUGH_PLAYERS = 3;
    public static final int ERROR_NO_TICKETS = 4;
    public static final int ERROR_INTERNAL = 5;

    private SnowWarConstants() {}
}

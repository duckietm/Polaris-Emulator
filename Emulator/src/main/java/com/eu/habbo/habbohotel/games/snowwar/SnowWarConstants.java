package com.eu.habbo.habbohotel.games.snowwar;

/**
 * Game constants for the SnowWar (SnowStorm) simulation.
 * Values must match Upgrade/README.md section 3.1 exactly - the client runs
 * the same deterministic simulation and verifies it through checksums.
 */
public final class SnowWarConstants {

    // Timing
    public static final int SUBTURNS_PER_TICK = 5;
    public static final int SERVER_TICK_MS = 300;
    public static final int SUBTURN_MS = 60;

    // Movement
    public static final int SUBTURN_MOVEMENT = 640;
    public static final int TILE_SIZE = 3200;
    public static final int VELOCITY_DIVISOR = 255;
    public static final int BASE_VELOCITY_MULTIPLIER = 2000;

    // Collision
    public static final int COLLISION_DISTANCE = 2000;
    public static final int BALL_HEIGHT_THRESHOLD = 5000;

    // Health and snowballs
    public static final int INITIAL_HEALTH = 4;
    public static final int MAX_SNOWBALLS = 5;

    // Activity timers (in subturns/frames, NOT milliseconds)
    public static final int STUNNED_TIMER = 125;
    public static final int INVINCIBILITY_TIMER = 60;
    public static final int CREATING_TIMER = 20;

    // Scoring
    public static final int HIT_SCORE = 1;
    public static final int STUN_SCORE = 5;

    // Cooldowns (real milliseconds)
    public static final int THROW_COOLDOWN_MS = 300;

    // Snowball machine (timers in subturns/frames)
    public static final int MACHINE_SNOWBALL_GENERATOR_TIME = 100;
    public static final int MACHINE_MAX_SNOWBALL_CAPACITY = 5;

    // Object type ids (FullGameStatus + checksums)
    public static final int OBJECT_TYPE_AVATAR = 1;
    public static final int OBJECT_TYPE_SNOWBALL = 2;
    public static final int OBJECT_TYPE_MACHINE = 3;

    // Generic error codes (SnowStormGenericErrorComposer)
    public static final int ERROR_QUEUE_FULL = 1;
    public static final int ERROR_ALREADY_IN_GAME = 2;
    public static final int ERROR_NOT_ENOUGH_PLAYERS = 3;
    public static final int ERROR_NO_TICKETS = 4;
    public static final int ERROR_INTERNAL = 5;

    private SnowWarConstants() {
    }
}

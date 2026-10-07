package com.eu.habbo.habbohotel.rooms.pathfinding.impl;

public final class PathfinderConstants {
    public static final int BASIC_MOVEMENT_COST = 10;
    public static final int DIAGONAL_MOVEMENT_COST = 11;
    public static final int DISTANCE_DOOR_THRESHOLD = 2;
    public static final int TIMEOUT_CHECK_INTERVAL = 64;

    // Configuration keys
    public static final String CONFIG_EXECUTION_TIME = "pathfinder.execution_time.milli";
    public static final String CONFIG_TIMEOUT_ENABLED = "pathfinder.max_execution_time.enabled";
    /** Clicking a tile someone stands on walks to the tile next to it (1, default) or nowhere (0). */
    public static final String CONFIG_OCCUPIED_GOAL_WALK_ADJACENT = "pathfinder.occupied_goal.walk_adjacent";

    private PathfinderConstants() {
        throw new IllegalStateException("Utility class");
    }
}

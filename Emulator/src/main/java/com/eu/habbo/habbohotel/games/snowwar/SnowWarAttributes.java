package com.eu.habbo.habbohotel.games.snowwar;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-player SnowWar state (README 8.2).
 * All simulation values are 32-bit ints so they can feed the checksum.
 */
public class SnowWarAttributes {

    // Movement
    private volatile boolean walking = false;
    private volatile SnowWarPoint currentPosition = new SnowWarPoint(0, 0);   // tile coords
    private volatile SnowWarPoint worldPosition = new SnowWarPoint(0, 0);     // world coords
    private volatile SnowWarPoint walkGoal = null;                            // tile coords
    private volatile SnowWarPoint nextGoal = null;                            // tile coords
    private volatile SnowWarPoint goalWorldCoordinates = null;                // raw client world coords

    // Combat
    private final AtomicInteger snowballCount = new AtomicInteger(0);
    private final AtomicInteger health = new AtomicInteger(0);
    private volatile int pendingHealth = 0;
    private volatile boolean pendingStun = false;
    private volatile long lastThrowTime = 0;

    // Pathfinding guard (SnowWarPathfinder.MAX_PATHFIND_ITERATIONS)
    private volatile int pathfindIterations = 0;

    // State
    private volatile SnowWarActivityState activityState = SnowWarActivityState.NORMAL;
    private volatile int activityTimer = 0;
    private volatile int rotation = 0;

    // Score
    private final AtomicInteger score = new AtomicInteger(0);

    public boolean isWalkableState() {
        return this.activityState == SnowWarActivityState.NORMAL
                || this.activityState == SnowWarActivityState.INVINCIBLE;
    }

    public boolean isDamageable() {
        return this.activityState == SnowWarActivityState.NORMAL;
    }

    public boolean isWalking() {
        return this.walking;
    }

    public void setWalking(boolean walking) {
        this.walking = walking;
    }

    public SnowWarPoint getCurrentPosition() {
        return this.currentPosition;
    }

    public void setCurrentPosition(SnowWarPoint currentPosition) {
        this.currentPosition = currentPosition;
    }

    public SnowWarPoint getWorldPosition() {
        return this.worldPosition;
    }

    public void setWorldPosition(SnowWarPoint worldPosition) {
        this.worldPosition = worldPosition;
    }

    public SnowWarPoint getWalkGoal() {
        return this.walkGoal;
    }

    public void setWalkGoal(SnowWarPoint walkGoal) {
        this.walkGoal = walkGoal;
    }

    public SnowWarPoint getNextGoal() {
        return this.nextGoal;
    }

    public void setNextGoal(SnowWarPoint nextGoal) {
        this.nextGoal = nextGoal;
    }

    public SnowWarPoint getGoalWorldCoordinates() {
        return this.goalWorldCoordinates;
    }

    public void setGoalWorldCoordinates(SnowWarPoint goalWorldCoordinates) {
        this.goalWorldCoordinates = goalWorldCoordinates;
    }

    public AtomicInteger getSnowballCount() {
        return this.snowballCount;
    }

    public AtomicInteger getHealth() {
        return this.health;
    }

    public int getPendingHealth() {
        return this.pendingHealth;
    }

    public void setPendingHealth(int pendingHealth) {
        this.pendingHealth = pendingHealth;
    }

    public boolean isPendingStun() {
        return this.pendingStun;
    }

    public void setPendingStun(boolean pendingStun) {
        this.pendingStun = pendingStun;
    }

    public long getLastThrowTime() {
        return this.lastThrowTime;
    }

    public void setLastThrowTime(long lastThrowTime) {
        this.lastThrowTime = lastThrowTime;
    }

    public int getPathfindIterations() {
        return this.pathfindIterations;
    }

    public void setPathfindIterations(int pathfindIterations) {
        this.pathfindIterations = pathfindIterations;
    }

    public SnowWarActivityState getActivityState() {
        return this.activityState;
    }

    public void setActivityState(SnowWarActivityState activityState) {
        this.activityState = activityState;
    }

    public int getActivityTimer() {
        return this.activityTimer;
    }

    public void setActivityTimer(int activityTimer) {
        this.activityTimer = activityTimer;
    }

    public int getRotation() {
        return this.rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public AtomicInteger getScore() {
        return this.score;
    }

    /**
     * Resets the attributes for a fresh (re)spawn at the given tile.
     */
    public void resetForSpawn(SnowWarPoint spawnTile) {
        this.walking = false;
        this.currentPosition = spawnTile;
        this.worldPosition = new SnowWarPoint(
                SnowWarMath.tileToWorld(spawnTile.getX()),
                SnowWarMath.tileToWorld(spawnTile.getY()));
        this.walkGoal = spawnTile;
        this.nextGoal = null;
        this.goalWorldCoordinates = null;
        this.snowballCount.set(SnowWarConstants.MAX_SNOWBALLS);
        this.health.set(SnowWarConstants.INITIAL_HEALTH);
        this.pendingHealth = SnowWarConstants.INITIAL_HEALTH;
        this.pendingStun = false;
        this.lastThrowTime = 0;
        this.pathfindIterations = 0;
        this.activityState = SnowWarActivityState.NORMAL;
        this.activityTimer = 0;
        this.score.set(0);
    }
}

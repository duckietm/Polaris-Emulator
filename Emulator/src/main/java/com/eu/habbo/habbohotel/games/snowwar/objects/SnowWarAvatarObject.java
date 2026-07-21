package com.eu.habbo.habbohotel.games.snowwar.objects;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarActivityState;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarAttributes;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarConstants;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGame;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGamePlayer;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarMath;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarPoint;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarPathfinder;
import com.eu.habbo.messages.ServerMessage;

/**
 * Player avatar object (README 9.2).
 */
public class SnowWarAvatarObject extends SnowWarGameObject {

    private final SnowWarGame game;
    private final SnowWarGamePlayer gamePlayer;

    public SnowWarAvatarObject(SnowWarGame game, SnowWarGamePlayer gamePlayer) {
        super(gamePlayer.getObjectId());
        this.game = game;
        this.gamePlayer = gamePlayer;
    }

    public SnowWarGamePlayer getGamePlayer() {
        return this.gamePlayer;
    }

    /**
     * Processes one subturn/frame for this avatar: activity timers + movement.
     */
    public void calculateFrameMovement() {
        SnowWarAttributes attr = this.gamePlayer.getAttributes();

        if (attr.getActivityTimer() > 0) {
            attr.setActivityTimer(attr.getActivityTimer() - 1);

            if (attr.getActivityTimer() == 0) {
                this.onActivityTimerExpired(attr);
            }
        }

        if (!this.hasDestination(attr)) {
            return;
        }

        if (!this.canMoveInCurrentState(attr)) {
            return;
        }

        boolean reachedDestination = this.moveOneFrame(attr);

        if (reachedDestination) {
            this.stopWalking();
        }
    }

    private boolean hasDestination(SnowWarAttributes attr) {
        return attr.isWalking() && attr.getWalkGoal() != null;
    }

    private boolean canMoveInCurrentState(SnowWarAttributes attr) {
        SnowWarActivityState state = attr.getActivityState();
        return state == SnowWarActivityState.NORMAL
                || state == SnowWarActivityState.CREATING_SNOWBALL
                || state == SnowWarActivityState.INVINCIBLE;
    }

    private boolean moveOneFrame(SnowWarAttributes attr) {
        int currentWorldX = attr.getWorldPosition().getX();
        int currentWorldY = attr.getWorldPosition().getY();

        int targetWorldX = SnowWarMath.tileToWorld(attr.getWalkGoal().getX());
        int targetWorldY = SnowWarMath.tileToWorld(attr.getWalkGoal().getY());

        if (currentWorldX == targetWorldX && currentWorldY == targetWorldY) {
            return true;
        }

        SnowWarPoint nextTile = attr.getNextGoal();
        if (nextTile == null) {
            // Guard against endless path recalculation (README 5.7).
            attr.setPathfindIterations(attr.getPathfindIterations() + 1);
            if (attr.getPathfindIterations() > SnowWarPathfinder.MAX_PATHFIND_ITERATIONS) {
                return true;
            }

            nextTile = SnowWarPathfinder.getNextDirection(this.game, this.gamePlayer);

            if (nextTile == null) {
                // No path available, stop where we are.
                return true;
            }

            attr.setNextGoal(nextTile);

            // Face the tile we start moving towards.
            int direction = SnowWarMath.getAngleFromComponents(
                    SnowWarMath.tileToWorld(nextTile.getX()) - currentWorldX,
                    SnowWarMath.tileToWorld(nextTile.getY()) - currentWorldY);
            attr.setRotation(SnowWarMath.direction360To8(direction));
        }

        int nextWorldX = SnowWarMath.tileToWorld(nextTile.getX());
        int nextWorldY = SnowWarMath.tileToWorld(nextTile.getY());

        currentWorldX = moveTowards(currentWorldX, nextWorldX, SnowWarConstants.SUBTURN_MOVEMENT);
        currentWorldY = moveTowards(currentWorldY, nextWorldY, SnowWarConstants.SUBTURN_MOVEMENT);

        attr.setWorldPosition(new SnowWarPoint(currentWorldX, currentWorldY));

        int newTileX = SnowWarMath.worldToTile(currentWorldX);
        int newTileY = SnowWarMath.worldToTile(currentWorldY);

        if (newTileX != attr.getCurrentPosition().getX() || newTileY != attr.getCurrentPosition().getY()) {
            attr.setCurrentPosition(new SnowWarPoint(newTileX, newTileY));
        }

        if (currentWorldX == nextWorldX && currentWorldY == nextWorldY) {
            attr.setNextGoal(null);
        }

        return currentWorldX == targetWorldX && currentWorldY == targetWorldY;
    }

    private static int moveTowards(int current, int target, int maxStep) {
        int delta = target - current;
        if (delta == 0) {
            return current;
        }
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        if (delta < 0) {
            return current - maxStep;
        }
        return current + maxStep;
    }

    private void onActivityTimerExpired(SnowWarAttributes attr) {
        switch (attr.getActivityState()) {
            case CREATING_SNOWBALL:
                attr.setActivityState(SnowWarActivityState.NORMAL);
                attr.getSnowballCount().incrementAndGet();
                break;

            case STUNNED:
                attr.setActivityState(SnowWarActivityState.INVINCIBLE);
                attr.setActivityTimer(SnowWarConstants.INVINCIBILITY_TIMER);
                attr.getHealth().set(SnowWarConstants.INITIAL_HEALTH);
                attr.setPendingHealth(SnowWarConstants.INITIAL_HEALTH);
                attr.setPendingStun(false);
                break;

            case INVINCIBLE:
                attr.setActivityState(SnowWarActivityState.NORMAL);
                break;

            default:
                break;
        }
    }

    public void stopWalking() {
        SnowWarAttributes attr = this.gamePlayer.getAttributes();
        attr.setWalking(false);
        attr.setWalkGoal(attr.getCurrentPosition());
        attr.setNextGoal(null);
        attr.setGoalWorldCoordinates(null);
    }

    public boolean isImmune() {
        SnowWarActivityState state = this.gamePlayer.getAttributes().getActivityState();
        return state == SnowWarActivityState.STUNNED || state == SnowWarActivityState.INVINCIBLE;
    }

    /**
     * Circle-to-circle collision test against a snowball (README 10).
     */
    public boolean testCollision(SnowWarSnowballObject ball) {
        if (ball.getHeight() > SnowWarConstants.BALL_HEIGHT_THRESHOLD) {
            return false;
        }

        if (!this.game.isOpponent(this.gamePlayer, ball.getThrower())) {
            return false;
        }

        SnowWarAttributes attr = this.gamePlayer.getAttributes();

        int playerX = attr.getWorldPosition().getX();
        int playerY = attr.getWorldPosition().getY();

        int distanceX = Math.abs(ball.getLocH() - playerX);
        int distanceY = Math.abs(ball.getLocV() - playerY);

        if (distanceX >= SnowWarConstants.COLLISION_DISTANCE) {
            return false;
        }
        if (distanceY >= SnowWarConstants.COLLISION_DISTANCE) {
            return false;
        }

        int distanceSquared = (distanceX * distanceX) + (distanceY * distanceY);
        return distanceSquared < SnowWarConstants.COLLISION_DISTANCE * SnowWarConstants.COLLISION_DISTANCE;
    }

    @Override
    public int[] getChecksumValues() {
        SnowWarAttributes attr = this.gamePlayer.getAttributes();

        SnowWarPoint current = attr.getCurrentPosition();
        SnowWarPoint nextGoal = attr.getNextGoal() != null ? attr.getNextGoal() : current;
        SnowWarPoint walkGoal = attr.getWalkGoal() != null ? attr.getWalkGoal() : current;

        return new int[]{
                SnowWarConstants.OBJECT_TYPE_AVATAR,
                this.objectId,
                SnowWarMath.tileToWorld(current.getX()),
                SnowWarMath.tileToWorld(current.getY()),
                attr.getRotation(),
                attr.getHealth().get(),
                attr.getSnowballCount().get(),
                0, // is_bot
                attr.getActivityTimer(),
                attr.getActivityState().getStateId(),
                nextGoal.getX(),
                nextGoal.getY(),
                SnowWarMath.tileToWorld(walkGoal.getX()),
                SnowWarMath.tileToWorld(walkGoal.getY()),
                attr.getScore().get(),
                this.gamePlayer.getUserId(),
                this.gamePlayer.getTeamId(),
                this.objectId
        };
    }

    @Override
    public void serialize(ServerMessage response) {
        SnowWarAttributes attr = this.gamePlayer.getAttributes();

        SnowWarPoint current = attr.getCurrentPosition();
        SnowWarPoint nextGoal = attr.getNextGoal() != null ? attr.getNextGoal() : current;
        SnowWarPoint walkGoal = attr.getWalkGoal() != null ? attr.getWalkGoal() : current;

        response.appendInt(SnowWarConstants.OBJECT_TYPE_AVATAR);
        response.appendInt(this.objectId);
        response.appendInt(attr.getWorldPosition().getX());
        response.appendInt(attr.getWorldPosition().getY());
        response.appendInt(attr.getRotation());
        response.appendInt(attr.getHealth().get());
        response.appendInt(attr.getSnowballCount().get());
        response.appendInt(0); // is_bot
        response.appendInt(attr.getActivityTimer());
        response.appendInt(attr.getActivityState().getStateId());
        response.appendInt(nextGoal.getX());
        response.appendInt(nextGoal.getY());
        response.appendInt(SnowWarMath.tileToWorld(walkGoal.getX()));
        response.appendInt(SnowWarMath.tileToWorld(walkGoal.getY()));
        response.appendInt(attr.getScore().get());
        response.appendInt(this.gamePlayer.getUserId());
        response.appendInt(this.gamePlayer.getTeamId());
        response.appendInt(this.objectId);
        response.appendString(this.gamePlayer.getHabbo().getHabboInfo().getUsername());
        response.appendString("");
        response.appendString(this.gamePlayer.getHabbo().getHabboInfo().getLook());
        response.appendString(this.gamePlayer.getHabbo().getHabboInfo().getGender().name().toUpperCase());
    }

    @Override
    public boolean isAlive() {
        return true;
    }
}

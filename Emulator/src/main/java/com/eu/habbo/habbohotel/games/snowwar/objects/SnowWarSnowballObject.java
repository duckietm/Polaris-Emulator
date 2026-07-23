package com.eu.habbo.habbohotel.games.snowwar.objects;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarConstants;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarGamePlayer;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarMath;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarTile;
import com.eu.habbo.messages.ServerMessage;

/**
 * Snowball projectile (README 9.3, 12.6, 12.7).
 * Positions are stored in WORLD coordinates from creation on.
 */
public class SnowWarSnowballObject extends SnowWarGameObject {

    private final SnowWarMap map;
    private final SnowWarGamePlayer thrower;

    private int locH;
    private int locV;
    private int height;

    private final int direction;
    private int timeToLive;
    private final int parabolaOffset;
    private final int trajectory;

    private volatile boolean alive = true;

    /**
     * @param fromTileX  thrower tile X
     * @param fromTileY  thrower tile Y
     * @param targetTileX target tile X
     * @param targetTileY target tile Y
     * @param trajectory 0 = quick throw, 1 = lob, 2 = long throw
     */
    public SnowWarSnowballObject(int objectId, SnowWarMap map, SnowWarGamePlayer thrower,
                                 int fromTileX, int fromTileY, int targetTileX, int targetTileY, int trajectory) {
        super(objectId);
        this.map = map;
        this.thrower = thrower;
        this.trajectory = trajectory;

        this.locH = SnowWarMath.tileToWorld(fromTileX);
        this.locV = SnowWarMath.tileToWorld(fromTileY);

        int[] flightPath = SnowWarMath.calculateFlightPath(fromTileX, fromTileY, targetTileX, targetTileY, trajectory);
        this.direction = flightPath[0];
        this.timeToLive = flightPath[1];
        this.parabolaOffset = flightPath[2];

        this.height = this.calculateHeight(this.timeToLive);
    }

    public SnowWarGamePlayer getThrower() {
        return this.thrower;
    }

    public int getLocH() {
        return this.locH;
    }

    public int getLocV() {
        return this.locV;
    }

    public int getHeight() {
        return this.height;
    }

    public int getDirection() {
        return this.direction;
    }

    public int getTimeToLive() {
        return this.timeToLive;
    }

    public int getTrajectory() {
        return this.trajectory;
    }

    public void kill() {
        this.alive = false;
    }

    private int calculateHeight(int timeToLive) {
        int distanceFromPeak = timeToLive - this.parabolaOffset;
        int heightMultiplier;
        int baseHeight;

        switch (this.trajectory) {
            case 0: // Quick throw (flat arc)
                if (timeToLive > 3) {
                    distanceFromPeak = 3 - this.parabolaOffset;
                }
                heightMultiplier = 4;
                baseHeight = 4000;
                break;
            case 1: // Medium lob
                heightMultiplier = 10;
                baseHeight = 3000;
                break;
            default: // 2: High arc
                heightMultiplier = 100;
                baseHeight = 3000;
                break;
        }

        return baseHeight + heightMultiplier
                * ((this.parabolaOffset * this.parabolaOffset) - (distanceFromPeak * distanceFromPeak));
    }

    /**
     * Processes one subturn/frame of flight (README 12.7 / 9.3).
     */
    public void calculateFrameMovement() {
        if (!this.alive) {
            return;
        }

        this.timeToLive--;

        int deltaH = (SnowWarMath.getBaseVelX(this.direction) * SnowWarConstants.BASE_VELOCITY_MULTIPLIER)
                / SnowWarConstants.VELOCITY_DIVISOR;
        int deltaV = (SnowWarMath.getBaseVelY(this.direction) * SnowWarConstants.BASE_VELOCITY_MULTIPLIER)
                / SnowWarConstants.VELOCITY_DIVISOR;

        this.locH = this.locH + deltaH;
        this.locV = this.locV + deltaV;

        this.height = this.calculateHeight(this.timeToLive);

        if (this.height < 0) {
            this.alive = false;
            return;
        }

        int currentTileX = SnowWarMath.worldToTile(this.locH);
        int currentTileY = SnowWarMath.worldToTile(this.locV);

        SnowWarTile tile = this.map.getTile(currentTileX, currentTileY);
        if (tile != null && tile.getHighestItem() != null) {
            int collisionHeight = tile.getHighestItem().getCollisionHeight();
            if (collisionHeight > 0 && this.height < collisionHeight) {
                this.alive = false;
            }
        }
    }

    @Override
    public int[] getChecksumValues() {
        return new int[]{
                SnowWarConstants.OBJECT_TYPE_SNOWBALL,
                this.objectId,
                this.locH,
                this.locV,
                this.height,
                this.direction,
                this.trajectory,
                this.timeToLive,
                this.thrower.getObjectId(),
                this.parabolaOffset
        };
    }

    @Override
    public void serialize(ServerMessage response) {
        response.appendInt(SnowWarConstants.OBJECT_TYPE_SNOWBALL);
        response.appendInt(this.objectId);
        response.appendInt(this.locH);
        response.appendInt(this.locV);
        response.appendInt(this.height);
        response.appendInt(this.direction);
        response.appendInt(this.trajectory);
        response.appendInt(this.timeToLive);
        response.appendInt(this.thrower.getObjectId());
        response.appendInt(this.parabolaOffset);
    }

    @Override
    public boolean isAlive() {
        return this.alive;
    }
}

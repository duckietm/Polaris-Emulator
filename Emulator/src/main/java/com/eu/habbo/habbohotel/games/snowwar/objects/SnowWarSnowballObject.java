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
    private final int planarVelocity;
    private int timeToLive;
    private final int parabolaOffset;
    private final int trajectory;

    private volatile boolean alive = true;

    /**
     * @param fromWorldX thrower world X
     * @param fromWorldY thrower world Y
     * @param targetWorldX target world X
     * @param targetWorldY target world Y
     * @param trajectory 0 = quick, 1 = short lob, 2 = long lob, 3 = adaptive
     */
    public SnowWarSnowballObject(
            int objectId,
            SnowWarMap map,
            SnowWarGamePlayer thrower,
            int fromWorldX,
            int fromWorldY,
            int targetWorldX,
            int targetWorldY,
            int trajectory) {
        super(objectId);
        this.map = map;
        this.thrower = thrower;

        this.locH = fromWorldX;
        this.locV = fromWorldY;

        int[] flightPath =
                SnowWarMath.calculateFlightPathWorld(fromWorldX, fromWorldY, targetWorldX, targetWorldY, trajectory);
        this.direction = flightPath[0];
        this.timeToLive = flightPath[1];
        this.parabolaOffset = flightPath[2];
        this.planarVelocity = flightPath[3];
        this.trajectory = flightPath[4];
        this.height = 3000;
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

    public int getPlanarVelocity() {
        return this.planarVelocity;
    }

    public void kill() {
        this.alive = false;
    }

    private int calculateHeight(int timeToLive) {
        int distanceFromPeak = timeToLive - this.parabolaOffset;
        int heightMultiplier;
        switch (this.trajectory) {
            case 0: // Quick throw (flat arc)
                if (timeToLive > 3) {
                    distanceFromPeak = 3 - this.parabolaOffset;
                }
                heightMultiplier = 10;
                break;
            case 1: // Short lob
                heightMultiplier = 25;
                break;
            default: // 2: Long lob
                heightMultiplier = 50;
                break;
        }

        int calculated = 3000
                + heightMultiplier
                        * ((this.parabolaOffset * this.parabolaOffset) - (distanceFromPeak * distanceFromPeak));
        return this.trajectory == SnowWarConstants.TRAJECTORY_QUICK ? Math.min(calculated, 3000) : calculated;
    }

    /**
     * Processes one subturn/frame of flight (README 12.7 / 9.3).
     */
    public void calculateFrameMovement() {
        if (!this.alive) {
            return;
        }

        this.timeToLive--;

        int deltaH =
                (SnowWarMath.getBaseVelX(this.direction) * this.planarVelocity) / SnowWarConstants.VELOCITY_DIVISOR;
        int deltaV =
                (SnowWarMath.getBaseVelY(this.direction) * this.planarVelocity) / SnowWarConstants.VELOCITY_DIVISOR;

        this.locH = this.locH + deltaH;
        this.locV = this.locV + deltaV;

        this.height = this.calculateHeight(this.timeToLive);
    }

    /** AIR tests the live ball altitude against the current tile after objects. */
    public boolean hasFloorCollision() {
        if (this.height < 1) {
            return true;
        }
        SnowWarTile tile = this.map.getTile(SnowWarMath.worldToTile(this.locH), SnowWarMath.worldToTile(this.locV));
        return tile != null && this.height < tile.getSnowballCollisionHeight();
    }

    @Override
    public int[] getChecksumValues() {
        return new int[] {
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

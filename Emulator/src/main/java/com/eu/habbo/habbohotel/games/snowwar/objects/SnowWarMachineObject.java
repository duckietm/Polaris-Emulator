package com.eu.habbo.habbohotel.games.snowwar.objects;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarActivityState;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarAttributes;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarConstants;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarMath;
import com.eu.habbo.messages.ServerMessage;

/**
 * Snowball machine / dispenser (README 9.4). Position is in TILE coordinates.
 */
public class SnowWarMachineObject extends SnowWarGameObject {

    private final int x;
    private final int y;

    private int snowballCount = 0;
    private int generatorTimer = SnowWarConstants.MACHINE_SNOWBALL_GENERATOR_TIME;

    public SnowWarMachineObject(int objectId, int x, int y) {
        super(objectId);
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getSnowballCount() {
        return this.snowballCount;
    }

    public void addSnowball() {
        this.snowballCount++;
    }

    /**
     * Advances the generator by one subturn. Returns true when a new
     * snowball should be generated this frame.
     */
    public boolean processGeneratorTick() {
        if (this.generatorTimer > 0) {
            this.generatorTimer--;
            return false;
        }

        this.generatorTimer = SnowWarConstants.MACHINE_SNOWBALL_GENERATOR_TIME;
        return this.canGenerateSnowball();
    }

    public boolean canGenerateSnowball() {
        return this.snowballCount < SnowWarConstants.MACHINE_MAX_SNOWBALL_CAPACITY;
    }

    public boolean canPlayerPickup(SnowWarAttributes attr) {
        if (!this.isPlayerAtPickupPosition(attr)) {
            return false;
        }
        if (attr.isWalking()) {
            return false;
        }
        if (attr.getActivityState() != SnowWarActivityState.NORMAL
                && attr.getActivityState() != SnowWarActivityState.INVINCIBLE) {
            return false;
        }
        return this.snowballCount > 0 && attr.getSnowballCount().get() < SnowWarConstants.MAX_SNOWBALLS;
    }

    private boolean isPlayerAtPickupPosition(SnowWarAttributes attr) {
        int pickupX = this.x;
        int pickupY = this.y + 1;
        return attr.getCurrentPosition().getX() == pickupX && attr.getCurrentPosition().getY() == pickupY;
    }

    public void transferSnowballTo(SnowWarAttributes attr) {
        if (this.snowballCount > 0 && attr.getSnowballCount().get() < SnowWarConstants.MAX_SNOWBALLS) {
            this.snowballCount--;
            attr.getSnowballCount().incrementAndGet();
        }
    }

    @Override
    public int[] getChecksumValues() {
        return new int[]{
                SnowWarConstants.OBJECT_TYPE_MACHINE,
                this.objectId,
                SnowWarMath.tileToWorld(this.x),
                SnowWarMath.tileToWorld(this.y),
                this.snowballCount
        };
    }

    @Override
    public void serialize(ServerMessage response) {
        response.appendInt(SnowWarConstants.OBJECT_TYPE_MACHINE);
        response.appendInt(this.objectId);
        response.appendInt(SnowWarMath.convertToWorldCoordinate(this.x));
        response.appendInt(SnowWarMath.convertToWorldCoordinate(this.y));
        response.appendInt(this.snowballCount);
    }

    @Override
    public boolean isAlive() {
        return true;
    }
}

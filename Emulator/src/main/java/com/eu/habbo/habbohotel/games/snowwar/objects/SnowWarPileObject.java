package com.eu.habbo.habbohotel.games.snowwar.objects;

import com.eu.habbo.habbohotel.games.snowwar.SnowWarActivityState;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarAttributes;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarConstants;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarMath;
import com.eu.habbo.messages.ServerMessage;

/** A finite official {@code snst_ballpile}. Position is in tile coordinates. */
public class SnowWarPileObject extends SnowWarGameObject {

    private final int x;
    private final int y;
    private int snowballCount = SnowWarConstants.PILE_MAX_SNOWBALL_CAPACITY;
    private int reservedPickups;

    public SnowWarPileObject(int objectId, int x, int y) {
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

    public boolean canPlayerPickup(SnowWarAttributes attributes) {
        int distance = Math.abs(attributes.getCurrentPosition().getX() - this.x)
                + Math.abs(attributes.getCurrentPosition().getY() - this.y);
        return distance == 1
                && !attributes.isWalking()
                && (attributes.getActivityState() == SnowWarActivityState.NORMAL
                        || attributes.getActivityState() == SnowWarActivityState.INVINCIBLE)
                && this.snowballCount > this.reservedPickups
                && attributes.getSnowballCount().get() < SnowWarConstants.MAX_SNOWBALLS;
    }

    public void reservePickup() {
        this.reservedPickups++;
    }

    public void transferReservedSnowballTo(SnowWarAttributes attributes) {
        if (this.reservedPickups > 0) {
            this.reservedPickups--;
        }
        if (this.snowballCount > 0 && attributes.getSnowballCount().get() < SnowWarConstants.MAX_SNOWBALLS) {
            this.snowballCount--;
            attributes.getSnowballCount().incrementAndGet();
        }
    }

    @Override
    public int[] getChecksumValues() {
        return new int[] {
            SnowWarConstants.OBJECT_TYPE_PILE,
            this.objectId,
            SnowWarMath.tileToWorld(this.x),
            SnowWarMath.tileToWorld(this.y),
            SnowWarConstants.PILE_MAX_SNOWBALL_CAPACITY,
            this.snowballCount
        };
    }

    @Override
    public void serialize(ServerMessage response) {
        response.appendInt(SnowWarConstants.OBJECT_TYPE_PILE);
        response.appendInt(this.objectId);
        response.appendInt(SnowWarMath.tileToWorld(this.x));
        response.appendInt(SnowWarMath.tileToWorld(this.y));
        response.appendInt(SnowWarConstants.PILE_MAX_SNOWBALL_CAPACITY);
        response.appendInt(this.snowballCount);
    }

    @Override
    public boolean isAlive() {
        // Empty piles remain synchronized so reconnecting clients render their
        // final depleted state; only their pickup zone becomes inactive.
        return true;
    }
}

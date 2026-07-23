package com.eu.habbo.habbohotel.games.snowwar.objects;

import com.eu.habbo.messages.ServerMessage;

/**
 * Base class for all SnowWar game objects (README 9.1).
 */
public abstract class SnowWarGameObject {

    protected final int objectId;

    protected SnowWarGameObject(int objectId) {
        this.objectId = objectId;
    }

    public int getObjectId() {
        return this.objectId;
    }

    /**
     * Values used for the deterministic checksum. Integer arithmetic only.
     */
    public abstract int[] getChecksumValues();

    /**
     * Serializes this object for the FULLGAMESTATUS packet (README 9).
     */
    public abstract void serialize(ServerMessage response);

    public abstract boolean isAlive();

    /**
     * Weighted sum of the checksum values (README 9.1).
     */
    public int getChecksumContribution() {
        int[] values = this.getChecksumValues();
        int sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i] * (i + 1);
        }
        return sum;
    }
}

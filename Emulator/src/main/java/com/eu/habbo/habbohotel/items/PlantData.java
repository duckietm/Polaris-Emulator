package com.eu.habbo.habbohotel.items;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Per placed-furni plant runtime state, stored in {@code item_plants_data} (one row per
 * {@code items.id}). Mutated in-memory by {@link com.eu.habbo.habbohotel.items.interactions.InteractionPlant}
 * as the plant grows or dies and flushed to the database asynchronously.
 */
public class PlantData {
    private volatile int countState;
    private volatile long lastWaterDate;
    private volatile boolean dead;

    public PlantData(ResultSet set) throws SQLException {
        this.countState = set.getInt("count_state");
        this.lastWaterDate = set.getLong("last_water_date");
        this.dead = set.getInt("state") != 0;
    }

    public PlantData(int countState, long lastWaterDate, boolean dead) {
        this.countState = countState;
        this.lastWaterDate = lastWaterDate;
        this.dead = dead;
    }

    public int getCountState() {
        return this.countState;
    }

    public void setCountState(int countState) {
        this.countState = countState;
    }

    public long getLastWaterDate() {
        return this.lastWaterDate;
    }

    public void setLastWaterDate(long lastWaterDate) {
        this.lastWaterDate = lastWaterDate;
    }

    public boolean isDead() {
        return this.dead;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
    }
}

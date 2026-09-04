package com.eu.habbo.habbohotel.items;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PlantConfig {
    private final int id;
    private final String itemName;
    private final int growCounts;
    private final int deathCount;

    public PlantConfig(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.itemName = set.getString("item_name");
        this.growCounts = set.getInt("grow_counts");
        this.deathCount = set.getInt("death_count");
    }

    public PlantConfig(int id, String itemName, int growCounts, int deathCount) {
        this.id = id;
        this.itemName = itemName;
        this.growCounts = growCounts;
        this.deathCount = deathCount;
    }

    public int getId() {
        return this.id;
    }

    public String getItemName() {
        return this.itemName;
    }

    public int getGrowCounts() {
        return this.growCounts;
    }

    public int getDeathCount() {
        return this.deathCount;
    }
}

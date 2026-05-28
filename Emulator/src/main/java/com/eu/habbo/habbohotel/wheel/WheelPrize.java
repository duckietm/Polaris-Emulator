package com.eu.habbo.habbohotel.wheel;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

// One slice of the wheel. type = item | badge | credits | points | spin | nothing.
public class WheelPrize {
    public final int id;
    public final String type;
    public final String value;   // item: base item id ; badge: badge code ; others: unused
    public final int amount;     // item qty / credits / points / extra spins
    public final int pointsType; // for type=points
    public final int weight;
    public final String label;
    public final int spriteId;   // resolved for item prizes so the client can render the furni icon

    public WheelPrize(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.type = set.getString("type");
        this.value = set.getString("value");
        this.amount = set.getInt("amount");
        this.pointsType = set.getInt("points_type");
        this.weight = Math.max(0, set.getInt("weight"));
        this.label = set.getString("label");
        this.spriteId = resolveSpriteId(this.type, this.value);
    }

    private static int resolveSpriteId(String type, String value) {
        if (!"item".equals(type) || value == null) return 0;
        try {
            Item item = Emulator.getGameEnvironment().getItemManager().getItem(Integer.parseInt(value.trim()));
            return item != null ? item.getSpriteId() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String badgeCode() {
        return "badge".equals(this.type) && this.value != null ? this.value : "";
    }
}

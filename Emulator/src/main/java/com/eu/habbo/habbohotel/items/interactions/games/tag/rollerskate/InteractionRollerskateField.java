package com.eu.habbo.habbohotel.items.interactions.games.tag.rollerskate;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.games.tag.RollerskateGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagField;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

public class InteractionRollerskateField extends InteractionTagField {
    private final HashMap<Habbo, Integer> stepTimes = new HashMap<>();

    public InteractionRollerskateField(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, RollerskateGame.class);
    }

    public InteractionRollerskateField(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, RollerskateGame.class);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo != null)
            this.stepTimes.put(habbo, Emulator.getIntUnixTimestamp());
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);
        if (habbo != null && this.stepTimes.containsKey(habbo)) {
            AchievementManager.progressAchievement(habbo, Emulator.getGameEnvironment().getAchievementManager().getAchievement("RbTagC"), (Emulator.getIntUnixTimestamp() - this.stepTimes.get(habbo)) / 60);
            this.stepTimes.remove(habbo);
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        Habbo itemOwner = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.getUserId());

        if (itemOwner != null) {
            AchievementManager.progressAchievement(itemOwner, Emulator.getGameEnvironment().getAchievementManager().getAchievement("RbTagA"));
        }
    }
}
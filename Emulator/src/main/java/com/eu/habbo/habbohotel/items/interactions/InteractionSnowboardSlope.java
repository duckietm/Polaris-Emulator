package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class InteractionSnowboardSlope extends InteractionMultiHeight {
    public InteractionSnowboardSlope(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionSnowboardSlope(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        room.giveEffect(roomUnit, 97, -1);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        if (roomUnit.getEffectId() == 97) {
            room.giveEffect(roomUnit, 0, -1);
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        Set<HabboItem> items = room.getRoomSpecialTypes().getItemsOfType(InteractionSnowboardSlope.class);

        Achievement snowboardBuild = Emulator.getGameEnvironment().getAchievementManager().getAchievement("snowBoardBuild");

        if (snowboardBuild == null) return;
        int progress;
        Habbo habbo = room.getHabbo(room.getOwnerId());

        if (habbo != null) {
            progress = habbo.getHabboStats().getAchievementProgress(snowboardBuild);


        } else {
            progress = AchievementManager.getAchievementProgressForHabbo(room.getOwnerId(), snowboardBuild);
        }

        progress = Math.max(items.size() - progress, 0);

        if (progress > 0) {
            AchievementManager.progressAchievement(room.getOwnerId(), snowboardBuild);
        }
    }

    @Override
    public void onPickUp(Room room) {
        for (Habbo habbo : room.getHabbosOnItem(this)) {
            if (habbo.getRoomUnit().getEffectId() == 97) {
                room.giveEffect(habbo, 0, -1);
            }
        }
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        Rectangle newRect = RoomLayout.getRectangle(newLocation.x, newLocation.y, this.getBaseItem().getWidth(), this.getBaseItem().getLength(), this.getRotation());

        for (Habbo habbo : room.getHabbosOnItem(this)) {
            if (habbo.getRoomUnit().getEffectId() == 97 && !newRect.contains(habbo.getRoomUnit().getCurrentLocation().x, habbo.getRoomUnit().getCurrentLocation().y)) {
                room.giveEffect(habbo, 0, -1);
            }
        }
    }
}

package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionBlackHole extends InteractionGate {
    public InteractionBlackHole(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionBlackHole(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onPlace(Room room) {
        Achievement holeCountAchievement = Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoHoleFurniCount");

        int holesCountProgress = 0;
        Habbo owner = room.getHabbo(this.getUserId());

        if (owner == null) {
            holesCountProgress = AchievementManager.getAchievementProgressForHabbo(this.getUserId(), holeCountAchievement);
        } else {
            holesCountProgress = owner.getHabboStats().getAchievementProgress(holeCountAchievement);
        }
        int holeDifference = room.getRoomSpecialTypes().getItemsOfType(InteractionBlackHole.class).size() - holesCountProgress;

        if (holeDifference > 0) {
            if (owner != null) {
                AchievementManager.progressAchievement(owner, holeCountAchievement, holeDifference);
            } else {
                AchievementManager.progressAchievement(this.getUserId(), holeCountAchievement, holeDifference);
            }
        }

        super.onPlace(room);
    }
}
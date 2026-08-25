package com.eu.habbo.habbohotel.wired.highscores;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.util.HotelDateTimeUtil;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public class WiredHighscoreMidnightUpdater implements Runnable {
    @Override
    public void run() {
        List<Room> rooms = Emulator.getGameEnvironment().getRoomManager().getActiveRooms();

        for (Room room : rooms) {
            if (room == null || room.getRoomSpecialTypes() == null) continue;

            Set<HabboItem> items = room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class);
            for (HabboItem item : items) {
                ((InteractionWiredHighscore) item).reloadData();
                room.updateItem(item);
            }
        }

        WiredHighscoreManager.midnightUpdater = Emulator.getThreading().run(new WiredHighscoreMidnightUpdater(), getNextUpdaterRun());
    }

    public static int getNextUpdaterRun() {
        long nextRunTimestamp = HotelDateTimeUtil.toEpochSecond(HotelDateTimeUtil.localDateTimeNow().with(LocalTime.MIDNIGHT).plusDays(1).plusSeconds(-1));
        return Math.toIntExact(nextRunTimestamp - Emulator.getIntUnixTimestamp()) + 5;
    }
}

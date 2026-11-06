package polaris.hotel.wired.highscores;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionWiredHighscore;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.util.HotelDateTimeUtil;

public class WiredHighscoreMidnightUpdater implements Runnable {
    @Override
    public void run() {
        List<Room> rooms = Emulator.getGameEnvironment().getRoomManager().getActiveRooms();

        for (Room room : rooms) {
            if (room == null || room.getRoomSpecialTypes() == null) continue;

            Set<UserItem> items = room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class);
            for (UserItem item : items) {
                ((InteractionWiredHighscore) item).reloadData();
                room.updateItem(item);
            }
        }

        WiredHighscoreManager.midnightUpdater =
                Emulator.getThreading().run(new WiredHighscoreMidnightUpdater(), getNextUpdaterRun());
    }

    public static int getNextUpdaterRun() {
        long nextRunTimestamp = HotelDateTimeUtil.toEpochSecond(HotelDateTimeUtil.localDateTimeNow()
                .with(LocalTime.MIDNIGHT)
                .plusDays(1)
                .plusSeconds(-1));
        return Math.toIntExact(nextRunTimestamp - Emulator.getIntUnixTimestamp()) + 5;
    }
}

package com.srth.plugin.Rooms.Cycleables;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class RoomPesca extends Scheduler {

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    public static int estado_peces = 0;

    public RoomPesca() {
        super(120);
        this.reloadConfig();
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            this.run();
        }
    }

    @Override
    public void run() {
        super.run();

        if (Emulator.getGameEnvironment().getRoomManager().getActiveRooms().size() >= 1) {

            Room room = Emulator.getGameEnvironment().getRoomManager()
                    .getRoom(RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room_normales.calle_del_lago"));
            if (room != null) {
                if (room.getUserCount() <= 0) {
                    return;
                }

                if (getEstadoPeces() <= 1) {
                    estado_peces = 2;
                } else {
                    estado_peces = 0;
                }

                for (Bot bot : room.getCurrentBots().values()) {

                    if (!bot.getType().equalsIgnoreCase("compradorPescados")) {
                        continue;
                    }

                    if (getEstadoPeces() == 0) {
                        String[] mensaje_aleatorio = RoleplayWebsocketsRoomsInfo.getTexts()
                                .getValue("keys_botCompradorPescados_msg_estate_change").split(";");
                        bot.shout(mensaje_aleatorio[Emulator.getRandom().nextInt(mensaje_aleatorio.length - 1)]);
                    }
                }

                for (HabboItem item : room.getFloorItems()) {

                    if (!item.getBaseItem().getName().equalsIgnoreCase("BW_FishingSpot")) {
                        continue;
                    }

                    item.setExtradata(estado_peces + "");
                    item.run();
                    room.updateItem(item);
                }
            }
        }

    }

    public static int getEstadoPeces() {
        return estado_peces;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }

}

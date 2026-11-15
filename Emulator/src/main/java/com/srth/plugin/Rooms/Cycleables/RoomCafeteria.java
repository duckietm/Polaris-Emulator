package com.srth.plugin.Rooms.Cycleables;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class RoomCafeteria extends Scheduler {

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    public static int estadoEstante;

    public RoomCafeteria() {
        super(5);
        this.reloadConfig();
        RoleplayWebsocketsRoomsInfo.getConfig().register("cafeteria_cafes_estante", "0");
        estadoEstante = RoleplayWebsocketsRoomsInfo.getConfig().getInt("cafeteria_cafes_estante");
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

            if (RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom("cafeteria") == null) {
                return;
            }
            Room room = Emulator.getGameEnvironment().getRoomManager()
                    .getRoom(RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom("cafeteria").getRoomId());
            if (room != null) {
                comprobateStatusEstanteria(room);
            }
        }

    }

    public static void comprobateStatusEstanteria(Room room) {

        int estadoCafes = 0;
        if (getEstadoEstante() >= 1 && getEstadoEstante() <= 5) {
            estadoCafes = 1;
        } else if (getEstadoEstante() > 5 && getEstadoEstante() <= 10) {
            estadoCafes = 2;
        } else if (getEstadoEstante() > 10 && getEstadoEstante() <= 15) {
            estadoCafes = 3;
        } else if (getEstadoEstante() == 0) {
            estadoCafes = 0;
        }

        for (HabboItem item : room.getFloorItems()) {
            if (!item.getBaseItem().getName().equalsIgnoreCase("rp_estanteria_cafe")) {
                continue;
            }
            item.setExtradata(estadoCafes + "");
            item.run();
            room.updateItem(item);
        }

    }

    public static void restarCafeEstante(int value) {
        int cantidadActual = getEstadoEstante();
        int operacion = cantidadActual - value;
        if (operacion <= 0) {
            operacion = 0;
        }

        RoleplayWebsocketsRoomsInfo.getConfig().updateData("cafeteria_cafes_estante", operacion + "");

        setEstadoEstante(operacion);
    }

    public static void incrementCafeEstante(int value) {
        int cantidadActual = getEstadoEstante();
        int operacion = cantidadActual + value;
        if (operacion >= 15) {
            operacion = 15;
        }

        RoleplayWebsocketsRoomsInfo.getConfig().updateData("cafeteria_cafes_estante", operacion + "");

        setEstadoEstante(operacion);
    }

    public static int getEstadoEstante() {
        return estadoEstante;
    }

    public static void setEstadoEstante(int value) {
        estadoEstante = value;
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }

}

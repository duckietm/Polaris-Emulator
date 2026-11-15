package com.srth.plugin.ActionItems.Actions.CofreInventorys;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class AbrirCofreInventory {

    private final HabboItem item;
    private final Habbo habbo;
    private HabboRp habboRp;
    private int startTime;

    private static final ConcurrentHashMap<Integer, Integer> cooldowns = new ConcurrentHashMap<>();
    private static final int COOLDOWN_TIME = 2; // Segundos de espera

    public AbrirCofreInventory(HabboItem item, Habbo habbo) {
        this.item = item;
        this.habbo = habbo;

        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        } catch (SQLException ignored) {
        }

        if (canStartAction()) {
            // Lógica de Cooldown antes de empezar
            int currentTime = Emulator.getIntUnixTimestamp();
            int lastAction = cooldowns.getOrDefault(habbo.getHabboInfo().getId(), 0);

            if (currentTime - lastAction >= COOLDOWN_TIME) {
                startProcess();
            }
        }
    }

    private boolean canStartAction() {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null)
            return false;

        if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                habbo.getRoomUnit().getCurrentLocation())) {
            RoomTile tile = HabboItem.getSquareInFront(room.getLayout(), item);
            if (tile != null && tile.isWalkable()) {
                habbo.getRoomUnit().setGoalLocation(tile);
            }
            return false;
        }
        return true;
    }

    private void startProcess() {
        try {
            this.startTime = Emulator.getIntUnixTimestamp();

            // Actualizamos el cooldown justo al abrir
            cooldowns.put(habbo.getHabboInfo().getId(), this.startTime);

            if (habboRp != null)
                habboRp.setProgressPendiente(true);
            updateItemState("1");

            RoleplayWebsocketsRoomsInfo.getCofreInventoryManager().openCofreInventory(habbo, item);
        } catch (Exception ignored) {
        }
    }

    private void updateItemState(String state) {
        item.setExtradata(state);
        item.needsUpdate(true);
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room != null) {
            room.updateItem(item);
        }
    }
}
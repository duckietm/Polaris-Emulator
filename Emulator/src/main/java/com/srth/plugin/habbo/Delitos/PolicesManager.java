package com.srth.plugin.habbo.Delitos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredHandler;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

public class PolicesManager {

    private static List<HabboItem> items_centro_policial;
    protected static List<HabboItem> items_calle;
    protected List<HabboItem> items_prisionero;

    private static THashMap<Integer, Short> itemListX = new THashMap<Integer, Short>();
    private static THashMap<Integer, Short> itemListY = new THashMap<Integer, Short>();

    public PolicesManager() {
        items_centro_policial = new ArrayList<>();
        items_calle = new ArrayList<>();

        this.items_prisionero = new ArrayList<>();
    }

    public void setCasillasItem(HabboItem habboItem) {
        int width = habboItem.getBaseItem().getWidth();
        int length = habboItem.getBaseItem().getLength();

        // Add a las listas la cantidad de casillas que ocupa el furni
        for (int i = 0; i < width; i++) {
            short newX = (short) ((short) habboItem.getX() + i);
            itemListX.put(habboItem.getX() + i, newX);
        }
        for (int i = 0; i < length; i++) {
            short newY = (short) ((short) habboItem.getY() + i);
            itemListY.put(habboItem.getY() + i, newY);
        }
    }

    public void ComprobateEnterCarcelPrisionero(Room room, Habbo habbo) {
        try {
            if (PoliceTransportManager.shouldSkipDefaultPrisonPlacement(habbo)) {
                return;
            }

            if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                // Cuando entras a una sala distinta a la prision, cuando fuiste arrestado
                if (room.getId() != RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("police").getRoomId()) {
                    // Si fuimos encarcelados
                    if (habboRp.getDelitosHabbo().getEncarceled() == true) {
                        Emulator.getThreading().run(() -> {
                            // nos envía
                            habbo.getClient().sendResponse(new ForwardToRoomComposer(
                                    RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_police.id")));
                        }, 500);
                    }
                }

                else

                // Cuando entres a la carcel
                if (room.getId() == RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("police").getRoomId()) {

                    for (HabboItem item : room.getFloorItems()) {
                        if (item.getBaseItem().getName().equalsIgnoreCase("army_police_officetent")) {
                            items_centro_policial.add(item);
                            this.setCasillasItem(item);
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("cpunk_c15_road")) {
                            items_calle.add(item);
                        }

                        if (item.getBaseItem().getName().equalsIgnoreCase("stone_police")) {
                            this.items_prisionero.add(item);
                        }

                    }

                    // Eeres prisionero te envia dentro de la prision.
                    if (habboRp.getDelitosHabbo().getEncarceled() == true) {
                        sendPrisonerToRandomRock(room, habbo, habboRp, this.items_prisionero);

                        return;
                    } else {

                        habboRp.checkSettings("enter_prision_encarceled", "0");

                        if (habboRp.getInt("enter_prision_encarceled") == 1) {

                            // Si no eres prisionero, te envia a la calle
                            if (items_calle.size() >= 1) {
                                int i = Emulator.getRandom().nextInt(items_calle.size());
                                HabboItem item = items_calle.get(i);

                                Emulator.getThreading().run(() -> {
                                    teleportUnitToTile(habbo.getRoomUnit(),
                                            room.getLayout().getTile(item.getX(), item.getY()));
                                }, 500);

                                Emulator.getThreading().run(() -> {
                                    teleportUnitToTile(habbo.getRoomUnit(),
                                            room.getLayout().getTile(item.getX(), item.getY()));
                                }, 1000);

                                habboRp.updateData("enter_prision_encarceled", "0");
                            }
                        }

                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public boolean sendTransportedPrisonerToRandomRock(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getHabboInfo().getCurrentRoom() != room) {
            return false;
        }

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp == null || !habboRp.getDelitosHabbo().getEncarceled()) {
                return false;
            }

            List<HabboItem> prisonRocks = new ArrayList<>();
            for (HabboItem item : room.getFloorItems()) {
                if (item.getBaseItem().getName().equalsIgnoreCase("stone_police")) {
                    prisonRocks.add(item);
                }
            }

            return sendPrisonerToRandomRock(room, habbo, habboRp, prisonRocks);
        } catch (java.sql.SQLException exception) {
            Emulator.getLogging().logSQLException(exception);
            return false;
        }
    }

    private boolean sendPrisonerToRandomRock(Room room, Habbo habbo, HabboRp habboRp,
            List<HabboItem> prisonRocks) {
        if (prisonRocks.isEmpty()) {
            return false;
        }

        HabboItem item = prisonRocks.get(Emulator.getRandom().nextInt(prisonRocks.size()));
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        if (tile == null) {
            return false;
        }

        Emulator.getThreading().run(() -> teleportUnitToTile(habbo.getRoomUnit(), tile), 500);
        Emulator.getThreading().run(() -> teleportUnitToTile(habbo.getRoomUnit(), tile), 1000);
        habboRp.getDelitosHabbo().createTaskActive("EncarceledMinarRocas");
        Emulator.getThreading().run(() -> habboRp.getDelitosHabbo().changeLookPrisionero(habbo), 500);
        return true;
    }

    public static void getReceibedStepOnTileEvent(UserTakeStepEvent event) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo);
            if (habboRp != null) {
                if (habboRp.getHabbo().getHabboInfo().getCurrentRoom() != null) {

                    Room room = habboRp.getHabbo().getHabboInfo().getCurrentRoom();

                    if (room.getId() == RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("police").getRoomId()) {

                        if (items_centro_policial.size() >= 1) {

                            int evento_X = (int) event.toLocation.x;
                            int evento_Y = (int) event.toLocation.y;

                            if (itemListX.get(evento_X) != null && itemListY.get(evento_Y) != null) {

                                // Cuando el nivel sea inferior a 1 o no tengas permitido el acceso
                                if (habboRp.getBoolean("work_polices") == false) {

                                    int i = Emulator.getRandom().nextInt(items_calle.size());
                                    HabboItem itemMove = items_calle.get(i);

                                    teleportUnitToTile(habboRp.getHabbo().getRoomUnit(),
                                            room.getLayout().getTile(itemMove.getX(), itemMove.getY()));

                                    habboRp.getHabbo().whisper(
                                            "Necesitas ser policia para acceder a esta zona. (Activa el reto correspondiente para otorgarte el acceso)");
                                    return;
                                }

                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
        Room room = roomUnit.getRoom();

        if (room == null) {
            return;
        }

        // makes a temporary effect
        roomUnit.getRoom().unIdle(roomUnit.getRoom().getHabbo(roomUnit));
        room.sendComposer(new RoomUserEffectComposer(roomUnit, 4).compose());
        Emulator.getThreading().run(new SendRoomUnitEffectComposer(room, roomUnit), WiredHandler.TELEPORT_DELAY + 1000);

        if (tile == roomUnit.getCurrentLocation()) {
            return;
        }

        if (tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {

            RoomTile alternativeTile = null;
            List<RoomTile> optionalTiles = room.getLayout().getTilesAround(tile);

            Collections.reverse(optionalTiles);
            for (RoomTile optionalTile : optionalTiles) {
                if (optionalTile.state != RoomTileState.INVALID && optionalTile.state != RoomTileState.LAY
                        && optionalTile.state != RoomTileState.BLOCKED) {
                    alternativeTile = optionalTile;

                    break;
                }
            }

            if (alternativeTile != null) {
                tile = alternativeTile;
            }
        }

        Emulator.getThreading().run(() -> {
            roomUnit.isWiredTeleporting = true;
        }, Math.max(0, WiredHandler.TELEPORT_DELAY - 500));
        Emulator.getThreading()
                .run(new RoomUnitTeleport(roomUnit, room, tile.x, tile.y,
                        tile.getStackHeight() + (tile.state == RoomTileState.SIT ? -0.5 : 0), roomUnit.getEffectId()),
                        WiredHandler.TELEPORT_DELAY);
    }
}

package com.srth.plugin.ActionItems.Actions.Rocas;

import java.sql.SQLException;
import java.util.EventListener;
import java.util.concurrent.ConcurrentHashMap;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.furniture.FurnitureMovedEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Turnos.HabboTurnos;
import com.srth.plugin.habbo.HabboRp;

public class RocasAction implements EventListener {

    private final HabboItem item;
    private final Habbo habbo;
    private HabboRp habboRp;
    private int hits = 0;

    private int maxHitsCalculated = 30;
    private int piedraMin = 1;
    private int piedraMax = 1;
    private String mensajeInformativo = "";

    private static final ConcurrentHashMap<Integer, Integer> cooldowns = new ConcurrentHashMap<>();
    private static final int COOLDOWN_TIME = 2;
    private static final int RESET_ROCK_TIME = 60;
    private boolean moverHabboToFurni = true;

    public RocasAction(HabboItem item, Habbo habbo) {
        this.item = item;
        this.habbo = habbo;

        if (canStartAction()) {
            int currentTime = Emulator.getIntUnixTimestamp();
            int lastAction = cooldowns.getOrDefault(habbo.getHabboInfo().getId(), 0);

            if (!item.getExtradata().equals("0")) {
                return;
            }

            try {
                habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                if (habboRp.getJobActive() != null) {
                    habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/actions/security.png",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_work_active.delito.disabled"));
                    return;
                }

                int handItem = habboRp.getHanditem();

                // Configuración dinámica según herramienta (Picos)
                if (handItem == 2005) { // Pico de Madera
                    this.maxHitsCalculated = 20;
                    this.piedraMin = 2;
                    this.piedraMax = 3;
                    this.mensajeInformativo = "Utilizando un pico de madera para picar esta roca y obtener "
                            + piedraMin + "/" + piedraMax + " piedras.";
                } else if (handItem == 2006) { // Pico de Piedra
                    this.maxHitsCalculated = 12;
                    this.piedraMin = 3;
                    this.piedraMax = 4;
                    this.mensajeInformativo = "Utilizando un pico de piedra (máxima eficiencia) para obtener "
                            + piedraMin + "/" + piedraMax + " piedras.";
                } else {
                    habboRp.showMessage("RoleplayWebsocketsRoomsInfo/recursos/pico_madera.png?v2",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("handitem_need_used.disabled")
                                    .replace("%name_objeto%", "un pico")
                                    .replace("%descripcion%", "para poder picar esta roca"));
                    return;
                }

                if (currentTime - lastAction >= COOLDOWN_TIME) {

                    HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
                    habboTurnos.registerTurno("picarRoca", this.maxHitsCalculated, "Tiempo para picar roca", 1,
                            "recursos/rocas.png?v2");
                    RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);

                    startProcess();

                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/recursos/rocas.png?v2",
                            this.mensajeInformativo,
                            9, true);
                }
            } catch (Exception e) {
                e.printStackTrace();
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
                if (moverHabboToFurni) {
                    habbo.getRoomUnit().setGoalLocation(tile);
                }
            }
            return false;
        }
        return true;
    }

    private void startProcess() {
        if (habboRp != null)
            habboRp.setProgressPendiente(true);
        runHitCycle();
    }

    private void runHitCycle() {
        Emulator.getThreading().run(() -> {
            moverHabboToFurni = false;

            if (habbo == null || !habbo.isOnline() || !canStartAction()) {
                stopProcess();
                return;
            }

            hits++;
            Room room = habbo.getHabboInfo().getCurrentRoom();
            room.giveEffect(habbo, 900, 1);

            if (hits < this.maxHitsCalculated) {
                if (hits == (this.maxHitsCalculated / 2)) {
                    updateItemState("1");
                    try {
                        habboRp.getVidaHambreDormir().showTimeProcess(
                                "RoleplayWebsocketsRoomsInfo/recursos/rocas.png?v2",
                                this.mensajeInformativo,
                                9, true);
                    } catch (Exception ignored) {
                    }
                }
                runHitCycle();
            } else {
                finishAction();
            }
        }, 1000);
    }

    private void finishAction() {
        if (habboRp != null) {
            habboRp.setProgressPendiente(false);
            cooldowns.put(habbo.getHabboInfo().getId(), Emulator.getIntUnixTimestamp());

            Room room = habbo.getHabboInfo().getCurrentRoom();
            room.giveEffect(habbo, 0, -1);

            updateItemState("2");

            String[] mensaje_fin = "¡LOGRO PICAR LA ROCA!; ¡Extrae los minerales de los escombros!".split(";");
            habbo.shout(mensaje_fin[Emulator.getRandom().nextInt(mensaje_fin.length)], RoomChatMessageBubbles.NORMAL);

            HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
            if (habboTurnos != null) {
                habboTurnos.removeTurno("picarRoca");
                RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);
            }

            int cantidadObtenida = piedraMin + Emulator.getRandom().nextInt((piedraMax - piedraMin) + 1);

            if (habboRp.getNewInventoryHabbo().getItem("roca") == null) {
                habboRp.getNewInventoryHabbo().createItem("roca", cantidadObtenida);
            } else {
                habboRp.getNewInventoryHabbo().addCantidadItem("roca", cantidadObtenida);
            }

            habbo.whisper("Has obtenido " + cantidadObtenida + " de roca.", RoomChatMessageBubbles.NORMAL);

            // Probabilidad de encontrar algo extra (Pepita de hierro o similar)
            if (Emulator.getRandom().nextInt(100) < 20) {
                if (habboRp.getNewInventoryHabbo().getItem("hierro") == null) {
                    habboRp.getNewInventoryHabbo().createItem("hierro", 1);
                } else {
                    habboRp.getNewInventoryHabbo().addCantidadItem("hierro", 1);
                }
                habbo.whisper("¡Has encontrado un fragmento de hierro entre las piedras!",
                        RoomChatMessageBubbles.NORMAL);
            }

            try {
                habboRp.getVidaHambreDormir().ocultarTimeProcess();
            } catch (Exception ignored) {
            }

            Emulator.getThreading().run(() -> {
                updateItemState("0");
            }, RESET_ROCK_TIME * 1000);
        }
        hits = 0;
    }

    private void stopProcess() {
        if (habboRp != null)
            habboRp.setProgressPendiente(false);
        moverHabboToFurni = true;

        if (item.getExtradata().equals("1")) {
            updateItemState("0");
        }

        if (habbo != null && habbo.getHabboInfo().getCurrentRoom() != null) {
            habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, 0, -1);
        }

        HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
        if (habboTurnos != null) {
            habboTurnos.removeTurno("picarRoca");
            RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);
        }

        try {
            habboRp.getVidaHambreDormir().ocultarTimeProcess();
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

    @EventHandler
    public static void onRoomItemMoved(FurnitureMovedEvent event) {
        if (event.furniture.getBaseItem().getName().contains("rp_roca_picar_byth")) {
            event.furniture.setExtradata("0");
            event.furniture.needsUpdate(true);
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(event.furniture.getRoomId());
            if (room != null) {
                room.updateItem(event.furniture);
            }
        }
    }
}
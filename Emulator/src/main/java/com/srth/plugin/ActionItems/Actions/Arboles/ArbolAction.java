package com.srth.plugin.ActionItems.Actions.Arboles;

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

public class ArbolAction implements EventListener {

    private final HabboItem item;
    private final Habbo habbo;
    private HabboRp habboRp;
    private int hits = 0;

    private int maxHitsCalculated = 30;
    private int maderaMin = 1;
    private int maderaMax = 1;
    private String mensajeInformativo = "";

    private static final ConcurrentHashMap<Integer, Integer> cooldowns = new ConcurrentHashMap<>();
    private static final int COOLDOWN_TIME = 2;
    private static final int RESET_TREE_TIME = 30;
    private boolean moverHabboToFurni = true;

    public ArbolAction(HabboItem item, Habbo habbo) {
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

                // Configuración dinámica según herramienta
                if (handItem == 2003) { // Hacha de Madera
                    this.maxHitsCalculated = 20;
                    this.maderaMin = 2;
                    this.maderaMax = 3;
                    this.mensajeInformativo = "Utilizando un hacha de madera para talar este árbol y obtener "
                            + maderaMin + "/" + maderaMax + " troncos.";
                } else if (handItem == 2004) { // Hacha de Piedra
                    this.maxHitsCalculated = 12;
                    this.maderaMin = 3;
                    this.maderaMax = 4;
                    this.mensajeInformativo = "Utilizando un hacha de piedra (máxima eficiencia) para obtener "
                            + maderaMin + "/" + maderaMax + " troncos.";
                } else if (handItem == 0) { // Mano
                    this.maxHitsCalculated = 30;
                    this.maderaMin = 1;
                    this.maderaMax = 1;
                    this.mensajeInformativo = "Utilizando sus manos para talar este árbol y obtener " + maderaMin + "/"
                            + maderaMax + " tronco de madera.";
                } else {
                    habboRp.showMessage("RoleplayWebsocketsRoomsInfo/recursos/hacha_madera.png?v2",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("handitem_need_used.disabled")
                                    .replace("%name_objeto%", "una hacha o tus manos")
                                    .replace("%descripcion%", "para poder talar este árbol"));
                    return;
                }

                if (currentTime - lastAction >= COOLDOWN_TIME) {

                    HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
                    habboTurnos.registerTurno("talarArbol", this.maxHitsCalculated, "Tiempo para talar arbol", 1,
                            "recursos/arboles.png?v2");
                    RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);

                    startProcess();

                    // Mostramos el mensaje informativo personalizado en la barra de progreso
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/recursos/arboles.png?v2",
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
                        // Mostramos el mensaje informativo personalizado en la barra de progreso
                        habboRp.getVidaHambreDormir().showTimeProcess(
                                "RoleplayWebsocketsRoomsInfo/recursos/arboles.png?v2",
                                this.mensajeInformativo,
                                9, true);
                    } catch (Exception e) {
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

            String[] mensaje_fin = "¡LOGRO TALAR EL ARBOL!; ¡Se aparta para dejar caer el Arbol!".split(";");
            habbo.shout(mensaje_fin[Emulator.getRandom().nextInt(mensaje_fin.length)], RoomChatMessageBubbles.NORMAL);

            HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
            if (habboTurnos != null) {
                habboTurnos.removeTurno("talarArbol");
                RoleplayWebsocketsRoomsInfo.getTurnosManager().showTimeTurnos(habbo);
            }

            int cantidadObtenida = maderaMin + Emulator.getRandom().nextInt((maderaMax - maderaMin) + 1);

            if (habboRp.getNewInventoryHabbo().getItem("madera") == null) {
                habboRp.getNewInventoryHabbo().createItem("madera", cantidadObtenida);
            } else {
                habboRp.getNewInventoryHabbo().addCantidadItem("madera", cantidadObtenida);
            }

            habbo.whisper("Has obtenido " + cantidadObtenida + " de madera.", RoomChatMessageBubbles.NORMAL);

            if (Emulator.getRandom().nextInt(100) < 30) {
                String tipoManzana = Emulator.getRandom().nextInt(2) == 0 ? "manzana_verde" : "manzana_roja";
                if (habboRp.getNewInventoryHabbo().getItem(tipoManzana) == null) {
                    habboRp.getNewInventoryHabbo().createItem(tipoManzana, 1);
                } else {
                    habboRp.getNewInventoryHabbo().addCantidadItem(tipoManzana, 1);
                }
                habbo.whisper("¡Has encontrado una " + tipoManzana.replace("_", " ") + " extra!",
                        RoomChatMessageBubbles.NORMAL);
            }

            try {
                habboRp.getVidaHambreDormir().ocultarTimeProcess();
            } catch (Exception ignored) {
            }

            Emulator.getThreading().run(() -> {
                updateItemState("0");
            }, RESET_TREE_TIME * 1000);
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
            habboTurnos.removeTurno("talarArbol");
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
        if (event.furniture.getBaseItem().getName().contains("rp_arbol_talar_byth")) {
            event.furniture.setExtradata("0");
            event.furniture.needsUpdate(true);
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(event.furniture.getRoomId());
            if (room != null) {
                room.updateItem(event.furniture);
            }
        }
    }
}
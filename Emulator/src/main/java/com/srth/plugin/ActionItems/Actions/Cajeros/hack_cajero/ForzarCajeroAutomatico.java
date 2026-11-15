package com.srth.plugin.ActionItems.Actions.Cajeros.hack_cajero;

import java.sql.SQLException;
import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_delitos.EventosDelitosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.TasksWorks;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;
import gnu.trove.map.hash.THashMap;

public class ForzarCajeroAutomatico extends Scheduler {

    private final HabboItem item;
    private Habbo habbo;
    private HabboRp habboRp;

    private final int timeActionSuccess = RoleplayWebsocketsRoomsInfo.getConfig()
            .getInt("hack_cajero.action.success.time.interval");
    private final int timeActiveInterval = RoleplayWebsocketsRoomsInfo.getConfig()
            .getInt("hack_cajero.active.time.interval");
    private final int cooldownSeconds = 15;

    private int startTime;
    private int startTimeProgress;
    private int roomUnitId = -1;
    private int aleatorioResult;
    private int amount;

    // Mensajes de Rol (Acciones iniciales detalladas)
    private final String[] accionInicio = {
            "*Saca una llave maestra de su bolsillo y la introduce con cuidado en la ranura del cajero*",
            "*Inserta su llave en el puerto de mantenimiento del cajero y comienza a forzar el mecanismo*",
            "*Aplica presión con la llave maestra mientras intenta escuchar el desbloqueo de los engranajes*",
            "*Introduce una ganzúa electrónica junto a su llave para puentear el sistema del cajero*",
            "*Forcejea la cerradura del cajero con movimientos precisos usando su llave de servicio*",
            "*Toma su llave maestra y la encaja en la ranura oculta del panel inferior*"
    };

    // Gritos de tensión y esfuerzo (Durante el progreso)
    private final String[] gritosProgreso = {
            "¡Casi... solo un giro más y la cerradura cederá!",
            "¡Maldición, esta llave se está atascando!",
            "¡Vamos, ábrete de una vez... no tengo todo el día!",
            "¡Escucho los engranajes... ya casi está!",
            "¡Rápido, rápido... alguien podría verme!",
            "¡Solo un poco más de presión y el panel caerá!"
    };

    // Mensajes de éxito (Al finalizar)
    private final String[] exitoRol = {
            "*Escucha un 'click' seco y el compartimento de efectivo se abre de golpe*",
            "*Logra girar la llave completamente y los billetes comienzan a ser expulsados*",
            "*Sonríe al ver cómo el mecanismo cede y accede a la bandeja de dinero*"
    };

    public ForzarCajeroAutomatico(HabboItem item, Habbo habbo) {
        super(1);
        this.item = item;
        this.habbo = habbo;

        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        } catch (SQLException ignored) {
        }

        if (canStartAction()) {
            startProcess();
        }
    }

    private boolean canStartAction() {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                .getInt("global_room.category.roleplay.id"))
            return false;

        if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                habbo.getRoomUnit().getCurrentLocation())) {
            RoomTile tile = HabboItem.getSquareInFront(room.getLayout(), item);
            if (tile != null && tile.isWalkable() && tile != room.getLayout().getDoorTile()) {
                habbo.getRoomUnit().setGoalLocation(tile);
            }
            return false;
        }

        if (item.getExtradata().equals("1") || habboRp.getHanditem() != 1035) {
            if (habboRp.getHanditem() != 1035) {
                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/hack_cajeros/llave.png?v2",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("hack_cajero.tablet.disabled"));
            }
            return false;
        }

        int ultimoTimeStamp = habboRp.checkSettings("hack_cajero.ultimo.timestamp", 0);
        if (Emulator.getIntUnixTimestamp() < ultimoTimeStamp + cooldownSeconds) {
            showCooldownMessage(ultimoTimeStamp);
            return false;
        }

        return true;
    }

    private void startProcess() {
        try {
            HabboClient habboClient = HabboClientManager.getHabboClient(habbo);
            if (habboClient != null)
                habboClient.send(new PlaySound("PlaySound", true, "/actions/forzandocajero.mp3", habbo));

            habboRp.getVidaHambreDormir().showTimeProcess(
                    "RoleplayWebsocketsRoomsInfo/hack_cajeros/CAJERO.png?v2",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.hackeo_cajero.process"),
                    timeActionSuccess, true);

            // FEEDBACK DE ROL (INICIO)
            habbo.talk(accionInicio[Emulator.getRandom().nextInt(accionInicio.length)],
                    RoomChatMessageBubbles.NORMAL);

            habboRp.setProgressPendiente(true);
            habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, 4, -1);

            this.roomUnitId = habbo.getRoomUnit().getId();
            this.startTime = Emulator.getIntUnixTimestamp();
            this.startTimeProgress = startTime;
            this.aleatorioResult = (Emulator.getRandom().nextInt(100) >= 30) ? 1 : 2;

            updateItemState("1");
            Emulator.getThreading().run(this);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void run() {
        super.run();
        if (roomUnitId == -1)
            return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || habbo.getRoomUnit().getId() != roomUnitId) {
            stopAction(false);
            return;
        }

        if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                habbo.getRoomUnit().getCurrentLocation())) {
            stopAction(true);
            return;
        }

        int now = Emulator.getIntUnixTimestamp();

        // FEEDBACK DE ROL (DURANTE PROGRESO - Cada 10s)
        if (now - startTimeProgress >= 10) {
            startTimeProgress = now;

            // Shouts aleatorios de tensión
            habbo.shout(gritosProgreso[Emulator.getRandom().nextInt(gritosProgreso.length)],
                    RoomChatMessageBubbles.RED);

            String[] progressMsgs = RoleplayWebsocketsRoomsInfo.getTexts()
                    .getValue("action_tablet_hacker.notif.accion.progress").split(";");
            habboRp.showMessage("RoleplayWebsocketsRoomsInfo/hack_cajeros/CAJERO.png",
                    progressMsgs[Emulator.getRandom().nextInt(progressMsgs.length)]);
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/hack_cajeros/CAJERO.png", "action_tablet_hacker.notif.accion.progress",
                    habbo.getClient());
        }

        if (now - startTime >= timeActionSuccess) {
            if (aleatorioResult == 2) {
                handleFailure(room);
            } else {
                handleSuccess(room);
            }
            this.roomUnitId = -1;
            this.setDisposed(true);
        }
    }

    private void handleSuccess(Room room) {
        int chanceMoney = Emulator.getRandom().nextInt(30);
        amount = (chanceMoney >= 20 || chanceMoney < 10) ? 1 : 2;

        habboRp.givePoints(amount);

        // Feedback de éxito aleatorio
        habbo.talk(exitoRol[Emulator.getRandom().nextInt(exitoRol.length)], RoomChatMessageBubbles.NORMAL);

        String[] successMsgs = RoleplayWebsocketsRoomsInfo.getTexts().getValue("hack_cajero.hackeo.notif.msg.success")
                .split(";");
        String msg = successMsgs[Emulator.getRandom().nextInt(successMsgs.length)];

        habbo.talk(msg, RoomChatMessageBubbles.GREEN);
        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/hack_cajeros/CAJERO.png", msg);

        sendBubbleReward();
        finalizeCommon(room, true);
    }

    private void handleFailure(Room room) {
        // Acción de fallo
        habbo.shout("¡Maldición! El sistema se ha bloqueado...", RoomChatMessageBubbles.RED);

        String[] failMsgs = RoleplayWebsocketsRoomsInfo.getTexts().getValue("hack_cajero.hackeo.notif.msg.nosuccess")
                .split(";");
        habbo.talk(failMsgs[Emulator.getRandom().nextInt(failMsgs.length)], RoomChatMessageBubbles.RED);
        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/hack_cajeros/CAJERO.png",
                "hack_cajero.hackeo.nosuccess", habbo.getClient());

        finalizeCommon(room, false);
    }

    private void finalizeCommon(Room room, boolean isSuccess) {
        try {
            habbo.getRoomUnit().setCanWalk(true);
            room.giveEffect(habbo, 0, -1);
            habboRp.getVidaHambreDormir().ocultarTimeProcess();
            habboRp.setProgressPendiente(false);
            habboRp.updateData("hack_cajero.ultimo.timestamp", Emulator.getIntUnixTimestamp() + "");

            if (isSuccess)
                habboRp.getTareas().insertProgress("ForzarCajero");

            new EventosDelitosTutorial().eventForzarCajero(habbo);
            RoleplayWebsocketsRoomsInfo.utilities().nextLevelProgress(habbo);
            new TasksWorks().CheckAndInsertTaskActive(item, habbo);

            habboRp.setHabboDelitos(habbo);
            habboRp.getDelitosHabbo().incrementDelitos(1);
            habboRp.getDelitosHabbo().sendAlertToPolices(habbo);

            scheduleItemReset(room);
        } catch (Exception ignored) {
        }
    }

    private void stopAction(boolean resetEffect) {
        try {
            this.roomUnitId = -1;
            updateItemState("0");
            habboRp.getVidaHambreDormir().ocultarTimeProcess();
            habboRp.setProgressPendiente(false);
            if (resetEffect)
                roomUnitId = -1; // Flag para parar el scheduler
            habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, 0, -1);
            this.setDisposed(true);
        } catch (Exception ignored) {
        }
    }

    private void updateItemState(String state) {
        item.setExtradata(state);
        item.needsUpdate(true);
        if (habbo.getHabboInfo().getCurrentRoom() != null) {
            habbo.getHabboInfo().getCurrentRoom().updateItem(item);
        }
    }

    private void scheduleItemReset(Room room) {
        Emulator.getThreading().run(() -> updateItemState("0"), timeActiveInterval * 1000L);
    }

    private void sendBubbleReward() {
        String itemInit = RoleplayWebsocketsRoomsInfo.getConfig().getValue("seasonal.rol.primary.item.starts.name");
        THashMap<String, String> keys = new THashMap<>();
        keys.put("display", "BUBBLE");
        keys.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/hack_cajeros/" + itemInit + "_"
                + amount + ".png");
        keys.put("message", RoleplayWebsocketsRoomsInfo.getTexts().getValue("interactions_global.notif.money.cash")
                .replace("%monto%", String.valueOf(amount))
                .replace("%type%", RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol")));
        habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
    }

    private void showCooldownMessage(int lastTs) {
        int remaining = (lastTs + cooldownSeconds) - Emulator.getIntUnixTimestamp();
        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/hack_cajeros/CAJERO.png",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue("interactions_global.time.interval")
                        .replace("%total_time%", remaining + " segundos.")
                        .replace("%descripcion_rol%", "forzar cajeros"));
    }
}
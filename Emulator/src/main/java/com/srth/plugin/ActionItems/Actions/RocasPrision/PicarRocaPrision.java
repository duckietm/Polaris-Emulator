package com.srth.plugin.ActionItems.Actions.RocasPrision;

import java.sql.SQLException;
import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;
import gnu.trove.map.hash.THashMap;

public class PicarRocaPrision extends Scheduler {

    private final HabboItem item;
    private Habbo habbo;
    private HabboRp habboRp;
    private int roomUnitId = -1;
    private int elapsedSeconds = 0;
    private final int default_sumatoria = 30;
    private final int penalizacionSegundos = 5;

    // Mensajes de Rol: Picando roca
    private final String[] accionPicar = {
            "*Levanta el pico con fuerza y lo descarga contra la roca*",
            "*Golpea la piedra buscando una veta más débil*",
            "*Limpia el sudor de su frente y sigue picando con insistencia*",
            "*El sonido del metal chocando contra la piedra resuena en la prisión*",
            "*Impacta la roca con precisión, saltando pequeños fragmentos por el aire*"
    };

    private final String[] quejasPrisionero = {
            "¡Maldita roca, es más dura que mi sentencia!",
            "¿Cuándo acabará este trabajo forzado...?",
            "¡Uf! Mis brazos ya no pueden más...",
            "¡Solo un poco más para terminar este turno!",
            "¡Pica, pica... esto parece no tener fin!"
    };

    public PicarRocaPrision(HabboItem item, Habbo habbo) {
        super(1);
        this.item = item;
        this.habbo = habbo;

        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (canStartAction()) {
                startProcess();
            }
        } catch (SQLException ignored) {
        }
    }

    private boolean canStartAction() {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                .getInt("global_room.category.roleplay.id"))
            return false;

        if (!habboRp.getDelitosHabbo().getEncarceled())
            return false;

        if (!item.getExtradata().equals("0"))
            return false;

        // Comprobación de penalización
        int penalizacionTs = habboRp.checkSettings("picar_roca.penalizacion.timestamp", 0);
        if (Emulator.getIntUnixTimestamp() < penalizacionTs) {
            int restantes = penalizacionTs - Emulator.getIntUnixTimestamp();
            habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/roca.png",
                    "Debes esperar " + restantes + " segundos por haber abandonado el trabajo.");
            return false;
        }

        return true;
    }

    private void startProcess() {
        sendBubbleNotification(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_encarceled.notif.time"));

        try {
            // Sonido y Efecto visual
            habboRp.getHabboClient().send(new PlaySound("PlaySound", true, "cajero_1.mp3", habbo));
            habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, 4, -1);

            // Barra de progreso
            habboRp.getVidaHambreDormir().showTimeProcess(
                    "RoleplayWebsocketsRoomsInfo/job_polices/roca.png?v2",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.roca.process"),
                    default_sumatoria, true);

            habboRp.setProgressPendiente(true);
        } catch (Exception ignored) {
        }

        habbo.talk("*Sujeta firmemente el pico y comienza su jornada de trabajo forzado*",
                RoomChatMessageBubbles.NORMAL);

        this.roomUnitId = habbo.getRoomUnit().getId();
        this.elapsedSeconds = 0;
        this.disposed = false;

        updateItemState("1");

        // Lanzar el ciclo repetitivo cada 1 segundo exacto
        Emulator.getThreading().run(this, 1000);
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            Emulator.getThreading().run(this, 1000);
        }
    }

    @Override
    public void run() {
        super.run();

        if (this.disposed || roomUnitId == -1 || habbo == null)
            return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || habbo.getRoomUnit().getId() != roomUnitId) {
            stopAction();
            return;
        }

        // Si se aleja de la roca
        if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                habbo.getRoomUnit().getCurrentLocation())) {
            stopAction();
            return;
        }

        // Animación de picar cada segundo o en cada tick
        room.sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.WAVE).compose());

        // Incrementamos el contador segundo a segundo
        elapsedSeconds++;

        // Feedback cada 10 segundos
        if (elapsedSeconds % 10 == 0) {
            if (Emulator.getRandom().nextBoolean()) {
                habbo.talk(accionPicar[Emulator.getRandom().nextInt(accionPicar.length)],
                        RoomChatMessageBubbles.NORMAL);
            } else {
                habbo.shout(quejasPrisionero[Emulator.getRandom().nextInt(quejasPrisionero.length)],
                        RoomChatMessageBubbles.NORMAL);
            }

            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/job_polices/roca.png",
                    "action_encarceled.notif.accion.progress", habbo.getClient());
        }

        // Finalización exitosa al alcanzar el tiempo total (default_sumatoria = 30s)
        if (elapsedSeconds >= default_sumatoria) {
            handleCompletion();
            this.roomUnitId = -1;
            this.setDisposed(true);
            return;
        }

        // Reprogramar el siguiente tick en 1 segundo exacto si sigue activo
        if (!this.disposed) {
            Emulator.getThreading().run(this, 1000);
        }
    }

    private void handleCompletion() {
        try {
            Room room = habbo.getHabboInfo().getCurrentRoom();
            resetHabboState(room);
            updateItemState("0");

            String[] msgs = RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_encarceled.notif.msg.success")
                    .split(";");
            habbo.talk(msgs[Emulator.getRandom().nextInt(msgs.length)], RoomChatMessageBubbles.BLACK);

            habboRp.getVidaHambreDormir().ocultarTimeProcess();
            habboRp.setProgressPendiente(false);
            habboRp.getDelitosHabbo().getPicarRocas().incrementProgress();
            habboRp.getDelitosHabbo().showTask();
            habboRp.getTareas().insertProgress("PicarRocasPrision");

            if (habboRp.getDelitosHabbo().getPicarRocas().getProgress() >= habboRp.getDelitosHabbo().getPicarRocas()
                    .getProgressRequired()) {
                liberationProcess();
            }

            ItemActionManager.deletePicarRocaPrision(habbo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void liberationProcess() {
        habboRp.getDelitosHabbo().setEncarceled(false);
        habboRp.getTareas().insertProgress("SerLiberadoPrision");
        habboRp.getNewInventoryHabbo().addCantidadItem("expediente", 1);
        habboRp.getDelitosHabbo().exitPrision();

        Emulator.getThreading().run(() -> {
            habboRp.getDelitosHabbo().getPicarRocas().complete();
            habboRp.getDelitosHabbo().completeTask();
            habboRp.getDelitosHabbo().resetDelitos();

            habbo.getClient().sendResponse(new ForwardToRoomComposer(habbo.getHabboInfo().getCurrentRoom().getId()));
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/job_polices/arresto_false.png",
                    "action_encarceled.notif.time.success", habbo.getClient());
        }, 100);
    }

    private void stopAction() {
        try {
            // Grito de abandono solicitado
            habbo.shout("¡Dejo de realizar la acción!", RoomChatMessageBubbles.RED);

            // Aplicar penalización de 5 segundos
            habboRp.updateData("picar_roca.penalizacion.timestamp",
                    (Emulator.getIntUnixTimestamp() + penalizacionSegundos) + "");

            this.roomUnitId = -1;
            resetHabboState(habbo.getHabboInfo().getCurrentRoom());
            updateItemState("0");
            habboRp.getVidaHambreDormir().ocultarTimeProcess();
            habboRp.setProgressPendiente(false);
            ItemActionManager.deletePicarRocaPrision(habbo);
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

    private void resetHabboState(Room room) {
        if (room == null)
            return;
        room.giveEffect(habbo, 0, -1);
        room.sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.NONE).compose());
    }

    private void sendBubbleNotification(String message) {
        THashMap<String, String> keys = new THashMap<>();
        keys.put("display", "BUBBLE");
        keys.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/job_polices/roca.png");
        keys.put("message", message);
        habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
    }
}
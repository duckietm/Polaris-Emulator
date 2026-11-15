package com.srth.plugin.ActionItems.Actions.PuertaPrision;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.rooms.RoomUserAction;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserActionComposer;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Delitos.Task.PicarRocas;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.ItemUsuario;
import com.srth.plugin.metrics.RoleplayMetrics;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.map.hash.THashMap;

public class ForzarPuertaPrision extends Scheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForzarPuertaPrision.class);
    private static final int DURATION_SECONDS = 60;
    private static final int FEEDBACK_INTERVAL_SECONDS = 10;
    private static final String LOCKPICK_ITEM = "ganzua";

    private final HabboItem item;
    private final Habbo habbo;
    private final HabboRp habboRp;
    private final Room startRoom;
    private final long startedAtNanos;
    private int roomUnitId = -1;
    private int startTimestamp;
    private int lastFeedbackTimestamp;

    public ForzarPuertaPrision(HabboItem item, Habbo habbo) throws SQLException {
        super(1);
        this.item = item;
        this.habbo = habbo;
        this.startedAtNanos = System.nanoTime();

        Room room = habbo == null ? null : habbo.getHabboInfo().getCurrentRoom();
        if (item == null || habbo == null || room == null
                || habbo.getRoomUnit().getRoomUnitType() != RoomUnitType.USER
                || room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {
            habboRp = null;
            startRoom = null;
            return;
        }

        habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        startRoom = room;
        if (habboRp == null || !habboRp.getDelitosHabbo().getEncarceled()) {
            return;
        }

        ForzarPuertaPrision currentAction = ItemActionManager.getForzarPuertaPrision(habbo);
        if (currentAction != null && !currentAction.isDisposed()) {
            return;
        }

        if (!"0".equals(item.getExtradata())) {
            showMessage("action.force_puerta_prision.busy");
            return;
        }

        ItemUsuario lockpick = habboRp.getNewInventoryHabbo().getItem(LOCKPICK_ITEM);
        if (lockpick == null || lockpick.getCantidad() < 1) {
            showMessage("action.force_puerta_prision.no_ganzua");
            return;
        }

        if (!isNextToDoor(room)) {
            RoleplayWebsocketsRoomsInfo.getItemActionManager().moverHabboFrontObjet(item, habbo);
            return;
        }

        habboRp.getNewInventoryHabbo().removeCantidadItem(LOCKPICK_ITEM, 1);
        this.roomUnitId = habbo.getRoomUnit().getId();
        this.startTimestamp = Emulator.getIntUnixTimestamp();
        this.lastFeedbackTimestamp = this.startTimestamp;
        this.disposed = false;
        ItemActionManager.addPuerta(habbo, this);

        try {
            showStartAlert();
            room.giveEffect(habbo, 4, -1);
            habboRp.getHabboClient().send(new PlaySound("PlaySound", true, "cajero_1.mp3", habbo));
            showProgress();
            habboRp.setProgressPendiente(true);
            habbo.shout("*Comienza a forzar la cerradura de la puerta de la prisión*",
                    RoomChatMessageBubbles.NORMAL);
            Emulator.getThreading().run(this, 1000);
        } catch (SQLException | RuntimeException exception) {
            habboRp.getNewInventoryHabbo().addCantidadItem(LOCKPICK_ITEM, 1);
            finish(room, true);
            throw exception;
        }
    }

    @Override
    public void run() {
        super.run();
        if (this.disposed || this.roomUnitId == -1) {
            return;
        }

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null || room != startRoom || habbo.getRoomUnit().getId() != roomUnitId
                || !isNextToDoor(room) || !habboRp.getDelitosHabbo().getEncarceled()) {
            cancelAttempt(room);
            return;
        }

        room.sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.WAVE).compose());
        int now = Emulator.getIntUnixTimestamp();
        if (now - lastFeedbackTimestamp >= FEEDBACK_INTERVAL_SECONDS) {
            room.giveEffect(habbo, 4, -1);
            showProgressMessage();
            lastFeedbackTimestamp = now;
        }

        if (now - startTimestamp >= DURATION_SECONDS) {
            completeAttempt(room);
            return;
        }

        Emulator.getThreading().run(this, 1000);
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    private boolean isNextToDoor(Room room) {
        if (room.getLayout() == null) {
            return false;
        }
        RoomTile doorTile = room.getLayout().getTile(item.getX(), item.getY());
        RoomTile userTile = habbo.getRoomUnit().getCurrentLocation();
        return doorTile != null && userTile != null && RoomLayout.tilesAdjecent(doorTile, userTile);
    }

    private void showStartAlert() {
        THashMap<String, String> keys = new THashMap<>();
        keys.put("display", "BUBBLE");
        keys.put("image", RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                .replace("%url.hotel.base%", RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base"))
                + "icons/" + item.getBaseItem().getName() + "_icon.png");
        keys.put("message", RoleplayWebsocketsRoomsInfo.getTexts()
                .getValue("action.force_puerta_prision.init_1"));
        habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));

        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                "RoleplayWebsocketsRoomsInfo/job_polices/prison_gate.png?v2",
                "action.force_puerta_prision.init_2", habbo.getClient());
    }

    private void showProgress() throws SQLException {
        String[] messages = RoleplayWebsocketsRoomsInfo.getTexts()
                .getValue("action.force_puerta_prision.notif.accion.progress").split(";");
        String message = messages[Emulator.getRandom().nextInt(messages.length)];
        habboRp.getVidaHambreDormir().showTimeProcess(
                "RoleplayWebsocketsRoomsInfo/job_polices/prison_gate.png?v2",
                message, DURATION_SECONDS, true);
    }

    private void showProgressMessage() {
        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                "RoleplayWebsocketsRoomsInfo/job_polices/prison_gate.png",
                "action.force_puerta_prision.notif.accion.progress", habbo.getClient());
    }

    private void completeAttempt(Room room) {
        String[] messages = RoleplayWebsocketsRoomsInfo.getTexts()
                .getValue("action.force_puerta_prision.finalice").split(";");
        habbo.shout(messages[Emulator.getRandom().nextInt(messages.length)], RoomChatMessageBubbles.BLACK);

        habboRp.getDelitosHabbo().setEncarceled(false);
        habboRp.getTareas().insertProgress("SerLiberadoPrision");
        habboRp.getNewInventoryHabbo().addCantidadItem("expediente", 1);

        Emulator.getThreading().run(() -> {
            PicarRocas prisonTask = habboRp.getDelitosHabbo().getPicarRocas();
            if (prisonTask != null) {
                prisonTask.complete();
            }
            habboRp.getDelitosHabbo().completeTask();
            habboRp.getDelitosHabbo().resetDelitos();
            habboRp.getDelitosHabbo().exitPrision();

            if (habbo.getClient() != null && habbo.getHabboInfo().getCurrentRoom() != null) {
                habbo.getClient().sendResponse(
                        new ForwardToRoomComposer(habbo.getHabboInfo().getCurrentRoom().getId()));
                RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                        "RoleplayWebsocketsRoomsInfo/job_polices/arresto_false.png",
                        "action_encarceled.notif.time.success", habbo.getClient());
            }
        }, 100);

        finish(room, false);
    }

    private void cancelAttempt(Room room) {
        if (habbo.getClient() != null) {
            habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts()
                    .getValue("action.force_puerta_prision.cancelled"), RoomChatMessageBubbles.RED);
        }
        finish(room, true);
    }

    private void finish(Room room, boolean failed) {
        finishAnimation(room);
        if (habboRp != null) {
            try {
                habboRp.getVidaHambreDormir().ocultarTimeProcess();
            } catch (SQLException exception) {
                LOGGER.error("Failed to hide prison lockpick progress for {}", habbo.getHabboInfo().getUsername(),
                        exception);
            }
            habboRp.setProgressPendiente(false);
        }
        cleanup();
        RoleplayMetrics.record("action.prison_lockpick", startedAtNanos, failed);
    }

    private void finishAnimation(Room room) {
        if (room != null && habbo.getRoomUnit() != null) {
            room.giveEffect(habbo, 0, -1);
            room.sendComposer(new RoomUserActionComposer(habbo.getRoomUnit(), RoomUserAction.NONE).compose());
        }
    }

    private void cleanup() {
        this.roomUnitId = -1;
        this.disposed = true;
        ItemActionManager.deleteForzarPuertaPrision(habbo);
    }

    private void showMessage(String key) {
        if (habbo.getClient() != null) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "RoleplayWebsocketsRoomsInfo/job_polices/prison_gate.png",
                    key, habbo.getClient());
        }
    }
}

package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.ActionUsers.comprobateTaskForAction;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.OutCancelTaskActive;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class UserActionArrestar {

    private boolean acostar = true;

    public UserActionArrestar(GameClient gameClient, Habbo habbo, int value) throws SQLException {

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            gameClient.getHabbo()
                    .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        if (habbo != null) {
            if (habbo != gameClient.getHabbo()) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(gameClient.getHabbo());

                Job job_active = habboRp.getJobActive();
                if (job_active == null) {
                    return;
                }

                // Cuando no eres policia
                if (job_active.getName().equalsIgnoreCase("police") == false) {
                    gameClient.getHabbo().whisper(
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_polices.need.active"),
                            RoomChatMessageBubbles.NORMAL);
                    return;
                }

                // Comprobaciones
                HabboRp habboRpObjetive = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                if (habboRp.getHanditem() != 2001) {

                    gameClient.getHabbo()
                            .shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_arrestar.no.enable.active")
                                    .replace("%username%", habbo.getHabboInfo().getUsername()),
                                    RoomChatMessageBubbles.NORMAL);

                    Emulator.getThreading().run(() -> {
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_polices/arma_disabled.png", "work_police.empty.arma",
                                gameClient);
                    }, 2000);

                    return;
                }

                if (habboRpObjetive.getDelitosHabbo().getDelitos() == 0) {
                    gameClient.getHabbo()
                            .whisper(
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_arrestar.no.permited")
                                            .replace("%username%", habbo.getHabboInfo().getUsername()),
                                    RoomChatMessageBubbles.NORMAL);
                    return;
                }

                if (habbo.getRoomUnit().getCurrentLocation()
                        .distance(gameClient.getHabbo().getRoomUnit().getCurrentLocation()) <= 10) {

                    // Agregar un reporte pendiente al policia
                    int ultimo_reporte_pendiente = habboRp.checkSettings("job_polices.reporte.pendiente", 0);
                    int operacion = ultimo_reporte_pendiente + 1;
                    habboRp.update("job_polices.reporte.pendiente", operacion + "");

                    habboRpObjetive.getDelitosHabbo().setEncarceled(true);

                    // Insertar progreso tarea
                    habboRpObjetive.getTareas().insertProgress("SerArrestado");

                    if (habboRpObjetive.getJobActive() != null) {
                        HabboClient client = HabboClientManager.getHabboClient(habbo);
                        LevelPlayer player = LevelPlayer.getLevelPlayer(habbo);

                        if (client != null && player != null) {
                            new OutCancelTaskActive(habbo);
                        }
                    }

                    habbo.getRoomUnit().lookAtPoint(gameClient.getHabbo().getRoomUnit().getCurrentLocation());
                    gameClient.getHabbo().getRoomUnit().lookAtPoint(habbo.getRoomUnit().getCurrentLocation());

                    // Reproducir sonido a todos
                    for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment().getHabboManager()
                            .getOnlineHabbos().entrySet()) {
                        Habbo habbomap = map.getValue();
                        if (habbomap != null && habbomap.getHabboInfo().getCurrentRoom() == room) {
                            HabboClient clientmap = HabboClientManager.getHabboClient(habbomap);
                            if (clientmap != null) {
                                clientmap.send(new PlaySound("PlaySound", true, "arresto.mp3", habbo));
                            }
                        }
                    }

                    // Insertar progreso tarea
                    habboRp.getTareas().insertProgress("ArrestarUsuario");

                    new EventosEmpleosTutorial(habboRp.getHabbo()).eventArrestarUsuario();

                    ////////////////////////////
                    // Mostrar alertas al policia
                    gameClient.getHabbo().talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_arrestar.talk")
                            .replace("%user%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.NORMAL);
                    room.giveEffect(gameClient.getHabbo(), Integer.valueOf(0), -1);
                    room.giveHandItem(gameClient.getHabbo(), 0);
                    habboRp.setHanditem(0);

                    Emulator.getThreading().run(() -> {
                        gameClient
                                .getHabbo().talk(
                                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_arrestar.talk.2")
                                                .replace("%user%", habbo.getHabboInfo().getUsername()),
                                        RoomChatMessageBubbles.NORMAL);

                    }, 1000);

                    Emulator.getThreading().run(() -> {
                        gameClient.getHabbo().whisper(
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_arrestar.talk.arma.avised"),
                                RoomChatMessageBubbles.NORMAL);
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/job_polices/arma_disabled.png", "work_police.utilice.arma",
                                gameClient);
                    }, 1500);

                    Emulator.getThreading().run(() -> {
                        // Mostrar ventana
                        RoleplayWebsocketsRoomsInfo.utilities().showAlertUser(
                                "RoleplayWebsocketsRoomsInfo/job_polices/reporte_police_pendiente.png",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_arrestar.success.2")
                                        .replace("%br%", "\n"),
                                gameClient);
                    }, 5000);

                    // Actualizar arrestos completos al policia
                    int arrestos_success_stats = habboRp.checkSettings("arrestos.success", 0);
                    habboRp.updateData("arrestos.success", arrestos_success_stats + 1 + "");

                    // Actualizar veces arrestado al delincuente
                    habboRpObjetive.getDelitosHabbo()
                            .incrementArrestos(1);

                    // Guardar el ultimo look que tenia
                    habboRpObjetive.updateData("global_ultimo_look_save", 
                            habboRpObjetive.getHabbo().getHabboInfo().getLook());

                    room.giveEffect(habboRpObjetive.getHabbo(), Integer.valueOf(12), -1);

                    InventoryHabboNew inventoryHabboNew = habboRpObjetive.getNewInventoryHabbo();
                    int cantidadCannabis = inventoryHabboNew.getItem("cannabis").getCantidad();
                    if (cantidadCannabis >= 1) {
                        inventoryHabboNew.removeCantidadItem("cannabis", cantidadCannabis);
                    }

                    RoomTile tile = habbo.getRoomUnit().getCurrentLocation();
                    if (tile == null) {
                        acostar = false;
                    }
                    for (int i = 0; i < 3; i++) {
                        RoomTile t = habbo.getHabboInfo().getCurrentRoom().getLayout().getTileInFront(tile,
                                habbo.getRoomUnit().getBodyRotation().getValue(), i);
                        if (t == null || !t.isWalkable()) {
                            acostar = false;
                        }
                    }
                    if (acostar == true) {
                        habbo.getRoomUnit().setStatus(RoomUnitStatus.LAY, 0.5 + "");
                        habbo.getHabboInfo().getCurrentRoom()
                                .sendComposer(new RoomUserStatusComposer(habbo.getRoomUnit()).compose());
                    }

                    Emulator.getThreading().run(() -> {
                        try {
                            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                    "RoleplayWebsocketsRoomsInfo/job_polices/arresto_true.gif",
                                    "cmd_arrestar.user.alert.1", habbo.getClient());

                            int room_prision = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("police")
                                    .getRoomId();

                            habbo.getClient().sendResponse(new ForwardToRoomComposer(room_prision));
                            gameClient.sendResponse(new ForwardToRoomComposer(room_prision));
                        } catch (Exception e) {
                        }
                    }, 2000);

                    Emulator.getThreading().run(() -> {
                        // Ventana alerta
                        RoleplayWebsocketsRoomsInfo.utilities().showAlertUser(
                                "RoleplayWebsocketsRoomsInfo/job_polices/roca.png", RoleplayWebsocketsRoomsInfo
                                        .getTexts().getValue("cmd_arrestar.user.alert.2").replace("%br%", "\n"),
                                habbo.getClient());
                    }, 3500);

                    new comprobateTaskForAction(this.getClass().getSimpleName(), gameClient.getHabbo());

                } else {
                    gameClient.getHabbo()
                            .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_arrestar.error.too_far")
                                    .replace("%username%", habbo.getHabboInfo().getUsername()));
                }
            } else {
                gameClient.getHabbo()
                        .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_arrestar.error.self"));
            }

        }
    }

    public void mostrarAlertaTiempodeEspera(GameClient gameClient, int ultimoTimeStamp, int time_espera_sumatoria) {

        int total_time = Emulator.getIntUnixTimestamp() - ultimoTimeStamp;

        int cuenta_total = time_espera_sumatoria - total_time;

        int seconds = cuenta_total;
        long minute = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds) * 60);
        long second = TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) * 60);

        // Mostrar alerta al usuario
        THashMap<String, String> keys2 = new THashMap<>();
        keys2.put("display", "BUBBLE");
        keys2.put("image", "${image.library.url}notifications/job_polices/arma_disabled.png");
        keys2.put("message",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue("interactions_global.time.interval")
                        .replace("segundos", "")
                        .replace("%total_time%",
                                minute + (minute > 1 ? " minutos, " : " minuto, ") + second
                                        + (second > 1 ? " segundos" : " segundo") + "")
                        .replace("%descripcion_rol%", "usar el comando de robar" + ""));

        gameClient.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));
    }

}

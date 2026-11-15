package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionUsers.comprobateTaskForAction;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

public class UserActionRobar {

    public UserActionRobar(GameClient gameClient, Habbo habbo, int value) throws SQLException {

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();
        HabboRp otherhabboStats = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        HabboRp clientHabboStats = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(gameClient.getHabbo());

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            gameClient.getHabbo()
                    .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        int gameClientID = gameClient.getHabbo().getHabboInfo().getId();
        if (gameClient.getHabbo().getRoomUnit().getCurrentLocation()
                .distance(habbo.getRoomUnit().getCurrentLocation()) <= 3) {

            if (otherhabboStats.getProgressPendiente()) {
                RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                        "/RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif",
                        "global_alert.no.accion.progress.pendiente", habbo.getClient());
                return;
            }

            int ultimoTimeStamp = clientHabboStats.checkSettings("robar.ultimo.timestamp", 0);
            int time = RoleplayWebsocketsRoomsInfo.getConfig().getInt("robar.time.interval");
            int sumatoria_time = ultimoTimeStamp + time;

            if (Emulator.getIntUnixTimestamp() >= sumatoria_time) {

                if (habbo == gameClient.getHabbo()) {
                    gameClient.getHabbo()
                            .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_robar.error.self"));
                    return;
                }

                if (otherhabboStats.getJobActive() != null
                        && otherhabboStats.getJobActive().getName().equalsIgnoreCase("police")) {
                    gameClient.getHabbo()
                            .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_robar.error.user.police")
                                    .replace("%username%", habbo.getHabboInfo().getUsername()));
                    return;
                }

                if (otherhabboStats.getProgressPendiente() == true) {
                    return;
                }

                int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");

                if (habbo.getHabboInfo().getCurrencyAmount(type_moneda) < 2) {
                    gameClient.getHabbo()
                            .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_robar.error.user.no.amount")
                                    .replace("%username%", habbo.getHabboInfo().getUsername()));
                    return;
                }

                // Registrar delito
                clientHabboStats.getDelitosHabbo().incrementDelitos(1);
                clientHabboStats.updateData("robar.ultimo.timestamp", Emulator.getIntUnixTimestamp() + "");

                // Genera una cantidad
                int monto = (int) (Math.random() * 3) + 1;
                if (monto >= 1) {

                    if (RoleplayWebsocketsRoomsInfo.getConfig()
                            .getBoolean("seasonal.rol.primary.type.its.credits") == true) {
                        // Entregar monedas al delicuente
                        gameClient.getHabbo().giveCredits(monto);
                    } else {
                        // Entregar monedas al delicuente
                        gameClient.getHabbo().givePoints(type_moneda, monto);
                    }

                    // El delincuente habla
                    Emulator.getThreading().run(() -> {

                        String[] MSG1 = RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_robar.robo.self.talk")
                                .replace("%username%", habbo.getHabboInfo().getUsername() + "")
                                .replace("%monto%", monto + "")
                                .replace("%type%", RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol"))
                                .split(";");

                        // gameClient.getHabbo().talk(MSG1[Emulator.getRandom().nextInt(MSG1.length)],
                        // RoomChatMessageBubbles.NORMAL);

                        clientHabboStats.showMessage("/RoleplayWebsocketsRoomsInfo/job_polices/arma_active.png",
                                MSG1[Emulator.getRandom().nextInt(MSG1.length)]);
                    }, 500);

                    // Restar monedas al usuario robado
                    if (RoleplayWebsocketsRoomsInfo.getConfig()
                            .getBoolean("seasonal.rol.primary.type.its.credits") == true) {
                        habbo.giveCredits(-monto);
                    } else {
                        habbo.givePoints(type_moneda, -monto);
                    }

                    // 539 (Amarrarlo)
                    // Generar efecto al usuario robado
                    room.giveEffect(habbo, Integer.valueOf(539), 6);
                    // Sienta al usuario
                    room.makeSit(habbo);

                    otherhabboStats.allowMove(false);
                    // El usuario robado habla
                    Emulator.getThreading().run(() -> {
                        String[] MSG2 = RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_robar.robo.user.talk")
                                .replace("%username%", gameClient.getHabbo().getHabboInfo().getUsername()).split(";");
                        habbo.talk(MSG2[Emulator.getRandom().nextInt(MSG2.length)], RoomChatMessageBubbles.NORMAL);
                    }, 2000);

                    // Guardar timestamp
                    clientHabboStats.updateData("work_polices_cmd_robar.ultimo.timestamp",
                            Emulator.getIntUnixTimestamp() + "");

                    Emulator.getThreading().run(() -> {
                        this.mostrarAlertaTiempodeEspera(gameClient.getHabbo().getClient(),
                                Emulator.getIntUnixTimestamp(), time);
                    }, 3000);

                    Emulator.getThreading().run(() -> {

                        // Generar efecto al usuario robado
                        room.giveEffect(habbo, Integer.valueOf(0), -1);
                        otherhabboStats.allowMove(true);
                        room.makeStand(habbo);

                    }, 10000);

                    new comprobateTaskForAction(this.getClass().getSimpleName(), gameClient.getHabbo());

                    // Insertar progreso tarea
                    clientHabboStats.getTareas().insertProgress("RobarUsuarios");
                } else {

                    gameClient.getHabbo()
                            .talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_robar.error.user.no.success")
                                    .replace("%username%", habbo.getHabboInfo().getUsername()));
                }

            } else {
                this.mostrarAlertaTiempodeEspera(gameClient.getHabbo().getClient(), ultimoTimeStamp, time);
            }

        } else {

            gameClient.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_robar.error.too_far")
                    .replace("%username%", habbo.getHabboInfo().getUsername()));

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

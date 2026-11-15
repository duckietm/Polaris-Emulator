package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

public class UserActionEmpujarOther {

    public UserActionEmpujarOther(GameClient gameClient, Habbo habbo, int value) throws SQLException {

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            gameClient.getHabbo()
                    .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        HabboRp otherhabboStats = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        if (otherhabboStats.getProgressPendiente()) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "/RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif",
                    "global_alert.no.accion.progress.pendiente", habbo.getClient());
            return;
        }

        if (gameClient.getHabbo().getRoomUnit().getCurrentLocation()
                .distance(habbo.getRoomUnit().getCurrentLocation()) <= 1) {

            RoomTile tFront = gameClient.getHabbo().getHabboInfo().getCurrentRoom().getLayout().getTileInFront(
                    gameClient.getHabbo().getRoomUnit().getCurrentLocation(),
                    gameClient.getHabbo().getRoomUnit().getBodyRotation().getValue());

            if (tFront != null && tFront.isWalkable()) {
                if (tFront.x == habbo.getRoomUnit().getX() && tFront.y == habbo.getRoomUnit().getY()) {
                    RoomTile tFrontTarget = gameClient.getHabbo().getHabboInfo().getCurrentRoom().getLayout()
                            .getTileInFront(habbo.getRoomUnit().getCurrentLocation(),
                                    gameClient.getHabbo().getRoomUnit().getBodyRotation().getValue());

                    if (tFrontTarget != null && tFrontTarget.isWalkable()) {
                        if (gameClient.getHabbo().getHabboInfo().getCurrentRoom().getLayout()
                                .getDoorTile() == tFrontTarget) {
                            gameClient.getHabbo()
                                    .whisper(Emulator.getTexts().getValue("commands.error.cmd_push.invalid")
                                            .replace("%username%", habbo.getHabboInfo().getUsername()));
                            return;
                        }
                        habbo.getRoomUnit().setGoalLocation(tFrontTarget);
                    }
                } else {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_push.cant_reach")
                            .replace("%user%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.ALERT);
                    return;
                }
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

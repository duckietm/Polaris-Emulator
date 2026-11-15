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
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.JobEmpleado;

import gnu.trove.map.hash.THashMap;

public class UserActionCurar {

    public UserActionCurar(GameClient gameClient, Habbo habbo) throws SQLException {

        Room room = gameClient.getHabbo().getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            gameClient.getHabbo()
                    .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        if (habbo != null) {

            HabboRp gameStats = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(gameClient.getHabbo());
            HabboRp habboStats = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            String job_name = "medico";
            Job job = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(job_name);

            if (job == null) {
                return;
            }

            if (gameStats.getJobActive() == null || gameStats.getJobActive() != null
                    && !gameStats.getJobActive().getName().equalsIgnoreCase("medico")) {
                gameStats.showMessage("/RoleplayWebsocketsRoomsInfo/job_medico/work_disabled.png",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.need.active"));
                return;
            }

            if (habbo != gameClient.getHabbo()) {

                if (habboStats.getVidaHambreDormir().getVida() >= 60) {
                    gameStats.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/vida.png",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.user.vida.suficiente")
                                    .replace("%username%", habbo.getHabboInfo().getUsername()));
                    return;
                }

                if (habbo.getRoomUnit().getCurrentLocation()
                        .distance(gameClient.getHabbo().getRoomUnit().getCurrentLocation()) <= 3) {

                    if (gameStats.getHanditem() != 1014) {
                        gameStats.showMessage("/RoleplayWebsocketsRoomsInfo/job_medico/jeringa.png",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("necesited_item_for_used")
                                        .replace("%NAME_OBJETO%", "una jeringa"));
                        return;
                    }

                    if (room.getId() != gameStats.getJobActive().getRoomId()) {
                        // Insertar tarea al empleado
                        gameStats.getTareas().insertProgress("CurarUsuarioFuera");
                    } else {
                        // Insertar tarea al empleado
                        gameStats.getTareas().insertProgress("CurarUsuario");
                    }

                    ////////////////////////////
                    // Mostrar alertas al medico
                    gameClient.getHabbo().talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.talk")
                            .replace("%user%", habbo.getHabboInfo().getUsername()), RoomChatMessageBubbles.NORMAL);
                    room.giveEffect(gameClient.getHabbo(), Integer.valueOf(0), -1);
                    gameStats.allowMove(false);
                    habboStats.allowMove(false);

                    if (room.getId() != gameStats.getJobActive().getRoomId()) {
                        // Insertar tarea
                        habboStats.getTareas().insertProgress("SerPacienteCuradoFuera");
                    } else {
                        // Insertar tarea
                        habboStats.getTareas().insertProgress("SerPacienteCurado");
                    }

                    /////////////////////////////
                    // Mostrar alerta al usuario
                    Emulator.getThreading().run(() -> {
                        try {
                            // Resetear vida y dar comida
                            habboStats.getVidaHambreDormir().resetVida(100);
                            habboStats.getVidaHambreDormir().comer(20);

                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "/RoleplayWebsocketsRoomsInfo/ws_hospital/vida_completa.png",
                                "global_alert.hospital.true.vida.success", habbo.getClient());
                        habbo.talk(
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.talk.user.success")
                                        .replace("%user%", gameClient.getHabbo().getHabboInfo().getUsername()),
                                RoomChatMessageBubbles.NORMAL);

                        room.giveEffect(habbo, Integer.valueOf(0), -1);

                        habboStats.allowMove(true);
                        gameStats.allowMove(true);

                        JobEmpleado jobEmpleado = job.getEmpleado(gameClient.getHabbo());
                        int sueldo = jobEmpleado.getSueldoAcumulado();
                        int increment = sueldo + 2;
                        // Entregar sueldo
                        jobEmpleado.pagarSueldo(increment);

                    }, 1500);

                    if (habboStats.getEnable() == 13) {
                        habboStats.setEnable(0);
                        habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, Integer.valueOf(0), -1);
                    }

                    new comprobateTaskForAction(this.getClass().getSimpleName(), gameClient.getHabbo());

                    return;
                } else {
                    gameClient.getHabbo()
                            .whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.error.too_far")
                                    .replace("%username%", habbo.getHabboInfo().getUsername()));
                }
            } else {
                gameClient.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_curar.error.self"));
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

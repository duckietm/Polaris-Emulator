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
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.habbo.Jobs.Job;
import java.util.Random;

import gnu.trove.map.hash.THashMap;

public class UserActionRevisar {

    public UserActionRevisar(GameClient gameClient, Habbo habbo) throws SQLException {

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

                // Validación de policía
                if (!job_active.getName().equalsIgnoreCase("police")) {
                    gameClient.getHabbo().whisper(
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_polices.need.active"),
                            RoomChatMessageBubbles.NORMAL);
                    return;
                }

                HabboRp habboRpObjetive = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                if (habbo.getRoomUnit().getCurrentLocation()
                        .distance(gameClient.getHabbo().getRoomUnit().getCurrentLocation()) <= 10) {

                    // --- NUEVA FUNCIÓN DE ROL Y NERVIOSISMO ---
                    executarRolRevision(gameClient, habbo);

                    InventoryHabboNew inventoryHabboNew = habboRpObjetive.getNewInventoryHabbo();
                    int cantidadCannabis = inventoryHabboNew.getItem("cannabis").getCantidad();

                    if (cantidadCannabis <= 0) {
                        gameClient.getHabbo().whisper("El ciudadano no tiene sustancias ilicitas en posesion.");
                        return;
                    }

                    // 2. Respuesta de nervios del ciudadano con delay
                    Emulator.getThreading().run(() -> {
                        gameClient.getHabbo()
                                .whisper(
                                        "El ciudadano tiene: " + cantidadCannabis + " en posesion, puedes arrestarlo.");
                    }, 1500);

                    habboRpObjetive.getDelitosHabbo().incrementDelitos(cantidadCannabis);

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

    // Función auxiliar para simular las acciones y los diálogos de tensión con
    // textos aleatorios
    private void executarRolRevision(GameClient policial, Habbo ciudadano) {

        Room room = policial.getHabbo().getHabboInfo().getCurrentRoom();
        String nombreCiudadano = ciudadano.getHabboInfo().getUsername();
        Random random = new Random();

        // Array de frases aleatorias para el policía al requisar
        String[] accionesPolicia = {
                " * Revisa minuciosamente las pertenencias y bolsillos de " + nombreCiudadano + " con cautela *",
                " * Palpa la chaqueta y el pantalón de " + nombreCiudadano + " buscando objetos sospechosos *",
                " * Le exige a " + nombreCiudadano + " que levante las manos mientras le revisa los bolsillos *",
                " * Pasa las manos con firmeza por el uniforme de " + nombreCiudadano + " inspeccionando cada rincón *"
        };

        // Array de frases aleatorias para los nervios del ciudadano
        String[] reaccionesCiudadano = {
                " * Traga grueso, le sudan las manos y evita mirar a los ojos al oficial por los nervios *",
                " * Empieza a temblar levemente mientras aprieta los puños e intenta mantener la calma *",
                " * Mira hacia los lados de forma esquiva, con la respiración agitada y sudor en la frente *",
                " * Da un leve respiro nervioso y muerde sus labios mientras el oficial lo examina *"
        };

        // Seleccionar una frase al azar para cada uno
        String frasePolicia = accionesPolicia[random.nextInt(accionesPolicia.length)];
        String fraseCiudadano = reaccionesCiudadano[random.nextInt(reaccionesCiudadano.length)];

        // 1. El oficial ejecuta la acción de revisar
        policial.getHabbo().talk(frasePolicia, RoomChatMessageBubbles.NORMAL);

        // 2. Respuesta de nervios del ciudadano con delay
        Emulator.getThreading().run(() -> {
            ciudadano.talk(fraseCiudadano, RoomChatMessageBubbles.NORMAL);
        }, 1200);
    }

    public void mostrarAlertaTiempodeEspera(GameClient gameClient, int ultimoTimeStamp, int time_espera_sumatoria) {
        int total_time = Emulator.getIntUnixTimestamp() - ultimoTimeStamp;
        int cuenta_total = time_espera_sumatoria - total_time;

        int seconds = cuenta_total;
        long minute = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds) * 60);
        long second = TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) * 60);

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
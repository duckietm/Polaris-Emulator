package com.srth.plugin.utilities;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.plugin.EventListener;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.JobEmpleado;

import gnu.trove.map.hash.THashMap;

public class UtilitesRoleplay implements EventListener {

    Logger LOGGER = LoggerFactory.getLogger(Emulator.class);

    public UtilitesRoleplay() {

    }

    public void LOGGER(String data) {
        LOGGER.info(data);
    }

    public void LOGGER_ERROR(String data) {
        LOGGER.error(data);
    }

    public String encode(String s) {
        String respuesta = "error";
        try {
            respuesta = new String(Base64.getEncoder().encode(s.getBytes("UTF8")), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return respuesta;
    }

    public void policeViewReportes(Habbo habbo) {

        try {
            int user_id = habbo.getHabboInfo().getId();
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            JobEmpleado jobEmpleado = habboRp.getJobActive().getEmpleado(habbo);

            // Mostrar ventana
            this.showAlertUser(
                    "RoleplayWebsocketsRoomsInfo/job_polices/police_level_1.gif",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("work_police.info.sueldo").replace("%br%", "\n")
                            .replace("%username%", habbo.getHabboInfo().getUsername() + "")
                            .replace("%sueldo%", jobEmpleado.getSueldoAcumulado() + "").replace("%reportes%",
                                    habboRp.getInt("job_polices.reporte.pendiente") + ""),
                    habbo.getClient());

        } catch (Exception e) {
        }
    }

    public THashMap<String, String> BubbleAlert(Habbo habbos, Habbo sender, String descripcion_delito,
            boolean followEnabled) {

        THashMap<String, String> notification = new THashMap<>();
        notification.put("display", "BUBBLE");
        notification.put("image", RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
                .replace("%LOOK%", sender.getHabboInfo().getLook()
                        + "&gesture=sml&amp;&amp;size=0&amp;direction=2&amp;head_direction=2&amp;crr=3&amp;gesture=sml&amp;frame=3"));
        notification.put("message",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue("interactions_global_delito_user")
                        .replace("%username%", sender.getHabboInfo().getUsername())
                        .replace("%descripcion%", descripcion_delito));

        final Room room = sender.getHabboInfo().getCurrentRoom();
        if (room != null && followEnabled) {
            notification.put("linkUrl", "event:navigator/goto/" + sender.getHabboInfo().getCurrentRoom().getId());
        }

        Emulator.getThreading().run(() -> {
            habbos.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, notification));
        }, 500);

        return notification;
    }

    public void nextLevelProgress(Habbo habbo) {
        int habboID = habbo.getHabboInfo().getId();
        int next_level = 0;

        // Número points level
        int ultimoNumberPointsLevel = RoleplayWebsocketsRoomsInfo.getConfigUser(habboID)
                .getInt("hack_cajero.points.level");

        // Level del usuario
        int intHackCajerosLevelUser = RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).getInt("hack_cajero.level");

        if (RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).getBoolean("hack_cajero.points.level.next") == false) {
            RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).register(habbo.getHabboInfo().getId(),
                    "hack_cajero.points.level.next", "1");
        }

        int next_points = RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).getInt("hack_cajero.points.level.next");

        // Update number points
        RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).saveToDatabase(next_points + 1 + "",
                "hack_cajero.points.level.next", habboID);

        if (RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).getInt("hack_cajero.points.level.next") >= 15) {

            // Restablecer a 1
            RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).saveToDatabase("1", "hack_cajero.points.level.next",
                    habboID);

            // Update number points
            RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).saveToDatabase(ultimoNumberPointsLevel + 1 + "",
                    "hack_cajero.points.level", habboID);

            int total_level_required = RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("hack_cajero.level." + intHackCajerosLevelUser + ".next.level.points.required");

            if (total_level_required > 0 && ultimoNumberPointsLevel + 1 >= total_level_required) {

                if (intHackCajerosLevelUser < 5) {
                    next_level = intHackCajerosLevelUser + 1;
                }

                // Update restablecer points level
                RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).saveToDatabase("1", "hack_cajero.points.level",
                        habboID);

                // Update level usuario
                RoleplayWebsocketsRoomsInfo.getConfigUser(habboID).saveToDatabase(next_level + "", "hack_cajero.level",
                        habboID);

                Emulator.getThreading().run(() -> {

                    // Mostrar alerta
                    THashMap<String, String> keys3 = new THashMap<>();
                    keys3.put("display", "BUBBLE");
                    keys3.put("image",
                            "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/level_increment/level.gif");
                    keys3.put("message",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("interactions_global_level_next.success")
                                    .replace("%total_level%", intHackCajerosLevelUser + 1 + "")
                                    .replace("%level_final%", "5"));
                    habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));

                }, 1500);

                return;

            }

            Emulator.getThreading().run(() -> {
                // Mostrar alerta
                THashMap<String, String> keys3 = new THashMap<>();
                keys3.put("display", "BUBBLE");
                keys3.put("image",
                        "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/level_increment/level_increment.gif");
                keys3.put("message",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("interactions_global_level_increment.next")
                                .replace("%total_process%", ultimoNumberPointsLevel + 1 + "")
                                .replace("%total_required%", total_level_required + ""));
                habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));

            }, 1500);

        }

    }

    public void showBubbleAlert(String image, String message, GameClient client) {

        String[] MSG = RoleplayWebsocketsRoomsInfo.getTexts().getValue(message).split(";");
        // Mostrar alerta
        THashMap<String, String> keys3 = new THashMap<>();
        keys3.put("display", "BUBBLE");
        keys3.put("image", "${image.library.url}notifications/" + image + "");
        keys3.put("message", MSG[Emulator.getRandom().nextInt(MSG.length)]);
        client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));

    }

    public void showBubbleAlertItem(String image, String message, GameClient client) {
        String[] MSG = RoleplayWebsocketsRoomsInfo.getTexts().getValue(message).split(";");
        // Mostrar alerta
        THashMap<String, String> keys3 = new THashMap<>();
        keys3.put("display", "BUBBLE");

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                .replace("%url.hotel.base%", url_hotel_base);

        keys3.put("image", url_images + "items/" + image + "");
        keys3.put("message", MSG[Emulator.getRandom().nextInt(MSG.length)]);
        client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));
    }

    public void showAlertUser(String image, String message, GameClient gameClient) {

        Habbo habbo = gameClient.getHabbo();

        THashMap<String, String> codes = new THashMap<>();
        codes.put("USERNAME", habbo.getHabboInfo().getUsername());

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String urlIMG = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base);

        codes.put("URL_IMAGE",
                urlIMG + image + "");

        codes.put("INFORMACION", message);

        ServerMessage msg = new BubbleAlertComposer("user.img.alert", codes).compose();

        gameClient.sendResponse(msg);
    }
}

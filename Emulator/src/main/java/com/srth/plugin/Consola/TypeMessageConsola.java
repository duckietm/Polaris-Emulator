package com.srth.plugin.Consola;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class TypeMessageConsola {

        // Base de la URL obtenida desde la configuración
        private static final String IMAGER_URL = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta");

        // Plantilla usando marcadores de texto explícitos en lugar de %s
        public static final String DEFAULT_MSG = "<div class='content-msg'>\n" +
                        "    <div class='content-keko'>\n" +
                        "        <img alt=\"%username%\" src=\"" + IMAGER_URL
                        + "%look%&headonly=1&size=s&gesture=sml&head_direction=2\"></img>\n" +
                        "    </div>\n" +
                        "    <b>%username%</b>\n" +
                        "    <span class='action'>\n" +
                        "        %action%\n" +
                        "    </span>\n" +
                        "</div>\n";

        public static final String USER_ACTION_MSG = "<div class='content-msg'>\n" +
                        "    <div class='content-keko'>\n" +
                        "        <img alt=\"%usernameFrom%\" src=\"" + IMAGER_URL
                        + "%lookFrom%&headonly=1&size=s&gesture=sml&head_direction=2\"></img>\n" +
                        "    </div>\n" +
                        "    <b>%usernameFrom%</b>\n" +
                        "    <span class='action'>\n" +
                        "        %action%\n" +
                        "    </span>\n" +
                        "    <div class='content-keko'>\n" +
                        "        <img alt=\"%usernameTo%\" src=\"" + IMAGER_URL
                        + "%lookTo%&headonly=1&size=s&gesture=sml&head_direction=2\"></img>\n" +
                        "    </div>\n" +
                        "    <b>%usernameTo%</b>\n" +
                        "    <div class='content-room'>\n" +
                        "        <img src=\"%imgHome%\" alt=\"\" />\n" +
                        "    </div>\n" +
                        "</div>\n";

        /**
         * Método de utilidad para formatear el mensaje de forma segura con .replace()
         */
        public static String getFormattedMessage(String type, Habbo habbo, String action) {
                String username = habbo.getHabboInfo().getUsername();
                String look = habbo.getHabboInfo().getLook();

                return type
                                .replace("%username%", username)
                                .replace("%look%", look)
                                .replace("%action%", action);
        }

        public static String getMessageUserToUser(Habbo fromHabbo, Habbo toHabbo, String action) {
                String usernameFrom = fromHabbo.getHabboInfo().getUsername();
                String lookFrom = fromHabbo.getHabboInfo().getLook();

                String usernameTo = toHabbo.getHabboInfo().getUsername();
                String lookTo = toHabbo.getHabboInfo().getLook();

                String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
                String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig()
                                .getValue("global_url.hotel.notifications")
                                .replace("%url.hotel.base%", url_hotel_base);
                String homeImg = urlNotifications + "/actions/mapa1.png";

                String formatearFrom = TypeMessageConsola.USER_ACTION_MSG.replace("%usernameFrom%", usernameFrom)
                                .replace("%lookFrom%", lookFrom).replace("%action%", action);
                String formatearTo = formatearFrom.replace("%usernameTo%", usernameTo).replace("%lookTo%", lookTo)
                                .replace("%imgHome%", homeImg);

                return formatearTo;
        }
}
package com.srth.plugin.habbo.Frank.Frank.Utils;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class TextFrank {

    public void load() {
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_welcome_ventana_title", "Mensaje de frank");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_welcome_ventana_text_inicio",
                "Hola, bienvenido al hotel, actualmente contamos con dos formas de entretenerte normal y roleplay puedes entrar a ellas a traves del navegador de salas, o presionando los siguientes botones...");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_welcome_ventana_text_buttones_quest",
                "Presiona estos botones para visitarlas");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_welcome_ventana_text_button_normal", "Normales");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_welcome_ventana_text_button_roleplay", "Roleplay");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_welcome_ventana_text_no_show", "No volver a mostrar");

        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_welcome_ventana_room_actual", "(Actual)");

        // Select roleplay
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_title", "Mensaje de frank");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_text_inicio",
                "Entraste al modo roleplay por primera vez ");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_text_buttones_quest",
                "¿Deseas realizar el tutorial, para aprender acerca del modo roleplay?");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_text_button_accept", "Si");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_accept_event", "IniciarTutorial");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_text_button_decline", "No");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_decline_event", "AbortarTutorial");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_room_actual", "(Actual)");
        RoleplayWebsocketsRoomsInfo.getTexts().register("frank_select_init_ventana_ocult_event", "OcultarVentana");
    }

}

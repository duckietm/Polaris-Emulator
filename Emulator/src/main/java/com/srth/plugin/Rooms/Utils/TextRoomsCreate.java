package com.srth.plugin.Rooms.Utils;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class TextRoomsCreate {

    public void load() {

        // Frank multiples selecciones custom
        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_title", "Seleccion multiple");
        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_text_inicio",
                "En esta ventana podras crear las salas roleplay basicas con las que funcionara el plugin roleplay...");
        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_text_buttones_quest",
                "Presiona los siguientes botones");

        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_text_hospital", "Hospital");
        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_text_button_5", "Undefined");
        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_text_calles_peligrosas_1",
                "Calles peligrosas (1)");
        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_text_calles_peligrosas_2",
                "Calles peligrosas (2)");
        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_text_calles_peligrosas_3",
                "Calles peligrosas (3)");

        RoleplayWebsocketsRoomsInfo.getTexts().register("create_rooms_roleplay_ventana_room_exist", "(Ya creada)");
    }

}

package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public abstract class AbstractParteTutorial {

    protected int parte; // El número de la parte del tutorial
    protected JsonArray array; // El contenido JSON de la parte

    // Lógica común que se ejecuta en el constructor de la clase base
    public AbstractParteTutorial(int parte) {
        this.parte = parte;
        // La lógica para crear el array específico se llama aquí.
        this.array = crearContenidoJson();

        // Lógica de registro común
        RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().registerParteTutorial(this.parte, this.array);
    }

    // Método abstracto: Obliga a cada parte a definir su contenido único
    protected abstract JsonArray crearContenidoJson();

    // Método para obtener URLs comunes
    protected String getUrlFrank() {
        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        return RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                .replace("%url.hotel.base%", url_hotel_base) + "frank/";
    }

    protected String getUrlBase() {
        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        return RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                .replace("%url.hotel.base%", url_hotel_base);
    }
}
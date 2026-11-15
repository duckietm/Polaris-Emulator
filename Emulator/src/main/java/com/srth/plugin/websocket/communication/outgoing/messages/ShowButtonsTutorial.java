package com.srth.plugin.websocket.communication.outgoing.messages;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class ShowButtonsTutorial extends OutgoingMessage {
    public ShowButtonsTutorial(String header, boolean show, int total_process) throws SQLException {
        super("ShowButtonsTutorial");

        int total_show;
        if (show == true) {
            total_show = 1;
        } else {
            total_show = 0;
        }

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";

        this.data.addProperty("show", total_show);
        this.data.addProperty("ver", total_show);

        JsonArray Button = new JsonArray();
        JsonObject jsonObject;

        jsonObject = new JsonObject();
        if (total_process <= 3) {
            jsonObject.addProperty("active", 1);
        } else {
            jsonObject.addProperty("active", 0);
        }
        jsonObject.addProperty("process", 1);
        jsonObject.addProperty("title", "1) Crea tu casa");
        jsonObject.addProperty("image", url_images + "casa.png");
        Button.add(jsonObject);

        jsonObject = new JsonObject();
        if (total_process >= 4 && total_process < 11) {
            jsonObject.addProperty("active", 1);
        } else {
            jsonObject.addProperty("active", 0);
        }
        jsonObject.addProperty("process", 2);
        jsonObject.addProperty("title", "2) Cocina platillos de comida");
        jsonObject.addProperty("image", url_images + "cocinar.png");
        Button.add(jsonObject);

        jsonObject = new JsonObject();
        if (total_process >= 11 && total_process < 100) {
            jsonObject.addProperty("active", 1);
        } else {
            jsonObject.addProperty("active", 0);
        }
        jsonObject.addProperty("process", 3);
        jsonObject.addProperty("title", "3) Consigue energia");
        jsonObject.addProperty("image", url_images + "dormir.png");
        Button.add(jsonObject);

        // this.data.add("button", jsonObject);
        this.data.add("button", Button);
        // this.data.add("tasks", tasksobj);
        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}

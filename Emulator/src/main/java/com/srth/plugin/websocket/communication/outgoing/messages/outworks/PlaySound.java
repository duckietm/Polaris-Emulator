package com.srth.plugin.websocket.communication.outgoing.messages.outworks;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class PlaySound extends OutgoingMessage {
    public PlaySound(String header, boolean show, String sound, Habbo habbo) throws SQLException {

        super("PlaySound");

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        this.data.addProperty("show_button", show);
        this.data.addProperty("sound", sound);

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                .replace("%url.hotel.base%", url_hotel_base);

        this.data.addProperty("url_sound",
                url_images+ "sounds/");
        this.data.addProperty("volumen_sound", habboRp.getValue("volumen_sound"));

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

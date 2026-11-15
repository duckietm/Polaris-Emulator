package com.srth.plugin.websocket.communication.outgoing.messages;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class ShowConfirmAlert extends OutgoingMessage {
    public ShowConfirmAlert(String header, String name_action, int action, int time_cancel, Boolean show, String descripcion) throws SQLException{

        super("ShowConfirmAlert");

        int mostrar;
        if(show == true){mostrar= 1;} else{mostrar= 0;}
        
        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";
        
        String[] getData = RoleplayWebsocketsRoomsInfo.getTexts().getValue("confirm_"+name_action+"_data").replace(";",":").split(":");
        
            this.data.addProperty( "name_action", name_action);
            this.data.addProperty("action", action);
            this.data.addProperty("time_cancel", time_cancel);
        
                this.data.addProperty("title", getData[1]);
                if(descripcion == ""){
                    this.data.addProperty("descripcion", getData[3]);
                } else {
                    this.data.addProperty("descripcion", descripcion);
                }
                this.data.addProperty("image", url_images+getData[5]);
                this.data.addProperty("title_next", getData[7]);
                this.data.addProperty("title_ant", getData[9]);
                this.data.addProperty("color_button_next", getData[11]);
                this.data.addProperty("sound", "dano.mp3");
            
            this.data.addProperty("page_max", 1);

        this.data.addProperty("page", action);
        this.data.addProperty("ver", mostrar);

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

package com.srth.plugin.websocket.communication.outgoing.messages;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;
import java.sql.SQLException;

public class ShowVentSelectConfirm extends OutgoingMessage {
    public ShowVentSelectConfirm(String header, String name_action, int id_item, Boolean show) throws SQLException{

        super("ShowVentSelectConfirm");

        int mostrar;
        if(show == true){mostrar= 1;} else{mostrar= 0;}
        
        if(show){
                
            String[] getData = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_"+name_action+"_confirm_data").replace(";",":").split(":");

                String[] getDataItem = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_"+name_action+"_item_"+id_item).replace(";",":").split(":");

                this.data.addProperty("id", id_item);
                this.data.addProperty( "name_action", name_action);
                this.data.addProperty( "action", 1);
            
                this.data.addProperty("title_ventana", getData[1]);
                this.data.addProperty("desc_ventana", getData[3].replace("%accion%", getDataItem[3]));
                
                this.data.addProperty("title_confirm", getData[5]);
                this.data.addProperty("title_cancel", getData[7]);
                    
        }

        this.data.addProperty("ver", mostrar);
        // this.data.add("items", itemsobj);
        this.dataArray.add(this.data);
        this.outgoing.addProperty("header", header);
        this.outgoing.add("data", this.dataArray);
    }

    @Override
    public String execute() {
        return this.outgoing.toString();
    }
}

package com.srth.plugin.websocket.communication.outgoing.messages;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.outgoing.OutgoingMessage;

import java.sql.SQLException;

public class ShowVentUserVersus extends OutgoingMessage {
    public ShowVentUserVersus(String header, Boolean show, Habbo habbo1, Habbo habbo2) throws SQLException{
        super("ShowVentUserVersus");

        int mostrar;
        if(show == true){mostrar= 1;} else{mostrar= 0;}
        
        if(show){
            String look = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")+"&headonly=1&size=a&gesture=sml&direction=2&head_direction=1";

            if(habbo2 == null){
                this.data.addProperty("habbo2_active", 0);
            } else {

                HabboRp habboRp2 = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo2);

                this.data.addProperty("habbo2_active", 1);
                this.data.addProperty("habbo2_id", habbo2.getHabboInfo().getId());
                this.data.addProperty("habbo2_name", habbo2.getHabboInfo().getUsername());
                this.data.addProperty("habbo2_look", look.replace("%LOOK%", habbo2.getHabboInfo().getLook()));
                this.data.addProperty("habbo2_vida", habboRp2.checkSettings("global.vida",0));
                this.data.addProperty("habbo2_max_vida", 100);
                this.data.addProperty("habbo2_escudo", habboRp2.checkSettings("global.escudo", 0));
                this.data.addProperty("habbo2_max_escudo", 100);
                this.data.addProperty("habbo2_municion", habboRp2.checkSettings("global.municion", 10));
                this.data.addProperty("habbo2_max_municion", habboRp2.checkSettings("global.max_municion", 10));
            }

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo1);

            this.data.addProperty("habbo_id", habbo1.getHabboInfo().getId());
            this.data.addProperty("habbo_name", habbo1.getHabboInfo().getUsername());
            this.data.addProperty("habbo_look", look.replace("%LOOK%", habbo1.getHabboInfo().getLook()));
            this.data.addProperty("habbo_vida", habboRp.checkSettings("global.vida",0));
            this.data.addProperty("habbo_max_vida", 100);
            this.data.addProperty("habbo_escudo", habboRp.checkSettings("global.escudo", 0));
            this.data.addProperty("habbo_max_escudo", 100);
            this.data.addProperty("habbo_municion", habboRp.checkSettings("global.municion", 10));
            this.data.addProperty("habbo_max_municion", habboRp.checkSettings("global.max_municion", 10));
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

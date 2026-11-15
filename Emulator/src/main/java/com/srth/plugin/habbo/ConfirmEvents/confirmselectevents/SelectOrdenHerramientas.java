package com.srth.plugin.habbo.ConfirmEvents.confirmselectevents;
import java.sql.SQLException;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class SelectOrdenHerramientas{
    
    public SelectOrdenHerramientas(String name, int id, Habbo habbo){

        // electrodomesticos_orden_select

        String[] getDataItem = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_"+name+"_item_"+id).replace(";",":").split(":");
        String[] getDataTexts = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_"+name+"_texts").replace(";",":").split(":");

        String clean_mount = getDataItem[5].replace("cash", "");
        int mount_total = Integer.parseInt(clean_mount.replace(" ", ""));
        int items_cantidad = Integer.parseInt(getDataItem[7].replace(" ",""));

        int amount = mount_total;
        int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");

        // Mostrar alerta
        THashMap<String, String> keys3 = new THashMap<>();
        keys3.put("display", "BUBBLE");
        keys3.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/tutorial/"+getDataItem[9].replace(" ", ""));
        keys3.put("message", getDataTexts[1].replace("%cantidad%",items_cantidad+"").replace("%precio%", mount_total+""));
        habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));

        // Comprobar monedas
        if(RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("seasonal.rol.primary.type.its.credits") == true){
            if(habbo.getHabboInfo().getCredits() < amount){
                habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.ingredientes.buy.no.currency"), RoomChatMessageBubbles.NORMAL);
                return;
            }
        } else {
            if(habbo.getHabboInfo().getCurrencyAmount(type_moneda) < amount){
                habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.ingredientes.buy.no.currency"), RoomChatMessageBubbles.NORMAL);
                return;
            }
        }

        HabboClient client = HabboClientManager.getHabboClient(habbo);
        try {
            client.send(new PlaySound("PlaySound", true, "buy.mp3", habbo));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String item_name = getDataItem[9].replace(" ","").replace(".png","");
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.getNewInventoryHabbo().createItem(item_name, 1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        

        this.restarMonedas(habbo.getClient(), amount);
    }

    public void restarMonedas(GameClient client, int cantidad){
        int amount = cantidad;
        int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");

        if(RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("seasonal.rol.primary.type.its.credits") == true){
            if(client.getHabbo().getHabboInfo().getCredits() >= amount){
                client.getHabbo().giveCredits(-amount);
            } else {
                client.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.ingredientes.buy.no.currency"), RoomChatMessageBubbles.NORMAL);
                return;
            }

        } else {
            if(client.getHabbo().getHabboInfo().getCurrencyAmount(type_moneda) >= amount){
                client.getHabbo().givePoints(type_moneda, -amount);
            } else {
                client.getHabbo().whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.ingredientes.buy.no.currency"), RoomChatMessageBubbles.NORMAL);
                return;
            }
        }
    }
        

}

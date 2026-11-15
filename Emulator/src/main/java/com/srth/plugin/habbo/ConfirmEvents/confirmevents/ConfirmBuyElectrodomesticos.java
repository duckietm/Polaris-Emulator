package com.srth.plugin.habbo.ConfirmEvents.confirmevents;

import java.sql.SQLException;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class ConfirmBuyElectrodomesticos{
    
    public ConfirmBuyElectrodomesticos(String name, int id, Habbo habbo) throws SQLException{

        // electrodomesticos_orden_select

        String[] getDataItem = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_"+name+"_item_"+id).replace(";",":").split(":");

        String[] getDataTexts = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_"+name+"_texts").replace(";",":").split(":");

        String clean_mount = getDataItem[5].replace("cash", "");
        int mount_total = Integer.parseInt(clean_mount.replace(" ", ""));
        int cantidad_buy = Integer.parseInt(getDataItem[7].replace(" ",""));

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if(room != null){

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            int amount = mount_total;
            int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");

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

            // Mostrar alerta
            THashMap<String, String> keys3 = new THashMap<>();
            keys3.put("display", "BUBBLE");
            keys3.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/tutorial/"+getDataItem[9].replace(" ", ""));
            keys3.put("message", getDataTexts[1].replace("%cantidad%",cantidad_buy+"").replace("%precio%", mount_total+""));
            habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));

            habboRp.allowMove(false);
            habboRp.setProgressPendiente(true);

            Emulator.getThreading().run(() -> {

                habboRp.allowMove(true);
                habboRp.setProgressPendiente(false);

                String item_name = getDataItem[9].replace(" ","").replace("_icon.png","");
                Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(item_name);
        
                for (int i = 0; i < cantidad_buy; i++) {
                    HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                    habbo.getHabboInfo().getId(),
                    baseItem,
                    0,
                    0,
                    "");
                    habbo.getInventory().getItemsComponent().addItem(habboItem);
                    habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
                }
                habbo.getClient().sendResponse(new InventoryRefreshComposer());

                this.restarMonedas(habbo.getClient(), amount);

                HabboClient client = HabboClientManager.getHabboClient(habbo);
                try {

                    client.send(new PlaySound("PlaySound", true, "buy.mp3", habbo));

                } catch (SQLException e) {
                    e.printStackTrace();
                }
                        
            }, 3000);
        }
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

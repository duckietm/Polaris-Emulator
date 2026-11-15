package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowConfirmAlert;

import gnu.trove.map.hash.THashMap;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionAseoContenedorBasura extends HabboItem {

    public InteractionAseoContenedorBasura(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionAseoContenedorBasura(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client != null) 
        {

            if(room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")){
                return;
            }
            
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

            HabboClient habboclient = HabboClientManager.getHabboClient(client.getHabbo());

            if (RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()), client.getHabbo().getRoomUnit().getCurrentLocation())) 
            {
                // Cuando no tienes empleo
                if(habboRp.getJobActive() == null){
                    return;
                }
                // Cuando no eres de aseo
                if(habboRp.getJobActive() != null && !habboRp.getJobActive().getName().equalsIgnoreCase("aseo")){
                    return;
                }

                if(habboRp.getProgressPendiente()){
                    return;
                }

                HabboItem item = this;

                String estructura = "item_id:%item_id%;";
                String total_string = estructura.replace("%item_id%",item.getId()+"");
                
                // Checkea si no existe
                habboRp.checkSettings("global_confirm_data", total_string);
                // Actualiza la data
                habboRp.update("global_confirm_data", total_string);

                int cantidad_inventario = 0;
                if(habboRp.getNewInventoryHabbo().getItem("basura") != null){
                    cantidad_inventario = habboRp.getNewInventoryHabbo().getItem("basura").getCantidad();
                }
                int operacion_acumulado = cantidad_inventario/5;

                if(operacion_acumulado == 0){
                    // Mostrar alerta
                    THashMap<String, String> keys2 = new THashMap<>();
                    keys2.put("display", "BUBBLE");
                    keys2.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/tutorial/basura.png");
                    keys2.put("message", RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_aseo_contenedor.error").replace("%br%","\n").replace("%cantidad%",cantidad_inventario+""));
                    client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

                    habboRp.showMessage("RoleplayWebsocketsRoomsInfo/tutorial/basura.png", RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_aseo_contenedor.error").replace("%br%","\n").replace("%cantidad%",cantidad_inventario+""));
                    return;
                }

                String name_action = "aseo_contenedor";
                String[] getData = RoleplayWebsocketsRoomsInfo.getTexts().getValue("confirm_"+name_action+"_data").replace(";",":").split(":");
                String descripcion = getData[3].replace("%cantidad%", cantidad_inventario+"").replace("%cantidad_dinero%", operacion_acumulado+"");
                
                // Mostrar confirmación
                habboclient.send(new ShowConfirmAlert("ShowConfirmAlert", name_action, 1, 120, true, descripcion));
            }
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
    }

    @Override
    public void onPickUp(Room room) {
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
    }
}

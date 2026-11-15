package com.srth.plugin.habbo.ConfirmEvents.confirmevents;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

import java.sql.SQLException;

public class EventConfirmBuyFoodsMachine {

    private Habbo habbo;
    private Room room;


    public EventConfirmBuyFoodsMachine(Habbo habbo) throws SQLException{
        this.habbo = habbo;

        if(this.habbo != null){

            room = habbo.getHabboInfo().getCurrentRoom();

            if(room != null){

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                
                int time_active_interval = RoleplayWebsocketsRoomsInfo.getConfig().getInt("foods_machine.time.active.interval");

                int amount = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_amount.price.buy.foods.machine");
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
                
                // Congelar al keko
                habbo.getRoomUnit().setCanWalk(false);

                String data[] = habboRp.checkSettings("global_confirm_data", "").replace(";",":").split(":");
                int item_id = Integer.parseInt(data[1]);
                HabboItem habbo_item = Emulator.getGameEnvironment().getItemManager().loadHabboItem(item_id);
                String name_item = habbo_item.getBaseItem().getName();

                habbo_item.setExtradata("1");
                room.updateItem(habbo_item);
                habbo_item.needsUpdate(true);

                RoleplayWebsocketsRoomsInfo.getConfigUser(habbo.getHabboInfo().getId()).saveToDatabase(Emulator.getIntUnixTimestamp()+"","foods_machine.ultimo.timestamp", habbo.getHabboInfo().getId());

                RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("RoleplayWebsocketsRoomsInfo/foods_machines/"+name_item+"_icon.png","foods_machine.user.shop.process", habbo.getClient());
                
                // Mostrar proceso
                habboRp.getVidaHambreDormir().showTimeProcess("", RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.action.pendiente"), 5, true);
                habboRp.setProgressPendiente(true);

                Emulator.getThreading().run(() -> {
                        
                    int cantidad_azar = 100;
                    int cuenta_aleatorio = Emulator.getRandom().nextInt(cantidad_azar);

                    String name_comida = "none";
                    int cantidad;
                    int precio = RoleplayWebsocketsRoomsInfo.getConfig().getInt("foods_machine.price.buy");

                    if(name_item.contains("opti_pizzas")){
                        if(cuenta_aleatorio >= 70){
                            name_comida = RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.comida.type.0");
                            cantidad = 50;
                        } else {
                            name_comida = RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.comida.type.1");
                            cantidad = 30;
                        }
                    } else if(name_item.contains("opti_pommes")){
                        if(cuenta_aleatorio >= 70){
                            name_comida = RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.comida.type.2");
                            cantidad = 50;
                        } else {
                            name_comida = RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.comida.type.3");
                            cantidad = 20;
                        }
                    } else if(name_item.contains("opti_cupcak")){
                        
                        if(cuenta_aleatorio >= 70){
                            name_comida = RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.comida.type.4");
                            cantidad = 50;
                        } else {
                            name_comida = RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.comida.type.5");
                            cantidad = 10;
                        }
                    } else {
                        name_comida = "none";
                        cantidad = 0;
                    }

                    if(RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("seasonal.rol.primary.type.its.credits") == true){
                        habbo.giveCredits(-precio);
                    } else {
                        habbo.givePoints(type_moneda, -precio);
                    }

                    try {
                        habboRp.getVidaHambreDormir().comer(cantidad);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    HabboClient client = HabboClientManager.getHabboClient(habbo);
                    try {
                        client.send(new PlaySound("PlaySound", true, "comer.mp3", habbo));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    

                    // Añadir item al lugar
                    Item rewardItem = Emulator.getGameEnvironment().getItemManager().getItem("rp_comido_food_ktchn");
                    HabboItem newItem = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), rewardItem, 0, 0, "");

                    if (newItem != null) {
                        newItem.setX(habbo.getRoomUnit().getX());
                        newItem.setY(habbo.getRoomUnit().getY());
                        newItem.setZ(room.getStackHeight(habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY(), false));
                        newItem.setRoomId(room.getId());
                        newItem.needsUpdate(true);
                        room.addHabboItem(newItem);
                        room.updateItem(newItem);
                        room.sendComposer(new AddFloorItemComposer(newItem, room.getFurniOwnerNames().get(newItem.getUserId())).compose());
                    }

                    // Mostrar alerta
                    THashMap<String, String> keys3 = new THashMap<>();
                    keys3.put("display", "BUBBLE");
                    keys3.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/foods_machines/"+name_item+"_icon.png");
                    keys3.put("message",RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.user.shop.success").replace("%COMIDA%", name_comida).replace("%COSTO%", precio+"").replace("%TYPE%", RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol")));
                    habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));

                    habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_buy_productos.success").replace("%cantidad%", "1").replace("%producto%", RoleplayWebsocketsRoomsInfo.getTexts().getValue("food_name_global")).replace("%amount%", precio+"").replace("%type%", RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol")));
            
                    RoleplayWebsocketsRoomsInfo.getConfigUser(habbo.getHabboInfo().getId()).saveToDatabase(Emulator.getIntUnixTimestamp()+"","foods_machine.ultimo.timestamp", habbo.getHabboInfo().getId());

                    try {
                        // Ocultar carga proceso
                        habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }


                    // Descongelar al keko y generar efecto
                    habbo.getRoomUnit().setCanWalk(true);
                    room.giveEffect(habbo,
                    Integer.valueOf(0),
                    -1);
                    habboRp.setProgressPendiente(false);

                    habboRp.update("global_confirm_data", "");

                }, 5000);
                                
                // El cajero vuelve a estar disponible
                Emulator.getThreading().run(() -> {

                    habbo_item.setExtradata("0");
                    habbo_item.needsUpdate(true);
                    room.updateItemState(habbo_item);
                    room.updateItem(habbo_item);

                }, time_active_interval);

            }

        }
    }
}

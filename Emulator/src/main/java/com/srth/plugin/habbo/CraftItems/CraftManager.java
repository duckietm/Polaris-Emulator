package com.srth.plugin.habbo.CraftItems;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.CraftItems.CraftHabbo.CraftHabbo;
import com.srth.plugin.habbo.CraftItems.CraftHabbo.CraftHabboManager;
import com.srth.plugin.habbo.CraftItems.ListCrafts.CraftCocineros;
import com.srth.plugin.habbo.Inventorys.ItemUsuario;
import com.srth.plugin.habbo.Inventorys.Items.Items;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;

import gnu.trove.map.hash.THashMap;

public class CraftManager {

  private THashMap<String,CraftData> craft_data = new THashMap<>();

  public CraftManager(){
  }

  public void setCraftManager(String header) throws SQLException{
    if(getCraft(header) == null){
      this.craft_data.put(header, new CraftData(header));
    }
  }

  public void update(){
    this.craft_data.clear();
  }

  public  THashMap<String, CraftData> Crafts(){
    return this.craft_data;
  }

  public CraftData getCraft(String header_craft) throws SQLException {
    if(Crafts().get(header_craft) != null){
        return Crafts().get(header_craft);
    } else {
        return null;
    }
  }

  public JsonArray getArray(){
    JsonArray Tarray = new JsonArray();
    JsonObject itemObj;
    for(CraftData craft : Crafts().values()) {
        itemObj = new JsonObject();
        itemObj.addProperty("header", craft.getHeader());
        itemObj.addProperty("number_items", craft.getNumberItems());
        Tarray.add(itemObj);
    }
    return Tarray;
  }

  public JsonArray getArrayData(String header, Habbo habbo) throws SQLException{

    JsonArray crafts = new JsonArray();
    JsonObject itemObj;

    CraftData craftData = getCraft(header);
    if(craftData != null){
      
      for (int i = 0; i < craftData.CraftsItems().values().size(); i++) {

        CraftItem item = craftData.CraftsItems().get(i);

          if(RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(item.getItemName()) != null){

            Items itemInfo = RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(item.getItemName());

            itemObj = new JsonObject();
            itemObj.addProperty("number", item.getId());
            itemObj.addProperty("id", itemInfo.getId());

            CraftHabboManager craftHabboManager = new CraftHabboManager(header);
            craftHabboManager.setCraftHabbo(habbo);
            CraftHabbo craftHabbo = craftHabboManager.getHabbo(habbo.getHabboInfo().getId());

            if(craftHabbo.getCraftItem(item.getId()) != null){

              int time_espera = this.getTimeEspera(craftHabbo.getCraftItem(item.getId()).getTimeFinal());
              itemObj.addProperty("crafteando", 1);
              itemObj.addProperty("time_espera", time_espera);

            } else {
              itemObj.addProperty("crafteando", 0);
              itemObj.addProperty("time_espera", 0);
            }

            itemObj.addProperty("item_name", itemInfo.getItemName());
            itemObj.addProperty("public_name", itemInfo.getPublicName());

            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            if(itemInfo.getItemExist() == 0){
              String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images").replace("%url.hotel.base%", url_hotel_base);
              itemObj.addProperty("item_img", global_url_images+"/items/"+itemInfo.getItemName()+".png");
            } else if(itemInfo.getItemExist() == 1){
              String global_url_hotel_furnis = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis").replace("%url.hotel.base%", url_hotel_base);
              itemObj.addProperty("item_img", global_url_hotel_furnis+"icons/"+itemInfo.getItemName()+"_icon.png");
            }

            itemObj.addProperty("title_ventain", craftData.getTitleVentana());
            itemObj.addProperty("title_buttons", craftData.getTextButton());
            itemObj.addProperty("desc_ventain", craftData.getDesc());
            itemObj.addProperty("cantidad_items_get", item.getCantidad());
            itemObj.addProperty("event", item.getNameEvent());
            itemObj.addProperty("items_craft_total", item.Ingredientes().values().size());

            crafts.add(itemObj);
          }
      }
    }
    return crafts;
  }


  public JsonArray getArrayDataIngredientes(String header, Habbo habbo) throws SQLException{

    JsonArray crafts = new JsonArray();
    JsonObject itemObj;

    CraftData craftData = getCraft(header);
    if(craftData != null){
      for(CraftItem item : craftData.CraftsItems().values()){
        
        int usuario_cantidad_items_stock = 0;
        int cantidad_items_for_unlock = 0;

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            
        if(RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(item.getItemName()) != null){

          int ultimo_getItems = item.Ingredientes().values().size()-1;
          for (int i = 0; i < item.Ingredientes().values().size(); i++) {

            IngredientesItem ingredientesInfo = item.Ingredientes().get(i);
            Items itemInfo = RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(ingredientesInfo.getItemName());

            itemObj = new JsonObject();
            itemObj.addProperty("number", item.getId());
            itemObj.addProperty("number_j", i);
            itemObj.addProperty("id", itemInfo.getId());
            itemObj.addProperty("public_name", itemInfo.getPublicName());

            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            if(itemInfo.getItemExist() == 0){

              String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images").replace("%url.hotel.base%", url_hotel_base);
              itemObj.addProperty("item_img", global_url_images+"items/"+itemInfo.getItemName()+".png");
              
            } else if(itemInfo.getItemExist() == 1){

              String global_url_hotel_furnis = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis").replace("%url.hotel.base%", url_hotel_base);
              itemObj.addProperty("item_img", global_url_hotel_furnis+"icons/"+itemInfo.getItemName()+"_icon.png");

            }

            
            if(habboRp.getNewInventoryHabbo().getItem(itemInfo.getItemName()) != null){

              ItemUsuario itemUsuario = habboRp.getNewInventoryHabbo().getItem(itemInfo.getItemName());

              if(itemInfo.getInventoryStock() == 0){
                usuario_cantidad_items_stock += itemUsuario.getCantidad();
                itemObj.addProperty("cantidad_stock", itemUsuario.getCantidad());
              } else {
                usuario_cantidad_items_stock += habboRp.getNewInventoryHabbo().getCantidadExistInventory(itemInfo);
                itemObj.addProperty("cantidad_stock", habboRp.getNewInventoryHabbo().getCantidadExistInventory(itemInfo));
              }

            } else {
              itemObj.addProperty("cantidad_stock", 0);
            }

            cantidad_items_for_unlock+= ingredientesInfo.getCantidadRequired();

            itemObj.addProperty("cantidad_required", ingredientesInfo.getCantidadRequired());
            itemObj.addProperty("count_items", 1);
            itemObj.addProperty("costo", item.getCostoCash());
            itemObj.addProperty("cantidad_items_get", item.getCantidad());

            if(i == ultimo_getItems){
              itemObj.addProperty("cantidad_items_user", usuario_cantidad_items_stock);
              itemObj.addProperty("cantidad_items_required", cantidad_items_for_unlock);
            }

            crafts.add(itemObj);

            
          }
        }
      }
      
      return crafts;
      
    }
    return crafts;
  }

  public void showCraftVentain(String header, Habbo habbo) throws SQLException{
    if(getCraft(header) != null){
      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
      if(habboRp.getHabboClient() != null){
        habboRp.getHabboClient().send(new MessageManagerDouble("MessageManager", true, Outcoming.OpenCraftItems, this.getArrayData(header, habbo), this.getArrayDataIngredientes(header, habbo)));
      }
    }
  }

  public void incomingList(THashMap<String, Object> data, HabboClient wsclient) throws SQLException{
    new CraftCocineros().incoming(data, wsclient.getHabbo());
  }

  public int getTimeSumatoria(int time_espera){
    Emulator.getRuntime().gc();
    int time_actual = Emulator.getIntUnixTimestamp() - Emulator.getTimeStarted();
    int segundos2 = time_espera*60;
    int total_espera = time_actual+segundos2;
    return total_espera;
  }

  public int getTimeEspera(int time_espera_segundos){
    Emulator.getRuntime().gc();
    int time_actual = Emulator.getIntUnixTimestamp() - Emulator.getTimeStarted();
    int segundos2 = time_espera_segundos;
    int total_espera = segundos2-time_actual;
    return total_espera;
  }

}

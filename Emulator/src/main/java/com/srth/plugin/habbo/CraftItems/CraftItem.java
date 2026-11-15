package com.srth.plugin.habbo.CraftItems;

import java.sql.SQLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Inventorys.Items.Items;

import gnu.trove.map.hash.THashMap;

public class CraftItem {

  private THashMap<Integer,IngredientesItem> ingredientes_items = new THashMap<>();

  private int id;
  private String header;
  private String item_name;
  private int cantidad;
  private int costo_cash;
  private int time_minutes;
  private String name_event;
  private Items item;

  public CraftItem(String header, int id) throws SQLException {
    this.id = id;
    this.header = header;

    String[] data_config = RoleplayWebsocketsRoomsInfo.getConfig().getValue(header+"-config_item_"+id).replace(" ", "").replace(";", ":").split(":");

    String[] data_ingredientes = RoleplayWebsocketsRoomsInfo.getConfig().getValue(header+"-list_items_"+id).replace(" ", "").split(";");
    
    for (int i = 0; i < data_ingredientes.length; i++) {

      // EJEMPLO (arma_ilegal:1;expediente:1;)
      
      String[] view_data_ingredientes = data_ingredientes[i].split(":");

      String name_item = setString(view_data_ingredientes[0]);
      int cantidad_items_required = setInt(view_data_ingredientes[1]);

      ingredientes_items.put(i, new IngredientesItem(name_item, cantidad_items_required));
    }

    // El data_text[1] es el name ej: 
    // (item_name:rp_food_ktchn; cantidad: 10; costo_cash:8; time_minutes:10; name_event:CraftPlatilloCocina;)
    this.item_name = setString(data_config[1]);
    this.cantidad = setInt(data_config[3]);
    this.costo_cash = setInt(data_config[5]);
    this.time_minutes = setInt(data_config[7]);
    this.name_event = setString(data_config[9]);

    if(RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(item_name) != null){
      this.item = RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(item_name);
    }
  }


  public String setString(String data){
    return data.replace(" ", "");
  }

  public int setInt(String data){
     return Integer.parseInt(data.replace(" ", ""));
  }

  public int getId(){
    return this.id;
  }


  public int getCantidad(){
    return this.cantidad;
  }

  public int getCostoCash(){
    return this.costo_cash;
  }

  public int getTimeMinutes(){
    return this.time_minutes;
  }

  public String getHeader(){
    return this.header;
  }

  public String getItemName(){
    return this.item_name;
  }
  
  public String getNameEvent(){
    return this.name_event;
  }

  public  THashMap<Integer, IngredientesItem> Ingredientes(){
    return this.ingredientes_items;
  }

  public Items getItem(){
    return this.item;
  }

  public JsonArray getArrayIngredientes(){
    JsonArray Tarray = new JsonArray();
    JsonObject itemObj;
    for(IngredientesItem ingredient : Ingredientes().values()) {
        itemObj = new JsonObject();
        itemObj.addProperty("id", ingredient.getItemId());
        itemObj.addProperty("item_name", ingredient.getItemName());
        itemObj.addProperty("cantidad_required", ingredient.getCantidadRequired());
        Tarray.add(itemObj);
    }
    return Tarray;
  }
  
}

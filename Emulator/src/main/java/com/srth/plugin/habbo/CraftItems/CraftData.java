package com.srth.plugin.habbo.CraftItems;

import java.sql.SQLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import gnu.trove.map.hash.THashMap;

public class CraftData {

  private THashMap<Integer,CraftItem> craft_items = new THashMap<>();

  private String header;
  private String title_ventana;
  private String text_button;
  private int number_items;
  private String desc;

  public CraftData(String header) throws SQLException {
      this.header = header;

      String[] data_config = RoleplayWebsocketsRoomsInfo.getConfig().getValue(header+"-config").replace(" ", "").replace(";", ":").split(":");
      String[] data_text = RoleplayWebsocketsRoomsInfo.getConfig().getValue(header+"-title").replace(";", ":").split(":");
      
      // El data_config[1] es la cantidad de items (items:2;)
      this.number_items = setInt(data_config[1]);

      // El data_text[1] es el titulo ej: (title_ventana:Peticion de platillos; text_button: Realizar peticion)
      this.title_ventana = data_text[1];
      this.text_button = data_text[3];
      this.desc = RoleplayWebsocketsRoomsInfo.getConfig().getValue(header+"-desc");

      for (int i = 0; i < number_items; i++) {
        this.craft_items.put(i, new CraftItem(header, i));
      }
  }

  public String setString(String data){
    return data.replace(" ", "");
  }

  public int setInt(String data){
     return Integer.parseInt(data.replace(" ", ""));
  }

  public int getNumberItems(){
    return this.number_items;
  }

  public String getHeader(){
    return this.header;
  }

  public String getTitleVentana(){
    return this.title_ventana;
  }
  
  public String getTextButton(){
    return this.text_button;
  }

  public String getDesc(){
    return this.desc;
  }

  public  THashMap<Integer, CraftItem> CraftsItems(){
    return this.craft_items;
  }

  public JsonArray getArray(){
    JsonArray Tarray = new JsonArray();
    JsonObject itemObj;
    for(CraftItem craft : CraftsItems().values()) {
        itemObj = new JsonObject();
        itemObj.addProperty("id", craft.getId());
        itemObj.addProperty("item_name", craft.getItemName());
        itemObj.addProperty("cantidad", craft.getCantidad());
        itemObj.addProperty("costo_cash", craft.getCostoCash());
        itemObj.addProperty("time_minutes", craft.getTimeMinutes());
        itemObj.addProperty("name_event", craft.getNameEvent());
        Tarray.add(itemObj);
    }
    return Tarray;
  }
  
}

package com.srth.plugin.habbo.CraftItems;

import java.sql.SQLException;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Inventorys.Items.Items;

public class IngredientesItem {

  private String item_name;
  private Items item;
  private int cantidad_required;
  private int id_item;

  public IngredientesItem(String name_item, int cantidad_required) throws SQLException {
    if(RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(name_item) != null){
      this.item_name = name_item;
      this.cantidad_required = cantidad_required;
      this.item = RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(name_item);
      this.id_item = RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(name_item).getId();
    }
  }

  public int getItemId(){
    return this.id_item;
  }

  public String getItemName(){
    return this.item_name;
  }

  public Items getItem(){
    return this.item;
  }

  public int getCantidadRequired(){
    return this.cantidad_required;
  }
  
}

package com.srth.plugin.habbo.CraftItems.CraftHabbo;

import java.sql.SQLException;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.CraftItems.CraftData;
import com.srth.plugin.habbo.CraftItems.CraftItem;

import gnu.trove.map.hash.THashMap;

public class CraftHabbo {

  private THashMap<Integer,CraftUserItem> craft_user_items = new THashMap<>();

  private String format_save = "id:%item_id%:time:%time_espera%;";
  private HabboRp habboRp;


  public CraftHabbo(String header, Habbo habbo) throws SQLException{

    if(RoleplayWebsocketsRoomsInfo.getCraftManager().getCraft(header) != null){

      this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      if(this.activeExistHabbo(habbo, header) == true){
        String data = habboRp.getValue(header+"_active");
        String[] array_data = data.split(";");

        for (int i = 0; i < array_data.length; i++) {
          String[] array_data_generate = array_data[i].split(":");
          // Borra los espacios
          int number_item = Integer.parseInt(array_data_generate[1].replace(" ", ""));
          // Borra los espacios
          int time = Integer.parseInt(array_data_generate[3].replace(" ", ""));
          this.craft_user_items.put(number_item, new CraftUserItem(number_item, time));
        }
      }
    }
  }

  public  THashMap<Integer, CraftUserItem> Items(){
    return this.craft_user_items;
  }

  public CraftUserItem getCraftItem(Integer id_furni){
    return Items().get(id_furni);
  }

  public void insertCraftActive(String header, Habbo habbo, int number_item) throws SQLException{

    if(RoleplayWebsocketsRoomsInfo.getCraftManager().getCraft(header) != null){

      CraftData craftData = RoleplayWebsocketsRoomsInfo.getCraftManager().getCraft(header);

      if(this.activeExistHabbo(habbo, header) == false){

        CraftItem item = craftData.CraftsItems().get(number_item);

        if(this.checkExistItem(item.getItemName())){

            int time_espera = item.getTimeMinutes();
            int total_espera = RoleplayWebsocketsRoomsInfo.getCraftManager().getTimeSumatoria(time_espera);

            String format_save_final = format_save.replace("%item_id%", number_item+"").replace("%time_espera%", total_espera+"");
            habboRp.checkSettings(header+"_active", format_save_final);
        }

      } else {

        String data = habboRp.getValue(header+"_active");
        String[] array_data = data.split(";");

        String format_save_final = "";

        for (int i=0; i < array_data.length; i++) {
          String[] getConfigDetail = array_data[i].split(":");
          String filter_detail = format_save.replace("%item_id%", getConfigDetail[1]).replace("%time_espera%", getConfigDetail[3]);
          format_save_final += filter_detail;
        }

        CraftItem item = craftData.CraftsItems().get(number_item);

        if(this.checkExistItem(item.getItemName())){
            int time_espera = item.getTimeMinutes();
            int total_espera = RoleplayWebsocketsRoomsInfo.getCraftManager().getTimeSumatoria(time_espera);
            format_save_final += format_save.replace("%item_id%", number_item+"").replace("%time_espera%", total_espera+"");
            habboRp.update(header+"_active", format_save_final);
            habboRp.saveToDatabase(format_save_final, header+"_active");
        }
        
      }
    }
  }

  public void removeCraftActive(String header, Habbo habbo, int number_item) throws SQLException{
    
    String format_save = "id:%item_id%:time:%time_espera%;";
    if(this.activeExistHabbo(habbo, header) == true){

      CraftData craftData = RoleplayWebsocketsRoomsInfo.getCraftManager().getCraft(header);
      if(craftData == null){
        return;
      }

      String data = habboRp.getValue(header+"_active");
      String[] array_data = data.split(";");
      String format_save_final = "";

      for (int i=0; i < array_data.length; i++) {
        String[] getConfigDetail = array_data[i].split(":");
        int id_item = Integer.parseInt(getConfigDetail[1].replace(" ", ""));

        if(id_item != number_item){

          String filter_detail = format_save.replace("%item_id%", getConfigDetail[1]).replace("%time_espera%", getConfigDetail[3]);

          format_save_final += filter_detail;
        }
      }

      if(format_save_final != ""){
        habboRp.update(header+"_active", format_save_final);
        habboRp.saveToDatabase(format_save_final, header+"_active");
      } else {

        habboRp.remove(header+"_active");
      }
    }
  }

  public Boolean activeExistHabbo(Habbo habbo, String header){

    if(habboRp.getValue(header+"_active") == "none"){
      return false;
    } else {
      return true;
    }

  }

  public Boolean checkExistItem(String name_furni) throws SQLException{
    if(RoleplayWebsocketsRoomsInfo.getItemsManager().getItemExist(name_furni) != null){
      return true;
    }
    return false;
  }


}

package com.srth.plugin.habbo.CraftItems.CraftHabbo;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import gnu.trove.map.hash.THashMap;

public class CraftHabboManager {

  private String header;
  private THashMap<Integer,CraftHabbo> craft_habbos = new THashMap<>();

  public CraftHabboManager(String header){
    this.header = header;
  }

  public void setCraftHabbo(Habbo habbo) throws SQLException{
    this.craft_habbos.clear();
    if(RoleplayWebsocketsRoomsInfo.getCraftManager().getCraft(header) != null){
      if(getHabbo(habbo.getHabboInfo().getId()) == null){
        this.craft_habbos.put(habbo.getHabboInfo().getId(), new CraftHabbo(header, habbo));
      }
    }
  }

  public  THashMap<Integer, CraftHabbo> Habbos(){
    return this.craft_habbos;
  }

  public CraftHabbo getHabbo(Integer user_id) throws SQLException {
    if(Habbos().get(user_id) != null){
        return Habbos().get(user_id);
    } else {
        return null;
    }
  }

  
}

package com.srth.plugin.habbo.CraftItems.CraftHabbo;

public class CraftUserItem {
  
  private Integer id_furni;
  private Integer time_final;
  
  public CraftUserItem(Integer id, Integer time){
    this.id_furni = id;
    this.time_final = time;
  }

  public int getIdFurni(){
    return id_furni;
  }
  public int getTimeFinal(){
    return time_final;
  }

}

package com.srth.plugin.habbo.Inventorys.Items;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Items {

    private int id;
    private String item_name;
    private String type;
    private String public_name;
    private String public_desc;
    private String public_info;
    private int collect;
    private String use_btn_name;
    private String event;
    private int inventory_stock;
    private int item_exist;

    public Items(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.item_name = set.getString("item_name");
        this.type = set.getString("type");
        this.public_name = set.getString("public_name");
        this.public_desc = set.getString("public_desc");
        this.public_info = set.getString("public_info");
        this.collect = set.getInt("collect");
        this.use_btn_name = set.getString("use_btn_name");
        this.event = set.getString("event");
        this.inventory_stock = set.getInt("inventory_stock");
        this.item_exist = set.getInt("item_exist");
    }

    public int getId() {
        return id;
    }

    public int getItemExist() {
      return item_exist;
    }

    public String getItemName() {
        return item_name;
    }

    public String getType(){
        return this.type;
    }

    public String getPublicName(){
      return this.public_name;
    }

    public String getPublicDesc(){
      return this.public_desc;
    }

    public String getPublicInfo(){
      return this.public_info;
    }

    public int getCollect() {
      return this.collect;
    }

    public String getUseBtnName() {
      return this.use_btn_name;
    }

    public String getEvent() {
      return this.event;
    }

    public int getInventoryStock() {
      return this.inventory_stock;
    }

}

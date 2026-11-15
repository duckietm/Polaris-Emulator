package com.srth.plugin.bots.shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ItemShop {

    public Integer id;
    public TypeShop typeShop;

    public String title;
    public String desc;

    public int cantidad_item_required;
    public String name_item_required;
    public int item_required_exist;

    public int points_reward;

    public String img_promo;

    ItemShop(ResultSet set) throws SQLException {

        this.id = set.getInt("id");
        this.title = set.getString("title");
        this.desc = set.getString("desc");
        this.cantidad_item_required = set.getInt("cantidad_item_required");
        this.name_item_required = set.getString("name_item_required");
        this.item_required_exist = set.getInt("item_required_exist");
        this.points_reward = set.getInt("points_reward");
        this.img_promo = set.getString("img_promo");

    }

    public void setTypeShop(TypeShop typeShop) {
        this.typeShop = typeShop;
    }

    public TypeShop getTypeShop() {
        return typeShop;
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDesc() {
        return desc;
    }

    public int getCantidad_item_required() {
        return cantidad_item_required;
    }

    public String getName_item_required() {
        return name_item_required;
    }

    public int getItem_required_exist() {
        return item_required_exist;
    }

    public int getPoints_reward() {
        return points_reward;
    }

    public String getImgPromo() {
        return img_promo;
    }

}

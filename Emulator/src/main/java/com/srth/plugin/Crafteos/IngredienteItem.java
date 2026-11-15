package com.srth.plugin.Crafteos;

import java.sql.ResultSet;
import java.sql.SQLException;

public class IngredienteItem {

    public int id;
    public int craft_id;
    public String item_name;
    public int item_exist;
    public String name;
    public String desc;
    public int cantidad;

    public IngredienteItem(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.craft_id = set.getInt("craft_id");
        this.item_name = set.getString("item_name");
        this.item_exist = set.getInt("item_exist");
        this.name = set.getString("name");
        this.desc = set.getString("desc");
        this.cantidad = set.getInt("cantidad");
    }

    // --- Getters ---

    public int getId() {
        return id;
    }

    public int getCraftId() {
        return craft_id;
    }

    public String getItemName() {
        return item_name;
    }

    public int getItemExist() {
        return item_exist;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public int getCantidad() {
        return cantidad;
    }
}
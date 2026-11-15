package com.srth.plugin.habbo.Inventorys;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ItemCofre {

    private int id;
    private int user_id;
    private int cofre_id;
    private int item_id;
    private int cantidad;

    public ItemCofre(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.cofre_id = set.getInt("cofre_id");
        this.item_id = set.getInt("item_id");
        this.cantidad = set.getInt("cantidad");
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return user_id;
    }

    public int getCofreId() {
        return cofre_id;
    }

    public int getItemId() {
        return this.item_id;
    }

    public int getCantidad() {
        return this.cantidad;
    }

}

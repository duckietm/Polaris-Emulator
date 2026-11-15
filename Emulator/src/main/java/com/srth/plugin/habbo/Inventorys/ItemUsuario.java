package com.srth.plugin.habbo.Inventorys;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ItemUsuario {

    private int id;
    private int user_id;
    private String ranura;
    private int item_id;
    private int cantidad;
    private int used;

    public ItemUsuario(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.user_id = set.getInt("user_id");
        this.item_id = set.getInt("item_id");
        this.cantidad = set.getInt("cantidad");
        this.used = set.getInt("used");
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return user_id;
    }

    public String getRanura() {
        return ranura;
    }

    public int getItemId() {
        return this.item_id;
    }

    public int getCantidad() {
        return this.cantidad;
    }

    public int getUsed() {
        return this.used;
    }

    public void setUsed(int used) {
        this.used = used;
    }
}

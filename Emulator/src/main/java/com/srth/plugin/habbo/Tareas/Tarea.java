package com.srth.plugin.habbo.Tareas;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Tarea {

    private int id;
    private int id_type;
    private String title;
    private String descripcion;
    private String imagen;
    private String name;
    private String type;
    private int usage_account;
    private int level_required;
    private int XP;
    private int cash;
    private String event;
    private int orden;

    public Tarea(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.id_type = set.getInt("id_type");
        this.title = set.getString("title");
        this.descripcion = set.getString("descripcion");
        this.imagen = set.getString("imagen");
        this.name = set.getString("name");
        this.type = set.getString("type");
        this.usage_account = set.getInt("usage_account");
        this.XP = set.getInt("xp");
        this.cash = set.getInt("cash");
        this.event = set.getString("event");
        this.level_required = set.getInt("level_required");
        this.orden = set.getInt("orden");
    }

    public int getId() {
        return id;
    }

    public int getTypeId() {
        return id_type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getImagen() {
        return imagen;
    }

    public int getXp() {
        return XP;
    }

    public int getCash() {
        return cash;
    }

    public int getOrden() {
        return orden;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getUsageAccount() {
        return usage_account;
    }

    public int getLevelRequired() {
        return level_required;
    }

    public String getEventName() {
        return event;
    }

}

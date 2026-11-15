package com.srth.plugin.habbo.Tareas;

import java.sql.ResultSet;
import java.sql.SQLException;

import gnu.trove.map.hash.THashMap;

public class TareaType {

    private int id;
    THashMap<Integer, Tarea> tareas = new THashMap<Integer, Tarea>();
    private String type;
    private String title;
    private String descripcion;
    private String imagen;
    private int orden;

    public TareaType(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.type = set.getString("type");
        this.title = set.getString("title");
        this.descripcion = set.getString("descripcion");
        this.imagen = set.getString("imagen");
        this.orden = set.getInt("orden");
    }

    public Tarea createTarea(ResultSet set) throws SQLException {
        Tarea newTarea = new Tarea(set);
        tareas.put(set.getInt("id"), newTarea);
        return newTarea;
    }

    public THashMap<Integer, Tarea> getTareas() {
        return tareas;
    }

    public int getId() {
        return id;
    }

    public String getType() {
        return type;
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

    public int getOrden() {
        return orden;
    }
}

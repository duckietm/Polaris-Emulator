package com.srth.plugin.habbo.Tareas.Level;

public class Reward {

    public String name;
    public int exist;
    public int cantidad;
    public String title;
    public String descripcion;

    public Reward(String name, int exist, int cantidad, String title, String descripcion) {

        this.name = name;
        this.exist = exist;
        this.cantidad = cantidad;
        this.title = title;
        this.descripcion = descripcion;

    }

    public String getName() {
        return name;
    }

    public int getExist() {
        return exist;
    }

    public int getCantidad() {
        return cantidad;
    }

    public String getTitle() {
        return title;
    }

    public String getDescripcion() {
        return descripcion;
    }

}

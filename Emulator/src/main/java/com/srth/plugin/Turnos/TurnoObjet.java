package com.srth.plugin.Turnos;

public class TurnoObjet {

    public int time;
    public String name;
    public String desc;
    public int reward;
    public String imagen;

    public TurnoObjet(int time, String name, String desc, int reward, String imagen) {
        this.time = time;
        this.name = name;
        this.desc = desc;
        this.reward = reward;
        this.imagen = imagen;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getTime() {
        return this.time;
    }

    public String getName() {
        return this.name;
    }

    public String getDesc() {
        return this.desc;
    }

    public int getReward() {
        return this.reward;
    }

    public String getImagen() {
        return this.imagen;
    }

}

package com.srth.plugin.habbo.Tareas.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Tareas.Tarea;

public class ProgresoTarea {

    private int id;
    private Tarea tarea;
    private int progress;
    private int completed;

    public ProgresoTarea(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.tarea = RoleplayWebsocketsRoomsInfo.getTareasManager().getTareaById(set.getInt("tarea_id"));
        this.progress = set.getInt("progress");
        this.completed = set.getInt("completed");
    }

    public void setCompleted(int value) {
        this.completed = value;
    }

    public int getId() {
        return id;
    }

    public Tarea getTarea() {
        return tarea;
    }

    public int getProgress() {
        return progress;
    }

    public int getCompleted() {
        return completed;
    }

}

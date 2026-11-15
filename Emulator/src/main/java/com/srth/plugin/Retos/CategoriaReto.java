package com.srth.plugin.Retos;

import java.util.HashMap;
import java.util.Map;

public class CategoriaReto {

    final int categoria;
    final String titulo;
    final String img;
    final String desc;
    final Map<Integer, TareaCategoria> tareasMap = new HashMap<>();

    CategoriaReto(int id, String titulo, String img, String desc) {
        this.categoria = id;
        this.titulo = titulo;
        this.img = img;
        this.desc = desc;
    }

    public String getTitle() {
        return titulo;
    }

    public int getCategoria() {
        return this.categoria;
    }

    public String getImg() {
        return img;
    }

    public String getDesc() {
        return desc;
    }

    public void agregarTarea(int idTarea, int type, String img, String img_secundary, String title, String descripcion,
            int events_required,
            String event, int points, int points_type) {
        this.tareasMap.put(idTarea,
                new TareaCategoria(idTarea, type, img, img_secundary, title, descripcion, events_required, event,
                        points, points_type));
    }

    public Map<Integer, TareaCategoria> getRetos() {
        return tareasMap;
    }

}

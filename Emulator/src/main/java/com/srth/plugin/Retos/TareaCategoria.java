package com.srth.plugin.Retos;

public class TareaCategoria {

    final int id;
    final int type;
    final String title;
    final String desc;
    final int events_required;
    final String event;
    final String img;
    final String img_secundary;
    final int points;
    final int points_type;

    TareaCategoria(int id, int type, String img, String img_secundary, String title, String desc, int events_required,
            String event, int points, int points_type) {
        this.id = id;
        this.type = type;
        this.img = img;
        this.title = title;
        this.desc = desc;
        this.events_required = events_required;
        this.event = event;
        this.img_secundary = img_secundary;
        this.points = points;
        this.points_type = points_type;
    }

    public int getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public String getImg() {
        return img;
    }

    public String getTitle() {
        return title;
    }

    public String getDesc() {
        return desc;
    }

    public String getImgSecundary() {
        return img_secundary;
    }

    public int getPoints() {
        return points;
    }

    public int getPoinsType() {
        return points_type;
    }

    public String getEvent() {
        return event;
    }

    public int getEventsRequired() {
        return events_required;
    }

}

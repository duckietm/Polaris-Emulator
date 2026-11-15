package com.srth.plugin.Rooms;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class RoomObjet {

    private String id;
    private String name_type;
    private String json;
    private int category;
    private Boolean create_button;
    private int room_id;
    private Boolean secure;

    public RoomObjet(String name, String json, Boolean create_button, Boolean secure) {
        this.name_type = name;
        this.json = json;
        this.create_button = create_button;
        this.category = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");
        this.registerRoomId(0);
        this.secure = secure;
    }

    public void registerRoomId(int id) {

        if (this.name_type.equalsIgnoreCase("hospital") || this.name_type.equalsIgnoreCase("basurero")
                || this.name_type.equalsIgnoreCase("centro_penitenciario")
                || this.name_type.equalsIgnoreCase("cafeteria")) {

            String name_empleo = "";
            if (this.name_type.equalsIgnoreCase("hospital")) {
                name_empleo = "medico";
            }
            if (this.name_type.equalsIgnoreCase("basurero")) {
                name_empleo = "aseo";
            }
            if (this.name_type.equalsIgnoreCase("centro_penitenciario")) {
                name_empleo = "police";
            }
            if (this.name_type.equalsIgnoreCase("cafeteria")) {
                name_empleo = "cafetero";
            }
            RoleplayWebsocketsRoomsInfo.getConfig().checkSettings("global_room.job_" + name_empleo + ".id", id + "");
            this.room_id = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_" + name_empleo + ".id");
        } else {

            // Type: normales
            String type = "normales";
            RoleplayWebsocketsRoomsInfo.getConfig().checkSettings("global_room_" + type + "." + this.name_type,
                    room_id + "");
            this.room_id = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room_" + type + "." + this.name_type);
        }
    }

    public void setRoomId(int id) {

        if (this.name_type.equalsIgnoreCase("hospital") || this.name_type.equalsIgnoreCase("basurero")
                || this.name_type.equalsIgnoreCase("centro_penitenciario")
                || this.name_type.equalsIgnoreCase("cafeteria")) {

            String name_empleo = "";
            if (this.name_type.equalsIgnoreCase("hospital")) {
                name_empleo = "medico";
            }
            if (this.name_type.equalsIgnoreCase("basurero")) {
                name_empleo = "aseo";
            }
            if (this.name_type.equalsIgnoreCase("centro_penitenciario")) {
                name_empleo = "police";
            }
            if (this.name_type.equalsIgnoreCase("cafeteria")) {
                name_empleo = "cafetero";
            }
            RoleplayWebsocketsRoomsInfo.getConfig().updateData("global_room.job_" + name_empleo + ".id", id + "");
            this.room_id = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_" + name_empleo + ".id");
        } else {

            // Type: normales
            String type = "normales";
            RoleplayWebsocketsRoomsInfo.getConfig().updateData("global_room_" + type + "." + this.name_type,
                    room_id + "");
            this.room_id = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room_" + type + "." + this.name_type);
        }
    }

    public int getRoomId() {
        return room_id;
    }

    public String getId() {
        return id;
    }

    public String getNameType() {
        return name_type;
    }

    public String getJson() {
        return json;
    }

    public Integer getCategory() {
        return category;
    }

    public String getFurnis() {
        return this.json;
    }

    public Boolean getCreateButtonActive() {
        return create_button;
    }

    public String getNamePublic() {
        return this.getNameType().toUpperCase().charAt(0)
                + this.getNameType().substring(1, this.getNameType().length()).toLowerCase().replace("_", " ");
    }

    public Boolean getSecure() {
        return secure;
    }
}

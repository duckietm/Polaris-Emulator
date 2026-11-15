package com.srth.plugin.habbo.Tareas.Level.habbo;

import java.sql.SQLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Tareas.Level.Level;

import gnu.trove.map.hash.THashMap;

public class LevelHabboManager {

    private THashMap<Integer, LevelHabbo> habbos = new THashMap<>();

    public LevelHabboManager() throws SQLException {
    }

    public void update() {
        habbos.clear();
    }

    public void setHabboLevel(Integer habbo_id) throws SQLException {
        if (getHabbo(habbo_id) == null) {
            LevelHabbo levelHabbo = new LevelHabbo(habbo_id);
            this.habbos.put(habbo_id, levelHabbo);
        }
    }

    public THashMap<Integer, LevelHabbo> Habbos() {
        return this.habbos;
    }

    public JsonArray getArrayHabbos() {
        JsonArray Tarray = new JsonArray();
        JsonObject itemObj;
        for (LevelHabbo habbos : Habbos().values()) {
            itemObj = new JsonObject();
            itemObj.addProperty("id", habbos.getHabboId());
            try {
                Level level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(habbos.getLevel());
                Level proximo_level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(habbos.getLevel() + 1);

                itemObj.addProperty("xp", habbos.getXP());
                itemObj.addProperty("xp_required", proximo_level.getXP());
                itemObj.addProperty("level", level.getLevel());
            } catch (Exception e) {
            }
            Tarray.add(itemObj);
        }
        return Tarray;
    }

    public LevelHabbo getHabbo(int habbo_id) throws SQLException {
        if (Habbos().get(habbo_id) != null) {
            return Habbos().get(habbo_id);
        } else {
            return null;
        }
    }

}

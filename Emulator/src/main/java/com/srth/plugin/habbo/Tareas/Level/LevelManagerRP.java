package com.srth.plugin.habbo.Tareas.Level;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Tareas.Level.config.LevelConfig;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

import gnu.trove.map.hash.THashMap;

public class LevelManagerRP {

    private THashMap<Integer, Level> levels = new THashMap<>();

    public LevelManagerRP() throws SQLException {
        new LevelConfig().register();
    }

    public void updateLevels() throws SQLException {
        this.levels.clear();
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement preparedStatement = conn
                    .prepareStatement("SELECT * FROM rp_settings WHERE `key` LIKE '%level_config.amount_levels%'")) {
                ResultSet set = preparedStatement.executeQuery();
                while (set.next()) {
                    for (int i = 1; i <= set.getInt("value"); i++) {
                        this.levels.put(i,
                                new Level(i));
                    }
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    public THashMap<Integer, Level> getLevels() {
        return this.levels;
    }

    public JsonArray getArrayLevels() {

        JsonArray Tarray = new JsonArray();
        JsonObject itemObj;

        for (Level level : getLevels().values()) {
            itemObj = new JsonObject();
            itemObj.addProperty("id", level.getLevel());
            itemObj.addProperty("xp", level.getXP());
            Tarray.add(itemObj);
        }

        return Tarray;
    }

    public Level getLevel(int level_id) throws SQLException {
        if (getLevels().get(level_id) != null) {
            return getLevels().get(level_id);
        } else {
            return null;
        }
    }

    public void IncomingListActions(THashMap<String, Object> data, HabboClient wsclient) {
        try {
            String name = (String) data.get("name");

            if (name.equalsIgnoreCase("OpenMenuLevel")) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());
                habboRp.getLevelHabbo().showWindow(habboRp.getLevelHabbo().getArrayData());

            }

            else if (name.equalsIgnoreCase("OpenMenuLevelOther")) {

                int user_id = (int) ((double) data.get("action"));

                HabboRp habboRp_other = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRpOffline(user_id);
                HabboRp habboRp_my = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());

                habboRp_my.getHabboClient().send(new MessageManager("MessageManager", true,
                        Outcoming.ViewLevelOtherHabboWindow, habboRp_other.getLevelHabbo().getArrayData()));
            }
        } catch (Exception e) {

        }
    }

}
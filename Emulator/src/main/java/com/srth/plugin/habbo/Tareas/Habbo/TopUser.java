package com.srth.plugin.habbo.Tareas.Habbo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.srth.plugin.habbo.HabboRp;

public class TopUser {

    private int id;
    private int level;
    private String look;
    private String username;
    private HabboRp habboRp;
    private int xp;

    public TopUser(HabboRp habboRp) {
        this.habboRp = habboRp;
        this.id = habboRp.getId();
        this.look = HabboManager.getOfflineHabboInfo(habboRp.getId()).getLook();
        this.username = HabboManager.getOfflineHabboInfo(habboRp.getId()).getUsername();
        this.level = this.loadFromDatabase("level_rp.level");
        this.xp = this.loadFromDatabase("level_rp.xp");
    }

    public int loadFromDatabase(String string) {
        int value = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT * FROM rp_users_settings WHERE user_id= ? AND `key`= ? ")) {
            statement.setInt(1, this.getId());
            statement.setString(2, string);
            if (statement.execute()) {
                try (ResultSet set = statement.getResultSet()) {
                    while (set.next()) {
                        value = set.getInt("value");
                        return value;
                    }
                }
            }
        } catch (SQLException e) {
        }
        return value;
    }

    public int getId() {
        return id;
    }

    public int getLevel() {
        return level;
    }

    public String getLook() {
        return look;
    }

    public String getUsername() {
        return username;
    }

    public HabboRp getHabboRp() {
        return habboRp;
    }

    public int getXp() {
        return xp;
    }

}

package com.srth.plugin.utilities;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Properties;

public class checkRPUsers {
    private static final Logger LOGGER = LoggerFactory.getLogger(checkRPUsers.class);

    private final Properties data;

    public boolean loaded = false;
    private boolean isLoading = false;

    public checkRPUsers(Integer habbo) {
        this.data = new Properties();
        this.loadFromDatabase(habbo);
    }

    public void reload(int user_id) {
    
        this.data.clear();
        this.loadFromDatabase(user_id);
        
    }
 
    public void loadFromDatabase(Integer habbo){
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM rp_users_settings WHERE user_id= ?")) {
            statement.setInt(1, habbo);
            if (statement.execute()) {
                try (ResultSet set = statement.getResultSet()) {
                    while (set.next()) {
                        this.data.put(set.getString("key"), set.getString("value"));
                    }
                }
                this.loaded = true;
            }
        } catch (SQLException e) {
        }
    }

    public void saveToDatabase(String value, String key, int habbo_id) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE rp_users_settings SET `value` = ? WHERE `key` = ? AND user_id= ? LIMIT 1")) {

            statement.setString(1, value);
            statement.setString(2, key);
            statement.setInt(3, habbo_id);
            statement.executeUpdate();

            this.update(key, value);

        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public String checkSettings(String key, String default_value, int user_id){
        String value = default_value;
        // Comprueba si no existe y lo registra
        if(this.getValue(key) == "none"){
            this.register(user_id, key, value);
            return value;
        } else {
            return value = this.getValue(key);
        }
    }

    public int checkSettings(String key, int default_value, int user_id){
        int value = default_value;
        // Comprueba si no existe y lo registra
        if(this.getValue(key) == "none"){
            this.register(user_id, key, value+"");
            return value;
        } else {
            return value = this.getInt(key);
        }
    }


    public String getValue(String key) {
        return this.getValue(key, "");
    }

    public String getValue(String key, String defaultValue) {
        if (this.isLoading)
            return defaultValue;

        if (!this.data.containsKey(key)) {
            return "none";
        }
        return this.data.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key) {
        return this.getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (this.isLoading)
            return defaultValue;
        try {
            return (this.getValue(key, "0").equals("1")) || (this.getValue(key, "false").equals("true"));
        } catch (Exception e) {
            LOGGER.error("Failed to parse key {} with value '{}' to type boolean.", key, this.getValue(key));
        }
        return defaultValue;
    }

    public int getInt(String key) {
        return this.getInt(key, 0);
    }

    public int getInt(String key, Integer defaultValue) {
        if (this.isLoading)
            return defaultValue;

        try {
            return Integer.parseInt(this.getValue(key, defaultValue.toString()));
        } catch (Exception e) {
            LOGGER.error("Failed to parse key {} with value '{}' to type integer.", key, this.getValue(key));
        }
        return defaultValue;
    }

    public double getDouble(String key) {
        return this.getDouble(key, 0.0);
    }

    public double getDouble(String key, Double defaultValue) {
        if (this.isLoading)
            return defaultValue;

        try {
            return Double.parseDouble(this.getValue(key, defaultValue.toString()));
        } catch (Exception e) {
            LOGGER.error("Failed to parse key {} with value '{}' to type double.", key, this.getValue(key));
        }

        return defaultValue;
    }

    public void update(String key, String value) {
        this.data.setProperty(key, value);
    }

    public void register(int habbo, String key, String value) {
        if (this.data.getProperty(key, null) != null)
        return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_users_settings VALUES (?, ?, ?)")) {
            statement.setInt(1, habbo);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.update(key, value);
    }

    public void remove(int habbo, String key) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM rp_users_settings WHERE `user_id` = ? AND `key` = ?")) {
            statement.setInt(1, habbo);
            statement.setString(2, key);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

}

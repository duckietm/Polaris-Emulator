package com.srth.plugin.utilities;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Properties;

public class checkRPSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(checkRPSettings.class);

    private final Properties properties;

    public boolean loaded = false;
    private boolean isLoading = false;

    public checkRPSettings() {
        this.properties = new Properties();
    }

    public void loadFromDatabase(){
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            if (statement.execute("SELECT * FROM rp_settings")) {
                try (ResultSet set = statement.getResultSet()) {
                    while (set.next()) {
                        this.properties.put(set.getString("key"), set.getString("value"));
                    }
                }

                this.loaded = true;
            }
        } catch (SQLException e) {

            LOGGER.error("[WEBSOCKETS ROOMS INFO] plugin its illegally, stopping emulator!");
            LOGGER.error("[WEBSOCKETS ROOMS INFO] plugin its illegally, stopping emulator!");
            LOGGER.error("[WEBSOCKETS ROOMS INFO] plugin its illegally, stopping emulator!");
            
            Emulator.prepareShutdown();
            Emulator.isShuttingDown = true;
            Emulator.isReady = false;
        }
    }

    public void saveToDatabase(String value, String key) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE rp_settings SET `value` = ? WHERE `key` = ? LIMIT 1")) {

            statement.setString(1, value);
            statement.setString(2, key);
            statement.executeUpdate();

            this.update(key, value);

        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void updateData(String key, String value){
        this.update(key, value);
        this.saveToDatabase(value, key);
    }

    public String checkSettings(String key, String default_value){
        String value = default_value;
        // Comprueba si no existe y lo registra
        if(this.getValue(key) == "none"){
            this.register(key, value);
            return value;
        } else {
            return value = this.getValue(key);
        }
    }


    public String getValue(String key) {
        return this.getValue(key, "none");
    }


    public String getValue(String key, String defaultValue) {
        if (this.isLoading)
            return defaultValue;

        if (!this.properties.containsKey(key)) {
            return defaultValue;
        }
        return this.properties.getProperty(key, defaultValue);
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
        this.properties.setProperty(key, value);
    }

    public void register(String key, String value) {
        if (this.properties.getProperty(key, null) != null)
            return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_settings VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.update(key, value);
    }

    public void reload() {
        this.isLoading = true;
        this.properties.clear();

        if (loaded) {
            this.loadFromDatabase();
        }

        this.isLoading = false;
        LOGGER.info("Configuration Roleplay -> Loaded!");
    }

}

package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Properties;

public class TextsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TextsManager.class);

    private final Properties texts;

    public TextsManager() {
        long millis = System.currentTimeMillis();

        this.texts = new Properties();

        try {
            this.reload();

            LOGGER.info("Texts Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reload() throws Exception {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM emulator_texts")) {
            while (set.next()) {
                if (this.texts.containsKey(set.getString("key"))) {
                    this.texts.setProperty(set.getString("key"), set.getString("value"));
                } else {
                    this.texts.put(set.getString("key"), set.getString("value"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public String getValue(String key) {
        return this.getValue(key, "");
    }

    public String getValue(String key, String defaultValue) {
        if (!this.texts.containsKey(key)) {
            LOGGER.error("Text key not found: {}", key);
        }
        return this.texts.getProperty(key, defaultValue);
    }

    public String getValueQuietly(String key, String defaultValue) {
        return this.texts.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key) {
        return this.getBoolean(key, false);
    }

    public boolean getBoolean(String key, Boolean defaultValue) {
        try {
            return (this.getValue(key, "0").equals("1")) || (this.getValue(key, "false").equals("true"));
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
        return defaultValue;
    }

    public int getInt(String key) {
        return this.getInt(key, 0);
    }

    public int getInt(String key, Integer defaultValue) {
        try {
            return Integer.parseInt(this.getValue(key, defaultValue.toString()));
        } catch (NumberFormatException e) {
            LOGGER.error("Caught exception", e);
        }
        return defaultValue;
    }

    public void update(String key, String value) {
        this.texts.setProperty(key, value);
    }

    public void register(String key, String value) {
        if (this.texts.getProperty(key, null) != null)
            return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO emulator_texts VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.update(key, value);
    }
}

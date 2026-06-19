package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigurationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationManager.class);
    private static final String EMULATOR_SETTINGS_TABLE = "emulator_settings";
    private static final String WIRED_SETTINGS_TABLE = "wired_emulator_settings";

    private final Properties properties;
    private final Properties wiredProperties;
    private final String configurationPath;
    public boolean loaded = false;
    public boolean isLoading = false;

    public ConfigurationManager(String configurationPath) {
        this.properties = new Properties();
        this.wiredProperties = new Properties();
        this.configurationPath = configurationPath;
        this.reload();
    }

    public void reload() {
        this.isLoading = true;
        this.properties.clear();
        this.wiredProperties.clear();

        InputStream input = null;

        String envDbHostname = System.getenv("DB_HOSTNAME");

        boolean useEnvVarsForDbConnection = false;

        if(envDbHostname != null)
        {
            useEnvVarsForDbConnection = envDbHostname.length() > 1;
        }

        if (!useEnvVarsForDbConnection) {
            try {
                File f = new File(this.configurationPath);
                input = new FileInputStream(f);
                this.properties.load(input);

            } catch (IOException ex) {
                LOGGER.error("Failed to load config file.", ex);
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        LOGGER.error("Failed to close config file input stream.", e);
                    }
                }
            }

        } else {

            Map<String, String> envMapping = new HashMap<>();

            // Database section
            envMapping.put("db.hostname", "DB_HOSTNAME");
            envMapping.put("db.port", "DB_PORT");
            envMapping.put("db.database", "DB_DATABASE");
            envMapping.put("db.username", "DB_USERNAME");
            envMapping.put("db.password", "DB_PASSWORD");
            envMapping.put("db.params", "DB_PARAMS");

            // Game Configuration
            envMapping.put("game.host", "EMU_HOST");
            envMapping.put("game.port", "EMU_PORT");

            // RCON
            envMapping.put("rcon.host", "RCON_HOST");
            envMapping.put("rcon.port", "RCON_PORT");
            envMapping.put("rcon.allowed", "RCON_ALLOWED");

            // Runtime
            envMapping.put("runtime.threads", "RT_THREADS");
            envMapping.put("logging.errors.runtime", "RT_LOG_ERRORS");
            envMapping.put("hotel.timezone", "HOTEL_TIMEZONE");

            for (Map.Entry<String, String> entry : envMapping.entrySet()) {
                String envValue = System.getenv(entry.getValue());

                if (envValue == null || envValue.length() == 0) {
                    LOGGER.info("Cannot find environment-value for variable `{}`", entry.getValue());
                } else {
                    this.properties.setProperty(entry.getKey(), envValue);
                }
            }
        }

        if (this.loaded) {
            this.loadFromDatabase();
        }

        this.isLoading = false;
        LOGGER.info("Configuration Manager -> Loaded!");

        if (Emulator.getPluginManager() != null) {
            Emulator.getPluginManager().fireEvent(new EmulatorConfigUpdatedEvent());
        }
    }

    public void loadFromDatabase() {
        LOGGER.info("Loading configuration from database...");

        long millis = System.currentTimeMillis();
        this.loadSettingsTable(EMULATOR_SETTINGS_TABLE, this.properties, false);
        this.loadSettingsTable(WIRED_SETTINGS_TABLE, this.wiredProperties, true);

        LOGGER.info("Configuration -> loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public void saveToDatabase() {
        this.saveSettingsTable(EMULATOR_SETTINGS_TABLE, this.properties);
        this.saveSettingsTable(WIRED_SETTINGS_TABLE, this.wiredProperties);
    }


    public String getValue(String key) {
        return this.getValue(key, "");
    }


    public String getValue(String key, String defaultValue) {
        if (this.isLoading)
            return defaultValue;

        Properties targetProperties = this.resolveProperties(key);

        if (targetProperties.containsKey(key)) {
            return targetProperties.getProperty(key, defaultValue);
        }

        if (this.isWiredSettingKey(key) && this.properties.containsKey(key)) {
            return this.properties.getProperty(key, defaultValue);
        }

        if (!targetProperties.containsKey(key)) {
            LOGGER.error("Config key not found {}", key);
        }

        return defaultValue;
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
        this.resolveProperties(key).setProperty(key, value);
    }

    public void register(String key, String value) {
        this.register(key, value, "");
    }

    public void register(String key, String value, String comment) {
        Properties targetProperties = this.resolveProperties(key);

        if (targetProperties.getProperty(key, null) != null)
            return;

        this.insertSetting(key, value, comment);
        this.update(key, value);
    }

    private void loadSettingsTable(String tableName, Properties targetProperties, boolean optional) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            if (statement.execute("SELECT * FROM " + tableName)) {
                try (ResultSet set = statement.getResultSet()) {
                    while (set.next()) {
                        targetProperties.put(set.getString("key"), set.getString("value"));
                    }
                }
            }
        } catch (SQLException e) {
            if (optional) {
                LOGGER.warn("Skipping optional config table {}: {}", tableName, e.getMessage());
            } else {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    private void saveSettingsTable(String tableName, Properties sourceProperties) {
        String sql = "UPDATE " + tableName + " SET `value` = ? WHERE `key` = ? LIMIT 1";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<Object, Object> entry : sourceProperties.entrySet()) {
                statement.setString(1, entry.getValue().toString());
                statement.setString(2, entry.getKey().toString());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            if (WIRED_SETTINGS_TABLE.equals(tableName)) {
                LOGGER.warn("Skipping wired config save for table {}: {}", tableName, e.getMessage());
            } else {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    private void insertSetting(String key, String value, String comment) {
        String tableName = this.isWiredSettingKey(key) ? WIRED_SETTINGS_TABLE : EMULATOR_SETTINGS_TABLE;
        String sql = this.isWiredSettingKey(key)
                ? "INSERT INTO " + tableName + " (`key`, `value`, `comment`) VALUES (?, ?, ?)"
                : "INSERT INTO " + tableName + " (`key`, `value`) VALUES (?, ?)";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);

            if (this.isWiredSettingKey(key)) {
                statement.setString(3, comment == null ? "" : comment);
            }

            statement.execute();
        } catch (SQLException e) {
            if (this.isWiredSettingKey(key)) {
                LOGGER.warn("Unable to insert wired setting {} into {}: {}", key, tableName, e.getMessage());
            } else {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    private Properties resolveProperties(String key) {
        return this.isWiredSettingKey(key) ? this.wiredProperties : this.properties;
    }

    private boolean isWiredSettingKey(String key) {
        return key != null && (key.startsWith("wired.") || key.startsWith("hotel.wired."));
    }
}

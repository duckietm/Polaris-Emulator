package com.srth.plugin.utilities;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Properties;

public class checkRPTexts {
    private static final Logger LOGGER = LoggerFactory.getLogger(checkRPTexts.class);

    private final Properties text;

    public boolean loaded = false;
    private boolean isLoading = false;
    private boolean encodingChecked = false; // Bandera para asegurar que la comprobación de encoding sea única

    public checkRPTexts() {
        this.text = new Properties();
    }

    private void ensureTableEncoding() throws SQLException {
        if (this.encodingChecked) {
            return;
        }

        // Sentencia SQL para modificar la tabla y sus columnas a UTF-8 (utf8mb4 es el
        // más robusto en MySQL)
        // Se modifica explícitamente la columna `value` a TEXT y se convierte toda la
        // tabla.
        final String ALTER_TABLE_SQL = "ALTER TABLE `rp_texts` MODIFY `value` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci, CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;";

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            // Ejecutar la sentencia ALTER TABLE
            statement.execute(ALTER_TABLE_SQL);
            this.encodingChecked = true;
        } catch (SQLException e) {
            LOGGER.error(
                    "[RP Texts] ERROR AL AJUSTAR CODIFICACIÓN UTF-8 en 'rp_texts'. Los caracteres especiales fallarán.",
                    e);
            throw e;
        }
    }

    public void loadFromDatabase() {
        try {
            // 1. Ejecutar la comprobación de encoding (solo una vez)
            this.ensureTableEncoding();

            // 2. Cargar datos
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    Statement statement = connection.createStatement()) {
                if (statement.execute("SELECT * FROM rp_texts")) {

                    try (ResultSet set = statement.getResultSet()) {
                        while (set.next()) {
                            this.text.put(set.getString("key"), set.getString("value"));
                        }
                    }
                    this.loaded = true;
                }
            }
        } catch (SQLException e) {
            LOGGER.error(
                    "[WEBSOCKETS ROOMS INFO] Error crítico al cargar/configurar la base de datos, deteniendo el emulador.");
            Emulator.prepareShutdown();
            Emulator.isShuttingDown = true;
            Emulator.isReady = false;
        }
    }

    public void saveToDatabase(String value, String key) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("UPDATE rp_texts SET `value` = ? WHERE `key` = ? LIMIT 1")) {

            statement.setString(1, value);
            statement.setString(2, key);
            statement.executeUpdate();

            this.update(key, value);

        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public String getValue(String key) {
        return this.getValue(key, "");
    }

    public String getValue(String key, String defaultValue) {
        if (this.isLoading)
            return defaultValue;

        if (!this.text.containsKey(key)) {

        }
        return this.text.getProperty(key, defaultValue);
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
        this.text.setProperty(key, value);
    }

    public void register(String key, String value) {
        if (this.text.getProperty(key, null) != null)
            return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO rp_texts VALUES (?, ?)")) {
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
        this.text.clear();

        if (loaded) {
            this.loadFromDatabase();
        }

        this.isLoading = false;
        LOGGER.info("Texts Roleplay -> Loaded!");
    }
}
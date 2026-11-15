package com.srth.plugin.bots;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.catalog.CatalogModeComposer;
import com.eu.habbo.messages.outgoing.catalog.CatalogUpdatedComposer;
import com.eu.habbo.messages.outgoing.catalog.DiscountComposer;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import com.eu.habbo.messages.outgoing.catalog.RecyclerLogicComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.bots.types.BotCompradorPescados;
import com.srth.plugin.bots.types.BotCraftearMaderas;

public class BotsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BotsManager.class);

    public BotsManager() {
        try {

            // Registrar bots
            BotManager.addBotDefinition("compradorPescados", BotCompradorPescados.class);
            BotManager.addBotDefinition("CraftearMaderas", BotCraftearMaderas.class);

            // Updatear los bots
            Emulator.getGameEnvironment().getBotManager().reload();

            // asumiendo que ya tienes la tabla 'bots' con la columna 'type'
            alterEnumColumn("bots", "type", "compradorPescados");
            alterEnumColumn("bots", "type", "CraftearMaderas");

            this.insertTexts();

            CatalogPage catalog_page = Emulator.getGameEnvironment().getCatalogManager()
                    .getCatalogPage("roleplay_plugin");
            if (catalog_page != null) {

                this.insertBots();

                // Actualizar los items para que se vean en el catalog
                Emulator.getGameEnvironment().getItemManager().loadItems();
            } else {
                // Si no se encuentra, intentamos buscar por caption y actualizar
                updateCatalogPageRoleplay("Roleplay Websockets (Furnis del plugin) by SrThrone", "roleplay_plugin");
                String ANSI_RED = "\u001B[31m";
                RoleplayWebsocketsRoomsInfo.utilities()
                        .LOGGER(ANSI_RED + "NO SE ENCUENTRA EL TYPE CATALOG PAGE: roleplay_plugin");
            }

        } catch (Exception e) {
        }
    }

    public void insertBots() {
        CatalogPage catalog_page = Emulator.getGameEnvironment().getCatalogManager()
                .getCatalogPage("roleplay_plugin");
        if (catalog_page != null) {

            // AGREGAR BOTS AL CATALOG PAGE
            this.insertFurnitureBot("compradorPescados", "Pescador", catalog_page.getId(), "Pescador profesional",
                    "hd-3093-10.lg-6010508-63.hr-170-49.ha-1002-63.ch-3032-63-64.ea-3196-62.fa-3344-62.he-61156490-62");

            // AGREGAR BOTS AL CATALOG PAGE
            this.insertFurnitureBot("CraftearMaderas", "Carpintero", catalog_page.getId(), "Carpintero profesional",
                    "hd-3093-10.lg-6010508-63.hr-170-49.ha-1002-63.ch-3032-63-64.ea-3196-62.fa-3344-62.he-61156490-62");

            // Actualizar los items para que se vean en el catalog
            Emulator.getGameEnvironment().getItemManager().loadItems();
            Emulator.getGameEnvironment().getItemManager().loadCrackable();
            Emulator.getGameEnvironment().getItemManager().loadSoundTracks();

            synchronized (Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                    if (room.isLoaded() && room.getUserCount() > 0 && room.getLayout() != null) {
                        room.sendComposer(new RoomRelativeMapComposer(room).compose());
                    }
                }
            }

        }
    }

    /**
     * Busca la página del catálogo por su descripción y actualiza el campo
     * caption_save (o el nombre interno)
     */
    public void updateCatalogPageRoleplay(String targetCaption, String name) {

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE catalog_pages SET caption_save = ? WHERE caption = ?")) {

            statement.setString(1, name);
            statement.setString(2, targetCaption);

            int rowsAffected = statement.executeUpdate();

            if (rowsAffected > 0) {

                Emulator.getGameEnvironment().getCatalogManager().initialize();
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new CatalogUpdatedComposer());
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new CatalogModeComposer(0));
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new DiscountComposer());
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new MarketplaceConfigComposer());
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new GiftConfigurationComposer());
                Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new RecyclerLogicComposer());
                Emulator.getGameEnvironment().getCraftingManager().reload();

                LOGGER.info("Se ha actualizado la página del catálogo con caption: " + targetCaption);
                this.insertBots();
            } else {
                LOGGER.warn("No se encontró ninguna página con el caption: " + targetCaption);
            }

        } catch (SQLException e) {
            LOGGER.error("Error al intentar actualizar catalog_pages por caption", e);
        }
    }

    public void insertTexts() {

        // Textos que se utilizaran
        RoleplayWebsocketsRoomsInfo.getConfig().register("pescadores_keys_json_array",
                "[{\"id\":4,\"keys\":\"si\"},{\"id\":3,\"keys\":\"vender\"},{\"id\":2,\"keys\":\"hola;hi\"},{\"id\":1,\"keys\":\"comprar\"}]");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCompradorPescados_hola",
                "¡Hola %username%! ¿Qué tal te encuentras?");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCompradorPescados_comprar",
                "%username%, ya te muestro las cañas que tengo disponible...");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCompradorPescados_msg_estate_change",
                "¡Vaya! al parecer aparecieron muchos peces... APROVECHA; Woow Woww ¡Hay muchos peces esperando a ser pescados!");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCompradorPescados_msg_actions",
                "¡Tengo cañas disponibles, acercate y di \"comprar\";¿tienes peces para venderme? acercate y di \"vender\"");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCompradorPescados_visitor",
                "¡Hola %username%! ¡Tengo cañas disponibles, acercate y di \"comprar\", ¿tienes peces para venderme? acercate a mi y di \"vender\"");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCompradorPescados_vender",
                "¡Mmm, a ver %username%! ¿Te parece venderme tus %cantidad% peces por %amount%? di si para confirmar.");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCompradorPescados_empty",
                "¡%username%, no tienes peces en tu inventario, no me hagas perder el tiempo!. :@");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCompradorPescados_comprados", "¡Peces comprados!");

        // OTRO BOT
        RoleplayWebsocketsRoomsInfo.getConfig().register("craftearMaderas_keys_json_array",
                "[{\"id\":3,\"keys\":\"craftear\"},{\"id\":2,\"keys\":\"hola;hi\"},{\"id\":1,\"keys\":\"vender\"}]");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCraftearMaderas_hola",
                "¡Hola %username%! ¿Cómo va la recolección de maderas?");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCraftearMaderas_craftear",
                "%username%, ya te muestro los productos que podrás craftear...");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCraftearMaderas_vender",
                "%username%, ¡Estos son los combos que estoy necesitando!...");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCraftearMaderas_msg_actions",
                "¡Objetos, tengo objetos para craftear, acercate y di \"craftear\";");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCraftearMaderas_visitor",
                "¡Hola %username%! ¡Objetos, tengo objetos para craftear, acercate y di \"craftear\";");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCraftearMaderas_empty",
                "¡%username%, no tienes madera en tu inventario, no me hagas perder el tiempo!. :@");
        RoleplayWebsocketsRoomsInfo.getTexts().register("keys_botCraftearMaderas_crafteados", "¡Objetos crafteados!");

        // Configuraciones del emulador de los bots
        Emulator.getConfig().register("hotel.bot.butler.commanddistance", "2");
        Emulator.getConfig().register("hotel.bot.butler.reachdistance", "3");

    }

    public static void alterEnumColumn(String tableName, String columnName, String newValue) {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            // Paso 1: Obtener la definición actual de la columna ENUM
            String currentValues = getCurrentEnumValues(conn, tableName, columnName);
            // Paso 2: Comprobar si el nuevo valor ya existe
            if (currentValues != null && !currentValues.contains("'" + newValue + "'")) {
                // Paso 3: Construir y ejecutar la consulta ALTER TABLE
                String newEnumValues = currentValues.replace(")", ", '" + newValue + "')");

                String alterQuery = "ALTER TABLE `" + tableName + "` CHANGE COLUMN `" + columnName + "` `" + columnName
                        + "` ENUM" + newEnumValues
                        + " NOT NULL DEFAULT 'generic' COLLATE latin1_swedish_ci AFTER `freeroam`";

                try (PreparedStatement statement = conn.prepareStatement(alterQuery)) {
                    statement.executeUpdate();
                    System.out.println("Se ha añadido el valor '" + newValue + "' a la columna '" + columnName + "'.");
                }
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private static String getCurrentEnumValues(Connection conn, String tableName, String columnName)
            throws SQLException {
        String query = "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";

        try (PreparedStatement statement = conn.prepareStatement(query)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    String columnType = rs.getString("COLUMN_TYPE");
                    // Extraer los valores ENUM de la cadena "ENUM('valor1','valor2')"
                    return columnType.substring(columnType.indexOf('('));
                }
            }
        }
        return null;
    }

    public static void userEnterRoom(Room room, Habbo habbo) {
        synchronized (room.getCurrentBots()) {
            for (Bot bot : room.getCurrentBots().values()) {
                try {
                    if (bot instanceof BotCompradorPescados) {
                        ((BotCompradorPescados) bot).onUserEnter(habbo);
                        break;
                    }

                    if (bot instanceof BotCraftearMaderas) {
                        ((BotCraftearMaderas) bot).onUserEnter(habbo);
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
    }

    public void insertFurnitureBot(String name_bot, String name_public, int catalogPage, String mission,
            String figure) {

        String name_bot_final = "bot_" + name_bot;
        if (Emulator.getGameEnvironment().getItemManager().getItem(name_bot_final) == null) {

            int id_nuevo_furni = this.getUltimoItemBase() + 1;
            int id_nuevo_catalog_item = this.getUltimoCatalogItem() + 1;

            String nameFilter = "name:%NAME_BOT%;motto:%MISSION%;figure:%FIGURE%;gender:m"
                    .replace("%NAME_BOT%", name_public).replace("%MISSION%", mission).replace("%FIGURE%", figure);

            this.insertItemBase(id_nuevo_furni, 0, name_bot_final, name_bot_final);
            this.insertCatalogItem(id_nuevo_catalog_item, id_nuevo_furni, catalogPage, name_bot_final, nameFilter);
        }
    }

    public int getUltimoItemBase() {
        int ultimo_item_base = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            if (statement.execute("SELECT * FROM items_base ORDER BY id DESC LIMIT 1")) {
                try (ResultSet set = statement.getResultSet()) {
                    while (set.next()) {
                        ultimo_item_base = set.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[WEBSOCKETS ROOMS INFO]" + e + "");
        }
        return ultimo_item_base;
    }

    public int getUltimoCatalogItem() {
        int ultimo_catalog_item = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            if (statement.execute("SELECT * FROM catalog_items ORDER BY id DESC LIMIT 1")) {
                try (ResultSet set = statement.getResultSet()) {
                    while (set.next()) {
                        ultimo_catalog_item = set.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[WEBSOCKETS ROOMS INFO]" + e + "");
        }
        return ultimo_catalog_item;
    }

    public void insertItemBase(int id_furni, int sprite_id, String item_name, String public_name) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement(
                                "INSERT INTO items_base (`id`,`sprite_id`,`item_name`, `public_name`, `type`) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, id_furni);
            statement.setInt(2, 0);
            statement.setString(3, item_name);
            statement.setString(4, public_name);
            statement.setString(5, "r");
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void insertCatalogItem(int id_catalog_item, int id_furni, int page_id, String catalog_name,
            String extradata) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement(
                                "INSERT INTO catalog_items (`id`,`item_ids`,`page_id`, `catalog_name`, `extradata`) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, id_catalog_item);
            statement.setInt(2, id_furni);
            statement.setInt(3, page_id);
            statement.setString(4, catalog_name);
            statement.setString(5, extradata);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

}
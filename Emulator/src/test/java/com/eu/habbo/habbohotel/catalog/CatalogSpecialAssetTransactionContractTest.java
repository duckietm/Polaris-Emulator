package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogSpecialAssetTransactionContractTest {
    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/" + relativePath));
    }

    @Test
    void specialAssetsAcceptTheOwningTransactionConnection() throws Exception {
        assertTrue(source("bots/BotManager.java").contains(
                "createBot(Connection connection, Map<String, String> data, String type, int ownerId)"));
        assertTrue(source("users/HabboBadge.java").contains("insert(Connection connection)"));
        assertTrue(source("pets/Pet.java").contains("save(Connection connection)"));
        assertTrue(source("users/inventory/EffectsComponent.java").contains(
                "persistEffect(Connection connection, int userId, int effectId, int duration)"));
        assertTrue(source("guilds/GuildManager.java").contains(
                "persistGuild(Connection connection, int furniId, int guildId)"));
    }

    @Test
    void connectionAwareMethodsPropagateSqlFailures() throws Exception {
        assertTrue(source("bots/BotManager.java").contains(
                "createBot(Connection connection, Map<String, String> data, String type, int ownerId) throws SQLException"));
        assertTrue(source("users/HabboBadge.java").contains("insert(Connection connection) throws SQLException"));
        assertTrue(source("pets/Pet.java").contains("save(Connection connection) throws SQLException"));
        assertTrue(source("users/inventory/EffectsComponent.java").contains(
                "persistEffect(Connection connection, int userId, int effectId, int duration) throws SQLException"));
        assertTrue(source("guilds/GuildManager.java").contains(
                "persistGuild(Connection connection, int furniId, int guildId) throws SQLException"));
    }
}

package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Small room and group checks that compared the wrong thing or none at all. */
class RoomSmallGuardsTest {
    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

    private static String source(String path) throws Exception {
        return Files.readString(MAIN.resolve(path)).replaceAll("\\s+", " ");
    }

    @Test
    void aRentedSpaceLetsTheRoomOwnerWalkAndKeepsItselfAtTheEndOfARent() throws Exception {
        String space = source("habbohotel/items/interactions/InteractionRentableSpace.java");

        assertTrue(space.contains("if (habbo.getHabboInfo().getId() == room.getOwnerId()) return true;"));
        assertTrue(space.contains("if (item != this && item.getUserId() == this.renterId)"));
    }

    @Test
    void aBotOffTheMapIsNotPlacedAndAPetMovesOnlyToAFreeTile() throws Exception {
        assertTrue(source("messages/incoming/rooms/bots/BotPlaceEvent.java").contains("if (tile == null) return;"));
        assertTrue(source("messages/incoming/rooms/pets/MovePetEvent.java")
                .contains("if (tile != null && tile.isWalkable() && !ridden)"));
    }

    @Test
    void removingAnAdminChangesOnlyAnAdminRow() throws Exception {
        String guilds = source("habbohotel/guilds/GuildManager.java");

        assertTrue(guilds.contains(
                "UPDATE guilds_members SET level_id = ? WHERE user_id = ? AND guild_id = ? AND level_id = ? LIMIT 1"));
        assertTrue(guilds.contains("statement.setInt(4, GuildRank.ADMIN.type);"));
    }
}

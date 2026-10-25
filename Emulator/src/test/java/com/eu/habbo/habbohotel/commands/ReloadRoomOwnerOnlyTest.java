package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * :reload ran for anyone the permission's "with rights" level let through, group members included: they
 * could unload and reload the room in a loop, sending everyone back in and resetting games and wired.
 * A failed reload also threw on the null room.
 */
class ReloadRoomOwnerOnlyTest {

    @Test
    void aRoomIsReloadedAtMostOncePerCooldown() {
        int room = 912_345;

        assertTrue(ReloadRoomCommand.claimReload(room, 1_000_000L));
        assertFalse(ReloadRoomCommand.claimReload(room, 1_000_000L + ReloadRoomCommand.COOLDOWN_MILLIS - 1));
        assertTrue(ReloadRoomCommand.claimReload(room, 1_000_000L + ReloadRoomCommand.COOLDOWN_MILLIS));
        assertTrue(ReloadRoomCommand.claimReload(room + 1, 1_000_000L), "another room has its own cooldown");
    }

    @Test
    void onlyTheOwnerReloadsAndAMissingRoomCannotThrow() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/ReloadRoomCommand.java"));

        int guard = source.indexOf("!current.isOwner(issuer)");
        int reload = source.indexOf("unloadRoom(room)");

        assertTrue(guard > 0 && guard < reload, "the owner check comes before the reload:\n" + source);
        assertTrue(
                source.contains("new ForwardToRoomComposer(roomId)"),
                "users are sent to the id, not a reloaded room that may be null");
    }
}

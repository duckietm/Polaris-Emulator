package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MonsterPlantSeedOwnershipContractTest {
    @Test
    void monsterPlantSeedsCanOnlyBeRedeemedByTheirOwner() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/ToggleFloorItemEvent.java"));
        int seedBranch = source.indexOf("item instanceof InteractionMonsterPlantSeed");

        assertTrue(seedBranch >= 0, "ToggleFloorItemEvent must keep monsterplant seed handling explicit");

        // Wide enough to cover the whole branch: ownership guard, the claim, the rarity
        // roll and the pet creation.
        String seedHandling = source.substring(seedBranch, Math.min(source.length(), seedBranch + 2000));

        String ownershipGuard = "if (item.getUserId() != this.client.getHabbo().getHabboInfo().getId())";
        String claim = "room.claimItem(item)";

        assertTrue(
                seedHandling.contains(ownershipGuard),
                "Monsterplant seed redemption must reject callers who do not own the seed");
        assertTrue(
                seedHandling.contains("createMonsterplant"),
                "Monsterplant seed handling must create the pet inside the guarded branch");
        assertTrue(
                seedHandling.indexOf(ownershipGuard) < seedHandling.indexOf("createMonsterplant"),
                "Ownership rejection must happen before creating the pet");
        assertTrue(
                seedHandling.contains(claim),
                "Monsterplant seed redemption must claim the seed out of the room, not just delete its row");
        assertTrue(
                seedHandling.indexOf(claim) < seedHandling.indexOf("createMonsterplant"),
                "The seed must be claimed before the pet it turns into exists");
    }
}

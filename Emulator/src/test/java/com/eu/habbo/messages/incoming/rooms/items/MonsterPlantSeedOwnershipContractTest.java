package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MonsterPlantSeedOwnershipContractTest {
    @Test
    void monsterPlantSeedsCanOnlyBeRedeemedByTheirOwner() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/ToggleFloorItemEvent.java"));
        int seedBranch = source.indexOf("item instanceof InteractionMonsterPlantSeed");

        assertTrue(seedBranch >= 0, "ToggleFloorItemEvent must keep monsterplant seed handling explicit");

        String seedHandling = source.substring(seedBranch, Math.min(source.length(), seedBranch + 1400));

        String ownershipGuard = "if (item.getUserId() != this.client.getHabbo().getHabboInfo().getId())";

        assertTrue(seedHandling.contains(ownershipGuard),
                "Monsterplant seed redemption must reject callers who do not own the seed");
        assertTrue(seedHandling.contains("createMonsterplant"),
                "Monsterplant seed handling must create the pet inside the guarded branch");
        assertTrue(seedHandling.indexOf(ownershipGuard) < seedHandling.indexOf("createMonsterplant"),
                "Ownership rejection must happen before creating the pet");
    }
}

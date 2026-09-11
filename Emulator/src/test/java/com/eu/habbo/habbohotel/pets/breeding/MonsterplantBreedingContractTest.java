package com.eu.habbo.habbohotel.pets.breeding;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MonsterplantBreedingContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void twoPlantsOfOneOwnerNeedNobodysConsent() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/rooms/pets/BreedMonsterplantsEvent.java");

        assertTrue(handler.contains("if (theirs.getUserId() == mine.getUserId())"));
        assertTrue(
                handler.indexOf("if (theirs.getUserId() == mine.getUserId())") < handler.indexOf("mine.breed(theirs)"));
    }

    @Test
    void theOtherOwnerIsAskedAndOnlyTheyCanAccept() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/rooms/pets/BreedMonsterplantsEvent.java");

        assertTrue(handler.contains("new PetBreedingStartComposer(ANSWER_ASKED, theirs.getId(), mine.getId())"));
        assertTrue(handler.contains("if (request == null || request.requesterPetId() != theirs.getId()) return;"));
    }

    @Test
    void aPlantNotOpenToEverybodyCannotBeBredByAStranger() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/rooms/pets/BreedMonsterplantsEvent.java");

        assertTrue(handler.contains("if (!ownsOne && !ownsTwo) return;"));
        assertTrue(handler.contains("if (!ownsOne && !plantOne.isPubliclyBreedable()) return;"));
        assertTrue(handler.contains("if (!ownsTwo && !plantTwo.isPubliclyBreedable()) return;"));
    }

    @Test
    void bothOwnersSeeTheSameResultWindow() throws Exception {
        String pet = source("com/eu/habbo/habbohotel/pets/MonsterplantPet.java");

        assertTrue(pet.contains("new MonsterplantBreedingResultComposer(seedOne, seedTwo)"));
        assertTrue(pet.contains("if (ownerOne != null) ownerOne.getClient().sendResponse(result);"));
        assertTrue(pet.contains("if (ownerTwo != null) ownerTwo.getClient().sendResponse(result);"));
    }

    @Test
    void aSeedNobodyGotIsStillWrittenAsASlot() throws Exception {
        String composer =
                source("com/eu/habbo/messages/outgoing/rooms/pets/breeding/MonsterplantBreedingResultComposer.java");

        assertTrue(composer.contains("Seed NONE = new Seed(null, 0, \"\", 0, false)"));
        assertTrue(composer.contains("seed.item() == null ? 0 : seed.item().getId()"));
    }
}

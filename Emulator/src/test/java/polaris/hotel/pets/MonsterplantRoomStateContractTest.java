package polaris.hotel.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The client keeps a plant's growth stage and its "can be revived" flag per pet and offers a fertiliser, a rebreed
 * potion or a revival potion from them, so the room has to hear when they change.
 */
class MonsterplantRoomStateContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    void aGrowthStageIsAnnouncedAsTheLevelWithTheNewStatus() throws Exception {
        String plant = source("polaris/hotel/pets/MonsterplantPet.java");

        assertTrue(plant.contains("new PetLevelUpdatedComposer(this, this.growthStage)"));
        // Once on reaching the last stage and once per stage before it, not on every cycle.
        assertEquals(2, count(plant, "this.announceGrowth();"));
        assertTrue(plant.contains("if (this.growthStage != 7) {"));
    }

    @Test
    void aPlantThatDiesInTheRoomIsAnnounced() throws Exception {
        String plant = source("polaris/hotel/pets/MonsterplantPet.java");
        int died = plant.indexOf("this.hasDied = true;");

        assertTrue(died > 0);
        assertTrue(plant.indexOf("new PetStatusUpdateComposer(this)", died)
                < plant.indexOf("this.roomUnit.clearStatus();", died));
    }

    @Test
    void theLevelPacketKeepsItsThreeFields() throws Exception {
        String composer = source("polaris/messages/outgoing/rooms/pets/PetLevelUpdatedComposer.java");

        assertEquals(3, count(composer, "this.response.appendInt("));
        assertTrue(composer.contains("this.response.appendInt(this.level);"));
    }
}

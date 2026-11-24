package polaris.hotel.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;

class HorseSaddleTest {
    private static Item saddle(String name, String customParams) {
        Item item = mock(Item.class);
        when(item.getName()).thenReturn(name);
        when(item.getCustomParams()).thenReturn(customParams);
        return item;
    }

    @Test
    void theFurniDataParamsDescribeTheSaddle() {
        assertEquals(new HorseSaddle(9, 0, 77), HorseSaddle.fromCustomParams("15 4 9 0 77"));
        assertEquals(new HorseSaddle(10, 0, 103), HorseSaddle.fromCustomParams(" 15 4 10 0 103 "));
        assertEquals(new HorseSaddle(12, 3, 200), HorseSaddle.of(saddle("horse_saddle9", "15 4 12 3 200")));
    }

    @Test
    void anythingElseIsNotASaddleDescription() {
        assertNull(HorseSaddle.fromCustomParams(null));
        assertNull(HorseSaddle.fromCustomParams(""));
        assertNull(HorseSaddle.fromCustomParams("15 2,3 50,50"));
        assertNull(HorseSaddle.fromCustomParams("15 3 9 0 77"));
        assertNull(HorseSaddle.fromCustomParams("15 4 x 0 77"));
        assertNull(HorseSaddle.fromCustomParams("15 4 9 0 0"));
    }

    @Test
    void aDefinitionWithoutParamsIsKnownByItsName() {
        assertEquals(HorseSaddle.CLASSIC, HorseSaddle.of(saddle("horse_saddle1", "")));
        assertEquals(new HorseSaddle(10, 0, 103), HorseSaddle.of(saddle("horse_saddle2", "")));
        assertEquals(HorseSaddle.CLASSIC, HorseSaddle.of(saddle("horse_saddle_other", null)));
        // A horse saddled before the saddle was recorded.
        assertEquals(HorseSaddle.CLASSIC, HorseSaddle.of(null));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theLookAndTheRidingEffectComeFromTheSaddleWorn() throws Exception {
        String figure = source("polaris/messages/outgoing/rooms/pets/RoomPetHorseFigureComposer.java");
        // Both hair layers, then the saddle: the look packet used to write layer 3 twice and drop layer 2.
        int hairTwo = figure.indexOf(
                "this.response.appendInt(2);\n            this.response.appendInt(this.pet.getHairStyle());");
        int saddleLayer = figure.indexOf("this.response.appendInt(HorseSaddle.LAYER);");
        assertTrue(hairTwo > 0 && hairTwo < saddleLayer);
        assertTrue(figure.contains("this.response.appendInt(this.pet.saddle().partId());"));

        assertTrue(source("polaris/messages/outgoing/rooms/pets/RoomPetComposer.java")
                .contains("saddlePart((HorsePet) pet)"));
        assertTrue(source("polaris/threading/runnables/RoomUnitRidePet.java")
                .contains("giveEffect(user, pet.saddle().effectId(), -1);"));
        assertTrue(source("polaris/hotel/rooms/RoomUnitManager.java")
                .contains("rider.getUserInfo().getRiding().saddle().effectId() != effectId"));
    }
}

package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomCopyWiredDataTest {
    private static final Map<Integer, Integer> IDS = Map.of(100, 900, 101, 901, 5, 905);

    @Test
    void thePickedFurniOfABoxBecomeTheCopies() {
        assertEquals(
                "{\"delay\":5,\"itemIds\":[900,901,777],\"furniSource\":100}",
                RoomCopyWiredData.remap("{\"delay\":5,\"itemIds\":[100,101,777],\"furniSource\":100}", IDS));
    }

    @Test
    void variablesChestsAndContractsAreFurniToo() {
        assertEquals(
                "{\"variableItemId\":900,\"chestIds\":[901],\"contractIds\":[900],\"amountVariableId\":901}",
                RoomCopyWiredData.remap(
                        "{\"variableItemId\":100,\"chestIds\":[101],\"contractIds\":[100],\"amountVariableId\":101}",
                        IDS));
    }

    @Test
    void theFurniOfAMatchSettingAreFoundInsideItsList() {
        assertEquals(
                "{\"items\":[{\"item_id\":900,\"state\":\"1\",\"rotation\":2,\"x\":5,\"y\":5}]}",
                RoomCopyWiredData.remap(
                        "{\"items\":[{\"item_id\":100,\"state\":\"1\",\"rotation\":2,\"x\":5,\"y\":5}]}", IDS));
    }

    @Test
    void otherNumbersThatHappenToBeAnIdStay() {
        // A delay, a base item, a hand item, a colour and a coordinate that equal the id of a copied furni.
        String data = "{\"delay\":5,\"baseItemId\":5,\"handItemId\":5,\"colorId\":5,\"x\":5,\"typeId\":5,"
                + "\"selectedGroupId\":5,\"spriteId\":100}";

        assertSame(data, RoomCopyWiredData.remap(data, IDS));
    }

    @Test
    void dataThatIsNotJsonOrNamesNoFurniIsLeftAsItIs() {
        String legacy = "5\t100;101";
        assertSame(legacy, RoomCopyWiredData.remap(legacy, IDS));
        assertSame("", RoomCopyWiredData.remap("", IDS));
        assertSame(null, RoomCopyWiredData.remap(null, IDS));
        String broken = "{\"itemIds\":[100,";
        assertSame(broken, RoomCopyWiredData.remap(broken, IDS));
        String none = "{\"message\":\"hi\"}";
        assertSame(none, RoomCopyWiredData.remap(none, IDS));
    }

    @Test
    void onlyFieldsThatNameFurniCount() {
        assertTrue(RoomCopyWiredData.namesFurni("itemIds"));
        assertTrue(RoomCopyWiredData.namesFurni("item_id"));
        assertTrue(RoomCopyWiredData.namesFurni("pickedFurniIds"));
        assertTrue(RoomCopyWiredData.namesFurni("referenceVariableItemId"));
        assertTrue(RoomCopyWiredData.namesFurni("audienceItemId"));
        assertFalse(RoomCopyWiredData.namesFurni("baseItemId"));
        assertFalse(RoomCopyWiredData.namesFurni("handItemId"));
        assertFalse(RoomCopyWiredData.namesFurni("furniSource"));
        assertFalse(RoomCopyWiredData.namesFurni("roomId"));
        assertFalse(RoomCopyWiredData.namesFurni("cachedPairItemId"));
        assertFalse(RoomCopyWiredData.namesFurni("delay"));
    }
}

package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A roller checked only the tile in front of it, so a furni larger than one tile could roll off the map or onto a hole. */
class RoomRollerFootprintTest {

    @Test
    void aLargeFurniRollsOnlyWhenItsWholeFootprintFits() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomRollerManager.java"));
        int check = source.indexOf("if (!this.footprintFitsInFront(item, roller, tileInFront)) {");
        int composer =
                source.indexOf("new FloorItemOnRollerComposer(item, roller, tileInFront, zOffset, this.room)", check);
        int helper = source.indexOf("private boolean footprintFitsInFront(");

        assertTrue(check > 0 && composer > check);
        assertTrue(source.indexOf("layout.fitsOnMap(tileInFront, width, length, item.getRotation())", helper) > helper);
        assertTrue(source.indexOf("tile.state == RoomTileState.INVALID", helper) > helper);
    }
}

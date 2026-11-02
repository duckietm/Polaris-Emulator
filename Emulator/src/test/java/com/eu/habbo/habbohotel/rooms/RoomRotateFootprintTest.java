package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Turning a furni on its own tile skipped every tile check, and a footprint past the map edge was not refused. */
class RoomRotateFootprintTest {

    @Test
    void aTurnInPlaceChecksTheNewTilesAndTheMapEdge() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemMovementService.java"));
        int method = source.indexOf(
                "HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits) {");
        int edge = source.indexOf("&& !layout.fitsOnMap(", method);
        int newTiles = source.indexOf("checkedTiles.removeAll(oldOccupiedTiles);", method);
        int checks = source.indexOf("for (RoomTile t : checkedTiles) {", newTiles);
        int turn = source.indexOf("item.setRotation(rotation);", method);

        assertTrue(method > 0 && edge > method && newTiles > edge && checks > newTiles && turn > checks);
        assertTrue(source.indexOf("if (oldLocation != tile) {", method) == -1
                || source.indexOf("if (oldLocation != tile) {", method) > turn);
    }
}

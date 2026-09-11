package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;

/**
 * The build height widget lets a user with furniture rights pick an absolute height above the floor
 * and place or move furniture there instead of on top of whatever is already on the square. The mode
 * lives on the actor's {@link RoomUnit} and is applied by the placement and the movement service, so
 * every rights, collision and stacking check still runs; only the resulting height changes.
 */
final class RoomBuildHeight {
    private RoomBuildHeight() {}

    static double apply(Habbo actor, RoomLayout layout, RoomTile tile, double height) {
        if (actor == null || actor.getRoomUnit() == null || !actor.getRoomUnit().isBuildHeightEnabled()) {
            return height;
        }

        if (layout == null || tile == null) {
            return height;
        }

        double floor = layout.getHeightAtSquare(tile.x, tile.y);
        return Math.min(floor + actor.getRoomUnit().getBuildHeight(), Room.MAXIMUM_FURNI_HEIGHT);
    }
}

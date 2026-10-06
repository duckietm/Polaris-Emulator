package com.eu.habbo.habbohotel.rooms.pathfinding.impl;

import com.eu.habbo.habbohotel.rooms.RoomTile;

/** What the search needs to know about a room, read once per search. */
interface PathGrid {
    int width();

    int height();

    /** The room's own tile, or null where the layout has none. Never written to. */
    RoomTile tile(int x, int y);

    /** Whether a unit other than the walker (and the pet it rides) stands on the tile. */
    boolean occupied(int x, int y);

    /** Whether the walker may enter the tile whatever its state (a teleporter it uses, a gate it may open). */
    boolean canOverride(RoomTile tile);
}

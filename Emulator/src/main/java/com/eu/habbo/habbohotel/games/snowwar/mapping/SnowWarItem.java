package com.eu.habbo.habbohotel.games.snowwar.mapping;

/**
 * An item placed on the SnowWar arena (obstacle, machine tile, ...).
 * Position is in TILE coordinates.
 */
public class SnowWarItem {

    private final String name;
    private final int x;
    private final int y;
    private final int rotation;
    private final int walkableHeight;
    private final int collisionHeight;
    private final boolean hidden;

    public SnowWarItem(String name, int x, int y, int rotation) {
        this(name, x, y, rotation,
                SnowWarItemProperties.getWalkableHeight(name),
                SnowWarItemProperties.getCollisionHeight(name));
    }

    /**
     * Explicit collision properties - used for arbitrary hotel furniture
     * saved into room_models.public_items by the arena editor, where the
     * classname is not in the built-in SnowWarItemProperties registry.
     */
    public SnowWarItem(String name, int x, int y, int rotation, int walkableHeight, int collisionHeight) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.walkableHeight = walkableHeight;
        this.collisionHeight = collisionHeight;
        this.hidden = name.equals("snowball_machine") || name.equals("snowball_machine_hidden");
    }

    public String getName() {
        return this.name;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getRotation() {
        return this.rotation;
    }

    public int getWalkableHeight() {
        return this.walkableHeight;
    }

    public int getCollisionHeight() {
        return this.collisionHeight;
    }

    /**
     * Machine tiles are not serialized in the LevelData item list -
     * machines are sent in their own section.
     */
    public boolean isHidden() {
        return this.hidden;
    }
}

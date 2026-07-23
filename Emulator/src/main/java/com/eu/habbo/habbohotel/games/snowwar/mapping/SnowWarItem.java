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
    private final String imageUrl;
    private final int offsetZ;

    public SnowWarItem(String name, int x, int y, int rotation) {
        this(name, x, y, rotation,
                SnowWarItemProperties.getWalkableHeight(name),
                SnowWarItemProperties.getCollisionHeight(name), "", 0);
    }

    public SnowWarItem(String name, int x, int y, int rotation, int walkableHeight, int collisionHeight) {
        this(name, x, y, rotation, walkableHeight, collisionHeight, "", 0);
    }

    public SnowWarItem(String name, int x, int y, int rotation, int walkableHeight, int collisionHeight, String imageUrl) {
        this(name, x, y, rotation, walkableHeight, collisionHeight, imageUrl, 0);
    }

    /**
     * Explicit collision properties + optional room-ad image URL and vertical
     * offset - used for arbitrary hotel furniture saved into
     * room_models.public_items by the arena editor, where the classname is not
     * in the built-in SnowWarItemProperties registry. imageUrl is non-empty
     * only for room-ad (ads_bg) furni so the arena can draw the ad image;
     * offsetZ nudges that full-screen backdrop up/down.
     */
    public SnowWarItem(String name, int x, int y, int rotation, int walkableHeight, int collisionHeight, String imageUrl, int offsetZ) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.rotation = rotation;
        this.walkableHeight = walkableHeight;
        this.collisionHeight = collisionHeight;
        this.hidden = name.equals("snowball_machine") || name.equals("snowball_machine_hidden");
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.offsetZ = offsetZ;
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

    public String getImageUrl() {
        return this.imageUrl;
    }

    public int getOffsetZ() {
        return this.offsetZ;
    }

    /**
     * Machine tiles are not serialized in the LevelData item list -
     * machines are sent in their own section.
     */
    public boolean isHidden() {
        return this.hidden;
    }
}

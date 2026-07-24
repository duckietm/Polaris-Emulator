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

    // Furni footprint in tiles (from the base item's furnidata). Defaults to a
    // single tile; SnowWarMapsManager fills in the real dimensions from the
    // item manager so multi-tile furni block their whole footprint and the
    // client can depth-sort them by their front tile. Kept mutable (set once at
    // load) to avoid widening every constructor.
    private int width = 1;
    private int length = 1;

    public SnowWarItem(String name, int x, int y, int rotation) {
        this(
                name,
                x,
                y,
                rotation,
                SnowWarItemProperties.getWalkableHeight(name),
                SnowWarItemProperties.getCollisionHeight(name),
                "",
                0);
    }

    public SnowWarItem(String name, int x, int y, int rotation, int walkableHeight, int collisionHeight) {
        this(name, x, y, rotation, walkableHeight, collisionHeight, "", 0);
    }

    public SnowWarItem(
            String name, int x, int y, int rotation, int walkableHeight, int collisionHeight, String imageUrl) {
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
    public SnowWarItem(
            String name,
            int x,
            int y,
            int rotation,
            int walkableHeight,
            int collisionHeight,
            String imageUrl,
            int offsetZ) {
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

    public int getWidth() {
        return this.width;
    }

    public int getLength() {
        return this.length;
    }

    /**
     * Sets the furni footprint (tile dimensions) once, at map load. Values are
     * clamped to at least 1 so a missing/zero furnidata entry still occupies a
     * single tile.
     */
    public void setSize(int width, int length) {
        this.width = Math.max(1, width);
        this.length = Math.max(1, length);
    }

    /**
     * Effective footprint width after rotation (width and length swap for the
     * 90/270-degree rotations, mirroring RoomLayout.getRectangle).
     */
    public int getEffectiveWidth() {
        return (this.rotation == 2 || this.rotation == 6) ? this.length : this.width;
    }

    /**
     * Effective footprint length after rotation (see getEffectiveWidth).
     */
    public int getEffectiveLength() {
        return (this.rotation == 2 || this.rotation == 6) ? this.width : this.length;
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

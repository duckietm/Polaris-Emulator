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
    private int width = 1;
    private int length = 1;
    private boolean blocksSnowball = true;

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

    public void setSize(int width, int length) {
        this.width = Math.max(1, width);
        this.length = Math.max(1, length);
    }

    public boolean blocksSnowball() {
        return this.blocksSnowball;
    }

    public void setBlocksSnowball(boolean blocksSnowball) {
        this.blocksSnowball = blocksSnowball;
    }

    public int getEffectiveWidth() {
        return (this.rotation == 2 || this.rotation == 6) ? this.length : this.width;
    }

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

    public boolean isHidden() {
        return this.hidden;
    }
}

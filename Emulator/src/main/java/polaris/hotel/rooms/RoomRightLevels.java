package polaris.hotel.rooms;

public enum RoomRightLevels {
    NONE(0),

    RIGHTS(1),

    GUILD_RIGHTS(2),

    GUILD_ADMIN(3),

    OWNER(4),

    MODERATOR(5),

    SIX(6),

    SEVEN(7),

    EIGHT(8),

    NINE(9);

    public final int level;

    RoomRightLevels(int level) {
        this.level = level;
    }

    public boolean equals(RoomRightLevels level) {
        return this.level == level.level;
    }

    public boolean isEqualOrGreaterThan(RoomRightLevels level) {
        return this.level >= level.level;
    }

    public boolean isGreaterThan(RoomRightLevels level) {
        return this.level > level.level;
    }

    public boolean isLessThan(RoomRightLevels level) {
        return this.level < level.level;
    }
}

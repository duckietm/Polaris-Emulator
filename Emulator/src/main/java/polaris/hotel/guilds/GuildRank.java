package polaris.hotel.guilds;

public enum GuildRank {
    OWNER(0),
    ADMIN(1),
    MEMBER(2),
    REQUESTED(3),
    /** Blocked by a group admin (AIR 13 "block member"): kept in guilds_members so the user cannot rejoin. */
    DELETED(4);

    public final int type;

    GuildRank(int type) {
        this.type = type;
    }

    public static GuildRank getRank(int type) {
        try {
            return values()[type];
        } catch (Exception e) {
            return MEMBER;
        }
    }
}

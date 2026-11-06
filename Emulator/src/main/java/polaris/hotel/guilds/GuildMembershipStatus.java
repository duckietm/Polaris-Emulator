package polaris.hotel.guilds;

public enum GuildMembershipStatus {
    NOT_MEMBER(0),
    MEMBER(1),
    PENDING(2);

    private final int status;

    GuildMembershipStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}

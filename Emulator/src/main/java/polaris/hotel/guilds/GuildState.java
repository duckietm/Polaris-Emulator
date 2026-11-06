package polaris.hotel.guilds;

public enum GuildState {
    OPEN(0),
    EXCLUSIVE(1),
    CLOSED(2),
    LARGE(3),
    LARGE_CLOSED(4);

    public final int state;

    GuildState(int state) {
        this.state = state;
    }

    public static GuildState valueOf(int state) {
        try {
            return values()[state];
        } catch (Exception e) {
            return OPEN;
        }
    }
}

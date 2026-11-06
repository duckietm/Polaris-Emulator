package polaris.hotel.wired.highscores;

public enum WiredHighscoreClearType {
    ALLTIME(0),
    DAILY(1),
    WEEKLY(2),
    MONTHLY(3);

    public final int type;

    WiredHighscoreClearType(int type) {
        this.type = type;
    }
}

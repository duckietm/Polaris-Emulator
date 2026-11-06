package polaris.hotel.games;

public enum GameTeamColors {
    NONE(0),

    RED(1),
    GREEN(2),
    BLUE(3),
    YELLOW(4),

    ONE(5),
    TWO(6),
    THREE(7),
    FOUR(8),
    FIVE(9),
    SIX(10),
    SEVEN(11),
    EIGHT(12),
    NINE(13),
    TEN(14);

    public final int type;

    GameTeamColors(int type) {
        this.type = type;
    }

    public static GameTeamColors fromType(int type) {
        for (GameTeamColors teamColors : values()) {
            if (teamColors.type == type) {
                return teamColors;
            }
        }

        return NONE;
    }
}

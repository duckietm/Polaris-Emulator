package polaris.hotel.users;

public enum SignType {
    ZERO(0),
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8),
    NINE(9),
    TEN(10),
    LOVE(11),
    HATE(12),
    EXCLAMATION(13),
    SMILE(14),
    FOOTBALL(15),
    CARD_YELLOW(16),
    CARD_RED(17),
    NONE(100);

    private final int id;

    SignType(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }
}

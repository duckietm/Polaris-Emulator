package polaris.hotel.games.battleball;

/** An action refused by a BattleBall game; its code is shown to the player (`battleball.error.<code>` in the client). */
public final class BattleBallException extends RuntimeException {

    private final String code;

    public BattleBallException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}

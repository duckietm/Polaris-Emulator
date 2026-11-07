package polaris.hotel.games.wobble;

/** An action refused by a duel; its code is shown to the player (`wobble.error.<code>` in the client). */
public final class WobbleException extends RuntimeException {

    private final String code;

    public WobbleException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}

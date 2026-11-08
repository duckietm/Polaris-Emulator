package polaris.hotel.games.lostmonkey;

/** An action refused on a Lost Monkey island; its code is shown to the player (`lostmonkey.error.<code>`). */
public final class LostMonkeyException extends RuntimeException {

    private final String code;

    public LostMonkeyException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}

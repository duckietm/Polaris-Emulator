package polaris.hotel.games.hall;

/** An action refused by a game of the game hall; its code is shown to the player (`hall.error.<code>` in the client). */
public final class HallException extends RuntimeException {

    private final String code;

    public HallException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}

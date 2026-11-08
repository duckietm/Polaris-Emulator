package polaris.hotel.games.fastfood;

/** An action refused by a FastFood game; its code is shown to the player (`fastfood.error.<code>` in the client). */
public final class FastFoodException extends RuntimeException {

    private final String code;

    public FastFoodException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}

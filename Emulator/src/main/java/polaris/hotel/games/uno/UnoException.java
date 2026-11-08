package polaris.hotel.games.uno;

/** An action the rules refuse; its code is the text key `uno.error.<code>` in the client. */
public final class UnoException extends RuntimeException {

    private final String code;

    public UnoException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}

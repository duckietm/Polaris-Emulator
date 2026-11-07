package polaris.hotel.games.werewolf;

/** An action the rules refuse; its code is the text key `werewolf.error.<code>` in the client. */
public final class WerewolfException extends RuntimeException {

    private final String code;

    public WerewolfException(String code) {
        super(code, null, false, false);
        this.code = code;
    }

    public String code() {
        return this.code;
    }
}

package polaris.hotel.users;

/**
 * Whether an account is still a "new identity" for the client (NewUserIdentityComposer): the client shows its
 * new-player hints, such as the chat reminder and the welcome bubbles, only to those.
 *
 * <p>An account is new for {@code hotel.new_identity.days} days after it was created (7 by default). 0 makes no
 * account new; a negative value restores the old behaviour, where every account was reported as new.
 */
public final class NewIdentityPolicy {
    public static final String DAYS_KEY = "hotel.new_identity.days";
    public static final int DEFAULT_DAYS = 7;

    /** The client's noobness levels: an old identity, and a new one. */
    public static final int OLD_IDENTITY = 0;

    public static final int NEW_IDENTITY = 1;

    private static final long SECONDS_PER_DAY = 86_400L;

    private NewIdentityPolicy() {}

    public static int status(int accountCreated, int now, int days) {
        if (days < 0) return NEW_IDENTITY;
        if (days == 0) return OLD_IDENTITY;

        // An account without a creation time is an old one: it predates the column, or was imported.
        if (accountCreated <= 0) return OLD_IDENTITY;

        long age = (long) now - accountCreated;

        return age < days * SECONDS_PER_DAY ? NEW_IDENTITY : OLD_IDENTITY;
    }
}

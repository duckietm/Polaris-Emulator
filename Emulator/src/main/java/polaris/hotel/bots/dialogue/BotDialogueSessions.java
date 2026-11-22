package polaris.hotel.bots.dialogue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * The page each player was last shown. A choice is accepted only against it: same bot, same room, the code of that
 * page, an option it had, before it expired; the page is then used up. Nothing the client sends picks an option by
 * itself, so an old, replayed or forged choice does nothing.
 */
final class BotDialogueSessions {
    /** What a player was shown: by which bot, where, its single-use code, the keys of its options, and until when. */
    record Session(int botId, int roomId, int nonce, List<String> keys, BotDialogue dialogue, long expiresAt) {}

    /** A choice that held: the dialogue to ask and the key the player picked. */
    record Choice(BotDialogue dialogue, String key) {}

    private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final IntSupplier codes;
    private final long lifetimeMillis;

    /** {@code codes} gives the page codes: random in the hotel; 0 is never used, it means "closed". */
    BotDialogueSessions(LongSupplier clock, IntSupplier codes, long lifetimeMillis) {
        this.clock = clock;
        this.codes = codes;
        this.lifetimeMillis = lifetimeMillis;
    }

    /** Remembers the page shown to {@code userId} and returns its code. */
    int start(int userId, int botId, int roomId, BotDialogue dialogue, BotDialoguePage page) {
        int nonce;

        do {
            nonce = this.codes.getAsInt();
        } while (nonce == 0);

        List<String> keys =
                page.options().stream().map(BotDialoguePage.Option::key).toList();
        this.sessions.put(
                userId,
                new Session(botId, roomId, nonce, keys, dialogue, this.clock.getAsLong() + this.lifetimeMillis));
        return nonce;
    }

    /** The choice, if it matches the page {@code userId} was shown; the page is used up either way it matched. */
    Optional<Choice> take(int userId, int botId, int roomId, int nonce, int index) {
        Session session = this.sessions.get(userId);

        if (session == null
                || session.botId() != botId
                || session.roomId() != roomId
                || session.nonce() != nonce
                || session.expiresAt() < this.clock.getAsLong()
                || index < 0
                || index >= session.keys().size()
                || !this.sessions.remove(userId, session)) {
            return Optional.empty();
        }

        return Optional.of(new Choice(session.dialogue(), session.keys().get(index)));
    }

    void forget(int userId) {
        this.sessions.remove(userId);
    }

    /** Drops every page of a dialogue that is going away. */
    void forget(BotDialogue dialogue) {
        this.sessions.values().removeIf(session -> session.dialogue() == dialogue);
    }
}

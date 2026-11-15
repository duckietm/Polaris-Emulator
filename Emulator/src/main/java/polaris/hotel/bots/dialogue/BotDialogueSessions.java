package polaris.hotel.bots.dialogue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * The page or window each player was last shown. A choice is accepted only against it: same bot, same room, the code
 * of that page, an option it had, within the amount that option allows, before it expired; the page is then used up.
 * Nothing the client sends picks an option by itself, so an old, replayed or forged choice does nothing.
 */
final class BotDialogueSessions {
    /**
     * What a player was shown: by which bot, where, its single-use code, the keys of its options, how many of each may
     * be taken (empty for a menu page), and until when.
     */
    record Session(
            int botId,
            int roomId,
            int nonce,
            List<String> keys,
            List<Integer> limits,
            BotDialogue dialogue,
            long expiresAt) {
        boolean window() {
            return !this.limits.isEmpty();
        }
    }

    /** A choice that held: the dialogue to ask, the key the player picked and how many. */
    record Choice(BotDialogue dialogue, String key, int amount) {}

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
        List<String> keys =
                page.options().stream().map(BotDialoguePage.Option::key).toList();
        return this.remember(userId, botId, roomId, dialogue, keys, List.of());
    }

    /** Remembers the window shown to {@code userId} and returns its code. */
    int start(int userId, int botId, int roomId, BotDialogue dialogue, BotDialogueWindow window) {
        List<String> keys =
                window.items().stream().map(BotDialogueWindow.Item::key).toList();
        List<Integer> limits =
                window.items().stream().map(BotDialogueWindow.Item::maxAmount).toList();
        return this.remember(userId, botId, roomId, dialogue, keys, limits);
    }

    private int remember(
            int userId, int botId, int roomId, BotDialogue dialogue, List<String> keys, List<Integer> limits) {
        int nonce;

        do {
            nonce = this.codes.getAsInt();
        } while (nonce == 0);

        this.sessions.put(
                userId,
                new Session(
                        botId, roomId, nonce, keys, limits, dialogue, this.clock.getAsLong() + this.lifetimeMillis));
        return nonce;
    }

    /** The choice, if it matches the menu page {@code userId} was shown; the page is used up when it matched. */
    Optional<Choice> take(int userId, int botId, int roomId, int nonce, int index) {
        return this.take(userId, botId, roomId, nonce, index, 0);
    }

    /**
     * The choice of {@code amount} of an item, if it matches the window {@code userId} was shown and the item allows
     * that many; the window is used up when it matched.
     */
    Optional<Choice> takeFromWindow(int userId, int botId, int roomId, int nonce, int index, int amount) {
        if (amount < 1) {
            return Optional.empty();
        }

        return this.take(userId, botId, roomId, nonce, index, amount);
    }

    /** {@code amount} is 0 for a menu page and at least 1 for a window. */
    private Optional<Choice> take(int userId, int botId, int roomId, int nonce, int index, int amount) {
        Session session = this.sessions.get(userId);

        if (session == null
                || session.botId() != botId
                || session.roomId() != roomId
                || session.nonce() != nonce
                || session.window() != (amount > 0)
                || session.expiresAt() < this.clock.getAsLong()
                || index < 0
                || index >= session.keys().size()
                || (amount > 0 && amount > session.limits().get(index))
                || !this.sessions.remove(userId, session)) {
            return Optional.empty();
        }

        return Optional.of(new Choice(session.dialogue(), session.keys().get(index), Math.max(amount, 1)));
    }

    void forget(int userId) {
        this.sessions.remove(userId);
    }

    /** Drops every page of a dialogue that is going away. */
    void forget(BotDialogue dialogue) {
        this.sessions.values().removeIf(session -> session.dialogue() == dialogue);
    }
}

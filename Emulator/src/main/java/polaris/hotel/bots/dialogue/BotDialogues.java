package polaris.hotel.bots.dialogue;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.BotDialogueComposer;

/**
 * The bot dialogues of the hotel, shown in a bot's menu. Plugins and emulator features register a {@link BotDialogue};
 * the client asks for one when a player opens a bot's menu ({@code BOT_DIALOGUE_OPEN}) and answers with the option
 * picked ({@code BOT_DIALOGUE_CHOOSE}). The player must be in the bot's room and within
 * {@code hotel.bot.dialogue.distance} tiles of it (default 6, 0 for anywhere in the room); a choice is matched against
 * the page that player was shown, as {@link BotDialogueSessions} describes.
 */
public final class BotDialogues {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotDialogues.class);

    /** How long a page waits for a choice. */
    public static final int SESSION_SECONDS = 60;

    /**
     * How near the bot a player must stand, in tiles; 0 for anywhere in the room. Bound to
     * {@code hotel.bot.dialogue.distance} by the room configuration binder.
     */
    private static final AtomicInteger MAX_DISTANCE = new AtomicInteger(6);

    /** Sets how near the bot a player must stand, in tiles; 0 for anywhere in the room. */
    public static void setMaxDistance(int tiles) {
        MAX_DISTANCE.set(Math.max(0, tiles));
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<BotDialogue> DIALOGUES = new CopyOnWriteArrayList<>();
    private static final BotDialogueSessions SESSIONS =
            new BotDialogueSessions(System::currentTimeMillis, RANDOM::nextInt, SESSION_SECONDS * 1000L);

    private BotDialogues() {}

    public static void register(BotDialogue dialogue) {
        if (dialogue != null && !DIALOGUES.contains(dialogue)) {
            DIALOGUES.add(dialogue);
        }
    }

    /** Takes a dialogue away, with every page it had open: a plugin calls it when it stops. */
    public static void unregister(BotDialogue dialogue) {
        DIALOGUES.remove(dialogue);
        SESSIONS.forget(dialogue);
    }

    /** The dialogue that speaks for {@code bot}, if any. */
    public static Optional<BotDialogue> dialogueFor(Bot bot) {
        for (BotDialogue dialogue : DIALOGUES) {
            try {
                if (dialogue.handles(bot)) {
                    return Optional.of(dialogue);
                }
            } catch (RuntimeException e) {
                LOGGER.error(
                        "Bot dialogue {} failed to answer for bot {}",
                        dialogue.getClass().getName(),
                        bot.getId(),
                        e);
            }
        }

        return Optional.empty();
    }

    /** {@code user} opened the menu of the bot the client knows as {@code clientBotId}. */
    public static void open(User user, int clientBotId) {
        Bot bot = nearBot(user, clientBotId);

        if (bot == null) {
            return;
        }

        Optional<BotDialogue> dialogue = dialogueFor(bot);

        if (dialogue.isEmpty()) {
            return;
        }

        show(
                user,
                bot,
                dialogue.get(),
                call(dialogue.get(), () -> dialogue.get().open(user, bot), bot));
    }

    /** {@code user} picked option {@code index} of the page with code {@code nonce}. */
    public static void choose(User user, int clientBotId, int nonce, int index) {
        Bot bot = nearBot(user, clientBotId);

        if (bot == null) {
            return;
        }

        int userId = user.getUserInfo().getId();
        int roomId = user.getUserInfo().getCurrentRoom().getId();
        Optional<BotDialogueSessions.Choice> choice = SESSIONS.take(userId, bot.getId(), roomId, nonce, index);

        if (choice.isEmpty()) {
            return;
        }

        BotDialogue dialogue = choice.get().dialogue();
        show(
                user,
                bot,
                dialogue,
                call(dialogue, () -> dialogue.choose(user, bot, choice.get().key()), bot));
    }

    /** The player left or logged out: their open page goes with them. */
    public static void forget(int userId) {
        SESSIONS.forget(userId);
    }

    private static void show(User user, Bot bot, BotDialogue dialogue, BotDialoguePage page) {
        if (user.getClient() == null) {
            return;
        }

        if (page == null) {
            SESSIONS.forget(user.getUserInfo().getId());
            user.getClient().sendResponse(BotDialogueComposer.closed(-bot.getId()));
            return;
        }

        int nonce = SESSIONS.start(
                user.getUserInfo().getId(),
                bot.getId(),
                user.getUserInfo().getCurrentRoom().getId(),
                dialogue,
                page);
        user.getClient().sendResponse(new BotDialogueComposer(-bot.getId(), nonce, page));
    }

    /** A dialogue's answer; a dialogue that throws closes the menu instead of breaking the packet handler. */
    private static BotDialoguePage call(
            BotDialogue dialogue, java.util.function.Supplier<BotDialoguePage> answer, Bot bot) {
        try {
            return answer.get();
        } catch (RuntimeException e) {
            LOGGER.error(
                    "Bot dialogue {} failed for bot {}", dialogue.getClass().getName(), bot.getId(), e);
            return null;
        }
    }

    /** The bot, when the player is in its room and near enough to talk to it; null otherwise. */
    private static Bot nearBot(User user, int clientBotId) {
        if (user == null
                || user.getUserInfo() == null
                || user.getRoomUnit() == null
                || clientBotId == 0
                || clientBotId == Integer.MIN_VALUE) {
            return null;
        }

        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null) {
            return null;
        }

        Bot bot = room.getBot(Math.abs(clientBotId));

        if (bot == null || bot.getRoomUnit() == null) {
            return null;
        }

        int distance = MAX_DISTANCE.get();
        RoomTile botTile = bot.getRoomUnit().getCurrentLocation();
        RoomTile userTile = user.getRoomUnit().getCurrentLocation();

        if (distance > 0 && (botTile == null || userTile == null || botTile.distance(userTile) > distance)) {
            return null;
        }

        return bot;
    }
}

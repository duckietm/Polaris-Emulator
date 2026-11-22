package polaris.hotel.bots.dialogue;

import polaris.hotel.bots.Bot;
import polaris.hotel.users.User;

/**
 * Something a bot can say to a player through its menu: when a player opens the menu of a bot this dialogue
 * {@link #handles}, the menu shows a short text and the options of {@link #open}; choosing one calls {@link #choose}
 * with its key, and the page it returns replaces the menu's content (null closes it).
 *
 * <p>Register one with {@link BotDialogues#register}. The emulator guards every choice: it only accepts an option it
 * just showed to that player, for that bot, once, within {@link BotDialogues#SESSION_SECONDS}, while the player is
 * still in the bot's room and near it. Keys never come from the client, so a dialogue may act on them directly. Both
 * methods run on the network thread: keep them short, and move slow work to the emulator's threads.
 */
public interface BotDialogue {
    /** Whether this dialogue speaks for {@code bot}. The first registered dialogue that does is used. */
    boolean handles(Bot bot);

    /** The first page for {@code user}, or null when the bot has nothing to say to them. */
    BotDialoguePage open(User user, Bot bot);

    /** {@code user} chose the option with {@code key} from the page they were shown; the next page, or null to close. */
    BotDialoguePage choose(User user, Bot bot, String key);
}

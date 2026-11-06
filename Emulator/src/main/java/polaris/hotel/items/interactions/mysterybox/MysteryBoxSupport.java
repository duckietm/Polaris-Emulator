package polaris.hotel.items.interactions.mysterybox;

import polaris.hotel.users.User;
import polaris.messages.outgoing.events.mysticbox.MysticBoxCloseComposer;
import polaris.messages.outgoing.events.mysticbox.MysticBoxPrizeComposer;
import polaris.messages.outgoing.events.mysticbox.MysticBoxStartOpenComposer;

/**
 * The three things the mystery box ever says to a client: start waiting, stop waiting, here is your
 * prize. The composer names come from an older reading of these packets and are kept because plugins
 * compile against them.
 */
final class MysteryBoxSupport {
    static final String PRIZE_TYPE_FURNI = "furni";

    private MysteryBoxSupport() {}

    static void sendWait(User user) {
        if (user == null || user.getClient() == null) return;

        user.getClient().sendResponse(new MysticBoxStartOpenComposer());
    }

    static void sendClose(User user) {
        if (user == null || user.getClient() == null) return;

        user.getClient().sendResponse(new MysticBoxCloseComposer());
    }

    static void sendPrize(User user, int classId) {
        if (user == null || user.getClient() == null) return;

        user.getClient().sendResponse(new MysticBoxPrizeComposer(PRIZE_TYPE_FURNI, classId));
    }
}

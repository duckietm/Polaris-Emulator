package com.eu.habbo.habbohotel.items.interactions.mysterybox;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.events.mysticbox.MysticBoxCloseComposer;
import com.eu.habbo.messages.outgoing.events.mysticbox.MysticBoxPrizeComposer;
import com.eu.habbo.messages.outgoing.events.mysticbox.MysticBoxStartOpenComposer;

/**
 * The three things the mystery box ever says to a client: start waiting, stop waiting, here is your
 * prize. The composer names come from an older reading of these packets and are kept because plugins
 * compile against them.
 */
final class MysteryBoxSupport {
    static final String PRIZE_TYPE_FURNI = "furni";

    private MysteryBoxSupport() {}

    static void sendWait(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null) return;

        habbo.getClient().sendResponse(new MysticBoxStartOpenComposer());
    }

    static void sendClose(Habbo habbo) {
        if (habbo == null || habbo.getClient() == null) return;

        habbo.getClient().sendResponse(new MysticBoxCloseComposer());
    }

    static void sendPrize(Habbo habbo, int classId) {
        if (habbo == null || habbo.getClient() == null) return;

        habbo.getClient().sendResponse(new MysticBoxPrizeComposer(PRIZE_TYPE_FURNI, classId));
    }
}

package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.NewUserGift;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.habboway.nux.NewUserGiftComposer;
import com.eu.habbo.messages.outgoing.habboway.nux.NuxAlertComposer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserNuxEvent extends MessageHandler {
    public static Map<Integer, String> keys = new HashMap<Integer, String>() {
        {
            this.put(1, "BOTTOM_BAR_RECEPTION");
            this.put(2, "BOTTOM_BAR_NAVIGATOR");
            this.put(3, "CHAT_INPUT");
            this.put(4, "CHAT_HISTORY_BUTTON");
            this.put(5, "MEMENU_CLOTHES");
            this.put(6, "BOTTOM_BAR_CATALOGUE");
            this.put(7, "CREDITS_BUTTON");
            this.put(8, "DUCKETS_BUTTON");
            this.put(9, "DIAMONDS_BUTTON");
            this.put(10, "FRIENDS_BAR_ALL_FRIENDS");
            this.put(11, "BOTTOM_BAR_NAVIGATOR");
        }
    };

    public static void handle(Habbo habbo) {
        habbo.getHabboStats().nux = true;
        int step = habbo.getHabboStats().nuxStep++;

        if (keys.containsKey(step)) {
            habbo.getClient()
                    .sendResponse(new NuxAlertComposer("helpBubble/add/" + keys.get(step) + "/"
                            + Emulator.getTexts().getValue("nux.step." + step)));
        } else if (!habbo.getHabboStats().nuxReward) {
            // The tour is over and the welcome gift has not been taken: offer what there is to pick.
            sendGiftOffer(habbo);
        } else {
            habbo.getClient().sendResponse(new NuxAlertComposer("nux/lobbyoffer/show"));
        }
    }

    /** The gifts a new player chooses between, as one step of options. */
    private static void sendGiftOffer(Habbo habbo) {
        if (!Emulator.getConfig().getBoolean("hotel.nux.gifts.enabled")) return;

        List<NewUserGift> gifts = Emulator.getGameEnvironment().getItemManager().getNewUserGifts();

        if (gifts.isEmpty()) return;

        habbo.getClient().sendResponse(new NewUserGiftComposer(List.of(gifts)));
    }

    /** Official HabboNuxDialogs: 0 comes from "Verify and get gifts". */
    public static final int REASON_VERIFY = 0;

    /** Official HabboNuxDialogs: 2 comes from the "never again" confirmation. */
    public static final int REASON_NEVER_AGAIN = 2;

    @Override
    public void handle() throws Exception {
        // The official composer (2132) sends the reason; older clients send nothing, so the
        // field is read only while bytes remain.
        int reason = REASON_VERIFY;

        if (this.packet.bytesAvailable() > 0) {
            reason = this.packet.readInt();
        }

        Habbo habbo = this.client.getHabbo();

        if (habbo == null) {
            return;
        }

        if (reason == REASON_NEVER_AGAIN) {
            // "Never again": the script is over for good, so no further step bubble is sent.
            habbo.getHabboStats().nux = true;
            habbo.getHabboStats().nuxReward = true;
            return;
        }

        handle(habbo);
    }
}

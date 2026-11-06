package polaris.messages.incoming.users;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.items.NewUserGift;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelway.nux.NewUserGiftComposer;
import polaris.messages.outgoing.hotelway.nux.NuxAlertComposer;

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

    public static void handle(User user) {
        user.getUserStats().nux = true;
        int step = user.getUserStats().nuxStep++;

        if (keys.containsKey(step)) {
            user.getClient()
                    .sendResponse(new NuxAlertComposer("helpBubble/add/" + keys.get(step) + "/"
                            + Emulator.getTexts().getValue("nux.step." + step)));
        } else if (!user.getUserStats().nuxReward) {
            // The tour is over and the welcome gift has not been taken: offer what there is to pick.
            sendGiftOffer(user);
        } else {
            user.getClient().sendResponse(new NuxAlertComposer("nux/lobbyoffer/show"));
        }
    }

    /** The gifts a new player chooses between, as one step of options. */
    private static void sendGiftOffer(User user) {
        if (!Emulator.getConfig().getBoolean("hotel.nux.gifts.enabled")) return;

        List<NewUserGift> gifts = Emulator.getGameEnvironment().getItemManager().getNewUserGifts();

        if (gifts.isEmpty()) return;

        user.getClient().sendResponse(new NewUserGiftComposer(List.of(gifts)));
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

        User user = this.client.getUser();

        if (user == null) {
            return;
        }

        if (reason == REASON_NEVER_AGAIN) {
            // "Never again": the script is over for good, so no further step bubble is sent.
            user.getUserStats().nux = true;
            user.getUserStats().nuxReward = true;
            return;
        }

        handle(user);
    }
}

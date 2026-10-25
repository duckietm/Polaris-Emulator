package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.NewUserGift;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.users.UserPickGiftEvent;
import java.util.List;

public class PickNewUserGiftEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // totalItems
        int keyA = this.packet.readInt(); // key 1
        int keyB = this.packet.readInt(); // key 2
        int index = this.packet.readInt();

        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        if (Emulator.getPluginManager()
                .fireEvent(new UserPickGiftEvent(habbo, keyA, keyB, index))
                .isCancelled()) {
            return;
        }

        if (habbo.getHabboStats().nuxReward || !Emulator.getConfig().getBoolean("hotel.nux.gifts.enabled")) {
            return;
        }

        // The index is a position in the list the offer sent, not a gift id: nux_gifts.id
        // is a database column and need not run 1..N, so resolving it as an id handed out
        // the wrong gift - or nothing - as soon as an operator deleted one.
        List<NewUserGift> gifts = Emulator.getGameEnvironment().getItemManager().getNewUserGifts();
        if (index < 0 || index >= gifts.size()) return;

        NewUserGift gift = gifts.get(index);
        if (gift == null) return;

        // Record the claim before handing anything over. nuxReward is never written to the
        // database - it is read back from nux at every login - so a user whose client never
        // sends UserNux could take a gift, log out, and take another, without end.
        habbo.getHabboStats().nuxReward = true;
        habbo.getHabboStats().nux = true;
        habbo.getHabboStats().run();

        gift.give(habbo);
    }
}

package polaris.messages.incoming.users;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.items.NewUserGift;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.users.UserPickGiftEvent;

public class PickNewUserGiftEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // totalItems
        int keyA = this.packet.readInt(); // key 1
        int keyB = this.packet.readInt(); // key 2
        int index = this.packet.readInt();

        User user = this.client.getUser();
        if (user == null) return;

        if (Emulator.getPluginManager()
                .fireEvent(new UserPickGiftEvent(user, keyA, keyB, index))
                .isCancelled()) {
            return;
        }

        if (user.getUserStats().nuxReward || !Emulator.getConfig().getBoolean("hotel.nux.gifts.enabled")) {
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
        user.getUserStats().nuxReward = true;
        user.getUserStats().nux = true;
        user.getUserStats().run();

        gift.give(user);
    }
}

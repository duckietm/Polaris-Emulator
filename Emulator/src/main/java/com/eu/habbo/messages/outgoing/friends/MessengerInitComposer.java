package com.eu.habbo.messages.outgoing.friends;

import com.eu.habbo.habbohotel.messenger.MessengerCategory;
import com.eu.habbo.habbohotel.permissions.RankLimits;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

public class MessengerInitComposer extends MessageComposer {
    private final Habbo habbo;

    public MessengerInitComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {

        this.response.init(Outgoing.MessengerInitComposer);
        if (this.habbo.hasPermission("acc_infinite_friends")) {
            this.response.appendInt(Integer.MAX_VALUE);
            this.response.appendInt(1337);
            this.response.appendInt(Integer.MAX_VALUE);
        } else {
            this.response.appendInt(RankLimits.friendsShown(this.habbo, false));
            this.response.appendInt(1337);
            this.response.appendInt(RankLimits.friendsShown(this.habbo, true));
        }
        if (!this.habbo.getHabboInfo().getMessengerCategories().isEmpty()) {

            List<MessengerCategory> messengerCategories =
                    this.habbo.getHabboInfo().getMessengerCategories();
            this.response.appendInt(messengerCategories.size());

            for (MessengerCategory mc : messengerCategories) {
                this.response.appendInt(mc.getId());
                this.response.appendString(mc.getName());
            }
        } else {
            this.response.appendInt(0);
        }
        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}

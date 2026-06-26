package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.UserBadgesComposer;

import java.util.ArrayList;

public class UserWearBadgeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        BadgesComponent.resetSlots(this.client.getHabbo());

        var updatedBadges = new ArrayList<HabboBadge>();
        var usedSlots = new ArrayList<Integer>();
        for (int i = 0; i < 5; i++) {
            int slot = this.packet.readInt();
            if (slot < 1 || slot > 5)
                return;

            String badgeId = this.packet.readString();

            if (badgeId.isEmpty())
                continue;

            HabboBadge badge = this.client.getHabbo().getInventory().getBadgesComponent().getBadge(badgeId);
            if (badge != null && !updatedBadges.contains(badge) && !usedSlots.contains(slot)) {
                usedSlots.add(slot);
                badge.setSlot(slot);
                badge.needsUpdate(true);
                Emulator.getThreading().run(badge);
                updatedBadges.add(badge);
            }
        }

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new UserBadgesComposer(updatedBadges, this.client.getHabbo().getHabboInfo().getId()).compose());
        } else {
            this.client.sendResponse(new UserBadgesComposer(updatedBadges, this.client.getHabbo().getHabboInfo().getId()));
        }
    }
}

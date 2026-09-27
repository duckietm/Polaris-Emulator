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
        java.util.Set<String> wornBefore = new java.util.HashSet<>();
        for (HabboBadge worn :
                this.client.getHabbo().getInventory().getBadgesComponent().getWearingBadges()) {
            wornBefore.add(worn.getCode());
        }
        BadgesComponent.resetSlots(this.client.getHabbo());

        ArrayList<HabboBadge> updatedBadges = new ArrayList<>();
        ArrayList<Integer> usedSlots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int slot = this.packet.readInt();
            if (slot < 1 || slot > 5) return;

            String badgeId = this.packet.readString();

            if (badgeId.isEmpty()) continue;

            HabboBadge badge =
                    this.client.getHabbo().getInventory().getBadgesComponent().getBadge(badgeId);
            if (badge != null && !updatedBadges.contains(badge) && !usedSlots.contains(slot)) {
                usedSlots.add(slot);
                badge.setSlot(slot);
                badge.needsUpdate(true);
                Emulator.getThreading().run(badge);
                updatedBadges.add(badge);
            }
        }

        if (updatedBadges.stream().anyMatch(badge -> !wornBefore.contains(badge.getCode()))) {
            com.eu.habbo.habbohotel.quests.QuestProgressEvents.progress(
                    this.client.getHabbo(), com.eu.habbo.habbohotel.quests.QuestGoalType.WEAR_BADGE, 1);
        }

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client
                    .getHabbo()
                    .getHabboInfo()
                    .getCurrentRoom()
                    .sendComposer(new UserBadgesComposer(
                                    updatedBadges,
                                    this.client.getHabbo().getHabboInfo().getId())
                            .compose());
        } else {
            this.client.sendResponse(new UserBadgesComposer(
                    updatedBadges, this.client.getHabbo().getHabboInfo().getId()));
        }
    }
}

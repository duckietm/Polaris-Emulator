package polaris.messages.incoming.users;

import java.util.ArrayList;
import polaris.Emulator;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserBadgesComposer;

public class UserWearBadgeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        java.util.Set<String> wornBefore = new java.util.HashSet<>();
        for (UserBadge worn :
                this.client.getUser().getInventory().getBadgesComponent().getWearingBadges()) {
            wornBefore.add(worn.getCode());
        }
        BadgesComponent.resetSlots(this.client.getUser());

        ArrayList<UserBadge> updatedBadges = new ArrayList<>();
        ArrayList<Integer> usedSlots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int slot = this.packet.readInt();
            if (slot < 1 || slot > 5) return;

            String badgeId = this.packet.readString();

            if (badgeId.isEmpty()) continue;

            UserBadge badge =
                    this.client.getUser().getInventory().getBadgesComponent().getBadge(badgeId);
            if (badge != null && !updatedBadges.contains(badge) && !usedSlots.contains(slot)) {
                usedSlots.add(slot);
                badge.setSlot(slot);
                badge.needsUpdate(true);
                Emulator.getThreading().run(badge);
                updatedBadges.add(badge);
            }
        }

        if (updatedBadges.stream().anyMatch(badge -> !wornBefore.contains(badge.getCode()))) {
            polaris.hotel.quests.QuestProgressEvents.progress(
                    this.client.getUser(), polaris.hotel.quests.QuestGoalType.WEAR_BADGE, 1);
        }

        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            this.client
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new UserBadgesComposer(
                                    updatedBadges,
                                    this.client.getUser().getUserInfo().getId())
                            .compose());
        } else {
            this.client.sendResponse(new UserBadgesComposer(
                    updatedBadges, this.client.getUser().getUserInfo().getId()));
        }
    }
}

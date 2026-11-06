package polaris.messages.incoming.users;

import polaris.hotel.users.UserBadge;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.BadgeInfoComposer;

/**
 * Official {@code GetBadgeInfoComposer} (AIR 13, header 2895): the badge
 * display furni asks for one badge code before it engraves the plate.
 */
public class GetBadgeInfoEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        String badgeCode = this.packet.readString();

        if (badgeCode == null || badgeCode.isEmpty()) {
            return;
        }

        int badgeId = 0;
        for (UserBadge badge :
                this.client.getUser().getInventory().getBadgesComponent().getBadges()) {
            if (badgeCode.equals(badge.getCode())) {
                badgeId = badge.getId();
                break;
            }
        }

        this.client.sendResponse(new BadgeInfoComposer(badgeId, badgeCode));
    }
}

package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewBadgeButtonConfigComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;

/**
 * Claims the badge the hotel view is offering. The badge behind a request code and whether the offer
 * is open are configuration: {@code hotelview.badgereward.<code>.badge} and {@code .enabled}. A
 * closed offer, an unknown code and a badge the visitor already owns all answer the same way the
 * query does, so the element settles into its claimed state instead of hanging.
 */
public class HotelViewClaimBadgeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String requestCode = this.packet.readString();

        Habbo habbo = this.client.getHabbo();

        if (habbo == null || !BadgeRequestGuard.isValidCode(requestCode)) return;

        String key = "hotelview.badgereward." + requestCode;
        String badgeCode = Emulator.getConfig().getValue(key + ".badge", "");

        if (badgeCode.isEmpty() || !Emulator.getConfig().getBoolean(key + ".enabled")) {
            this.client.sendResponse(new HotelViewBadgeButtonConfigComposer(requestCode, false));
            return;
        }

        if (!habbo.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
            HabboBadge badge = BadgesComponent.createBadge(badgeCode, habbo);

            if (badge != null) {
                this.client.sendResponse(new AddUserBadgeComposer(badge));
            }
        }

        this.client.sendResponse(new HotelViewBadgeButtonConfigComposer(requestCode, true));
    }
}

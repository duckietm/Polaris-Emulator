package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewBadgeButtonConfigComposer;

/**
 * The hotel view asks whether a badge on offer has already been claimed, once per refresh of the
 * element that shows it. The badge behind a request code is configured as
 * {@code hotelview.badgereward.<code>.badge}, and {@code .enabled} is what closes the offer.
 */
public class GetIsBadgeRequestFulfilledEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String requestCode = this.packet.readString();

        Habbo habbo = this.client.getHabbo();

        if (habbo == null || !BadgeRequestGuard.isValidCode(requestCode)) return;

        String badge = Emulator.getConfig().getValue("hotelview.badgereward." + requestCode + ".badge", "");
        boolean claimed =
                !badge.isEmpty() && habbo.getInventory().getBadgesComponent().hasBadge(badge);

        this.client.sendResponse(new HotelViewBadgeButtonConfigComposer(requestCode, claimed));
    }
}

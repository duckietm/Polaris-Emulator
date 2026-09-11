package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.habbohotel.users.inventory.BadgeOwnerCounts;
import com.eu.habbo.habbohotel.users.inventory.BadgeRarity;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Official {@code BadgeInfoMessageEvent} (AIR 13, header 3228): the answer to
 * {@code GetBadgeInfo}. The badge display furni engraves its plate from this -
 * {@code FurnitureBadgeDisplayWidgetHandler.onBadgeInfo} reads the rarity tier
 * and the owner count and colours the frame with them.
 */
public class BadgeInfoComposer extends MessageComposer {

    private final int badgeId;
    private final String badgeCode;

    public BadgeInfoComposer(int badgeId, String badgeCode) {
        this.badgeId = badgeId;
        this.badgeCode = badgeCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        int ownerCount = BadgeOwnerCounts.ownerCount(this.badgeCode);

        this.response.init(Outgoing.BadgeInfoComposer);
        this.response.appendInt(this.badgeId);
        this.response.appendString(this.badgeCode);
        this.response.appendInt(ownerCount);
        this.response.appendInt(BadgeRarity.tierForOwnerCount(ownerCount));
        return this.response;
    }

    public int getBadgeId() {
        return badgeId;
    }

    public String getBadgeCode() {
        return badgeCode;
    }
}

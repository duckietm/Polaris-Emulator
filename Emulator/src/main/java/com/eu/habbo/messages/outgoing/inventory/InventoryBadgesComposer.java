package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.inventory.BadgeOwnerCounts;
import com.eu.habbo.habbohotel.users.inventory.BadgeRarity;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.HashSet;
import java.util.Set;

public class InventoryBadgesComposer extends MessageComposer {
    private final Habbo habbo;

    public InventoryBadgesComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.habbo == null) return null;

        Set<HabboBadge> equippedBadges = new HashSet<>();

        this.response.init(Outgoing.InventoryBadgesComposer);

        this.response.appendInt(
                this.habbo.getInventory().getBadgesComponent().getBadges().size());
        for (HabboBadge badge : this.habbo.getInventory().getBadgesComponent().getBadges()) {
            // Official `BadgesMessageParser` (AIR 13): every badge carries how many
            // Habbos own it and its rarity tier, which is what `BadgesModel.initBadges`
            // builds each `Badge` from -- the inventory needs no leaderboard call.
            int ownerCount = BadgeOwnerCounts.ownerCount(badge.getCode());

            this.response.appendInt(badge.getId());
            this.response.appendString(badge.getCode());
            this.response.appendInt(ownerCount);
            this.response.appendInt(BadgeRarity.tierForOwnerCount(ownerCount));

            if (badge.getSlot() > 0) equippedBadges.add(badge);
        }

        this.response.appendInt(equippedBadges.size());

        for (HabboBadge badge : equippedBadges) {
            this.response.appendInt(badge.getSlot());
            this.response.appendString(badge.getCode());
        }

        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}

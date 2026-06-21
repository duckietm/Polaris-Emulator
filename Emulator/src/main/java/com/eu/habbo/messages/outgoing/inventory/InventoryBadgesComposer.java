package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
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
        if (this.habbo == null)
            return null;

        Set<HabboBadge> equippedBadges = new HashSet<>();

        this.response.init(Outgoing.InventoryBadgesComposer);

        this.response.appendInt(this.habbo.getInventory().getBadgesComponent().getBadges().size());
        for (HabboBadge badge : this.habbo.getInventory().getBadgesComponent().getBadges()) {
            this.response.appendInt(badge.getId());
            this.response.appendString(badge.getCode());

            if (badge.getSlot() > 0)
                equippedBadges.add(badge);
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

package polaris.messages.outgoing.inventory;

import java.util.HashSet;
import java.util.Set;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.inventory.BadgeOwnerCounts;
import polaris.hotel.users.inventory.BadgeRarity;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class InventoryBadgesComposer extends MessageComposer {
    private final User user;

    public InventoryBadgesComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.user == null) return null;

        Set<UserBadge> equippedBadges = new HashSet<>();

        this.response.init(Outgoing.InventoryBadgesComposer);

        this.response.appendInt(
                this.user.getInventory().getBadgesComponent().getBadges().size());
        for (UserBadge badge : this.user.getInventory().getBadgesComponent().getBadges()) {
            int ownerCount = BadgeOwnerCounts.ownerCount(badge.getCode());

            this.response.appendInt(badge.getId());
            this.response.appendString(badge.getCode());
            this.response.appendInt(ownerCount);
            this.response.appendInt(BadgeRarity.tierForOwnerCount(ownerCount));

            if (badge.getSlot() > 0) equippedBadges.add(badge);
        }

        this.response.appendInt(equippedBadges.size());

        for (UserBadge badge : equippedBadges) {
            this.response.appendInt(badge.getSlot());
            this.response.appendString(badge.getCode());
        }

        return this.response;
    }

    public User getUser() {
        return user;
    }
}

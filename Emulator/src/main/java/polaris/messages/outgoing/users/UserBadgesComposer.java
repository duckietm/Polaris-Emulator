package polaris.messages.outgoing.users;

import java.util.ArrayList;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.inventory.BadgeOwnerCounts;
import polaris.hotel.users.inventory.BadgeRarity;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * UserCurrentBadges (1087) in the official layout: per worn slot
 * {@code int slot, string code, int ownerCount, int badgeRarityId}, the
 * rarity tier being derived from how many users hold the badge.
 */
public class UserBadgesComposer extends MessageComposer {
    private final ArrayList<UserBadge> badges;
    private final int user;

    public UserBadgesComposer(ArrayList<UserBadge> badges, int user) {
        this.badges = badges;
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserBadgesComposer);
        this.response.appendInt(this.user);
        synchronized (this.badges) {
            this.response.appendInt(this.badges.size());
            for (UserBadge badge : this.badges) {
                int ownerCount = BadgeOwnerCounts.ownerCount(badge.getCode());
                this.response.appendInt(badge.getSlot());
                this.response.appendString(badge.getCode());
                this.response.appendInt(ownerCount);
                this.response.appendInt(BadgeRarity.tierForOwnerCount(ownerCount));
            }
        }
        return this.response;
    }

    public ArrayList<UserBadge> getBadges() {
        return badges;
    }

    public int getUser() {
        return user;
    }
}

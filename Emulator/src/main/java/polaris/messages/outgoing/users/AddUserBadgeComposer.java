package polaris.messages.outgoing.users;

import polaris.hotel.users.UserBadge;
import polaris.hotel.users.inventory.BadgeOwnerCounts;
import polaris.hotel.users.inventory.BadgeRarity;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AddUserBadgeComposer extends MessageComposer {
    private final UserBadge badge;
    private final String senderName;

    public AddUserBadgeComposer(UserBadge badge) {
        this(badge, "");
    }

    public AddUserBadgeComposer(UserBadge badge, String senderName) {
        this.badge = badge;
        this.senderName = senderName == null ? "" : senderName;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AddUserBadgeComposer);
        this.response.appendInt(this.badge.getId());
        this.response.appendString(this.badge.getCode());
        this.response.appendString(this.senderName);
        // The cached count can predate this grant; the receiver owns it now.
        int ownerCount = Math.max(1, BadgeOwnerCounts.ownerCount(this.badge.getCode()));
        this.response.appendInt(ownerCount);
        this.response.appendInt(BadgeRarity.tierForOwnerCount(ownerCount));
        return this.response;
    }

    public UserBadge getBadge() {
        return badge;
    }

    public String getSenderName() {
        return senderName;
    }
}

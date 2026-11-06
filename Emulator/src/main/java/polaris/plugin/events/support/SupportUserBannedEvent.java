package polaris.plugin.events.support;

import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.users.User;

public class SupportUserBannedEvent extends SupportEvent {

    public final User target;

    public final ModToolBan ban;

    public SupportUserBannedEvent(User moderator, User target, ModToolBan ban) {
        super(moderator);

        this.target = target;
        this.ban = ban;
    }
}

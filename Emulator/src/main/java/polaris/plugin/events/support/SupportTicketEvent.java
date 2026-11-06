package polaris.plugin.events.support;

import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.users.User;

public class SupportTicketEvent extends SupportEvent {
    public final ModToolIssue ticket;

    public SupportTicketEvent(User moderator, ModToolIssue ticket) {
        super(moderator);

        this.ticket = ticket;
    }
}

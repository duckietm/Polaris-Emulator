package polaris.plugin.events.support;

import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.users.User;

public class SupportTicketStatusChangedEvent extends SupportTicketEvent {

    public SupportTicketStatusChangedEvent(User moderator, ModToolIssue ticket) {
        super(moderator, ticket);
    }
}

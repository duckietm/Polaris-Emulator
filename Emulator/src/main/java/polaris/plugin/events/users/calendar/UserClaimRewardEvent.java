package polaris.plugin.events.users.calendar;

import polaris.hotel.campaign.calendar.CalendarCampaign;
import polaris.hotel.campaign.calendar.CalendarRewardObject;
import polaris.hotel.users.User;
import polaris.plugin.events.users.UserEvent;

public class UserClaimRewardEvent extends UserEvent {

    public CalendarCampaign campaign;
    public int day;
    public CalendarRewardObject reward;
    public boolean force;

    public UserClaimRewardEvent(
            User user, CalendarCampaign campaign, int day, CalendarRewardObject reward, boolean force) {
        super(user);

        this.campaign = campaign;
        this.day = day;
        this.reward = reward;
        this.force = force;
    }
}

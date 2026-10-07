package com.eu.habbo.habbohotel.commands;

import static java.time.temporal.ChronoUnit.DAYS;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarCampaign;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.outgoing.events.calendar.AdventCalendarDataComposer;
import java.sql.Timestamp;
import java.util.Date;

public class CalendarCommand extends Command {
    public CalendarCommand() {
        super(
                "cmd_calendar",
                Emulator.getTexts().getValue("commands.keys.cmd_calendar").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (Emulator.getConfig().getBoolean("hotel.calendar.enabled")) {
            String campaignName = Emulator.getConfig().getValue("hotel.calendar.default");

            if (params.length > 1 && gameClient.getHabbo().hasPermission("cmd_calendar_staff")) {
                campaignName = params[1];
            }
            CalendarCampaign campaign =
                    Emulator.getGameEnvironment().getCalendarManager().getCalendarCampaign(campaignName);
            if (campaign == null) {
                gameClient
                        .getHabbo()
                        .whisperLocalizedOrDefault(
                                "commands.error.cmd_calendar.not_found",
                                "That calendar campaign does not exist.",
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
            int daysBetween = (int) DAYS.between(
                    new Timestamp(campaign.getStartTimestamp() * 1000L).toInstant(), new Date().toInstant());
            if (daysBetween >= 0) {
                gameClient.sendResponse(new AdventCalendarDataComposer(
                        campaign.getName(),
                        campaign.getImage(),
                        campaign.getTotalDays(),
                        daysBetween,
                        gameClient.getHabbo().getHabboStats().calendarRewardsClaimed,
                        campaign.getLockExpired()));
            }
        }

        return true;
    }
}

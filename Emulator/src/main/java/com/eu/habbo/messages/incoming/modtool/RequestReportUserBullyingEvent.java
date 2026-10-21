package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.BullyReportRequestComposer;

/** GetGuideReportingStatus (3786): may this user report a bully now? */
public class RequestReportUserBullyingEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GuardianTicket ticket = Emulator.getGameEnvironment().getGuideManager().getRecentTicket(this.client.getHabbo());

        if (ticket != null) {
            if (ticket.inProgress()) {
                this.client.sendResponse(
                        new BullyReportRequestComposer(BullyReportRequestComposer.ONGOING_HELPER_CASE, ticket));
                return;
            }

            // The ticket date is in milliseconds and the cooldown in seconds.
            long secondsSince = (System.currentTimeMillis() - ticket.getDate().getTime()) / 1000L;

            if (secondsSince < Emulator.getConfig().getInt("guardians.reporting.cooldown")) {
                this.client.sendResponse(new BullyReportRequestComposer(BullyReportRequestComposer.TOO_RECENT));
                return;
            }
        }

        this.client.sendResponse(new BullyReportRequestComposer(BullyReportRequestComposer.START_REPORT));
    }
}

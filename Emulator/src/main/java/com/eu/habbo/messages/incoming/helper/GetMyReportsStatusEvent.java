package com.eu.habbo.messages.incoming.helper;

import com.eu.habbo.habbohotel.modtool.MyReportStatusRepository;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.MyReportsStatusComposer;

/**
 * AIR 13 composer 2935 (`HabboHelp.requestReportsStatus`): the player opened the
 * "my reports" window, so send back every report they filed with its decision and appeal
 * state.
 */
public class GetMyReportsStatusEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new MyReportsStatusComposer(MyReportStatusRepository.findByReporter(
                this.client.getHabbo().getHabboInfo().getId())));
    }
}

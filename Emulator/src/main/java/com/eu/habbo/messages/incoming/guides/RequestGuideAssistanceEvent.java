package com.eu.habbo.messages.incoming.guides;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guides.GuideTour;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guides.GuideSessionErrorComposer;

public class RequestGuideAssistanceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt();
        String message = this.packet.readString();

        GuideTour activeTour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByHabbo(this.client.getHabbo());

        if (activeTour != null) {
            this.client.sendResponse(new GuideSessionErrorComposer(GuideSessionErrorComposer.SOMETHING_WRONG_REQUEST));
            return;
        }

        var tour = new GuideTour(this.client.getHabbo(), message);
        tour.setStartTime(Emulator.getIntUnixTimestamp());

        Emulator.getGameEnvironment().getGuideManager().findHelper(tour);
    }
}

package com.eu.habbo.messages.incoming.guides;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guides.GuideTour;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guides.GuideSessionInvitedToGuideRoomComposer;

public class GuideInviteUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GuideTour tour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByHelper(this.client.getHabbo());

        // The invite names the guide's room: outside one, or with either side gone, it threw and logged a stack trace.
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (tour == null || room == null) return;

        ServerMessage message = new GuideSessionInvitedToGuideRoomComposer(room).compose();
        for (Habbo participant : new Habbo[] {tour.getNoob(), tour.getHelper()}) {
            if (participant != null && participant.getClient() != null) {
                participant.getClient().sendResponse(message);
            }
        }
    }
}

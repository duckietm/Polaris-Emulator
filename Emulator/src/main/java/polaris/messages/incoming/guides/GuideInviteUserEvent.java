package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guides.GuideSessionInvitedToGuideRoomComposer;

public class GuideInviteUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GuideTour tour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByHelper(this.client.getUser());

        // The invite names the guide's room: outside one, or with either side gone, it threw and logged a stack trace.
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (tour == null || room == null) return;

        ServerMessage message = new GuideSessionInvitedToGuideRoomComposer(room).compose();
        for (User participant : new User[] {tour.getNoob(), tour.getHelper()}) {
            if (participant != null && participant.getClient() != null) {
                participant.getClient().sendResponse(message);
            }
        }
    }
}

package polaris.messages.incoming.users.firstlogin;

import polaris.hotel.users.User;
import polaris.hotel.users.firstlogin.FirstLoginSetup;
import polaris.hotel.users.firstlogin.FirstLoginSetupService;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;
import polaris.messages.outgoing.users.UserHomeRoomComposer;

/**
 * The answer to the first-login setup (custom, 9497): the starter room's template id, or 0 for none. The offer ends
 * either way; with a room the user gets it as their home room and is sent there.
 */
public class FirstLoginSetupChooseEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        int templateId = this.packet.readInt();

        FirstLoginSetupService service = FirstLoginSetup.service();
        if (service == null) return;

        int roomId =
                service.complete(user.getUserInfo().getId(), user.getUserInfo().getUsername(), templateId);
        if (roomId <= 0) return;

        user.getUserInfo().setHomeRoom(roomId);
        this.client.sendResponse(new UserHomeRoomComposer(roomId, 0));
        this.client.sendResponse(new ForwardToRoomComposer(roomId));
    }
}

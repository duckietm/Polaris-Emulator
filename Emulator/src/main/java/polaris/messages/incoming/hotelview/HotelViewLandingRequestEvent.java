package polaris.messages.incoming.hotelview;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewLandingComposer;

public class HotelViewLandingRequestEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        User user = this.client.getUser();

        if (user == null) return;

        this.client.sendResponse(new HotelViewLandingComposer(
                user.hasPermission(Permission.ACC_HOTELVIEW_EDIT),
                Emulator.getGameEnvironment().getHotelViewManager().getScene(),
                Emulator.getGameEnvironment()
                        .getHotelViewManager()
                        .getSlotsForUser(user.getUserInfo().getId())));
    }
}

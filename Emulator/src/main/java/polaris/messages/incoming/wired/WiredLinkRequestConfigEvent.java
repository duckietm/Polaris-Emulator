package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.outgoing.wired.WiredLinkConfigurationComposer;

/** WIRED Link configuration request (AIR 3208): {@code int furniId}; answered with the configuration (3405). */
public class WiredLinkRequestConfigEvent extends WiredLinkMessageHandler {
    @Override
    protected int operation() {
        return 0;
    }

    @Override
    protected void handleLink(Room room, User user, InteractionWiredLink link) {
        this.client.sendResponse(new WiredLinkConfigurationComposer(
                WiredLinkService.get().configuration(user.getUserInfo().getId(), room.getId(), link.getId())));
    }

    @Override
    public int getRatelimit() {
        return 0;
    }
}

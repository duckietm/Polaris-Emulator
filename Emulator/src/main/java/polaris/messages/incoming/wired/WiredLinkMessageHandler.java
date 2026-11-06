package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.InteractionWiredLink;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.link.WiredLinkService;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredLinkOperationResultComposer;

/**
 * The WIRED Link window's packets (AIR {@code WiredLinkController}). Every one names the Link furni
 * first; it must stand in the user's current room, belong to the user (the official owner-only rule)
 * and the user must control that room (owner or rights), so a Link can only join a network for rooms
 * its owner may build in. A request that fails these gets the official result code back.
 */
public abstract class WiredLinkMessageHandler extends MessageHandler {
    static final String RATE_LIMIT_GROUP = "wired_link";

    @Override
    public final void handle() throws Exception {
        int furniId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        Room room = currentRoom();
        if (room == null || this.client.getUser() == null) {
            return;
        }
        User user = this.client.getUser();
        UserItem item = room.getUserItem(furniId);
        if (!InteractionWiredLink.isLink(item)) {
            this.refuse(room, furniId, WiredLinkService.LINK_GONE);
            return;
        }
        if (item.getUserId() != user.getUserInfo().getId() || !room.hasRights(user)) {
            this.refuse(room, furniId, WiredLinkService.NOT_OWNER);
            return;
        }
        this.handleLink(room, user, (InteractionWiredLink) item);
    }

    /** The official operation this packet answers for (0 when it gets no result packet). */
    protected abstract int operation();

    /** Handles the request for a Link the user owns in a room they control. */
    protected abstract void handleLink(Room room, User user, InteractionWiredLink link) throws Exception;

    protected final void refuse(Room room, int furniId, int resultCode) {
        if (this.operation() > 0) {
            this.client.sendResponse(new WiredLinkOperationResultComposer(
                    WiredLinkService.Result.failure(room.getId(), furniId, this.operation(), resultCode)));
        }
    }

    @Override
    public int getRatelimit() {
        return 200;
    }

    @Override
    public String getRatelimitGroup() {
        return RATE_LIMIT_GROUP;
    }
}

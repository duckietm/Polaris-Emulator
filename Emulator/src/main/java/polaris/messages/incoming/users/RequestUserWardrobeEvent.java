package polaris.messages.incoming.users;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserWardrobeComposer;

public class RequestUserWardrobeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt(); // requested wardrobe page
        this.client.sendResponse(
                new UserWardrobeComposer(this.client.getUser().getInventory().getWardrobeComponent()));
    }
}

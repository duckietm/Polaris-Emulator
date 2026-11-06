package polaris.messages.incoming.inventory.prefixes;

import polaris.hotel.users.User;
import polaris.hotel.users.inventory.UserVisualSettingsComponent;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;

public class SetDisplayOrderEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();

        if (user == null
                || user.getInventory() == null
                || user.getInventory().getUserVisualSettingsComponent() == null) {
            return;
        }

        String displayOrder = UserVisualSettingsComponent.sanitizeDisplayOrder(this.packet.readString());
        user.getInventory().getUserVisualSettingsComponent().setDisplayOrder(displayOrder);
        this.client.sendResponse(new UserNickIconsComposer(user));

        if (user.getUserInfo().getCurrentRoom() != null) {
            user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(user).compose());
        }
    }
}

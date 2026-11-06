package polaris.messages.incoming.inventory.nickicons;

import polaris.hotel.users.UserNickIcon;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;

public class SetActiveNickIconEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int nickIconId = this.packet.readInt();

        if (nickIconId == 0) {
            this.client.getUser().getInventory().getNickIconsComponent().deactivateAll();
            this.client.sendResponse(new UserNickIconsComposer(this.client.getUser()));
            if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
                this.client
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .sendComposer(new RoomUserDataComposer(this.client.getUser()).compose());
            }
            return;
        }

        UserNickIcon nickIcon =
                this.client.getUser().getInventory().getNickIconsComponent().getNickIcon(nickIconId);

        if (nickIcon == null) {
            return;
        }

        this.client.getUser().getInventory().getNickIconsComponent().setActive(nickIconId);
        this.client.sendResponse(new UserNickIconsComposer(this.client.getUser()));
        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            this.client
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserDataComposer(this.client.getUser()).compose());
        }
    }
}

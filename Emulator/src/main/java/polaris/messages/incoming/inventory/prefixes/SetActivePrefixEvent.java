package polaris.messages.incoming.inventory.prefixes;

import polaris.hotel.users.UserPrefix;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import polaris.messages.outgoing.inventory.prefixes.ActivePrefixUpdatedComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;

public class SetActivePrefixEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int prefixId = this.packet.readInt();

        if (prefixId == 0) {
            this.client.getUser().getInventory().getPrefixesComponent().deactivateAll();
            this.client.sendResponse(new ActivePrefixUpdatedComposer(null));
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

        UserPrefix prefix =
                this.client.getUser().getInventory().getPrefixesComponent().getPrefix(prefixId);

        if (prefix == null) return;

        this.client.getUser().getInventory().getPrefixesComponent().setActive(prefixId);
        this.client.sendResponse(new ActivePrefixUpdatedComposer(prefix));
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

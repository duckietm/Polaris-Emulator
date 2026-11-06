package polaris.messages.incoming.wired;

import polaris.hotel.items.interactions.wired.contract.ContractFurniCatalog;
import polaris.hotel.items.interactions.wired.contract.InteractionWiredContract;
import polaris.hotel.items.interactions.wired.contract.WiredNativeContract;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.wired.WiredContractContentsComposer;

/**
 * The official contract editor asking for the contract the server opened (AIR {@code WiredContractController}). Read
 * access is enough, as for any wired box: the editor itself greys out the save button for a viewer without write
 * rights.
 */
public class GetWiredContractContentsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.currentRoom();
        int furniId = BuildersClubItemIds.fromWire(room, this.packet.readInt());

        if (room == null || !room.canInspectWired(this.client.getUser())) return;

        UserItem item = room.getUserItem(furniId);
        if (!(item instanceof InteractionWiredContract contract)) return;

        WiredNativeContract contents = contract.toNative(contract.getId(), ContractFurniCatalog.live());
        if (contents == null) return;

        this.client.sendResponse(new WiredContractContentsComposer(contents));
    }
}

package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.contract.ContractFurniCatalog;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.WiredNativeContract;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredContractContentsComposer;

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

        if (room == null || !room.canInspectWired(this.client.getHabbo())) return;

        HabboItem item = room.getHabboItem(furniId);
        if (!(item instanceof InteractionWiredContract contract)) return;

        WiredNativeContract contents = contract.toNative(contract.getId(), ContractFurniCatalog.live());
        if (contents == null) return;

        this.client.sendResponse(new WiredContractContentsComposer(contents));
    }
}

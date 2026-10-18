package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.ContractFurniCatalog;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract;
import com.eu.habbo.habbohotel.items.interactions.wired.contract.WiredNativeContract;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredContractUpdateResultComposer;

/**
 * The official contract editor's save (AIR {@code AbstractContract.addContentsToComposer}). The whole packet is read
 * before anything else, and a malformed one is dropped without an answer; a well-formed one the contract refuses
 * gets {@code invalid_rules}. Saving only changes the contract's configuration: nothing is paid or handed out.
 */
public class UpdateWiredContractEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public String getRatelimitGroup() {
        return "wired.save";
    }

    @Override
    public void handle() throws Exception {
        WiredNativeContract contract;
        try {
            contract = WiredNativeContract.read(this.packet.getBuffer());
        } catch (IllegalArgumentException malformed) {
            return;
        }

        Room room = this.currentRoom();
        if (room == null || !room.canModifyWired(this.client.getHabbo())) return;

        int furniId = BuildersClubItemIds.fromWire(room, contract.contractId());
        HabboItem item = room.getHabboItem(furniId);
        if (!(item instanceof InteractionWiredContract target) || !target.supportsNativeEditor()) return;

        boolean saved = target.applyNative(contract, ContractFurniCatalog.live());
        if (saved) {
            target.markEditorSaved();
            target.needsUpdate(true);
            if (WiredPlatform.threading() != null) WiredPlatform.threading().run(target);
            WiredManager.invalidateRoom(room);
        }

        this.client.sendResponse(new WiredContractUpdateResultComposer(target.getId(), saved));
    }
}

package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.WiredFurniOpacityComposer;

public class WiredFeatureCapabilitiesEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null || this.packet.bytesAvailable() < 8) {
            return;
        }

        int protocolVersion = this.packet.readInt();
        int capabilities = this.packet.readInt();
        this.client.setWiredFeatureCapabilities(protocolVersion, capabilities);

        if (this.client.supportsWiredFeature(
                GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_OPACITY)) {
            int userId = this.client.getHabbo().getHabboInfo().getId();
            this.client.sendResponse(new WiredFurniOpacityComposer(
                    room.getId(), room.getWiredRuntime().opacitySnapshot(userId), 0, 0));
        }
    }

    @Override
    public int getRatelimit() {
        return 500;
    }
}

package polaris.messages.incoming.wired;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.WiredFurniOpacityComposer;

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
            int userId = this.client.getUser().getUserInfo().getId();
            this.client.sendResponse(new WiredFurniOpacityComposer(
                    room.getId(), room.getWiredRuntime().opacitySnapshot(userId), 0, 0));
        }
    }

    @Override
    public int getRatelimit() {
        return 500;
    }
}

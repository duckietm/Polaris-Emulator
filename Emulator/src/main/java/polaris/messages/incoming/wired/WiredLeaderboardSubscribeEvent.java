package polaris.messages.incoming.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.wired.leaderboard.WiredLeaderboardSupport;
import polaris.messages.incoming.MessageHandler;

/**
 * Official variable leaderboard subscription (AIR 2833): {@code (0, 0, "0", [])} is the hello a client
 * sends on entering a room, answered with the room's panels; anything else names the one or two
 * chunks of a panel the player now looks at, replacing what they looked at before.
 *
 * <p>No rate limit: a client answers a snapshot with one request per panel at once. The service skips
 * a request that repeats the player's current subscription.
 */
public class WiredLeaderboardSubscribeEvent extends MessageHandler {
    static final int MAX_CHUNKS = 2;
    static final int MAX_VARIABLE_ID_LENGTH = 20;

    @Override
    public void handle() throws Exception {
        int overlayId = this.packet.readInt();
        int revision = this.packet.readInt();
        String variableId = this.packet.readString();
        int count = this.packet.readInt();
        if (count < 0 || count > MAX_CHUNKS) {
            return;
        }
        int[] chunks = new int[count];
        for (int i = 0; i < count; i++) {
            chunks[i] = this.packet.readInt();
        }

        Room room = currentRoom();
        if (room == null || variableId == null || variableId.length() > MAX_VARIABLE_ID_LENGTH) {
            return;
        }
        if (count == 0) {
            if (overlayId == 0 && revision == 0 && variableId.equals("0")) {
                WiredLeaderboardSupport.hello(room, this.client.getUser());
            }
            return;
        }
        WiredLeaderboardSupport.subscribe(room, this.client.getUser(), overlayId, revision, variableId, chunks);
    }
}

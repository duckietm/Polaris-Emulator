package polaris.messages.incoming;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.messages.ClientMessage;

public abstract class MessageHandler {
    public GameClient client;
    public ClientMessage packet;
    public boolean isCancelled = false;

    public abstract void handle() throws Exception;

    public int getRatelimit() {
        return 0;
    }

    public String getRatelimitGroup() {
        return "";
    }

    protected final Room currentRoom() {
        if (this.client == null || this.client.getUser() == null) return null;
        if (this.client.getUser().getUserInfo() == null) return null;
        return this.client.getUser().getUserInfo().getCurrentRoom();
    }
}

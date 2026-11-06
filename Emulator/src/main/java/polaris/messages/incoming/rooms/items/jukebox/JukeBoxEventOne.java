package polaris.messages.incoming.rooms.items.jukebox;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.TraxManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.jukebox.JukeBoxMySongsComposer;
import polaris.messages.outgoing.rooms.items.jukebox.JukeBoxPlayListComposer;

public class JukeBoxEventOne extends MessageHandler {
    @Override
    public void handle() throws Exception {
        // Sent outside a room, this threw a NullPointerException logged with its stack trace.
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        TraxManager traxManager = room.getTraxManager();
        this.client.sendResponse(new JukeBoxPlayListComposer(traxManager.getSongs(), traxManager.totalLength()));
        this.client.sendResponse(new JukeBoxMySongsComposer(traxManager.myList(this.client.getUser())));
        traxManager.updateCurrentPlayingSong(this.client.getUser());
    }
}

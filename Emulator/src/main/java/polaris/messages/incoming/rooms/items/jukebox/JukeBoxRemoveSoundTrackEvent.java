package polaris.messages.incoming.rooms.items.jukebox;

import polaris.hotel.items.interactions.InteractionMusicDisc;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class JukeBoxRemoveSoundTrackEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int index = this.packet.readInt();

        // Adding a disc needs rights; removing one did not, so any visitor emptied the playlist and
        // sent every disc back to its owner.
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || !room.hasRights(this.client.getUser())) return;

        if (index < 0 || index >= room.getTraxManager().getSongs().size()) return;

        InteractionMusicDisc musicDisc = room.getTraxManager().getSongs().get(index);

        if (musicDisc != null) {
            room.getTraxManager().removeSong(musicDisc.getId());
        }
    }
}

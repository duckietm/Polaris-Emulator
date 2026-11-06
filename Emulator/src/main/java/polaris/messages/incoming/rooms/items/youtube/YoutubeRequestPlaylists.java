package polaris.messages.incoming.rooms.items.youtube;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.YoutubeManager;
import polaris.hotel.items.interactions.InteractionYoutubeTV;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;
import polaris.messages.outgoing.rooms.items.youtube.YoutubeDisplayListComposer;

public class YoutubeRequestPlaylists extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(YoutubeRequestPlaylists.class);

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            UserItem item = this.client.getUser().getUserInfo().getCurrentRoom().getUserItem(itemId);

            if (item instanceof InteractionYoutubeTV) {
                InteractionYoutubeTV tv = (InteractionYoutubeTV) item;

                int furniItemId = item.getId();
                ArrayList<YoutubeManager.YoutubePlaylist> playlists = Emulator.getGameEnvironment()
                        .getItemManager()
                        .getYoutubeManager()
                        .getPlaylistsForItemId(furniItemId);

                if (playlists == null) {
                    LOGGER.error(
                            "No YouTube playlists loaded for item #{}. Check: 1) youtube_playlists table has entries with item_id={}, 2) youtube.apikey is set in emulator_settings, 3) playlist IDs are valid YouTube playlist IDs (start with PL)",
                            furniItemId,
                            furniItemId);
                    this.client.sendResponse(new ConnectionErrorComposer(1000));
                    return;
                }

                this.client.sendResponse(new YoutubeDisplayListComposer(itemId, playlists, tv.currentPlaylist));
            }
        }
    }
}

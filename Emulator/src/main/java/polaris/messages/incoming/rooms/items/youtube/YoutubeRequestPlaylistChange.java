package polaris.messages.incoming.rooms.items.youtube;

import java.util.ArrayList;
import java.util.Optional;
import polaris.Emulator;
import polaris.hotel.items.YoutubeManager;
import polaris.hotel.items.interactions.InteractionYoutubeTV;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.rooms.items.youtube.YoutubeVideoComposer;
import polaris.threading.runnables.YoutubeAdvanceVideo;

public class YoutubeRequestPlaylistChange extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String playlistId = RoomItemInputGuard.trimToMax(
                this.packet.readString(), RoomItemInputGuard.MAX_YOUTUBE_PLAYLIST_ID_LENGTH);

        if (!RoomItemInputGuard.isPositiveId(itemId) || playlistId.isEmpty()) return;

        User user = this.client.getUser();

        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null) return;
        if (!room.isOwner(user) && !user.hasPermission(Permission.ACC_ANYROOMOWNER)) return;

        UserItem item = room.getUserItem(itemId);

        if (item == null || !(item instanceof InteractionYoutubeTV)) return;

        ArrayList<YoutubeManager.YoutubePlaylist> playlists = Emulator.getGameEnvironment()
                .getItemManager()
                .getYoutubeManager()
                .getPlaylistsForItemId(item.getId());

        if (playlists == null) return;

        Optional<YoutubeManager.YoutubePlaylist> playlist =
                playlists.stream().filter(p -> p.getId().equals(playlistId)).findAny();

        if (playlist.isPresent()) {
            if (playlist.get().getVideos().isEmpty()) return;

            YoutubeManager.YoutubeVideo video = playlist.get().getVideos().get(0);
            if (video == null) return;

            ((InteractionYoutubeTV) item).currentVideo = video;
            ((InteractionYoutubeTV) item).currentPlaylist = playlist.get();

            ((InteractionYoutubeTV) item).cancelAdvancement();

            room.updateItem(item);
            room.sendComposer(new YoutubeVideoComposer(itemId, video, true, 0).compose());
            ((InteractionYoutubeTV) item).autoAdvance = Emulator.getThreading()
                    .run(new YoutubeAdvanceVideo((InteractionYoutubeTV) item), video.getDuration() * 1000L);

            item.needsUpdate(true);
        }
    }
}

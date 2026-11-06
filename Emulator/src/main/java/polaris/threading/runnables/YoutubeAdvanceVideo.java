package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionYoutubeTV;
import polaris.hotel.rooms.Room;
import polaris.messages.outgoing.rooms.items.youtube.YoutubeVideoComposer;

public class YoutubeAdvanceVideo implements Runnable {
    private final InteractionYoutubeTV tv;

    public YoutubeAdvanceVideo(InteractionYoutubeTV tv) {
        this.tv = tv;
    }

    @Override
    public void run() {
        if (this.tv.autoAdvance == null) return;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.tv.getRoomId());

        // An unloaded room keeps its instance, and a reloaded one has a new object for the same furni:
        // the chain stops instead of changing the video under the TV the visitors see now.
        if (room == null
                || !room.isLoaded()
                || room.getUserItem(this.tv.getId()) != this.tv
                || this.tv.currentPlaylist == null
                || this.tv.currentPlaylist.getVideos().isEmpty()) {
            this.tv.autoAdvance = null;
            return;
        }

        int nextIndex = tv.currentPlaylist.getVideos().indexOf(tv.currentVideo) + 1;
        if (nextIndex >= tv.currentPlaylist.getVideos().size()) nextIndex = 0;
        tv.currentVideo = tv.currentPlaylist.getVideos().get(nextIndex);
        tv.startedWatchingAt = Emulator.getIntUnixTimestamp();
        tv.offset = 0;
        room.updateItem(this.tv);
        room.sendComposer(new YoutubeVideoComposer(tv.getId(), tv.currentVideo, true, 0).compose());

        tv.autoAdvance =
                Emulator.getThreading().run(new YoutubeAdvanceVideo(this.tv), tv.currentVideo.getDuration() * 1000L);
    }
}

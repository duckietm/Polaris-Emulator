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

        if (room == null) return;

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

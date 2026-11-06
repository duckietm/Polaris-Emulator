package polaris.messages.incoming.rooms.items.youtube;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionYoutubeTV;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.rooms.items.youtube.YoutubeStateChangeComposer;
import polaris.messages.outgoing.rooms.items.youtube.YoutubeVideoComposer;
import polaris.threading.runnables.YoutubeAdvanceVideo;

public class YoutubeRequestStateChange extends MessageHandler {
    public enum YoutubeState {
        PREVIOUS(0),
        NEXT(1),
        PAUSE(2),
        RESUME(3);

        private int state;

        YoutubeState(int state) {
            this.state = state;
        }

        public int getState() {
            return state;
        }

        public static YoutubeState getByState(int state) {
            switch (state) {
                case 0:
                    return PREVIOUS;
                case 1:
                    return NEXT;
                case 2:
                    return PAUSE;
                case 3:
                    return RESUME;
                default:
                    return null;
            }
        }
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        YoutubeState state = YoutubeState.getByState(this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        if (state == null) return;

        User user = this.client.getUser();

        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null) return;
        if (!room.isOwner(user) && !user.hasPermission(Permission.ACC_ANYROOMOWNER)) return;

        UserItem item = room.getUserItem(itemId);

        if (!(item instanceof InteractionYoutubeTV)) return;

        InteractionYoutubeTV tv = (InteractionYoutubeTV) item;

        if (tv.currentPlaylist == null || tv.currentPlaylist.getVideos().isEmpty()) return;

        switch (state) {
            case PAUSE:
                tv.playing = false;
                tv.offset += Emulator.getIntUnixTimestamp() - tv.startedWatchingAt;
                if (tv.autoAdvance != null) tv.autoAdvance.cancel(true);
                room.sendComposer(new YoutubeStateChangeComposer(tv.getId(), 2).compose());
                break;
            case RESUME:
                // A second resume used to start a second advancing chain next to the first.
                tv.cancelAdvancement();
                tv.playing = true;
                tv.startedWatchingAt = Emulator.getIntUnixTimestamp();
                tv.autoAdvance = Emulator.getThreading()
                        .run(new YoutubeAdvanceVideo(tv), (tv.currentVideo.getDuration() - tv.offset) * 1000L);
                room.sendComposer(new YoutubeStateChangeComposer(tv.getId(), 1).compose());
                break;
            case PREVIOUS:
                int previousIndex = tv.currentPlaylist.getVideos().indexOf(tv.currentVideo) - 1;
                if (previousIndex < 0)
                    previousIndex = tv.currentPlaylist.getVideos().size() - 1;
                tv.currentVideo = tv.currentPlaylist.getVideos().get(previousIndex);
                break;
            case NEXT:
                int nextIndex = tv.currentPlaylist.getVideos().indexOf(tv.currentVideo) + 1;
                if (nextIndex >= tv.currentPlaylist.getVideos().size()) nextIndex = 0;
                tv.currentVideo = tv.currentPlaylist.getVideos().get(nextIndex);
                break;
        }

        if (state == YoutubeState.PREVIOUS || state == YoutubeState.NEXT) {
            room.sendComposer(new YoutubeVideoComposer(tv.getId(), tv.currentVideo, true, 0).compose());

            tv.cancelAdvancement();
            tv.autoAdvance =
                    Emulator.getThreading().run(new YoutubeAdvanceVideo(tv), tv.currentVideo.getDuration() * 1000L);
            tv.startedWatchingAt = Emulator.getIntUnixTimestamp();
            tv.offset = 0;
            tv.playing = true;
            room.updateItem(tv);
        }
    }
}

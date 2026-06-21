package com.eu.habbo.messages.incoming.rooms.items.youtube;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionYoutubeTV;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.incoming.rooms.items.RoomItemInputGuard;
import com.eu.habbo.messages.outgoing.rooms.items.youtube.YoutubeStateChangeComposer;
import com.eu.habbo.messages.outgoing.rooms.items.youtube.YoutubeVideoComposer;
import com.eu.habbo.threading.runnables.YoutubeAdvanceVideo;

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
        int itemId = this.packet.readInt();
        YoutubeState state = YoutubeState.getByState(this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        if (state == null) return;

        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;


        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room == null) return;
        if (!room.isOwner(habbo) && !habbo.hasPermission(Permission.ACC_ANYROOMOWNER)) return;


        HabboItem item = room.getHabboItem(itemId);

        if (!(item instanceof InteractionYoutubeTV)) return;

        InteractionYoutubeTV tv = (InteractionYoutubeTV) item;

        if(tv.currentPlaylist == null || tv.currentPlaylist.getVideos().isEmpty()) return;

        switch (state) {
            case PAUSE:
                tv.playing = false;
                tv.offset += Emulator.getIntUnixTimestamp() - tv.startedWatchingAt;
                if (tv.autoAdvance != null) tv.autoAdvance.cancel(true);
                room.sendComposer(new YoutubeStateChangeComposer(tv.getId(), 2).compose());
                break;
            case RESUME:
                tv.playing = true;
                tv.startedWatchingAt = Emulator.getIntUnixTimestamp();
                tv.autoAdvance = Emulator.getThreading().run(new YoutubeAdvanceVideo(tv), (tv.currentVideo.getDuration() - tv.offset) * 1000L);
                room.sendComposer(new YoutubeStateChangeComposer(tv.getId(), 1).compose());
                break;
            case PREVIOUS:
                int previousIndex = tv.currentPlaylist.getVideos().indexOf(tv.currentVideo) - 1;
                if (previousIndex < 0) previousIndex = tv.currentPlaylist.getVideos().size() - 1;
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
            tv.autoAdvance = Emulator.getThreading().run(new YoutubeAdvanceVideo(tv), tv.currentVideo.getDuration() * 1000L);
            tv.startedWatchingAt = Emulator.getIntUnixTimestamp();
            tv.offset = 0;
            tv.playing = true;
            room.updateItem(tv);
        }
    }
}

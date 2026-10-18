package com.eu.habbo.messages.outgoing.rooms.items.youtube;

import com.eu.habbo.habbohotel.items.YoutubeManager;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.ArrayList;

public class YoutubeDisplayListComposer extends MessageComposer {
    private final int itemId;
    private final ArrayList<YoutubeManager.YoutubePlaylist> playlists;
    private final YoutubeManager.YoutubePlaylist currentPlaylist;

    public YoutubeDisplayListComposer(
            int itemId,
            ArrayList<YoutubeManager.YoutubePlaylist> playlists,
            YoutubeManager.YoutubePlaylist currentPlaylist) {
        this.itemId = itemId;
        this.playlists = playlists;
        this.currentPlaylist = currentPlaylist;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.YoutubeDisplayListComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.itemId));
        this.response.appendInt(this.playlists.size());

        for (YoutubeManager.YoutubePlaylist item : this.playlists) {
            this.response.appendString(item.getId()); // playlist ID
            this.response.appendString(item.getName()); // playlist title
            this.response.appendString(item.getDescription()); // playlist description
        }

        this.response.appendString(
                this.currentPlaylist == null ? "" : this.currentPlaylist.getId()); // current playlist ID
        return this.response;
    }

    public int getItemId() {
        return itemId;
    }

    public ArrayList<YoutubeManager.YoutubePlaylist> getPlaylists() {
        return playlists;
    }

    public YoutubeManager.YoutubePlaylist getCurrentPlaylist() {
        return currentPlaylist;
    }
}

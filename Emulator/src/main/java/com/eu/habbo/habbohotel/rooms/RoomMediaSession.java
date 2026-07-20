package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer;
import com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomWatchersComposer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class RoomMediaSession {

    private final Room room;
    private final List<String> playlist = new CopyOnWriteArrayList<>();
    private final Set<Integer> watchers = ConcurrentHashMap.newKeySet();
    private boolean youtubeEnabled;
    private boolean soundboardEnabled;
    private String currentVideo = "";
    private String senderName = "";

    RoomMediaSession(Room room) {
        this.room = room;
    }

    boolean youtubeEnabled() {
        return this.youtubeEnabled;
    }

    void youtubeEnabled(boolean enabled) {
        this.youtubeEnabled = enabled;
    }

    boolean soundboardEnabled() {
        return this.soundboardEnabled;
    }

    void soundboardEnabled(boolean enabled) {
        this.soundboardEnabled = enabled;
    }

    String currentVideo() {
        return this.currentVideo;
    }

    String senderName() {
        return this.senderName;
    }

    List<String> playlist() {
        return this.playlist;
    }

    Set<Integer> watchers() {
        return this.watchers;
    }

    void setVideo(String videoId, String senderName, List<String> playlist) {
        this.currentVideo = videoId;
        this.senderName = senderName;
        this.playlist.clear();
        if (playlist != null) {
            this.playlist.addAll(playlist);
        }
    }

    void clearVideo() {
        this.currentVideo = "";
        this.senderName = "";
        this.playlist.clear();
    }

    void removeWatcher(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        if (!this.currentVideo.isEmpty() && habbo.getHabboInfo().getUsername().equals(this.senderName)) {
            this.clearVideo();
            this.room.sendComposer(new YouTubeRoomBroadcastComposer("", "", Collections.emptyList()).compose());
        }

        if (this.watchers.remove(userId)) {
            this.room.sendComposer(new YouTubeRoomWatchersComposer(this.watchers).compose());
        }
    }
}

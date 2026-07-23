package com.eu.habbo.messages.outgoing.soundboard;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

// Broadcast to everyone in the room when a pad is pressed — they all play the clip.
public class SoundboardPlayComposer extends MessageComposer {
    private final int soundId;
    private final String url;
    private final String soundName;
    private final int actorUserId;
    private final int actorRoomIndex;
    private final String username;

    public SoundboardPlayComposer(int soundId, String url, String username) {
        this(soundId, url, "", 0, 0, username);
    }

    public SoundboardPlayComposer(
            int soundId,
            String url,
            String soundName,
            int actorUserId,
            int actorRoomIndex,
            String username) {
        this.soundId = soundId;
        this.url = url != null ? url : "";
        this.soundName = soundName != null ? soundName : "";
        this.actorUserId = actorUserId;
        this.actorRoomIndex = actorRoomIndex;
        this.username = username != null ? username : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SoundboardPlayComposer);
        this.response.appendInt(this.soundId);
        this.response.appendString(this.url);
        this.response.appendString(this.soundName);
        this.response.appendInt(this.actorUserId);
        this.response.appendInt(this.actorRoomIndex);
        this.response.appendString(this.username);
        return this.response;
    }
}

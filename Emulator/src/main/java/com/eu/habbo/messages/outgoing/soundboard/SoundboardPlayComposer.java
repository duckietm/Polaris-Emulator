package com.eu.habbo.messages.outgoing.soundboard;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

// Broadcast to everyone in the room when a pad is pressed — they all play the clip.
public class SoundboardPlayComposer extends MessageComposer {
    private final int soundId;
    private final String url;
    private final String username;

    public SoundboardPlayComposer(int soundId, String url, String username) {
        this.soundId = soundId;
        this.url = url != null ? url : "";
        this.username = username != null ? username : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SoundboardPlayComposer);
        this.response.appendInt(this.soundId);
        this.response.appendString(this.url);
        this.response.appendString(this.username);
        return this.response;
    }
}

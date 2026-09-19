package com.eu.habbo.messages.outgoing.quests;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Map;

/**
 * The localization texts of the active reward tracks, as full keys ("reward_track.&lt;track&gt;.name"),
 * sent right before the tracks so the client registers them before drawing.
 */
public class RewardTrackTextsComposer extends MessageComposer {
    private final Map<String, String> texts;

    public RewardTrackTextsComposer(Map<String, String> texts) {
        this.texts = texts;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RewardTrackTextsComposer);
        this.response.appendInt(this.texts.size());
        for (Map.Entry<String, String> entry : this.texts.entrySet()) {
            this.response.appendString(entry.getKey());
            this.response.appendString(entry.getValue());
        }
        return this.response;
    }
}

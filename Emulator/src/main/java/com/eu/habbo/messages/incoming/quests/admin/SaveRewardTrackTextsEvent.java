package com.eu.habbo.messages.incoming.quests.admin;

import com.eu.habbo.habbohotel.quests.RewardTrackAdmin;
import java.util.LinkedHashMap;
import java.util.Map;

/** Replaces the texts of a track: track id, then count and key/value pairs; an empty value drops the key. */
public class SaveRewardTrackTextsEvent extends RewardTrackAdminEvent {
    @Override
    public void handle() throws Exception {
        if (!authorize()) {
            return;
        }
        String trackId = this.packet.readString().trim();
        int count = Math.max(0, Math.min(this.packet.readInt(), RewardTrackAdmin.MAX_TEXTS_PER_TRACK));
        Map<String, String> texts = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            texts.put(this.packet.readString(), this.packet.readString());
        }
        this.apply(
                RewardTrackAdmin.ENTITY_TEXTS,
                trackId,
                "",
                RewardTrackAdmin.validateTexts(trackId, texts),
                () -> RewardTrackAdmin.saveTexts(trackId, texts));
    }
}

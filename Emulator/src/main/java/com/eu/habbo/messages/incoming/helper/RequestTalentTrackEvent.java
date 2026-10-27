package com.eu.habbo.messages.incoming.helper;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.achievements.talenttrack.TalentTrackComposer;

public class RequestTalentTrackEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (Emulator.getConfig().getBoolean("hotel.talenttrack.enabled")) {
            TalentTrackType type =
                    TalentTrackType.valueOf(this.packet.readString().toUpperCase());
            GameEnvironment environment = WiredPlatform.gameEnvironment();
            AchievementManager manager = environment == null ? null : environment.getAchievementManager();

            // The level is otherwise only worked out when an achievement progresses: catch up before showing it.
            if (manager != null && manager.getTalenTrackLevels(type) != null) {
                manager.handleTalentTrackAchievement(this.client.getHabbo(), type, null);
            }

            this.client.sendResponse(new TalentTrackComposer(this.client.getHabbo(), type));
        }
    }
}

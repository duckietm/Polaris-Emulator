package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.UserCitizinShipComposer;

/**
 * Official {@code GetTalentTrackLevelMessageComposer} (2127): the client asks for the level pair
 * of one talent track by name and gets {@code TalentTrackLevel} (1203) back.
 */
public class RequestUserCitizinShipEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        TalentTrackType type = talentTrackType(this.packet.readString());

        if (type == null) {
            return;
        }

        this.client.sendResponse(UserCitizinShipComposer.forHabbo(this.client.getHabbo(), type));
    }

    static TalentTrackType talentTrackType(String name) {
        if (name == null) {
            return null;
        }

        for (TalentTrackType type : TalentTrackType.values()) {
            if (type.name().equalsIgnoreCase(name.trim())) {
                return type;
            }
        }

        return null;
    }
}

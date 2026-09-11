package com.eu.habbo.messages.outgoing.achievements.talenttrack;

import com.eu.habbo.habbohotel.achievements.TalentTrackLevel;
import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class TalentLevelUpdateComposer extends MessageComposer {
    private final TalentTrackType talentTrackType;
    private final TalentTrackLevel talentTrackLevel;

    public TalentLevelUpdateComposer(TalentTrackType talentTrackType, TalentTrackLevel talentTrackLevel) {
        this.talentTrackType = talentTrackType;
        this.talentTrackLevel = talentTrackLevel;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TalentLevelUpdateComposer);
        this.response.appendString(this.talentTrackType.name().toLowerCase(java.util.Locale.ROOT));
        this.response.appendInt(this.talentTrackLevel.level);

        if (this.talentTrackLevel.perks != null) {
            this.response.appendInt(this.talentTrackLevel.perks.length);
            for (String s : this.talentTrackLevel.perks) {
                this.response.appendString(s);
            }
        } else {
            this.response.appendInt(0);
        }

        this.response.appendInt(this.talentTrackLevel.items.size() + (this.talentTrackLevel.hcDays > 0 ? 1 : 0));
        for (Item item : this.talentTrackLevel.items) {
            this.response.appendString(item.getName());
            this.response.appendInt(0);
        }
        if (this.talentTrackLevel.hcDays > 0) {
            this.response.appendString("HABBO_CLUB");
            this.response.appendInt(this.talentTrackLevel.hcDays);
        }
        return this.response;
    }

    public TalentTrackType getTalentTrackType() {
        return talentTrackType;
    }

    public TalentTrackLevel getTalentTrackLevel() {
        return talentTrackLevel;
    }
}

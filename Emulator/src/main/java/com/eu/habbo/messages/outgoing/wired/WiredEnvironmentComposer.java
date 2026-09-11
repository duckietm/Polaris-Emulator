package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveAchievement;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class WiredEnvironmentComposer extends MessageComposer {
    private final Room room;

    public WiredEnvironmentComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        var items = this.room.getFloorItems();
        var achievements = items.stream()
                .filter(WiredEffectGiveAchievement.class::isInstance)
                .map(WiredEffectGiveAchievement.class::cast)
                .map(WiredEffectGiveAchievement::getAchievementCode)
                .map(code -> code.startsWith("WF_") ? code.substring(3) : code)
                .filter(code -> !code.isBlank())
                .distinct()
                .sorted()
                .toList();
        this.response.init(Outgoing.WiredEnvironmentComposer);
        this.response.appendBoolean(items.stream().anyMatch(WiredTriggerHabboClicksUser.class::isInstance));
        this.response.appendInt(achievements.size());
        for (String achievement : achievements) this.response.appendString(achievement);
        return this.response;
    }
}

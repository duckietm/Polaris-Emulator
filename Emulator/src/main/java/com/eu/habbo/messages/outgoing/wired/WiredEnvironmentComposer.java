package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveAchievement;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Official AIR 13 {@code WiredEnvironment} (347), sent on room entry.
 *
 * <p>{@code hasClickUserWired} tells the client to route avatar clicks through the server instead
 * of opening the menu itself; {@code enabledAchievements} lists the achievements the room's wired
 * can hand out, which is what gates the room-tools achievements button.
 */
public class WiredEnvironmentComposer extends MessageComposer {
    private final Room room;

    public WiredEnvironmentComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredEnvironmentComposer);

        this.response.appendBoolean(hasClickUserWired(this.room));

        List<String> achievements = enabledAchievements(this.room);
        this.response.appendInt(achievements.size());

        for (String achievement : achievements) {
            this.response.appendString(achievement);
        }

        return this.response;
    }

    private static boolean hasClickUserWired(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return false;
        }

        for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers()) {
            if (trigger instanceof WiredTriggerHabboClicksUser) {
                return true;
            }
        }

        return false;
    }

    private static List<String> enabledAchievements(Room room) {
        Set<String> achievements = new LinkedHashSet<>();

        if (room != null && room.getRoomSpecialTypes() != null) {
            for (InteractionWiredEffect effect : room.getRoomSpecialTypes().getEffects()) {
                if (!(effect instanceof WiredEffectGiveAchievement)) {
                    continue;
                }

                String achievement = ((WiredEffectGiveAchievement) effect).getAchievement();

                if (achievement != null && !achievement.isBlank()) {
                    achievements.add(achievement);
                }
            }
        }

        return new ArrayList<>(achievements);
    }
}

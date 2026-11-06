package polaris.messages.outgoing.wired;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import polaris.WiredPlatform;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.effects.WiredEffectGiveAchievement;
import polaris.hotel.items.interactions.wired.extra.WiredExtraAchievementEnabler;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserClicksUser;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredHotelProgressPolicy;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official AIR 13 {@code WiredEnvironment} (347), sent on room entry.
 *
 * <p>{@code hasClickUserWired} tells the client to route avatar clicks through the server instead
 * of opening the menu itself; {@code enabledAchievements} lists the achievements the room's wired
 * can hand out, which is what gates the room-tools achievements button: those of the give-achievement
 * boxes, and those the room's achievement enablers name that the hotel lets wired progress.
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
            if (trigger instanceof WiredTriggerUserClicksUser) {
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

            WiredHotelProgressPolicy policy = WiredHotelProgressPolicy.achievements(WiredPlatform.configuration());
            if (policy.enabled()) {
                for (String achievement : WiredExtraAchievementEnabler.declaredAchievements(room)) {
                    if (policy.allows(achievement)) {
                        achievements.add(achievement);
                    }
                }
            }
        }

        return new ArrayList<>(achievements);
    }
}

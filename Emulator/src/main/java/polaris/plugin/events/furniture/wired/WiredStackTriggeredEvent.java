package polaris.plugin.events.furniture.wired;

import java.util.Set;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.plugin.events.roomunit.RoomUnitEvent;

public class WiredStackTriggeredEvent extends RoomUnitEvent {

    public final InteractionWiredTrigger trigger;

    public final Set<InteractionWiredEffect> effects;

    public final Set<InteractionWiredCondition> conditions;

    public WiredStackTriggeredEvent(
            Room room,
            RoomUnit roomUnit,
            InteractionWiredTrigger trigger,
            Set<InteractionWiredEffect> effects,
            Set<InteractionWiredCondition> conditions) {
        super(room, roomUnit);

        this.trigger = trigger;
        this.effects = effects;
        this.conditions = conditions;
    }
}

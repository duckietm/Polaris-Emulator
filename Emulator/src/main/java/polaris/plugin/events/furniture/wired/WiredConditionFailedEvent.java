package polaris.plugin.events.furniture.wired;

import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.plugin.events.roomunit.RoomUnitEvent;

public class WiredConditionFailedEvent extends RoomUnitEvent {

    public final InteractionWiredTrigger trigger;

    public final InteractionWiredCondition condition;

    public WiredConditionFailedEvent(
            Room room, RoomUnit roomUnit, InteractionWiredTrigger trigger, InteractionWiredCondition condition) {
        super(room, roomUnit);
        this.trigger = trigger;
        this.condition = condition;
    }
}

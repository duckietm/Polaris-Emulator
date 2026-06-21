package com.eu.habbo.plugin.events.furniture.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.plugin.events.roomunit.RoomUnitEvent;

import java.util.Set;

public class WiredStackExecutedEvent extends RoomUnitEvent {

    public final InteractionWiredTrigger trigger;


    public final Set<InteractionWiredEffect> effects;


    public final Set<InteractionWiredCondition> conditions;


    public WiredStackExecutedEvent(Room room, RoomUnit roomUnit, InteractionWiredTrigger trigger, Set<InteractionWiredEffect> effects, Set<InteractionWiredCondition> conditions) {
        super(room, roomUnit);

        this.trigger = trigger;
        this.effects = effects;
        this.conditions = conditions;
    }
}

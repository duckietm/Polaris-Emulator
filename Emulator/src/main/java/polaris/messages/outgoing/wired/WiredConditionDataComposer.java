package polaris.messages.outgoing.wired;

import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.protocol.WiredDefinitionWire;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class WiredConditionDataComposer extends MessageComposer {
    private final InteractionWiredCondition condition;
    private final Room room;

    public WiredConditionDataComposer(InteractionWiredCondition condition, Room room) {
        this.condition = condition;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredConditionDataComposer);
        // Each box still writes the legacy layout; it goes out in the official one.
        ServerMessage legacy = new ServerMessage(Outgoing.WiredConditionDataComposer);
        this.condition.serializeWiredData(legacy, this.room);
        WiredDefinitionWire.writeOfficial(legacy, WiredDefinitionWire.Holder.CONDITION, this.response, this.condition);
        this.condition.needsUpdate(true);
        return this.response;
    }

    public InteractionWiredCondition getCondition() {
        return condition;
    }

    public Room getRoom() {
        return room;
    }
}

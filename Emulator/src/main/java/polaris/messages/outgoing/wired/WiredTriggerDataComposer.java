package polaris.messages.outgoing.wired;

import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.protocol.WiredDefinitionWire;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class WiredTriggerDataComposer extends MessageComposer {
    private final InteractionWiredTrigger trigger;
    private final Room room;

    public WiredTriggerDataComposer(InteractionWiredTrigger trigger, Room room) {
        this.trigger = trigger;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTriggerDataComposer);
        // Each box still writes the legacy layout; it goes out in the official one.
        ServerMessage legacy = new ServerMessage(Outgoing.WiredTriggerDataComposer);
        this.trigger.serializeWiredData(legacy, this.room);
        WiredDefinitionWire.writeOfficial(legacy, WiredDefinitionWire.Holder.TRIGGER, this.response, this.trigger);
        this.trigger.needsUpdate(true);
        return this.response;
    }

    public InteractionWiredTrigger getTrigger() {
        return trigger;
    }

    public Room getRoom() {
        return room;
    }
}

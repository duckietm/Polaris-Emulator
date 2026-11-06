package polaris.messages.outgoing.wired;

import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.protocol.WiredDefinitionWire;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class WiredEffectDataComposer extends MessageComposer {
    private final InteractionWiredEffect effect;
    private final Room room;

    public WiredEffectDataComposer(InteractionWiredEffect effect, Room room) {
        this.effect = effect;
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        // Selectors have their own official definition packet.
        boolean selector = this.effect.isSelector();
        this.response.init(selector ? Outgoing.WiredSelectorDataComposer : Outgoing.WiredEffectDataComposer);
        // Each box still writes the legacy layout; it goes out in the official one.
        ServerMessage legacy = new ServerMessage(Outgoing.WiredEffectDataComposer);
        this.effect.serializeWiredData(legacy, this.room);
        WiredDefinitionWire.writeOfficial(
                legacy,
                selector ? WiredDefinitionWire.Holder.SELECTOR : WiredDefinitionWire.Holder.ACTION,
                this.response,
                this.effect);
        this.effect.needsUpdate(true);
        return this.response;
    }

    public InteractionWiredEffect getEffect() {
        return effect;
    }

    public Room getRoom() {
        return room;
    }
}

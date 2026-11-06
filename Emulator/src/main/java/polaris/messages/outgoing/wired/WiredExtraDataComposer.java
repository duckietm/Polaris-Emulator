package polaris.messages.outgoing.wired;

import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.wired.protocol.WiredDefinitionWire;
import polaris.hotel.wired.protocol.WiredHolders;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.SecretBearingComposer;

public class WiredExtraDataComposer extends MessageComposer implements SecretBearingComposer {
    private final InteractionWiredExtra extra;
    private final Room room;
    private final User viewer;

    public WiredExtraDataComposer(InteractionWiredExtra extra, Room room) {
        this(extra, room, null);
    }

    public WiredExtraDataComposer(InteractionWiredExtra extra, Room room, User viewer) {
        this.extra = extra;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        // Variable definitions and addons have their own official definition packets.
        boolean variable = WiredHolders.isVariable(this.extra);
        this.response.init(variable ? Outgoing.WiredVariableDataComposer : Outgoing.WiredAddonDataComposer);
        // Each box still writes the legacy layout; it goes out in the official one.
        ServerMessage legacy = new ServerMessage(Outgoing.WiredEffectDataComposer);
        if (this.viewer != null) {
            this.extra.serializeWiredDataFor(legacy, this.room, this.viewer);
        } else {
            this.extra.serializeWiredData(legacy, this.room);
        }
        WiredDefinitionWire.writeOfficial(
                legacy,
                variable ? WiredDefinitionWire.Holder.VARIABLE : WiredDefinitionWire.Holder.ADDON,
                this.response,
                this.extra);
        this.extra.needsUpdate(true);
        return this.response;
    }

    @Override
    public boolean carriesSecret() {
        return this.extra instanceof WiredExtraVariableWebApi;
    }
}

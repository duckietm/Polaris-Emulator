package polaris.messages.outgoing.wired;

import polaris.hotel.rooms.variables.PermanentUserVariables;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official holder detail list (AIR {@code WiredUserPermanentVariablesList}): the holder, then each permanent variable
 * it holds with its value and its creation and last-update times, as a 64-bit number and as text. The holder is
 * always sent as entity type 1 (a Habbo), the type whose message carries no owner block.
 */
public class WiredUserPermanentVariablesListComposer extends MessageComposer {
    public static final int ENTITY_HABBO = 1;

    private final PermanentUserVariables.Listing listing;

    public WiredUserPermanentVariablesListComposer(PermanentUserVariables.Listing listing) {
        this.listing = listing;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredUserPermanentVariablesListComposer);
        this.response.appendInt(ENTITY_HABBO);
        this.response.appendInt(this.listing.holderKey());
        this.response.appendString(this.listing.name());
        this.response.appendString(this.listing.figure());
        this.response.appendInt(this.listing.entries().size());

        for (PermanentUserVariables.Entry entry : this.listing.entries()) {
            this.response.appendString(entry.variableId());
            this.response.appendInt(entry.value());
            WiredVariableHoldersPageComposer.appendLong(this.response, entry.createdAt());
            this.response.appendString(WiredVariableHoldersPageComposer.formatTimestamp(entry.createdAt()));
            WiredVariableHoldersPageComposer.appendLong(this.response, entry.updatedAt());
            this.response.appendString(WiredVariableHoldersPageComposer.formatTimestamp(entry.updatedAt()));
        }

        return this.response;
    }

    public PermanentUserVariables.Listing getListing() {
        return listing;
    }
}

package polaris.messages.outgoing.catalog.specialitems;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The official claim result (43): {@code (claimId, result)}. The cursed dragon adoption window waits for it after a
 * claim: 0 and 1 show the dragon as adopted, 2 and 3 switch the buttons off, anything else lets the user try again
 * ({@code SpecialItemClaimService.Outcome}).
 */
public class ClaimProductResultComposer extends MessageComposer {
    private final String claimId;
    private final int result;

    public ClaimProductResultComposer(String claimId, int result) {
        this.claimId = claimId;
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ClaimProductResultComposer);
        this.response.appendString(this.claimId);
        this.response.appendInt(this.result);
        return this.response;
    }
}

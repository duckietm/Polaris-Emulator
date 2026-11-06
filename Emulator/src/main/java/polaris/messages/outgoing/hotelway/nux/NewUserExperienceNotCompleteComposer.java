package polaris.messages.outgoing.hotelway.nux;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * This player has not been through the new user experience yet, so the offer that opens it is worth
 * showing. The packet carries nothing: it is the invitation itself.
 */
public class NewUserExperienceNotCompleteComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewUserExperienceNotCompleteComposer);
        return this.response;
    }
}

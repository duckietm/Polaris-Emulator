package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * CitizenshipVipOfferPromoEnabled (2764): the citizenship track just gave the player free club
 * days. The official client answers with its "welcome to the club" box that starts the club quests.
 */
public class CitizenshipVipOfferPromoEnabledComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CitizenshipVipOfferPromoEnabledComposer);
        return this.response;
    }
}

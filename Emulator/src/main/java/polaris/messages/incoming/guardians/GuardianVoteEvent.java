package polaris.messages.incoming.guardians;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.guides.GuardianTicket;
import polaris.hotel.guides.GuardianVoteType;
import polaris.messages.incoming.MessageHandler;

public class GuardianVoteEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuardianVoteEvent.class);

    @Override
    public void handle() throws Exception {
        int voteType = this.packet.readInt();

        GuardianTicket ticket =
                Emulator.getGameEnvironment().getGuideManager().getTicketForGuardian(this.client.getUser());

        if (ticket != null) {
            GuardianVoteType type = GuardianVoteType.NOT_VOTED;

            if (voteType == 0) {
                type = GuardianVoteType.ACCEPTABLY;
            } else if (voteType == 1) {
                type = GuardianVoteType.BADLY;
            } else if (voteType == 2) {
                type = GuardianVoteType.AWFULLY;
            } else {
                LOGGER.error("Uknown vote type: {}", voteType);
            }

            ticket.vote(this.client.getUser(), type);
        }
    }
}

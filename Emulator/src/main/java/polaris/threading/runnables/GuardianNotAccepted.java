package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.guides.GuardianTicket;
import polaris.hotel.guides.GuardianVote;
import polaris.hotel.guides.GuardianVoteType;
import polaris.hotel.users.User;

public class GuardianNotAccepted implements Runnable {
    private final GuardianTicket ticket;
    private final User user;

    public GuardianNotAccepted(GuardianTicket ticket, User user) {
        this.ticket = ticket;
        this.user = user;
    }

    @Override
    public void run() {
        GuardianVote vote = this.ticket.getVoteForGuardian(this.user);

        if (vote != null) {
            if (vote.type == GuardianVoteType.SEARCHING) {
                Emulator.getGameEnvironment().getGuideManager().acceptTicket(this.user, false);
            }
        }
    }
}

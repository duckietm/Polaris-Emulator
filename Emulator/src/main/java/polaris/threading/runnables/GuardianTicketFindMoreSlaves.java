package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.guides.GuardianTicket;

public class GuardianTicketFindMoreSlaves implements Runnable {
    private final GuardianTicket ticket;

    public GuardianTicketFindMoreSlaves(GuardianTicket ticket) {
        this.ticket = ticket;
    }

    @Override
    public void run() {
        Emulator.getGameEnvironment().getGuideManager().findGuardians(this.ticket);
    }
}

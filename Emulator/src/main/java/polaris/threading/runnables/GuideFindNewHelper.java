package polaris.threading.runnables;

import polaris.Emulator;
import polaris.hotel.guides.GuideTour;
import polaris.hotel.users.User;
import polaris.messages.outgoing.guides.GuideSessionDetachedComposer;

public class GuideFindNewHelper implements Runnable {
    private final GuideTour tour;
    private final User helper;
    private final int checkSum;

    public GuideFindNewHelper(GuideTour tour, User helper) {
        this.tour = tour;
        this.helper = helper;
        this.checkSum = tour.checkSum;
    }

    @Override
    public void run() {
        if (!this.tour.isEnded() && this.tour.checkSum == this.checkSum && this.tour.getHelper() == null) {
            if (this.helper != null && this.helper.getClient() != null) {
                this.helper.getClient().sendResponse(new GuideSessionDetachedComposer());
            }

            Emulator.getGameEnvironment().getGuideManager().findHelper(this.tour);
        }
    }
}

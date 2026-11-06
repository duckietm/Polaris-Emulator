package polaris.messages.incoming.users;

import polaris.hotel.users.subscriptions.SubscriptionClub;
import polaris.messages.incoming.MessageHandler;

public class RequestClubCenterEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(
                SubscriptionClub.calculatePayday(this.client.getUser().getUserInfo()));
    }
}

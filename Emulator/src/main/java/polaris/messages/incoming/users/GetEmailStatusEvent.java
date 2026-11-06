package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.EmailStatusComposer;

/**
 * Official {@code GetEmailStatusMessageComposer} (2557): the talent-track task dialog asks for
 * the account e-mail and whether it is verified, and gets {@code EmailStatus} (612) back with
 * {@code (email, isVerified, allowChange)}.
 */
public class GetEmailStatusEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String mail = this.client.getUser().getUserInfo().getMail();
        boolean verified = this.client.getUser().getUserInfo().isMailVerified();

        this.client.sendResponse(new EmailStatusComposer(
                mail == null ? "" : mail,
                verified,
                Emulator.getConfig().getBoolean("hotel.email.change.enabled", true)));
    }
}

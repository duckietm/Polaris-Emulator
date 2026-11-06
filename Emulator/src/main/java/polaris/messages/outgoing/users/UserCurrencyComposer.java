package polaris.messages.outgoing.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserCurrencyComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserCurrencyComposer.class);

    private final User user;

    public UserCurrencyComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserCurrencyComposer);
        String[] pointsTypes = Emulator.getConfig().getValue("seasonal.types").split(";");
        this.response.appendInt(pointsTypes.length);
        for (String s : pointsTypes) {
            int type;
            try {
                type = Integer.parseInt(s);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
                return null;
            }

            this.response.appendInt(type);
            this.response.appendInt(this.user.getUserInfo().getCurrencyAmount(type));
        }
        return this.response;
    }

    public User getUser() {
        return user;
    }
}

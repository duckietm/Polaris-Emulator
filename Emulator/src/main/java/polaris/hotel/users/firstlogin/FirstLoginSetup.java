package polaris.hotel.users.firstlogin;

import java.util.List;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.database.Database;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.users.firstlogin.FirstLoginSetupComposer;

/** The first-login setup at login: starts it for a new account and sends the offer while it is unanswered. */
public final class FirstLoginSetup {
    private FirstLoginSetup() {}

    /** The service on the hotel's database, or null while the hotel is not up or the setting turns the offer off. */
    public static FirstLoginSetupService service() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        Database database = WiredPlatform.database();
        if (configuration == null || database == null) return null;
        if (!configuration.getBoolean(FirstLoginSetupService.ENABLED_KEY, true)) return null;

        return new FirstLoginSetupService(database.getDataSource());
    }

    /** Adds the offer to the login messages of a user who has not answered it. */
    public static void atLogin(User user, List<ServerMessage> messages) {
        FirstLoginSetupService service = service();
        if (service == null) return;

        int userId = user.getUserInfo().getId();
        if (user.getUserInfo().firstVisit)
            service.begin(userId, user.getUserInfo().getHomeRoom());
        if (!service.isPending(userId)) return;

        messages.add(new FirstLoginSetupComposer(service.templates()).compose());
    }
}

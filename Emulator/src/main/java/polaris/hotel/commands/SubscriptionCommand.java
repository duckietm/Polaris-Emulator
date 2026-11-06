package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.subscriptions.Subscription;

/**
 * @author Beny
 */
public class SubscriptionCommand extends Command {
    public SubscriptionCommand() {
        super(
                "cmd_subscription",
                Emulator.getTexts().getValue("commands.keys.cmd_subscription").split(";"));
    }

    /**
     * Allows you to give/extend/remove subscription on a given user.
     *
     * Parameters:
     * [username] = Username of user to execute command on
     * [type] = Subscription type (e.g. CLUB)
     * [add|remove] = Use add or remove to increase/decrease sub duration
     * [time] = Time string e.g. "1 week", "18 days", "4 minutes". Can be complex e.g. "1 month 5 days 2 minutes"
     *
     * Examples:
     * :sub Beny habbo_club add 1 month - adds 1 month of CLUB subscription duration on the user Beny
     * :sub Beny builders_club add 1 month - adds 1 month of BUILDERS_CLUB subscription duration on the user Beny
     * :sub Beny habbo_club remove 3 days - removes 3 days of CLUB subscription duration on the user Beny
     * :sub Beny habbo_club remove - removes all remaining time from the CLUB subscription (expires it) on the user Beny
     *
     * @param gameClient Client that executed the command
     * @param params Command parameters
     * @return Boolean indicating success
     * @throws Exception Exception
     */
    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length >= 4) {
            var environment = Emulator.getGameEnvironment();
            User online = environment.getUserManager().getUser(params[1]);
            UserInfo info = online != null ? online.getUserInfo() : UserManager.getOfflineUserInfo(params[1]);

            if (info != null) {
                UserStats stats = info.getUserStats();
                String subscription = params[2].toUpperCase();
                String action = params[3];

                StringBuilder message = new StringBuilder();
                if (params.length > 4) {
                    for (int i = 4; i < params.length; i++) {
                        message.append(params[i]).append(" ");
                    }
                }

                if (!environment.getSubscriptionManager().types.containsKey(subscription)) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue(
                                                    "commands.error.cmd_subscription.type_not_found",
                                                    "%subscription% is not a valid subscription type")
                                            .replace("%subscription%", subscription),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }

                if (action.equalsIgnoreCase("add") || action.equalsIgnoreCase("+") || action.equalsIgnoreCase("a")) {
                    int timeToAdd = Emulator.timeStringToSeconds(message.toString());

                    if (timeToAdd < 1) {
                        gameClient
                                .getUser()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue(
                                                        "commands.error.cmd_subscription.invalid_params_time",
                                                        "Invalid time span, try: x minutes/days/weeks/months"),
                                        RoomChatMessageBubbles.ALERT);
                        return true;
                    }

                    stats.createSubscription(subscription, timeToAdd);
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue(
                                                    "commands.error.cmd_subscription.success_add_time",
                                                    "Successfully added %time% seconds to %subscription% on %user%")
                                            .replace("%time%", timeToAdd + "")
                                            .replace("%user%", params[1])
                                            .replace("%subscription%", subscription),
                                    RoomChatMessageBubbles.ALERT);
                } else if (action.equalsIgnoreCase("remove")
                        || action.equalsIgnoreCase("-")
                        || action.equalsIgnoreCase("r")) {
                    int timeToRemove = message.isEmpty() ? -1 : Emulator.timeStringToSeconds(message.toString());
                    if (!message.isEmpty() && timeToRemove < 1) {
                        gameClient
                                .getUser()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue(
                                                        "commands.error.cmd_subscription.invalid_params_time",
                                                        "Invalid time span, try: x minutes/days/weeks/months"),
                                        RoomChatMessageBubbles.ALERT);
                        return true;
                    }
                    Subscription s = stats.removeSubscription(subscription, timeToRemove);

                    if (s == null) {
                        gameClient
                                .getUser()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue(
                                                        "commands.error.cmd_subscription.user_not_have",
                                                        "%user% does not have the %subscription% subscription")
                                                .replace("%user%", params[1])
                                                .replace("%subscription%", subscription),
                                        RoomChatMessageBubbles.ALERT);
                        return true;
                    }

                    if (message.length() != 0) {
                        gameClient
                                .getUser()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue(
                                                        "commands.error.cmd_subscription.success_remove_time",
                                                        "Successfully removed %time% seconds from %subscription% on %user%")
                                                .replace("%time%", timeToRemove + "")
                                                .replace("%user%", params[1])
                                                .replace("%subscription%", subscription),
                                        RoomChatMessageBubbles.ALERT);
                    } else {
                        gameClient
                                .getUser()
                                .whisper(
                                        Emulator.getTexts()
                                                .getValue(
                                                        "commands.error.cmd_subscription.success_remove_sub",
                                                        "Successfully removed %subscription% sub from %user%")
                                                .replace("%user%", params[1])
                                                .replace("%subscription%", subscription),
                                        RoomChatMessageBubbles.ALERT);
                    }
                } else {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts()
                                            .getValue(
                                                    "commands.error.cmd_subscription.invalid_action",
                                                    "Invalid action specified. Must be add, +, remove or -"),
                                    RoomChatMessageBubbles.ALERT);
                }

            } else {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue(
                                                "commands.error.cmd_subscription.user_not_found",
                                                "%user% was not found")
                                        .replace("%user%", params[1]),
                                RoomChatMessageBubbles.ALERT);
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue(
                                            "commands.error.cmd_subscription.invalid_params", "Invalid command format"),
                            RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}

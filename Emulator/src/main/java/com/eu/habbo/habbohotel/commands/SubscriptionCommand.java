package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;

/**
 * @author Beny
 */
public class SubscriptionCommand extends Command {
    public SubscriptionCommand() {
        super("cmd_subscription", Emulator.getTexts().getValue("commands.keys.cmd_subscription").split(";"));
    }

    /**
     * Allows you to give/extend/remove subscription on a given user.
     *
     * Parameters:
     * [username] = Username of user to execute command on
     * [type] = Subscription type (e.g. HABBO_CLUB)
     * [add|remove] = Use add or remove to increase/decrease sub duration
     * [time] = Time string e.g. "1 week", "18 days", "4 minutes". Can be complex e.g. "1 month 5 days 2 minutes"
     *
     * Examples:
     * :sub Beny habbo_club add 1 month - adds 1 month of HABBO_CLUB subscription duration on the user Beny
     * :sub Beny builders_club add 1 month - adds 1 month of BUILDERS_CLUB subscription duration on the user Beny
     * :sub Beny habbo_club remove 3 days - removes 3 days of HABBO_CLUB subscription duration on the user Beny
     * :sub Beny habbo_club remove - removes all remaining time from the HABBO_CLUB subscription (expires it) on the user Beny
     *
     * @param gameClient Client that executed the command
     * @param params Command parameters
     * @return Boolean indicating success
     * @throws Exception Exception
     */
    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length >= 4) {
            HabboInfo info = HabboManager.getOfflineHabboInfo(params[1]);

            if (info != null) {
                HabboStats stats = info.getHabboStats();
                String subscription = params[2].toUpperCase();
                String action = params[3];

                var message = new StringBuilder();
                if (params.length > 4) {
                    for (int i = 4; i < params.length; i++) {
                        message.append(params[i]).append(" ");
                    }
                }
                
                if(!Emulator.getGameEnvironment().getSubscriptionManager().types.containsKey(subscription)) {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.type_not_found", "%subscription% is not a valid subscription type").replace("%subscription%", subscription), RoomChatMessageBubbles.ALERT);
                    return true;
                }

                if(action.equalsIgnoreCase("add") || action.equalsIgnoreCase("+") || action.equalsIgnoreCase("a")) {
                    int timeToAdd = Emulator.timeStringToSeconds(message.toString());

                    if(timeToAdd < 1) {
                        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.invalid_params_time", "Invalid time span, try: x minutes/days/weeks/months"), RoomChatMessageBubbles.ALERT);
                        return true;
                    }

                    stats.createSubscription(subscription, timeToAdd);
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.success_add_time", "Successfully added %time% seconds to %subscription% on %user%").replace("%time%", timeToAdd + "").replace("%user%", params[1]).replace("%subscription%", subscription), RoomChatMessageBubbles.ALERT);
                }
                else if(action.equalsIgnoreCase("remove") || action.equalsIgnoreCase("-") || action.equalsIgnoreCase("r")) {
                    Subscription s = stats.getSubscription(subscription);

                    if (s == null) {
                        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.user_not_have", "%user% does not have the %subscription% subscription").replace("%user%", params[1]).replace("%subscription%", subscription), RoomChatMessageBubbles.ALERT);
                        return true;
                    }

                    if(message.length() != 0) {
                        int timeToRemove = Emulator.timeStringToSeconds(message.toString());

                        if (timeToRemove < 1) {
                            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.invalid_params_time", "Invalid time span, try: x minutes/days/weeks/months"), RoomChatMessageBubbles.ALERT);
                            return true;
                        }

                        s.addDuration(-timeToRemove);
                        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.success_remove_time", "Successfully removed %time% seconds from %subscription% on %user%").replace("%time%", timeToRemove + "").replace("%user%", params[1]).replace("%subscription%", subscription), RoomChatMessageBubbles.ALERT);
                    }
                    else {
                        s.addDuration(-s.getRemaining());
                        gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.success_remove_sub", "Successfully removed %subscription% sub from %user%").replace("%user%", params[1]).replace("%subscription%", subscription), RoomChatMessageBubbles.ALERT);
                    }
                }
                else {
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.invalid_action", "Invalid action specified. Must be add, +, remove or -"), RoomChatMessageBubbles.ALERT);
                }

            } else {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.user_not_found", "%user% was not found").replace("%user%", params[1]), RoomChatMessageBubbles.ALERT);
            }
        } else {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_subscription.invalid_params", "Invalid command format"), RoomChatMessageBubbles.ALERT);
        }
        return true;
    }
}

package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;

public class CreditsCommand extends Command {
    public CreditsCommand() {
        super("cmd_credits", text("commands.keys.cmd_credits").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User staff = gameClient.getUser();

        if (params.length != 3) {
            staff.whisper(text("commands.error.cmd_credits.invalid_amount"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        UserInfo info = UserManager.getOfflineUserInfo(params[1]);

        if (info == null) {
            staff.whisper(
                    text("commands.error.cmd_credits.user_not_found")
                            .replace("%amount%", params[2])
                            .replace("%user%", params[1]),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        int credits;
        try {
            credits = Integer.parseInt(params[2]);
        } catch (NumberFormatException e) {
            staff.whisper(text("commands.error.cmd_credits.invalid_amount"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (credits == 0) {
            staff.whisper(text("commands.error.cmd_credits.invalid_amount"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        User user = Emulator.getGameServer().getGameClientManager().getUser(params[1]);
        int staffId = staff.getUserInfo().getId();

        // Recorded as done by the staff member, and reported only when the ledger took it: a debit below zero or an
        // overflow used to be refused in silence while both sides were told it went through.
        boolean applied = user != null
                ? CommandEconomy.credits(user, credits, "cmd_credits", staffId)
                : CommandEconomy.credits(
                        Emulator.getGameEnvironment().getUserManager(), info.getId(), credits, "cmd_credits", staffId);

        if (!applied) {
            staff.whisper(
                    text("commands.error.cmd_credits.failed", "The credits of %user% could not be changed.")
                            .replace("%user%", params[1]),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (user != null) {
            String received = text("commands.generic.cmd_credits.received").replace("%amount%", credits + "");

            if (user.getUserInfo().getCurrentRoom() != null) user.whisper(received, RoomChatMessageBubbles.ALERT);
            else user.alert(received);
        }

        staff.whisper(
                text("commands.succes.cmd_credits.send")
                        .replace("%amount%", credits + "")
                        .replace("%user%", params[1]),
                RoomChatMessageBubbles.ALERT);
        return true;
    }

    private static String text(String key) {
        return text(key, null);
    }

    private static String text(String key, String fallback) {
        return fallback == null
                ? Emulator.getTexts().getValue(key)
                : Emulator.getTexts().getValue(key, fallback);
    }
}

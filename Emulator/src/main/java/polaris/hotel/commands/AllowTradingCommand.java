package polaris.hotel.commands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.users.UserPerksComposer;

public class AllowTradingCommand extends Command {
    public AllowTradingCommand() {
        super(
                "cmd_allow_trading",
                Emulator.getTexts().getValue("commands.keys.cmd_allow_trading").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 1) {
            gameClient
                    .getUser()
                    .whisper(Emulator.getTexts().getValue("commands.error.cmd_allow_trading.forgot_username"));
            return true;
        }

        if (params.length == 2) {
            gameClient
                    .getUser()
                    .whisper(Emulator.getTexts()
                            .getValue("commands.error.cmd_allow_trading.forgot_trade")
                            .replace("%username%", params[1]));
            return true;
        }

        final String username = params[1];
        final String option = params[2];

        if (option.equalsIgnoreCase(Emulator.getTexts().getValue("generic.yes"))
                || option.equalsIgnoreCase(Emulator.getTexts().getValue("generic.no"))) {
            final boolean enabled = option.equalsIgnoreCase(Emulator.getTexts().getValue("generic.yes"));
            final User user = Emulator.getGameEnvironment().getUserManager().getUser(username);
            final UserInfo target = user != null ? user.getUserInfo() : UserManager.getOfflineUserInfo(username);

            // A trade lock is a sanction: never set or lifted on a higher rank, online or not.
            if (CommandTargetGuard.refuse(gameClient.getUser(), target)) {
                return true;
            }

            if (user != null) {
                if (!enabled) {
                    try (Connection connection =
                                    Emulator.getDatabase().getDataSource().getConnection();
                            PreparedStatement statement = connection.prepareStatement(
                                    "UPDATE users_settings SET tradelock_amount = tradelock_amount + 1 WHERE user_id = ?")) {
                        statement.setInt(1, user.getUserInfo().getId());
                        statement.executeUpdate();
                    }
                }
                user.getUserStats().setAllowTrade(enabled);
                gameClient
                        .getUser()
                        .whisper(Emulator.getTexts()
                                .getValue("commands.succes.cmd_allow_trading." + (enabled ? "enabled" : "disabled"))
                                .replace("%username%", params[1]));
                user.getClient().sendResponse(new UserPerksComposer(user));
                return true;
            } else {
                boolean found;
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "UPDATE users_settings INNER JOIN users ON users.id = users_settings.user_id SET can_trade = ?, tradelock_amount = tradelock_amount + ? WHERE users.username = ?")) {
                    statement.setString(1, enabled ? "1" : "0");
                    statement.setInt(2, enabled ? 0 : 1);
                    statement.setString(3, username);
                    found = statement.executeUpdate() > 0;
                }

                if (!found) {
                    gameClient
                            .getUser()
                            .whisper(Emulator.getTexts()
                                    .getValue("commands.error.cmd_allow_trading.user_not_found")
                                    .replace("%username%", params[1]));
                    return true;
                }

                gameClient
                        .getUser()
                        .whisper(Emulator.getTexts()
                                .getValue("commands.succes.cmd_allow_trading." + (enabled ? "enabled" : "disabled"))
                                .replace("%username%", params[1]));
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(Emulator.getTexts()
                            .getValue("commands.error.cmd_allow_trading.incorrect_setting")
                            .replace("%enabled%", Emulator.getTexts().getValue("generic.yes"))
                            .replace("%disabled%", Emulator.getTexts().getValue("generic.no")));
        }
        return true;
    }
}

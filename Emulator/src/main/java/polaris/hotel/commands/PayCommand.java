package polaris.hotel.commands;

import java.sql.Connection;
import java.util.Locale;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;
import polaris.messages.outgoing.users.UserPointsComposer;

/**
 * :pay &lt;username&gt; &lt;amount&gt; &lt;currency&gt; - a player hands part of their own balance to another online player.
 * The currency is credits or one of the names in seasonal.currency.names. The amount always comes out of the
 * sender's real balance, infinite-currency permissions included, so a payment never creates currency.
 */
public class PayCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayCommand.class);

    static final String CREDITS_NAME = "credits";

    public PayCommand() {
        super("cmd_pay", text("essentials.cmd_pay.keys", "pay;transfer").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User sender = gameClient.getUser();

        if (params.length != 4) {
            sender.whisper(
                    text("essentials.cmd_pay.incorrect.usage", "Missing arguments for pay command!"),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        int amount = amount(params[2]);
        if (amount <= 0) {
            sender.whisper(
                    text("essentials.cmd_pay.invalid_amount", "Invalid amount! Please use a positive value."),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        Currency currency = currency(
                params[3],
                Emulator.getConfig().getValue("seasonal.currency.names", ""),
                name -> Emulator.getConfig().getInt("seasonal.currency." + name, -2));
        if (currency == null) {
            sender.whisper(
                    text("essentials.cmd_pay.invalid_type", "The currency %type% does not exist!")
                            .replace("%type%", params[3]),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        User receiver = Emulator.getGameServer().getGameClientManager().getUser(params[1]);
        if (receiver == null) {
            sender.whisper(
                    text("essentials.cmd_pay.user_offline", "%username% is not online.")
                            .replace("%username%", params[1]),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        int senderId = sender.getUserInfo().getId();
        int receiverId = receiver.getUserInfo().getId();
        if (senderId == receiverId) {
            sender.whisper(
                    text("essentials.cmd_pay.self", "You cannot pay yourself, silly!"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        String receiverName = receiver.getUserInfo().getUsername();
        PayTransaction.Result result;
        try {
            result = LedgerWalletMutation.coordinated(sender, receiver, () -> {
                PayTransaction.Result committed;
                try (Connection connection =
                        Emulator.getDatabase().getDataSource().getConnection()) {
                    committed = PayTransaction.execute(connection, senderId, receiverId, currency.type(), amount);
                }
                if (committed.status() == PayTransaction.Status.PAID) {
                    LedgerWalletMutation.applyCommitted(sender, currency.type(), committed.senderBalance());
                    LedgerWalletMutation.applyCommitted(receiver, currency.type(), committed.receiverBalance());
                }
                return committed;
            });
        } catch (Exception exception) {
            LOGGER.error(
                    "Unable to transfer {} of currency {} from {} to {}",
                    amount,
                    currency.type(),
                    senderId,
                    receiverId,
                    exception);
            sender.whisper(
                    text("essentials.cmd_pay.failed", "The payment to %username% could not be made.")
                            .replace("%username%", receiverName),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (result.status() == PayTransaction.Status.NOT_ENOUGH) {
            sender.whisper(
                    text("essentials.cmd_pay.not_enough", "Cannot pay %username%. You don't have %amount% %type%!")
                            .replace("%username%", receiverName)
                            .replace("%amount%", Integer.toString(amount))
                            .replace("%type%", currency.name()),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }
        if (result.status() == PayTransaction.Status.RECEIVER_FULL) {
            sender.whisper(
                    text("essentials.cmd_pay.failed", "The payment to %username% could not be made.")
                            .replace("%username%", receiverName),
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        publish(sender, currency.type(), -amount);
        publish(receiver, currency.type(), amount);

        String received = text("essentials.cmd_pay.received", "%username% has paid you %amount% %type%!")
                .replace("%username%", sender.getUserInfo().getUsername())
                .replace("%amount%", Integer.toString(amount))
                .replace("%type%", currency.name());
        if (receiver.getUserInfo().getCurrentRoom() != null) receiver.whisper(received, RoomChatMessageBubbles.ALERT);
        else receiver.alert(received);

        sender.whisper(
                text("essentials.cmd_pay.transferred", "You paid %username% %amount% %type%!")
                        .replace("%username%", receiverName)
                        .replace("%amount%", Integer.toString(amount))
                        .replace("%type%", currency.name()),
                RoomChatMessageBubbles.ALERT);
        return true;
    }

    /** A currency the command accepts: its ledger type and the name it is reported with. */
    record Currency(int type, String name) {}

    /** The amount typed, or -1 when it is not a whole number in (0, Integer.MAX_VALUE]. */
    static int amount(String raw) {
        if (raw == null) return -1;
        try {
            int amount = Integer.parseInt(raw.strip());
            return amount > 0 ? amount : -1;
        } catch (NumberFormatException notANumberOrTooLarge) {
            return -1;
        }
    }

    /**
     * Credits, or one of the configured currency names (a trailing "s" is accepted, so "duckets" is "ducket"); null
     * for anything else, or for a configured name without a valid type.
     */
    static Currency currency(String raw, String configuredNames, ToIntFunction<String> typeOf) {
        if (raw == null || raw.isBlank()) return null;
        String typed = raw.strip().toLowerCase(Locale.ROOT);

        if (typed.equals(CREDITS_NAME) || typed.equals("credit")) {
            return new Currency(EconomyLedger.CREDITS, CREDITS_NAME);
        }

        if (configuredNames == null) return null;
        for (String configured : configuredNames.split(";")) {
            String name = configured.strip().toLowerCase(Locale.ROOT);
            if (name.isEmpty() || name.equals(CREDITS_NAME)) continue;
            if (typed.equals(name) || typed.equals(name + "s")) {
                int type = typeOf.applyAsInt(name);
                return type >= 0 ? new Currency(type, name) : null;
            }
        }
        return null;
    }

    private static void publish(User user, int type, int delta) {
        if (user.getClient() == null) return;
        if (type == EconomyLedger.CREDITS) user.getClient().sendResponse(new UserCreditsComposer(user));
        else if (type == 0) user.getClient().sendResponse(new UserCurrencyComposer(user));
        else
            user.getClient()
                    .sendResponse(new UserPointsComposer(user.getUserInfo().getCurrencyAmount(type), delta, type));
    }

    private static String text(String key, String fallback) {
        return Emulator.getTexts().getValue(key, fallback);
    }
}

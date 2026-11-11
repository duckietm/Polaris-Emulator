package polaris.hotel.commands;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserPrefix;
import polaris.hotel.users.customization.UserCustomizationPurchaseService;
import polaris.hotel.users.customization.UserCustomizationRepository;
import polaris.hotel.users.inventory.PrefixesComponent;
import polaris.messages.outgoing.inventory.prefixes.PrefixReceivedComposer;
import polaris.messages.outgoing.inventory.prefixes.UserPrefixesComposer;

public class GivePrefixCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(GivePrefixCommand.class);

    public GivePrefixCommand() {
        super(
                "cmd_give_prefix",
                Emulator.getTexts().getValue("commands.keys.cmd_give_prefix").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 4) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_give_prefix.usage"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        String targetName = params[1];
        String text = params[2];
        String color = params[3];
        String icon = params.length > 4 ? params[4] : "";
        String effect = params.length > 5 ? params[5] : "";

        // Validate color
        String[] colorParts = color.split(",");
        for (String part : colorParts) {
            if (!part.matches("^#[0-9A-Fa-f]{6}$")) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_give_prefix.invalid_color"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(
                new UserCustomizationRepository(Emulator.getDatabase().getDataSource()),
                Emulator.getGameEnvironment().getWordFilter().getWords());

        User target = Emulator.getGameEnvironment().getUserManager().getUser(targetName);

        if (target == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_give_prefix.user_not_found"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!CommandTargetGuard.canTarget(gameClient.getUser(), target)) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_ban.target_rank_higher"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        // The same text, filter, icon, effect and count rules as a purchase; only the prefix blacklist is left to
        // staff judgement, so a "Staff" prefix can still be handed out.
        String failure = service.grantedPrefixFailure(target, text, color, icon, effect);
        if (failure != null) {
            gameClient.getUser().whisper(failure, RoomChatMessageBubbles.ALERT);
            return true;
        }

        UserPrefix prefix = new UserPrefix(
                target.getUserInfo().getId(), text, color, icon, effect.trim().toLowerCase());
        PrefixesComponent prefixes = target.getInventory().getPrefixesComponent();
        synchronized (prefixes) {
            try {
                new UserCustomizationRepository(Emulator.getDatabase().getDataSource()).insertPrefix(prefix);
            } catch (SQLException exception) {
                LOGGER.error("Unable to store the prefix given to {}", targetName, exception);
                gameClient.getUser().whisper("Unable to store the prefix.", RoomChatMessageBubbles.ALERT);
                return true;
            }
            prefixes.addPrefix(prefix);
        }

        if (target.getClient() != null) {
            target.getClient().sendResponse(new PrefixReceivedComposer(prefix));
            target.getClient().sendResponse(new UserPrefixesComposer(target));
        }

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue("commands.succes.cmd_give_prefix")
                                .replace("%user%", targetName)
                                .replace("%prefix%", text),
                        RoomChatMessageBubbles.ALERT);

        return true;
    }
}

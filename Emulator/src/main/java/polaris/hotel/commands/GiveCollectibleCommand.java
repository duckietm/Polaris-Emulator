package polaris.hotel.commands;

import java.util.List;
import java.util.Optional;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectiblesRuntime;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.incoming.collectibles.CollectibleGrants;

/**
 * :givecollectible &lt;user&gt; &lt;product&gt;: mints the next serial of a collectible product
 * ({@code collectible_products.product_code}) to a user, online or not, as the hub's purchases and claims do. A
 * product with a {@code collectible_avatar_outfits} row is an avatar collectible: it shows up in the user's avatar
 * editor NFT tab, ready to wear. Needs {@code hotel.collectibles.enabled}; the mint limit applies.
 */
public class GiveCollectibleCommand extends Command {
    public GiveCollectibleCommand() {
        super(
                "cmd_give_collectible",
                texts().getValue("commands.keys.cmd_give_collectible", "givecollectible;givenft")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        TextsManager texts = texts();
        if (params.length < 3) {
            return this.whisper(
                    gameClient,
                    texts.getValue("commands.error.cmd_give_collectible", "Usage: :givecollectible <user> <product>"));
        }

        CollectiblesRuntime runtime = CollectiblesRuntime.installed();
        if (runtime == null || !CollectiblesRuntime.currentSettings().enabled()) {
            return this.whisper(
                    gameClient,
                    texts.getValue(
                            "commands.error.cmd_give_collectible.disabled",
                            "Collectibles are switched off (hotel.collectibles.enabled)."));
        }

        String username = params[1];
        String productCode = params[2];
        UserInfo target = UserManager.getOfflineUserInfo(username);
        if (target == null) {
            return this.whisper(
                    gameClient,
                    texts.getValue("commands.error.cmd_give_collectible.user", "There is no user called %user%.")
                            .replace("%user%", username));
        }

        CollectiblesService service = runtime.service();
        Optional<Owned> minted = CollectiblesService.isValidCode(productCode)
                ? service.mint(target.getId(), productCode, "staff", CollectiblesRuntime.now())
                : Optional.empty();
        if (minted.isEmpty()) {
            return this.whisper(
                    gameClient,
                    texts.getValue(
                                    "commands.error.cmd_give_collectible.product",
                                    "There is no collectible product %product%, or its mint limit is reached.")
                            .replace("%product%", productCode));
        }

        User online = runtime.onlineUser().apply(target.getId());
        if (online != null) CollectibleGrants.announce(online, List.of(minted.get()));

        return this.whisper(
                gameClient,
                texts.getValue("commands.succes.cmd_give_collectible", "%user% got collectible %product% #%serial%.")
                        .replace("%user%", target.getUsername())
                        .replace("%product%", productCode)
                        .replace("%serial%", Integer.toString(minted.get().serial())));
    }

    private static TextsManager texts() {
        return Emulator.getTexts();
    }

    private boolean whisper(GameClient gameClient, String message) {
        gameClient.getUser().whisper(message, RoomChatMessageBubbles.ALERT);
        return true;
    }
}

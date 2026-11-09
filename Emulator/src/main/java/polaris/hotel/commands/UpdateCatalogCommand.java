package polaris.hotel.commands;

import java.util.concurrent.atomic.AtomicLong;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.gameclients.GameClientManager;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.messages.outgoing.catalog.CatalogModeComposer;
import polaris.messages.outgoing.catalog.CatalogUpdatedComposer;
import polaris.messages.outgoing.catalog.DiscountComposer;
import polaris.messages.outgoing.catalog.GiftConfigurationComposer;
import polaris.messages.outgoing.catalog.RecyclerLogicComposer;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;

public class UpdateCatalogCommand extends Command {

    private static final long COOLDOWN_MS = 5_000L;
    private static final AtomicLong LAST_RUN = new AtomicLong(0L);

    public UpdateCatalogCommand() {
        super(
                "cmd_update_catalogue",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_update_catalogue")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) {
        if (!reload()) {
            gameClient
                    .getUser()
                    .whisper(
                            "The catalog was just updated, please wait a few seconds before running this again.",
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_catalog"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }

    /**
     * The reload itself, without the whisper: also run by the RCON reloadcontent command. False, and
     * nothing reloaded, when the catalog was reloaded less than five seconds ago.
     */
    public static boolean reload() {
        long now = System.currentTimeMillis();
        long previous = LAST_RUN.get();

        if (now - previous < COOLDOWN_MS || !LAST_RUN.compareAndSet(previous, now)) {
            return false;
        }

        Emulator.getGameEnvironment().getCatalogManager().initialize();
        GameClientManager clients = Emulator.getGameServer().getGameClientManager();
        clients.sendBroadcastResponse(new CatalogUpdatedComposer());
        clients.sendBroadcastResponse(new CatalogModeComposer(0));
        clients.sendBroadcastResponse(new DiscountComposer());
        clients.sendBroadcastResponse(new MarketplaceConfigComposer());
        clients.sendBroadcastResponse(new GiftConfigurationComposer());
        clients.sendBroadcastResponse(new RecyclerLogicComposer());
        Emulator.getGameEnvironment().getCraftingManager().reload();
        return true;
    }

    /**
     * Loads the discount.* settings again and sends every online client the new bulk discount rules, so the prices
     * they show match what a purchase charges. No catalogue reload, so no cooldown. Run by RCON reloadcontent
     * "discounts".
     */
    public static void reloadDiscounts() {
        UpdateConfigCommand.reload();
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new DiscountComposer());
    }
}

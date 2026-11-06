package polaris.hotel.commands;

import java.util.concurrent.atomic.AtomicLong;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
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
        long now = System.currentTimeMillis();
        long previous = LAST_RUN.get();

        if (now - previous < COOLDOWN_MS || !LAST_RUN.compareAndSet(previous, now)) {
            gameClient
                    .getUser()
                    .whisper(
                            "The catalog was just updated, please wait a few seconds before running this again.",
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        Emulator.getGameEnvironment().getCatalogManager().initialize();
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new CatalogUpdatedComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new CatalogModeComposer(0));
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new DiscountComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new MarketplaceConfigComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new GiftConfigurationComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new RecyclerLogicComposer());
        Emulator.getGameEnvironment().getCraftingManager().reload();
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_catalog"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}

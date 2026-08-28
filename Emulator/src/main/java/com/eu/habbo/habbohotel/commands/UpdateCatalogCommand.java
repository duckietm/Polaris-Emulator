package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.outgoing.catalog.CatalogModeComposer;
import com.eu.habbo.messages.outgoing.catalog.CatalogUpdatedComposer;
import com.eu.habbo.messages.outgoing.catalog.DiscountComposer;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import com.eu.habbo.messages.outgoing.catalog.RecyclerLogicComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;
import java.util.concurrent.atomic.AtomicLong;

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
                    .getHabbo()
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
                .getHabbo()
                .whisper(
                        Emulator.getTexts().getValue("commands.succes.cmd_update_catalog"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}

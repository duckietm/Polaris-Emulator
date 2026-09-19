package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.messages.outgoing.catalog.CatalogModeComposer;
import com.eu.habbo.messages.outgoing.catalog.CatalogUpdatedComposer;
import com.eu.habbo.messages.outgoing.catalog.DiscountComposer;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import com.eu.habbo.messages.outgoing.catalog.RecyclerLogicComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateAllCommand extends Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateAllCommand.class);

    public UpdateAllCommand() {
        super(
                "cmd_update_all",
                Emulator.getTexts().getValue("commands.keys.cmd_update_all").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        LOGGER.info("[UpdateAll] Reloading all subsystems...");

        // Achievements
        Emulator.getGameEnvironment().getAchievementManager().reload();

        // Bots
        Emulator.getGameEnvironment().getBotManager().reload();

        // Catalog
        Emulator.getGameEnvironment().getCatalogManager().initialize();
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new CatalogUpdatedComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new CatalogModeComposer(0));
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new DiscountComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new MarketplaceConfigComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new GiftConfigurationComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new RecyclerLogicComposer());

        // Crafting
        Emulator.getGameEnvironment().getCraftingManager().reload();

        // Config
        Emulator.getConfig().reload();

        // Guild Parts
        Emulator.getGameEnvironment().getGuildManager().loadGuildParts();
        Emulator.getBadgeImager().reload();

        // Hotel View
        Emulator.getGameEnvironment().getHotelViewManager().getNewsList().reload();
        Emulator.getGameEnvironment().getHotelViewManager().getHallOfFame().reload();

        // Items
        Emulator.getGameEnvironment().getItemManager().loadItems();
        Emulator.getGameEnvironment().getItemManager().loadCrackable();
        Emulator.getGameEnvironment().getItemManager().loadSoundTracks();

        synchronized (Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                if (room.isLoaded() && room.getUserCount() > 0 && room.getLayout() != null) {
                    room.sendComposer(new RoomRelativeMapComposer(room).compose());
                }
            }
        }

        // Navigator
        Emulator.getGameEnvironment().getNavigatorManager().loadNavigator();

        // Room Models
        Emulator.getGameEnvironment().getRoomManager().loadRoomModels();
        Emulator.getGameEnvironment().getRoomManager().loadPublicRooms();

        // Permissions
        Emulator.getGameEnvironment().getPermissionsManager().reload();

        // Pet Data
        Emulator.getGameEnvironment().getPetManager().reloadPetData();

        // Polls
        Emulator.getGameEnvironment().getPollManager().loadPolls();

        // Reward tracks
        UpdateRewardTracksCommand.reloadAndBroadcast();

        // Texts & Commands
        try {
            Emulator.getTexts().reload();
            Emulator.getGameEnvironment().getCommandHandler().reloadCommands();
        } catch (Exception e) {
            gameClient
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_update_texts.failed"),
                            RoomChatMessageBubbles.ALERT);
        }

        // Word Filter
        Emulator.getGameEnvironment().getWordFilter().reload();

        // YouTube
        Emulator.getGameEnvironment().getItemManager().getYoutubeManager().load();

        LOGGER.info("[UpdateAll] All subsystems reloaded successfully!");
        gameClient
                .getHabbo()
                .whisper(Emulator.getTexts().getValue("commands.succes.cmd_update_all"), RoomChatMessageBubbles.ALERT);
        return true;
    }
}

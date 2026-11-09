package polaris.hotel.rooms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.interactions.InteractionYoutubeTV;
import polaris.hotel.items.interactions.games.InteractionGameTimer;
import polaris.hotel.items.interactions.games.InteractionGameUpCounter;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.raidprotection.RaidProtectionManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.outgoing.hotelview.HotelViewComposer;

final class RoomDisposer {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomDisposer.class);

    private final Room room;

    RoomDisposer(Room room) {
        this.room = room;
    }

    void dispose(boolean wasLoaded) {
        if (wasLoaded) {
            this.disposeLoadedState();
        }
        this.resetPreloadedState();
    }

    private void disposeLoadedState() {
        try {
            TraxManager traxManager = this.room.getTraxManager();
            if (traxManager != null && !traxManager.disposed()) {
                traxManager.dispose();
            }

            RaidProtectionManager.get().onRoomUnloaded(this.room.getId());
            this.room.scheduledTasks.clear();
            this.room.scheduledComposers.clear();
            this.room.getChatManager().clearMutes();

            RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
            for (InteractionGameTimer timer : specialTypes.getGameTimers().values()) {
                if (timer instanceof InteractionGameUpCounter counter) {
                    counter.resetOnRoomUnload(this.room);
                } else {
                    timer.setRunning(false);
                }
            }

            this.room.getGameManager().dispose();
            // A playlist that advances by itself would keep the TV and the room alive after the unload.
            for (UserItem item : this.room.getFloorItems()) {
                if (item instanceof InteractionYoutubeTV tv) {
                    tv.cancelAdvancement();
                }
            }
            this.room.removeAllPets(this.room.getOwnerId());
            this.room.getItemManager().saveAllPendingItems();

            WiredManager.unregisterRoomTickables(this.room);
            this.room.disposeWiredRuntimeState();
            specialTypes.dispose();
            this.clearWiredCaches();

            this.room.getItemManager().clear();
            this.room.getUnitManager().clearQueue();

            for (User user : this.room.getCurrentUsers().values()) {
                this.room.gameEnvironment().getRoomManager().leaveRoom(user, this.room);
            }

            this.room.sendComposer(new HotelViewComposer().compose());
            this.saveBotsAndPets();

            this.room.getUnitManager().clear();
            this.room.getUnitManager().clearBots();
            this.room.getUnitManager().clearPets();
        } catch (Exception exception) {
            LOGGER.error("Caught exception", exception);
        }
    }

    /**
     * Drops what a load registered before the room was disposed under it. Nobody is in a room that never finished
     * loading, so no user is moved and nothing is sent; the items, bots and pets were only read, so nothing is saved.
     */
    void discardAbandonedLoad() {
        try {
            this.room.getItemManager().clear();
            this.room.getUnitManager().clearBots();
            this.room.getUnitManager().clearPets();
        } catch (Exception exception) {
            LOGGER.error("Caught exception discarding an abandoned room load", exception);
        }

        try {
            WiredManager.unregisterRoomTickables(this.room);
            this.room.disposeWiredRuntimeState();
            this.room.getRoomSpecialTypes().dispose();
            this.clearWiredCaches();
        } catch (Exception exception) {
            LOGGER.error("Caught exception discarding the wired state of an abandoned room load", exception);
        }
    }

    private void clearWiredCaches() {
        this.room.getArrayVariableManager().clearCache();
        if (WiredManager.getStackIndex() != null) {
            WiredManager.getStackIndex().invalidateAll(this.room);
        }
        if (WiredManager.getEngine() != null) {
            WiredManager.getEngine().clearRoomRecursionDepth(this.room.getId());
            WiredManager.getEngine().clearRoomRateLimiters(this.room.getId());
            WiredManager.getEngine().clearRoomBan(this.room.getId());
            WiredManager.getEngine().clearRoomDiagnostics(this.room.getId());
        }
    }

    private void saveBotsAndPets() {
        for (Bot bot : this.room.getCurrentBots().values()) {
            bot.needsUpdate(true);
            bot.run();
        }
        for (Pet pet : this.room.getCurrentPets().values()) {
            pet.needsUpdate = true;
            pet.run();
        }
    }

    private void resetPreloadedState() {
        try {
            this.room.wordQuiz = "";
            this.room.yesVotes = 0;
            this.room.noVotes = 0;
            this.room.updateDatabaseUserCount();
            this.room.setLayout(null);
        } catch (Exception exception) {
            LOGGER.error("Caught exception", exception);
        }
    }
}

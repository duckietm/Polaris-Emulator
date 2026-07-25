package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            this.room.removeAllPets(this.room.getOwnerId());
            this.room.getItemManager().saveAllPendingItems();

            WiredManager.unregisterRoomTickables(this.room);
            this.room.disposeWiredRuntimeState();
            specialTypes.dispose();
            this.clearWiredCaches();

            this.room.getItemManager().clear();
            this.room.getUnitManager().clearQueue();

            for (Habbo habbo : this.room.getCurrentHabbos().values()) {
                this.room.gameEnvironment().getRoomManager().leaveRoom(habbo, this.room);
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

    private void clearWiredCaches() {
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

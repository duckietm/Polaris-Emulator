package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.RankLimits;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomCategory;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.CanCreateRoomComposer;
import com.eu.habbo.messages.outgoing.navigator.RoomCreatedComposer;
import com.eu.habbo.messages.outgoing.unknown.RoomCategoryUpdateMessageComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestCreateRoomEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCreateRoomEvent.class);

    static final String STAFF_MODELS = "acc_navigator_staff";

    @Override
    public int getRatelimit() {
        return 3000;
    }

    @Override
    public void handle() throws Exception {
        String name = this.packet.readString();
        String description = this.packet.readString();
        String modelName = this.packet.readString();
        int categoryId = this.packet.readInt();
        int maxUsers = this.packet.readInt();
        int tradeType = this.packet.readInt();

        RoomManager roomManager = Emulator.getGameEnvironment().getRoomManager();

        if (!roomManager.layoutExists(modelName)) {
            LOGGER.error(
                    "[SCRIPTER] Incorrect layout name \"{}\". {}",
                    modelName,
                    this.client.getHabbo().getHabboInfo().getUsername());
            return;
        }

        Habbo creator = this.client.getHabbo();

        if (!mayUseModel(
                modelName,
                roomManager.layoutClubOnly(modelName),
                creator.getHabboStats().hasActiveClub(),
                creator.hasPermission(STAFF_MODELS))) {
            LOGGER.warn(
                    "[SCRIPTER] {} may not create a room with model \"{}\"",
                    creator.getHabboInfo().getUsername(),
                    modelName);
            return;
        }

        RoomCategory category = Emulator.getGameEnvironment().getRoomManager().getCategory(categoryId);

        // Official flow: a room created without a usable category lands in a
        // fallback category and the client is asked to pick one right away
        // (RoomCategoryUpdateMessage 3896 -> enforce category dialog).
        boolean enforceCategory = false;
        if (category == null
                || category.getMinRank()
                        > this.client.getHabbo().getHabboInfo().getRank().getId()) {
            RoomCategory fallback = fallbackCategory(this.client.getHabbo());

            if (fallback == null) {
                LOGGER.error(
                        "[SCRIPTER] Incorrect rank or non existing category ID: \"{}\".{}",
                        categoryId,
                        this.client.getHabbo().getHabboInfo().getUsername());
                return;
            }

            LOGGER.info(
                    "Room created without a valid category ({}) by {}; using category {} and enforcing a choice",
                    categoryId,
                    this.client.getHabbo().getHabboInfo().getUsername(),
                    fallback.getId());
            categoryId = fallback.getId();
            enforceCategory = true;
        }

        if (maxUsers > 250) return;

        if (tradeType > 2) return;

        if (name.trim().length() < 3
                || name.length() > 25
                || !Emulator.getGameEnvironment()
                        .getWordFilter()
                        .filter(name, this.client.getHabbo())
                        .equals(name)) return;

        if (description.length() > 128
                || !Emulator.getGameEnvironment()
                        .getWordFilter()
                        .filter(description, this.client.getHabbo())
                        .equals(description)) return;

        int count = Emulator.getGameEnvironment()
                .getRoomManager()
                .getRoomsForHabbo(this.client.getHabbo())
                .size();
        int max = RankLimits.rooms(this.client.getHabbo());

        if (count >= max) {
            this.client.sendResponse(new CanCreateRoomComposer(count, max));
            return;
        }

        final Room room = Emulator.getGameEnvironment()
                .getRoomManager()
                .createRoomForHabbo(
                        this.client.getHabbo(), name, description, modelName, maxUsers, categoryId, tradeType);

        if (room != null) {
            this.client.sendResponse(new RoomCreatedComposer(room));
            if (enforceCategory) {
                this.client.sendResponse(new RoomCategoryUpdateMessageComposer(
                        RoomCategoryUpdateMessageComposer.SELECTION_ROOM_CREATED));
            }
        }
    }

    /**
     * The client only offers navigator models (it sends "model_" + the configured name); public
     * room and game layouts are for staff. club_only models need Habbo Club.
     */
    static boolean mayUseModel(String modelName, boolean clubOnly, boolean hasClub, boolean staff) {
        if (staff) return true;

        if (!modelName.startsWith("model_")) return false;

        return !clubOnly || hasClub;
    }

    /** Lowest-id category the user may use, or null when there is none. */
    static RoomCategory fallbackCategory(Habbo habbo) {
        RoomCategory fallback = null;
        for (RoomCategory candidate :
                Emulator.getGameEnvironment().getRoomManager().roomCategoriesForHabbo(habbo)) {
            if (fallback == null || candidate.getId() < fallback.getId()) {
                fallback = candidate;
            }
        }
        return fallback;
    }
}

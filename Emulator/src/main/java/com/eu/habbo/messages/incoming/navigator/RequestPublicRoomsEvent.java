package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.navigation.NavigatorPublicCategory;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.OfficialRoomsComposer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AIR 13 {@code GetOfficialRooms}: answers with {@code OfficialRooms} (438)
 * built from the navigator public categories and the promoted rooms, the
 * official-room data this emulator owns.
 */
public class RequestPublicRoomsEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        // The official GetOfficialRooms composer sends the ad index it wants
        // filled; the emulator has no official-view ad slot, so it is read and
        // dropped.
        this.packet.readInt();

        List<NavigatorPublicCategory> categories = new ArrayList<>(Emulator.getGameEnvironment()
                .getNavigatorManager()
                .publicCategories
                .values());
        categories.sort(Comparator.comparingInt(category -> category.order));

        this.client.sendResponse(new OfficialRoomsComposer(
                categories, Emulator.getGameEnvironment().getRoomManager().getRoomsPromoted()));
    }
}

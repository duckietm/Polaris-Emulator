package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.navigation.NavigatorPublicCategory;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AIR 13 room hopper ({@code RoomHopperNetworkWidget.onRoomForwardButton} ->
 * {@code HabboNavigator.goToRoomNetwork(networkId, useHomeRoom)}): the client
 * asks to be dropped into a random room of a "network".
 *
 * <p>Wire: {@code int networkId, int homeRoomId}. We define a network as a
 * navigator public category: {@code networkId} is the id of the public category
 * (the {@code landing.view.roomhopper.network.id} property in the official
 * client), and 0 means "any official room". A room is only offered when it has
 * a free slot, and the second int - the client's home room - is preferred when
 * it is part of the network, exactly as the official composer intends.
 */
public class RoomNetworkOpenConnectionEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        int networkId = this.packet.readInt();
        int homeRoomId = this.packet.readInt();

        List<Room> candidates = new ArrayList<>();
        for (NavigatorPublicCategory category : Emulator.getGameEnvironment()
                .getNavigatorManager()
                .publicCategories
                .values()) {
            if (networkId > 0 && category.id != networkId) {
                continue;
            }
            for (Room room : category.rooms) {
                if (room != null && room.getUserCount() < room.getUsersMax()) {
                    candidates.add(room);
                }
            }
        }

        if (candidates.isEmpty()) {
            this.client.sendResponse(new GenericAlertComposer("${navigator.noroomsfound}"));
            return;
        }

        for (Room room : candidates) {
            if (room.getId() == homeRoomId) {
                this.client.sendResponse(new ForwardToRoomComposer(room.getId()));
                return;
            }
        }

        Room target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        this.client.sendResponse(new ForwardToRoomComposer(target.getId()));
    }
}

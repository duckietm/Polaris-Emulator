package polaris.messages.incoming.navigator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.navigation.NavigatorPublicCategory;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.OfficialRoomsComposer;

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

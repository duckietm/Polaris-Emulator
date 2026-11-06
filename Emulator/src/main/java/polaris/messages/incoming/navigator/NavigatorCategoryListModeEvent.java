package polaris.messages.incoming.navigator;

import polaris.Emulator;
import polaris.hotel.navigation.ListMode;
import polaris.hotel.rooms.RoomCategory;
import polaris.messages.incoming.MessageHandler;

public class NavigatorCategoryListModeEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String category = this.packet.readString();
        int viewMode = this.packet.readInt();

        RoomCategory roomCategory =
                Emulator.getGameEnvironment().getRoomManager().getCategory(category);
        this.client
                .getUser()
                .getUserStats()
                .navigatorWindowSettings
                .setListMode(
                        roomCategory != null ? roomCategory.getCaptionSave() : category,
                        viewMode == 1 ? ListMode.THUMBNAILS : ListMode.LIST);
    }
}

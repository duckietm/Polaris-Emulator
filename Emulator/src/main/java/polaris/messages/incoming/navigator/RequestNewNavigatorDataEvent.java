package polaris.messages.incoming.navigator;

import java.util.ArrayList;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.rooms.RoomCategory;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.NewNavigatorCategoryUserCountComposer;
import polaris.messages.outgoing.navigator.NewNavigatorCollapsedCategoriesComposer;
import polaris.messages.outgoing.navigator.NewNavigatorEventCategoriesComposer;
import polaris.messages.outgoing.navigator.NewNavigatorLiftedRoomsComposer;
import polaris.messages.outgoing.navigator.NewNavigatorMetaDataComposer;
import polaris.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;
import polaris.messages.outgoing.navigator.NewNavigatorSettingsComposer;

public class RequestNewNavigatorDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        GameEnvironment environment = Emulator.getGameEnvironment();
        this.client.sendResponse(
                new NewNavigatorSettingsComposer(this.client.getUser().getUserStats().navigatorWindowSettings));
        this.client.sendResponse(new NewNavigatorMetaDataComposer());
        this.client.sendResponse(new NewNavigatorLiftedRoomsComposer(
                environment.getNavigatorManager().getLiftedRooms()));
        this.client.sendResponse(new NewNavigatorCollapsedCategoriesComposer());
        this.client.sendResponse(new NewNavigatorSavedSearchesComposer(
                this.client.getUser().getUserInfo().getSavedSearches()));
        this.client.sendResponse(new NewNavigatorEventCategoriesComposer());

        List<RoomCategory> categories =
                new ArrayList<>(environment.getRoomManager().roomCategoriesForUser(this.client.getUser()));
        this.client.sendResponse(new NewNavigatorCategoryUserCountComposer(categories));
    }
}

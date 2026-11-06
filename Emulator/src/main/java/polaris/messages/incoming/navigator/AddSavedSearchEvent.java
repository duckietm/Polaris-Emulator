package polaris.messages.incoming.navigator;

import polaris.hotel.navigation.NavigatorSavedSearch;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;

public class AddSavedSearchEvent extends MessageHandler {
    private static final int MAX_SAVED_SEARCHES = 50;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        String searchCode = NavigatorInputGuard.normalizeSavedSearchValue(this.packet.readString());
        String filter = NavigatorInputGuard.normalizeSavedSearchValue(this.packet.readString());

        if (this.client.getUser().getUserInfo().getSavedSearches().size() >= MAX_SAVED_SEARCHES) {
            this.client.sendResponse(new NewNavigatorSavedSearchesComposer(
                    this.client.getUser().getUserInfo().getSavedSearches()));
            return;
        }

        this.client.getUser().getUserInfo().addSavedSearch(new NavigatorSavedSearch(searchCode, filter));

        this.client.sendResponse(new NewNavigatorSavedSearchesComposer(
                this.client.getUser().getUserInfo().getSavedSearches()));
    }
}

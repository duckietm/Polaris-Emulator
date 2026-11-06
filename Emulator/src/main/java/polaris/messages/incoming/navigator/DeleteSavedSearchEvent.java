package polaris.messages.incoming.navigator;

import polaris.hotel.navigation.NavigatorSavedSearch;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;

public class DeleteSavedSearchEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int searchId = this.packet.readInt();

        NavigatorSavedSearch search = null;
        for (NavigatorSavedSearch savedSearch :
                this.client.getUser().getUserInfo().getSavedSearches()) {
            if (savedSearch.getId() == searchId) {
                search = savedSearch;
                break;
            }
        }

        if (search == null) return;

        this.client.getUser().getUserInfo().deleteSavedSearch(search);

        this.client.sendResponse(new NewNavigatorSavedSearchesComposer(
                this.client.getUser().getUserInfo().getSavedSearches()));
    }
}

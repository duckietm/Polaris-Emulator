package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.habbohotel.navigation.NavigatorSavedSearch;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;

public class DeleteSavedSearchEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int searchId = this.packet.readInt();

        NavigatorSavedSearch search = null;
        for (NavigatorSavedSearch savedSearch : this.client.getHabbo().getHabboInfo().getSavedSearches()) {
            if (savedSearch.getId() == searchId) {
                search = savedSearch;
                break;
            }
        }

        if (search == null) return;

        this.client.getHabbo().getHabboInfo().deleteSavedSearch(search);

        this.client.sendResponse(new NewNavigatorSavedSearchesComposer(this.client.getHabbo().getHabboInfo().getSavedSearches()));
    }
}

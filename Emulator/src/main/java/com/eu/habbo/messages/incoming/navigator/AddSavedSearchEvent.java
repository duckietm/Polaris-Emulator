package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.habbohotel.navigation.NavigatorSavedSearch;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorSavedSearchesComposer;

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

        if (this.client.getHabbo().getHabboInfo().getSavedSearches().size() >= MAX_SAVED_SEARCHES) {
            this.client.sendResponse(new NewNavigatorSavedSearchesComposer(this.client.getHabbo().getHabboInfo().getSavedSearches()));
            return;
        }

        this.client.getHabbo().getHabboInfo().addSavedSearch(new NavigatorSavedSearch(searchCode, filter));

        this.client.sendResponse(new NewNavigatorSavedSearchesComposer(this.client.getHabbo().getHabboInfo().getSavedSearches()));
    }
}

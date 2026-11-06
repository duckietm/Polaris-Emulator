package polaris.messages.outgoing.navigator;

import java.util.List;
import polaris.hotel.navigation.NavigatorSavedSearch;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewNavigatorSavedSearchesComposer extends MessageComposer {
    private final List<NavigatorSavedSearch> searches;

    public NewNavigatorSavedSearchesComposer(List<NavigatorSavedSearch> searches) {
        this.searches = searches;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorSavedSearchesComposer);
        this.response.appendInt(this.searches.size());

        for (NavigatorSavedSearch search : this.searches) {
            this.response.appendInt(search.getId());
            this.response.appendString(search.getSearchCode());
            this.response.appendString(search.getFilter() == null ? "" : search.getFilter());
            this.response.appendString("");
        }

        return this.response;
    }

    public List<NavigatorSavedSearch> getSearches() {
        return searches;
    }
}

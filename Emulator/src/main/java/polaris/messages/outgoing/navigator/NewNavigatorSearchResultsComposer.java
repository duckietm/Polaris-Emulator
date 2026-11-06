package polaris.messages.outgoing.navigator;

import java.util.List;
import polaris.hotel.navigation.SearchResultList;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class NewNavigatorSearchResultsComposer extends MessageComposer {
    private final String searchCode;
    private final String searchQuery;
    private final List<SearchResultList> resultList;

    public NewNavigatorSearchResultsComposer(String searchCode, String searchQuery, List<SearchResultList> resultList) {
        this.searchCode = searchCode;
        this.searchQuery = searchQuery;
        this.resultList = resultList;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NewNavigatorSearchResultsComposer);
        this.response.appendString(this.searchCode);
        this.response.appendString(this.searchQuery);

        this.response.appendInt(this.resultList.size()); // Count

        for (SearchResultList item : this.resultList) {
            item.serialize(this.response);
        }

        return this.response;
    }

    public String getSearchCode() {
        return searchCode;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public List<SearchResultList> getResultList() {
        return resultList;
    }
}

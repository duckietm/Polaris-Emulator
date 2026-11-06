package polaris.hotel.navigation;

public class NavigatorSavedSearch {
    private String searchCode;
    private String filter;
    private int id;

    public NavigatorSavedSearch(String searchCode, String filter) {
        this.searchCode = searchCode;
        this.filter = filter;
    }

    public NavigatorSavedSearch(String searchCode, String filter, int id) {
        this.searchCode = searchCode;
        this.filter = filter;
        this.id = id;
    }

    public String getSearchCode() {
        return searchCode;
    }

    public String getFilter() {
        return filter;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}

package polaris.hotel.navigation;

import java.lang.reflect.Method;

public class NavigatorFilterField {
    public final String key;
    public final Method field;
    public final String databaseQuery;
    public final NavigatorFilterComparator comparator;

    public NavigatorFilterField(String key, Method field, String databaseQuery, NavigatorFilterComparator comparator) {
        this.key = key;
        this.field = field;
        this.databaseQuery = databaseQuery;
        this.comparator = comparator;
    }
}

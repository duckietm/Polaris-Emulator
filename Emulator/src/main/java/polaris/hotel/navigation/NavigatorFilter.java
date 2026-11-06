package polaris.hotel.navigation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.Strings;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public abstract class NavigatorFilter {
    public final String viewName;

    public NavigatorFilter(String viewName) {
        this.viewName = viewName;
    }

    public void filter(Method method, Object value, List<SearchResultList> collection) {
        if (method == null) {
            return;
        }

        if (value instanceof String) {
            if (((String) value).isEmpty()) {
                return;
            }
        }

        for (SearchResultList result : collection) {
            if (!result.filter) {
                continue;
            }

            this.filterRooms(method, value, result.rooms);
        }
    }

    public void filterRooms(Method method, Object value, List<Room> result) {
        if (method == null) {
            return;
        }

        if (value instanceof String) {
            if (((String) value).isEmpty()) {
                return;
            }
        }

        List<Room> toRemove = new ArrayList<>();
        try {
            method.setAccessible(true);

            for (Room room : result) {
                Object o = method.invoke(room);
                if (o.getClass() == value.getClass()) {
                    if (o instanceof String) {
                        NavigatorFilterComparator comparator = Emulator.getGameEnvironment()
                                .getNavigatorManager()
                                .comperatorForField(method);

                        if (comparator != null) {
                            if (!this.applies(comparator, (String) o, (String) value)) {
                                toRemove.add(room);
                            }
                        } else {
                            toRemove.add(room);
                        }
                    } else if (o instanceof String[]) {
                        for (String s : (String[]) o) {
                            NavigatorFilterComparator comparator = Emulator.getGameEnvironment()
                                    .getNavigatorManager()
                                    .comperatorForField(method);

                            if (comparator != null) {
                                if (!this.applies(comparator, s, (String) value)) {
                                    toRemove.add(room);
                                }
                            }
                        }
                    } else {
                        if (o != value) {
                            toRemove.add(room);
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        result.removeAll(toRemove);
        toRemove.clear();
    }

    public abstract List<SearchResultList> getResult(User user);

    public List<SearchResultList> getResult(
            User user, NavigatorFilterField filterField, String value, int roomCategory) {
        return this.getResult(user);
    }

    private boolean applies(NavigatorFilterComparator comparator, String o, String value) {
        switch (comparator) {
            case CONTAINS:
                if (Strings.CI.contains(o, value)) {
                    return true;
                }
                break;

            case EQUALS:
                if (o.equals(value)) {
                    return true;
                }
                break;

            case EQUALS_IGNORE_CASE:
                if (o.equalsIgnoreCase(value)) {
                    return true;
                }
        }

        return false;
    }
}

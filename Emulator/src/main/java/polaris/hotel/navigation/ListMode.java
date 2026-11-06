package polaris.hotel.navigation;

public enum ListMode {
    LIST(0),
    THUMBNAILS(1),
    FORCED_THUNBNAILS(2);

    public final int type;

    ListMode(int type) {
        this.type = type;
    }

    public static ListMode fromType(int type) {
        for (ListMode m : ListMode.values()) {
            if (m.type == type) {
                return m;
            }
        }

        return LIST;
    }
}

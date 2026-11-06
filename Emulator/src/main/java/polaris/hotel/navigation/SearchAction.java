package polaris.hotel.navigation;

public enum SearchAction {
    NONE(0),
    MORE(1),
    BACK(2);

    public final int type;

    SearchAction(int type) {
        this.type = type;
    }
}

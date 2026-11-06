package polaris.hotel.modtool;

public enum ModToolIssueChatlogType {
    NORMAL(0),
    CHAT(1),
    IM(2),
    FORUM_THREAD(3),
    FORUM_COMMENT(4),
    SELFIE(5),
    PHOTO(6);

    private int type;

    ModToolIssueChatlogType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }
}

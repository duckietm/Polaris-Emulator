package polaris.hotel.modtool;

public enum ModToolBanType {
    ACCOUNT("account"),
    MACHINE("machine"),
    SUPER("super"),
    IP("ip"),
    UNKNOWN("???");

    private final String type;

    ModToolBanType(String type) {
        this.type = type;
    }

    public static ModToolBanType fromString(String type) {
        for (ModToolBanType t : ModToolBanType.values()) {
            if (t.type.equalsIgnoreCase(type)) {
                return t;
            }
        }

        return UNKNOWN;
    }

    public String getType() {
        return this.type;
    }
}

package polaris.hotel.guilds;

public enum SettingsState {
    EVERYONE(0),
    MEMBERS(1),
    ADMINS(2),
    OWNER(3);

    public final int state;

    SettingsState(int state) {
        this.state = state;
    }

    public static SettingsState fromValue(int state) {
        try {
            switch (state) {
                case 0:
                    return EVERYONE;
                case 1:
                    return MEMBERS;
                case 2:
                    return ADMINS;
                case 3:
                    return OWNER;
            }
        } catch (Exception e) {
        }

        return EVERYONE;
    }
}

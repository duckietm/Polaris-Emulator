package polaris.hotel.rooms;

public enum RoomUnitStatus {
    MOVE("mv", true),

    SIT_IN("sit-in"),
    SIT("sit", true),
    SIT_OUT("sit-out"),

    LAY_IN("lay-in"),
    LAY("lay", true),
    LAY_OUT("lay-out"),

    FLAT_CONTROL("flatctrl"),
    SIGN("sign"),
    GESTURE("gst"),
    WAVE("wav"),
    TRADING("trd"),

    DIP("dip"),

    EAT_IN("eat-in"),
    EAT("eat"),
    EAT_OUT("eat-out"),

    BEG("beg", true),

    DEAD_IN("ded-in"),
    DEAD("ded", true),
    DEAD_OUT("ded-out"),

    JUMP_IN("jmp-in"),
    JUMP("jmp", true),
    JUMP_OUT("jmp-out"),

    PLAY_IN("pla-in"),
    PLAY("pla", true),
    PLAY_OUT("pla-out"),

    SPEAK("spk"),
    CROAK("crk"),
    RELAX("rlx"),
    WINGS("wng", true),
    FLAME("flm"),
    RIP("rip"),
    GROW("grw"),
    GROW_1("grw1"),
    GROW_2("grw2"),
    GROW_3("grw3"),
    GROW_4("grw4"),
    GROW_5("grw5"),
    GROW_6("grw6"),
    GROW_7("grw7"),

    KICK("kck"),
    WAG_TAIL("wag"),
    DANCE("dan"),
    RINGOFFIRE("rng"),
    SWING("swg"),
    HANG("hg"),
    ROLL("rll"),
    FLAT("flt"),
    AMS("ams"),
    SWIM("swm"),
    TURN("trn"),

    SRP("srp"),
    SRP_IN("srp-in"),

    SLEEP_IN("slp-in"),
    SLEEP("slp", true),
    SLEEP_OUT("slp-out");

    public final String key;
    public final boolean removeWhenWalking;

    RoomUnitStatus(String key) {
        this.key = key;
        this.removeWhenWalking = false;
    }

    RoomUnitStatus(String key, boolean removeWhenWalking) {
        this.key = key;
        this.removeWhenWalking = removeWhenWalking;
    }

    public static RoomUnitStatus fromString(String key) {
        for (RoomUnitStatus status : values()) {
            if (status.key.equalsIgnoreCase(key)) {
                return status;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return this.key;
    }
}

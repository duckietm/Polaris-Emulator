package polaris.hotel.pets;

public enum PetTasks {
    NONE(""),
    FREE(""),
    SIT("sit"),
    DOWN("lay"),
    HERE(""),
    BEG("beg"),
    PLAY_DEAD("ded"),
    STAY(""),
    FOLLOW(""),
    STAND("std"),
    JUMP("jmp"),
    SPEAK("spk"),
    PLAY(""),
    SILENT(""),
    NEST(""),
    DRINK(""),
    FOLLOW_LEFT(""),
    FOLLOW_RIGHT(""),
    PLAY_FOOTBALL(""),
    COME_HERE(""),
    BOUNCE(""),
    FLAT(""),
    DANCE(""),
    SPIN(""),
    SWITCH_TV(""),
    MOVE_FORWARD(""),
    TURN_LEFT(""),
    TURN_RIGHT(""),
    RELAX(""),
    CROAK(""),
    DIP(""),
    WAVE(""),
    MAMBO(""),
    HIGH_JUMP(""),
    CHICKEN_DANCE(""),
    TRIPLE_JUMP(""),
    SPREAD_WINGS(""),
    BREATHE_FIRE(""),
    HANG(""),
    TORCH(""),
    SWING(""),
    ROLL(""),
    RING_OF_FIRE(""),
    EAT("eat"),
    WAG_TAIL(""),
    COUNT(""),
    BREED(""),
    RIDE("");

    private final String status;

    PetTasks(String status) {
        this.status = status;
    }

    public String getStatus() {
        return this.status;
    }
}

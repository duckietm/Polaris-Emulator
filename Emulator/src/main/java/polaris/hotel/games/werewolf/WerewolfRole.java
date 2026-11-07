package polaris.hotel.games.werewolf;

import java.util.ArrayList;
import java.util.List;

/**
 * The roles of the village, the classic ones first. Each has its camp: the village, the werewolves, or alone (it wins
 * on its own). The werewolf roles wake up with the pack from the first night.
 */
public enum WerewolfRole {
    WEREWOLF(Camp.WOLVES),
    VILLAGER(Camp.VILLAGE),
    SEER(Camp.VILLAGE),
    WITCH(Camp.VILLAGE),
    HUNTER(Camp.VILLAGE),
    CUPID(Camp.VILLAGE),
    GUARD(Camp.VILLAGE),
    LITTLE_GIRL(Camp.VILLAGE),
    ELDER(Camp.VILLAGE),
    IDIOT(Camp.VILLAGE),
    SCAPEGOAT(Camp.VILLAGE),
    RAVEN(Camp.VILLAGE),
    FOX(Camp.VILLAGE),
    BEAR_TAMER(Camp.VILLAGE),
    KNIGHT(Camp.VILLAGE),
    SISTER(Camp.VILLAGE),
    WILD_CHILD(Camp.VILLAGE),
    WOLF_DOG(Camp.VILLAGE),
    BIG_BAD_WOLF(Camp.WOLVES),
    ACCURSED_FATHER(Camp.WOLVES),
    WHITE_WOLF(Camp.ALONE),
    PIPER(Camp.ALONE),
    ANGEL(Camp.ALONE);

    public enum Camp {
        VILLAGE,
        WOLVES,
        ALONE
    }

    private final Camp camp;

    WerewolfRole(Camp camp) {
        this.camp = camp;
    }

    public Camp camp() {
        return this.camp;
    }

    /** Whether the role wakes up with the pack from the first night. */
    public boolean wolf() {
        return this == WEREWOLF || this == BIG_BAD_WOLF || this == ACCURSED_FATHER || this == WHITE_WOLF;
    }

    public String code() {
        return this.name().toLowerCase();
    }

    public static WerewolfRole fromCode(String code) {
        for (WerewolfRole role : values()) {
            if (role.code().equals(code)) return role;
        }

        return null;
    }

    /** The roles a host can put in the village (the werewolves and villagers fill the rest). */
    public static List<WerewolfRole> specials() {
        List<WerewolfRole> roles = new ArrayList<>();

        for (WerewolfRole role : values()) {
            if (role != WEREWOLF && role != VILLAGER) roles.add(role);
        }

        return roles;
    }
}

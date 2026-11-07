package polaris.hotel.games.werewolf;

/** The roles of the village. Every role but the werewolf plays for the village. */
public enum WerewolfRole {
    WEREWOLF,
    VILLAGER,
    SEER,
    WITCH,
    HUNTER,
    CUPID,
    GUARD;

    public boolean wolf() {
        return this == WEREWOLF;
    }

    public String code() {
        return this.name().toLowerCase();
    }
}

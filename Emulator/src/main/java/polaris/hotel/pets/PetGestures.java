package polaris.hotel.pets;

public enum PetGestures {
    THIRSTY("thr"),
    TIRED("trd"),
    PLAYFULL("plf"),
    HUNGRY("hng"),
    SAD("sad"),
    HAPPY("sml"),
    QUESTION("que"),
    LVLUP("exp"),
    LOVE("lov"),
    WARNING("und"),
    ENERGY("nrg");

    private final String key;

    PetGestures(String key) {
        this.key = key;
    }

    public String getKey() {
        return this.key;
    }
}

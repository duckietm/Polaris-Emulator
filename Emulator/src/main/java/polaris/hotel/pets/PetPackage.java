package polaris.hotel.pets;

import java.util.Map;
import polaris.hotel.items.Item;

/**
 * A furni that holds a pet to be named: the furni data describes it as "[pet type] [nest furni] ..." in its custom
 * params (e.g. "1 newbie_nest", "12 nest_dragon"). Naming the pet replaces the package with the nest and puts the
 * pet on it. A hotel whose item definition carries no custom params gets the official packages by name.
 */
public record PetPackage(int petType, String nestItemName) {
    /** The official packages, for an items_base row without custom params. */
    private static final Map<String, PetPackage> KNOWN = Map.ofEntries(
            Map.entry("newbie_present", new PetPackage(1, "newbie_nest")),
            Map.entry("newbie_present_2014", new PetPackage(1, "newbie_nest")),
            Map.entry("newbie_cat_package", new PetPackage(1, "newbie_nest")),
            Map.entry("newbie_crocodile_package", new PetPackage(2, "waterbowl*2")),
            Map.entry("nest_with_egg", new PetPackage(8, "nest_chick")),
            Map.entry("newbie_bronze_cave_dragon_package", new PetPackage(12, "nest_dragon")),
            Map.entry("nest_with_statue", new PetPackage(14, "nest_monkey")));

    /** The package a pet of this type is handed out in, when one of the official packages holds that type. */
    private static final Map<Integer, String> PACKAGE_OF_TYPE = Map.of(
            1, "newbie_present",
            2, "newbie_crocodile_package",
            8, "nest_with_egg",
            12, "newbie_bronze_cave_dragon_package",
            14, "nest_with_statue");

    /** The package this furni is, or null when it is none. */
    public static PetPackage of(Item item) {
        if (item == null) return null;

        PetPackage described = fromCustomParams(item.getCustomParams());
        if (described != null) return described;

        return item.getName() == null ? null : KNOWN.get(item.getName().toLowerCase(java.util.Locale.ROOT));
    }

    /** Reads "[pet type] [nest furni] ..."; null when the text is anything else. */
    static PetPackage fromCustomParams(String customParams) {
        if (customParams == null) return null;

        String[] parts = customParams.trim().split("\\s+");
        if (parts.length < 2) return null;

        try {
            int petType = Integer.parseInt(parts[0]);
            // The second word is a furni name: a number there is another kind of furni (a saddle is "15 4 9 0 77").
            if (petType < 0 || !parts[1].matches("[A-Za-z_][A-Za-z0-9_*]*")) return null;

            return new PetPackage(petType, parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The name of the official package furni for a pet type, or null when none holds that type. */
    public static String packageNameFor(int petType) {
        return PACKAGE_OF_TYPE.get(petType);
    }

    /**
     * The pet figure a package carries in its state ("[type] [race] [colour]"), when it is one of this package's pet
     * type; null for a package that holds any pet of its type.
     */
    public String figureIn(String extradata) {
        if (extradata == null) return null;

        String[] parts = extradata.trim().split("\\s+");
        if (parts.length != 3) return null;

        try {
            if (Integer.parseInt(parts[0]) != this.petType || Integer.parseInt(parts[1]) < 0) return null;
        } catch (NumberFormatException e) {
            return null;
        }

        return parts[2].matches("[0-9A-Fa-f]{6}") ? this.petType + " " + parts[1] + " " + parts[2].toUpperCase() : null;
    }
}

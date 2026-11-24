package polaris.hotel.pets;

/**
 * The body palettes of a horse and what the horse products change. A horse's race is its body palette: every coat
 * colour has four palettes in a row, one per breed (the marking), so a body dye moves the horse to the same breed of
 * the new colour instead of always to its first breed.
 *
 * <p>The palettes, as the horse figure lists them: colours 1 to 12 are palettes 2 to 49, colours 13 to 17 are 61 to
 * 80, colour 18 is 86 to 89; the ids between them are hair palettes. Palette 0 is the undyed default.
 */
public final class HorseCoat {
    /** The breeds of one coat colour. */
    public static final int BREEDS = 4;

    private HorseCoat() {}

    /** The first palette (breed 1) of a dye colour; 0 for dye 0, the default coat; -1 for a colour that has none. */
    static int firstPalette(int colour) {
        if (colour == 0) return 0;
        if (colour >= 1 && colour <= 12) return (colour * BREEDS) - 2;
        if (colour >= 13 && colour <= 17) return (colour * BREEDS) + 9;
        if (colour == 18) return 86;
        return -1;
    }

    /** The breed (1 to 4) of a body palette; 1 for the default coat and for anything that is not a coat palette. */
    static int breedOf(int palette) {
        if (palette >= 2 && palette <= 49) return ((palette - 2) % BREEDS) + 1;
        if (palette >= 61 && palette <= 80) return ((palette - 61) % BREEDS) + 1;
        if (palette >= 86 && palette <= 89) return palette - 85;
        return 1;
    }

    /**
     * The palette a horse gets from a body dye of this colour: the colour's palette of the breed the horse has. Dye 0
     * gives the default coat. A colour without palettes keeps the old rule (its number times four, less two), so a
     * hotel's own dye does what it did.
     */
    public static int dyed(int currentPalette, int colour) {
        int first = firstPalette(colour);

        if (first == 0) return 0;
        if (first < 0) return (colour * BREEDS) - 2;

        return first + breedOf(currentPalette) - 1;
    }

    /**
     * The value a hair dye or hair style puts on the horse, from the furni data's custom params "15 2,3 [value],[value]"
     * (pet type, the hair and tail layers, the palette or part for each); null when the text is anything else, and the
     * furni's name decides.
     */
    public static Integer hairValue(String customParams) {
        if (customParams == null) return null;

        String[] parts = customParams.trim().split("\\s+");
        if (parts.length < 3 || !"15".equals(parts[0]) || !parts[1].contains(",")) return null;

        try {
            return Integer.parseInt(parts[2].split(",")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

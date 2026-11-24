package polaris.hotel.pets;

import polaris.hotel.items.Item;

/**
 * What a saddle furni puts on a horse: the figure part drawn on the saddle layer, its palette, and the effect the
 * rider gets. The furni data describes a saddle as "15 4 [part] [palette] [effect]" (pet type, layer, part, palette,
 * effect); a hotel whose item definition does not carry those custom params gets the values of the two known saddles
 * by name, and any other saddle the classic one.
 */
public record HorseSaddle(int partId, int paletteId, int effectId) {
    /** The layer of a horse figure the saddle is drawn on. */
    public static final int LAYER = 4;

    /** The classic saddle ("horse_saddle1"), also what a horse saddled before saddles were told apart wears. */
    public static final HorseSaddle CLASSIC = new HorseSaddle(9, 0, 77);

    private static final HorseSaddle SECOND = new HorseSaddle(10, 0, 103);

    public static HorseSaddle of(Item item) {
        if (item == null) return CLASSIC;

        HorseSaddle described = fromCustomParams(item.getCustomParams());
        if (described != null) return described;

        return "horse_saddle2".equalsIgnoreCase(item.getName()) ? SECOND : CLASSIC;
    }

    /** Reads "15 4 [part] [palette] [effect]"; null when the text is anything else. */
    static HorseSaddle fromCustomParams(String customParams) {
        if (customParams == null) return null;

        String[] parts = customParams.trim().split("\\s+");
        if (parts.length != 5) return null;

        try {
            if (Integer.parseInt(parts[1]) != LAYER) return null;

            int partId = Integer.parseInt(parts[2]);
            int paletteId = Integer.parseInt(parts[3]);
            int effectId = Integer.parseInt(parts[4]);

            return partId > 0 && paletteId >= 0 && effectId > 0 ? new HorseSaddle(partId, paletteId, effectId) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

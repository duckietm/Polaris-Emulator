package polaris.hotel.catalog;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * The box a Furni-Matic exchange gives. The classic one is "ecotron_box"; the official client's "Mystery Box" is
 * "matic_box", which a hotel chooses with the setting {@link #SETTING}. Boxes of either kind keep opening whichever
 * one is given today.
 */
public final class RecyclerBox {
    public static final String CLASSIC = "ecotron_box";
    public static final String MYSTERY = "matic_box";

    /** emulator_settings key naming the box to give: "ecotron_box" (default) or "matic_box". */
    public static final String SETTING = "hotel.ecotron.box";

    private static final DateTimeFormatter MYSTERY_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private RecyclerBox() {}

    /** The box the setting asks for; anything but the Mystery Box is the classic one. */
    public static String chosen(String configured) {
        return configured != null && MYSTERY.equalsIgnoreCase(configured.trim()) ? MYSTERY : CLASSIC;
    }

    /** Whether a furni of this name is a recycler box, so opening it gives its prize. */
    public static boolean isBox(String itemName) {
        return CLASSIC.equals(itemName) || MYSTERY.equals(itemName);
    }

    /**
     * The day written on a new box: the Mystery Box carries it as yyyy-MM-dd, the classic box as d-M-yyyy as it always
     * did.
     */
    public static String date(String boxName, LocalDate day) {
        if (MYSTERY.equals(boxName)) return MYSTERY_DATE.format(day);

        return day.getDayOfMonth() + "-" + day.getMonthValue() + "-" + day.getYear();
    }
}

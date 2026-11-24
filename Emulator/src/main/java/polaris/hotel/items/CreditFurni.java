package polaris.hotel.items;

import polaris.Emulator;

/**
 * The hotel's rule for CFC_ credit furni. Habbo names every CFC_ furni a "Non-redeemable item": it stays furni in
 * the exchange window, the redeem command and a trade. A hotel that wants that sets {@link #SETTING} to 0; without
 * the setting they are redeemed like CF_ furni, as before. Wired chest payments count their value either way.
 */
public final class CreditFurni {
    public static final String SETTING = "hotel.furni.cfc.redeemable";

    private CreditFurni() {}

    /** Whether the hotel redeems CFC_ furni; true unless the setting switches it off. */
    public static boolean cfcRedeemable() {
        return Emulator.getConfig() == null || Emulator.getConfig().getBoolean(SETTING, true);
    }

    /** Whether a furni of this name is credit furni that can be redeemed: CF_ always, CFC_ where the hotel allows. */
    public static boolean isRedeemableCreditName(String name) {
        return isRedeemableCreditName(name, cfcRedeemable());
    }

    static boolean isRedeemableCreditName(String name, boolean cfcRedeemable) {
        if (name == null) return false;

        return name.startsWith("CF_") || (cfcRedeemable && name.startsWith("CFC_"));
    }
}

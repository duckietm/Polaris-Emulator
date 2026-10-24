package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GotwPointsScheduler extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GotwPointsScheduler.class);

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static String GOTW_POINTS_NAME;
    public static double HC_MODIFIER;

    private volatile boolean warnedMissingCurrency;

    public GotwPointsScheduler() { // TODO MOVE TO A PLUGIN. IS NOT PART OF OFFICIAL HABBO.

        super(Emulator.getConfig().getInt("hotel.auto.gotwpoints.interval"));
        this.reloadConfig();
    }

    public void reloadConfig() {
        if (Emulator.getConfig().getBoolean("hotel.auto.gotwpoints.enabled")) {
            IGNORE_HOTEL_VIEW = Emulator.getConfig().getBoolean("hotel.auto.gotwpoints.ignore.hotelview");
            IGNORE_IDLED = Emulator.getConfig().getBoolean("hotel.auto.gotwpoints.ignore.idled");
            HC_MODIFIER = Emulator.getConfig().getDouble("hotel.auto.gotwpoints.hc_modifier", 1.0);
            GOTW_POINTS_NAME = Emulator.getConfig().getValue("hotel.auto.gotwpoints.name");

            this.restart();
        } else {
            this.setDisposed(true);
        }
    }

    @Override
    protected void tick() {

        Habbo habbo;
        for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .entrySet()) {
            habbo = map.getValue();

            try {
                if (habbo != null) {
                    if (habbo.getHabboInfo().getCurrentRoom() == null && IGNORE_HOTEL_VIEW) continue;

                    if (habbo.getRoomUnit().isIdle() && IGNORE_IDLED) continue;

                    int type = Emulator.getConfig().getInt("seasonal.currency." + GOTW_POINTS_NAME, -1);
                    if (type == -1) {
                        // A name listed in seasonal.currency.names with no
                        // seasonal.currency.<name> behind it used to pay into currency
                        // type -1, which is no currency at all. Say so once instead.
                        this.warnMissingCurrencyOnce();
                        return;
                    }

                    habbo.givePoints(type, (int) (habbo.getHabboInfo().getRank().getGotwTimerAmount()
                            * (habbo.getHabboStats().hasActiveClub() ? HC_MODIFIER : 1.0)));
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    private void warnMissingCurrencyOnce() {
        if (this.warnedMissingCurrency) {
            return;
        }

        this.warnedMissingCurrency = true;
        LOGGER.warn(
                "Game of the week points are enabled for '{}', but seasonal.currency.{} is not configured: "
                        + "nothing is paid until it is set.",
                GOTW_POINTS_NAME,
                GOTW_POINTS_NAME);
    }
}

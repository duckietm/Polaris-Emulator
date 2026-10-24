package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PointsScheduler extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PointsScheduler.class);

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    public PointsScheduler() {

        super(Emulator.getConfig().getInt("hotel.auto.points.interval"));
        this.reloadConfig();
    }

    public void reloadConfig() {
        if (Emulator.getConfig().getBoolean("hotel.auto.points.enabled")) {
            IGNORE_HOTEL_VIEW = Emulator.getConfig().getBoolean("hotel.auto.points.ignore.hotelview");
            IGNORE_IDLED = Emulator.getConfig().getBoolean("hotel.auto.points.ignore.idled");
            HC_MODIFIER = Emulator.getConfig().getDouble("hotel.auto.points.hc_modifier", 1.0);
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

                    // habbo.givePoints(POINTS);
                    habbo.givePoints((int) (habbo.getHabboInfo().getRank().getDiamondsTimerAmount()
                            * (habbo.getHabboStats().hasActiveClub() ? HC_MODIFIER : 1.0)));
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }
}

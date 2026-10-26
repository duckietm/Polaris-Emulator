package com.eu.habbo.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreditsScheduler extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreditsScheduler.class);

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    public CreditsScheduler() {

        super(Emulator.getConfig().getInt("hotel.auto.credits.interval"));
        this.reloadConfig();
    }

    public void reloadConfig() {
        if (Emulator.getConfig().getBoolean("hotel.auto.credits.enabled")) {
            IGNORE_HOTEL_VIEW = Emulator.getConfig().getBoolean("hotel.auto.credits.ignore.hotelview");
            IGNORE_IDLED = Emulator.getConfig().getBoolean("hotel.auto.credits.ignore.idled");
            HC_MODIFIER = Emulator.getConfig().getDouble("hotel.auto.credits.hc_modifier", 1.0);

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

                    habbo.giveCredits((int) (habbo.getHabboInfo().getRank().getCreditsTimerAmount()
                            * (habbo.getHabboStats().hasActiveClub() ? HC_MODIFIER : 1.0)));
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }
}

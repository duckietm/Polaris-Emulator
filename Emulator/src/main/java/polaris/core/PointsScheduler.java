package polaris.core;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;

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

        User user;
        for (Map.Entry<Integer, User> map :
                Emulator.getGameEnvironment().getUserManager().getOnlineUsers().entrySet()) {
            user = map.getValue();

            try {
                if (user != null) {
                    if (user.getUserInfo().getCurrentRoom() == null && IGNORE_HOTEL_VIEW) continue;

                    if (user.getRoomUnit().isIdle() && IGNORE_IDLED) continue;

                    // user.givePoints(POINTS);
                    user.givePoints((int) (user.getUserInfo().getRank().getDiamondsTimerAmount()
                            * (user.getUserStats().hasActiveClub() ? HC_MODIFIER : 1.0)));
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }
}

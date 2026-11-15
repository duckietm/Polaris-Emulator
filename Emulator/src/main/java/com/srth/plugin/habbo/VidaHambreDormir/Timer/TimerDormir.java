package com.srth.plugin.habbo.VidaHambreDormir.Timer;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class TimerDormir extends Scheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimerDormir.class);

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    public TimerDormir() {
        super(RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.rooms.dormir.interval"));
        this.reloadConfig();
    }

    public void reloadConfig() {
        if (RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("rp.rooms.dormir.enable")) {
            if (this.disposed) {
                this.disposed = false;
                this.run();
            }
        } else {
            this.disposed = true;
        }
    }

    @Override
    public void run() {
        super.run();

        if (Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().size() == 0) {
            return;
        }

        Habbo habbo;
        for (Map.Entry<Integer, Habbo> map : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos()
                .entrySet()) {

            habbo = map.getValue();
            final Room room = habbo.getHabboInfo().getCurrentRoom();

            if (habbo != null) {

                if (room != null) {

                    if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                            .getInt("global_room.category.roleplay.id"))
                        continue;

                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        if (habboRp.getConstructorSalas() == true) {
                            continue;
                        }

                        if (habboRp.getShooterHabbo().getPvpActive() == 1) {
                            continue;
                        }

                        if (habboRp.getVidaHambreDormir().getDormirActive() == true) {
                            continue;
                        }

                        if (habboRp.getVidaHambreDormir().camillaActive) {
                            continue;
                        }

                        int decrement = Math.max(0,
                                RoleplayWebsocketsRoomsInfo.getConfig()
                                        .getInt("rp.rooms.dormir.decrement.amount"));
                        if (habboRp.getHabboTutorial().getCompleted() == true) {
                            habboRp.getVidaHambreDormir().restarDormir(decrement);
                        }

                        if (habboRp.getVidaHambreDormir().getDormir() < 100) {

                            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                    "/RoleplayWebsocketsRoomsInfo/dormir/suenos/dormir2.png",
                                    "global_alert.dormir.avise", habbo.getClient());

                            habboRp.getVidaHambreDormir().comprobateDormir();
                        }

                        if (habboRp.getVidaHambreDormir().getDormir() - decrement <= 5) {
                            habboRp.getVidaHambreDormir().forceHambre(60);
                        }

                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }
            }

        }
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }
}

package com.srth.plugin.Horario.Timer;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

public class TimerCash extends Scheduler {

    public static boolean IGNORE_HOTEL_VIEW;
    public static boolean IGNORE_IDLED;
    public static double HC_MODIFIER;

    public TimerCash() {
        super(RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_timer_give.cash.interval"));
        this.reloadConfig();
    }

    public void reloadConfig() {
        if (RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("global_timer_give.cash.active")) {
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

        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            try {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                if (habboRp == null) {
                    continue;
                }
                Room roomCurrent = habbo.getHabboInfo().getCurrentRoom();
                if (roomCurrent == null) {
                    continue;
                }
                if (roomCurrent.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {
                    continue;
                }

                int type_points = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");
                int amount = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_timer_give.cash.amount");

                habbo.givePoints(type_points, amount);
                habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/cash/0.png",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_timer_give.cash.success")
                                .replace("%AMOUNT%", amount + "").replace("%TYPE%",
                                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol")));

                // Mostrar alerta arriba
                THashMap<String, String> keys = new THashMap<>();
                keys.put("display", "BUBBLE");
                keys.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cash/0.png");
                keys.put("message",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_give_points_cash")
                                .replace("%AMOUNT%", amount + "").replace("%TYPE%",
                                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol")));
                habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));

            } catch (Exception e) {
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

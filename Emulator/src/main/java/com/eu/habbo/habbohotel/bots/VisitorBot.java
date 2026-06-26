package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolRoomVisit;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.users.Habbo;
import gnu.trove.set.hash.THashSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class VisitorBot extends Bot {
    private static SimpleDateFormat DATE_FORMAT;
    private boolean showedLog = false;
    private THashSet<ModToolRoomVisit> visits = new THashSet<>(3);

    public VisitorBot(ResultSet set) throws SQLException {
        super(set);
    }

    public VisitorBot(Bot bot) {
        super(bot);
    }

    public static void initialise() {
        DATE_FORMAT = new SimpleDateFormat(Emulator.getConfig().getValue("bots.visitor.dateformat"));
    }

    @Override
    public void onUserSay(final RoomChatMessage message) {
        if (!this.showedLog) {
            if (message.getMessage().equalsIgnoreCase(Emulator.getTexts().getValue("generic.yes"))) {
                this.showedLog = true;

                String visitMessage = Emulator.getTexts().getValue("bots.visitor.list").replace("%count%", this.visits.size() + "");

                var list = new StringBuilder();
                for (ModToolRoomVisit visit : this.visits) {
                    list.append("\r");
                    list.append(visit.roomName).append(" ");
                    list.append(Emulator.getTexts().getValue("generic.time.at")).append(" ");
                    list.append(DATE_FORMAT.format(new Date((visit.timestamp * 1000L))));
                }

                visitMessage = visitMessage.replace("%list%", list.toString());

                this.talk(visitMessage);

                this.visits.clear();
            }
        }
    }

    public void onUserEnter(Habbo habbo) {
        if (!this.showedLog) {
            if (habbo.getHabboInfo().getCurrentRoom() != null) {
                this.visits = Emulator.getGameEnvironment().getModToolManager().getVisitsForRoom(habbo.getHabboInfo().getCurrentRoom(), 10, true, habbo.getHabboInfo().getLastOnline(), Emulator.getIntUnixTimestamp(), habbo.getHabboInfo().getCurrentRoom().getOwnerName());

                if (this.visits.isEmpty()) {
                    this.talk(Emulator.getTexts().getValue("bots.visitor.no_visits"));
                } else {
                    this.talk(Emulator.getTexts().getValue("bots.visitor.visits").replace("%count%", this.visits.size() + "").replace("%positive%", Emulator.getTexts().getValue("generic.yes")));
                }
            }
        }
    }

}

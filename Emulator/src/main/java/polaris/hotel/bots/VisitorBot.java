package polaris.hotel.bots;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.modtool.ModToolRoomVisit;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.users.User;

public class VisitorBot extends Bot {
    private static DateTimeFormatter DATE_FORMAT;
    private boolean showedLog = false;
    private Set<ModToolRoomVisit> visits = new HashSet<>(3);

    public VisitorBot(ResultSet set) throws SQLException {
        super(set);
    }

    public VisitorBot(Bot bot) {
        super(bot);
    }

    public static void initialise() {
        initialise(Emulator.getConfig().getValue("bots.visitor.dateformat"));
    }

    static void initialise(String pattern) {
        DATE_FORMAT = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
    }

    @Override
    public void onUserSay(final RoomChatMessage message) {
        if (!this.showedLog) {
            if (message.getMessage().equalsIgnoreCase(Emulator.getTexts().getValue("generic.yes"))) {
                this.showedLog = true;

                String visitMessage =
                        Emulator.getTexts().getValue("bots.visitor.list").replace("%count%", this.visits.size() + "");

                StringBuilder list = new StringBuilder();
                for (ModToolRoomVisit visit : this.visits) {
                    list.append("\r");
                    list.append(visit.roomName).append(" ");
                    list.append(Emulator.getTexts().getValue("generic.time.at")).append(" ");
                    list.append(formatTimestamp(visit.timestamp));
                }

                visitMessage = visitMessage.replace("%list%", list.toString());

                this.talk(visitMessage);

                this.visits.clear();
            }
        }
    }

    public void onUserEnter(User user) {
        if (!this.showedLog) {
            if (user.getUserInfo().getCurrentRoom() != null) {
                this.visits = Emulator.getGameEnvironment()
                        .getModToolManager()
                        .getVisitsForRoom(
                                user.getUserInfo().getCurrentRoom(),
                                10,
                                true,
                                user.getUserInfo().getLastOnline(),
                                Emulator.getIntUnixTimestamp(),
                                user.getUserInfo().getCurrentRoom().getOwnerName());

                if (this.visits.isEmpty()) {
                    this.talk(Emulator.getTexts().getValue("bots.visitor.no_visits"));
                } else {
                    this.talk(Emulator.getTexts()
                            .getValue("bots.visitor.visits")
                            .replace("%count%", this.visits.size() + "")
                            .replace("%positive%", Emulator.getTexts().getValue("generic.yes")));
                }
            }
        }
    }

    static String formatTimestamp(int timestamp) {
        return DATE_FORMAT.format(Instant.ofEpochSecond(timestamp));
    }
}

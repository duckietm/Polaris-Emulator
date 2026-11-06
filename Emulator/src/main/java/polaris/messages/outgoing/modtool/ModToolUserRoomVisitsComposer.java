package polaris.messages.outgoing.modtool;

import java.util.Calendar;
import java.util.Set;
import java.util.TimeZone;
import polaris.hotel.modtool.ModToolRoomVisit;
import polaris.hotel.users.UserInfo;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolUserRoomVisitsComposer extends MessageComposer {
    private final UserInfo userInfo;
    private final Set<ModToolRoomVisit> roomVisits;

    public ModToolUserRoomVisitsComposer(UserInfo userInfo, Set<ModToolRoomVisit> roomVisits) {
        this.userInfo = userInfo;
        this.roomVisits = roomVisits;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolUserRoomVisitsComposer);
        this.response.appendInt(this.userInfo.getId());
        this.response.appendString(this.userInfo.getUsername());
        this.response.appendInt(this.roomVisits.size());

        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        for (ModToolRoomVisit visit : this.roomVisits) {
            cal.setTimeInMillis(visit.timestamp * 1000L);
            this.response.appendInt(visit.roomId);
            this.response.appendString(visit.roomName);
            this.response.appendInt(cal.get(Calendar.HOUR));
            this.response.appendInt(cal.get(Calendar.MINUTE));
        }
        return this.response;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public Set<ModToolRoomVisit> getRoomVisits() {
        return roomVisits;
    }
}

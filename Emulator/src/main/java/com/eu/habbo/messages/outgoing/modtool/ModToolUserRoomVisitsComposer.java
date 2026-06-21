package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.habbohotel.modtool.ModToolRoomVisit;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Calendar;
import java.util.Set;
import java.util.TimeZone;

public class ModToolUserRoomVisitsComposer extends MessageComposer {
    private final HabboInfo habboInfo;
    private final Set<ModToolRoomVisit> roomVisits;

    public ModToolUserRoomVisitsComposer(HabboInfo habboInfo, Set<ModToolRoomVisit> roomVisits) {
        this.habboInfo = habboInfo;
        this.roomVisits = roomVisits;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolUserRoomVisitsComposer);
        this.response.appendInt(this.habboInfo.getId());
        this.response.appendString(this.habboInfo.getUsername());
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

    public HabboInfo getHabboInfo() {
        return habboInfo;
    }

    public Set<ModToolRoomVisit> getRoomVisits() {
        return roomVisits;
    }
}

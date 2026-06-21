package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RoomUserStatusComposer extends MessageComposer {
    private Collection<Habbo> habbos;
    private Collection<RoomUnit> roomUnits;
    private double overrideZ = -1;

    public RoomUserStatusComposer(RoomUnit roomUnit) {
        this.roomUnits = Collections.singletonList(roomUnit);
    }

    public RoomUserStatusComposer(RoomUnit roomUnit, double overrideZ) {
        this(roomUnit);
        this.overrideZ = overrideZ;
    }

    public RoomUserStatusComposer(Collection<RoomUnit> roomUnits, boolean value) {
        this.roomUnits = roomUnits;
    }

    public RoomUserStatusComposer(Collection<Habbo> habbos) {
        this.habbos = habbos;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserStatusComposer);
        if (this.roomUnits != null) {
            List<RoomUnit> roomUnits = new ArrayList<>();

            for (RoomUnit roomUnit : this.roomUnits) {
                if (roomUnit == null
                        || WiredMoveCarryHelper.shouldSuppressStatusComposer(roomUnit)
                        || WiredUserMovementHelper.shouldSuppressStatusComposer(roomUnit)) {
                    continue;
                }

                roomUnits.add(roomUnit);
            }

            this.response.appendInt(roomUnits.size());
            for (RoomUnit roomUnit : roomUnits) {
                this.response.appendInt(roomUnit.getId());
                this.response.appendInt(roomUnit.getPreviousLocation().x);
                this.response.appendInt(roomUnit.getPreviousLocation().y);
                this.response.appendString((this.overrideZ != -1 ? this.overrideZ : roomUnit.getPreviousLocationZ()) + "");


                this.response.appendInt(roomUnit.getHeadRotation().getValue());
                this.response.appendInt(roomUnit.getBodyRotation().getValue());

                StringBuilder status = new StringBuilder("/");
                for (Map.Entry<RoomUnitStatus, String> entry : roomUnit.getStatusMap().entrySet()) {
                    status.append(entry.getKey()).append(" ").append(entry.getValue()).append("/");
                }

                this.response.appendString(status.toString());
                roomUnit.setPreviousLocation(roomUnit.getCurrentLocation());
            }
        } else {
            synchronized (this.habbos) {
                List<Habbo> habbos = new ArrayList<>();

                for (Habbo habbo : this.habbos) {
                    if (habbo == null
                            || habbo.getRoomUnit() == null
                            || WiredMoveCarryHelper.shouldSuppressStatusComposer(habbo.getRoomUnit())
                            || WiredUserMovementHelper.shouldSuppressStatusComposer(habbo.getRoomUnit())) {
                        continue;
                    }

                    habbos.add(habbo);
                }

                this.response.appendInt(habbos.size());
                for (Habbo habbo : habbos) {
                    this.response.appendInt(habbo.getRoomUnit().getId());
                    this.response.appendInt(habbo.getRoomUnit().getPreviousLocation().x);
                    this.response.appendInt(habbo.getRoomUnit().getPreviousLocation().y);
                    this.response.appendString(habbo.getRoomUnit().getPreviousLocationZ() + "");


                    this.response.appendInt(habbo.getRoomUnit().getHeadRotation().getValue());
                    this.response.appendInt(habbo.getRoomUnit().getBodyRotation().getValue());

                    StringBuilder status = new StringBuilder("/");

                    for (Map.Entry<RoomUnitStatus, String> entry : habbo.getRoomUnit().getStatusMap().entrySet()) {
                        status.append(entry.getKey()).append(" ").append(entry.getValue()).append("/");
                    }
                    this.response.appendString(status.toString());
                    habbo.getRoomUnit().setPreviousLocation(habbo.getRoomUnit().getCurrentLocation());
                }
            }
        }
        return this.response;
    }

    public Collection<Habbo> getHabbos() {
        return habbos;
    }

    public Collection<RoomUnit> getRoomUnits() {
        return roomUnits;
    }

    public double getOverrideZ() {
        return overrideZ;
    }
}

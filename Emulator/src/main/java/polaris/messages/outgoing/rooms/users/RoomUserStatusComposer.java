package polaris.messages.outgoing.rooms.users;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredMoveCarryHelper;
import polaris.hotel.wired.core.WiredUserMovementHelper;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserStatusComposer extends MessageComposer {
    private Collection<User> users;
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

    public RoomUserStatusComposer(Collection<User> users) {
        this.users = users;
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
                this.response.appendString(
                        (this.overrideZ != -1 ? this.overrideZ : roomUnit.getPreviousLocationZ()) + "");

                this.response.appendInt(roomUnit.getHeadRotation().getValue());
                this.response.appendInt(roomUnit.getBodyRotation().getValue());

                StringBuilder status = new StringBuilder("/");
                for (Map.Entry<RoomUnitStatus, String> entry :
                        roomUnit.getStatusMap().entrySet()) {
                    status.append(entry.getKey())
                            .append(" ")
                            .append(entry.getValue())
                            .append("/");
                }

                this.response.appendString(status.toString());
                roomUnit.setPreviousLocation(roomUnit.getCurrentLocation());
            }
        } else {
            synchronized (this.users) {
                List<User> users = new ArrayList<>();

                for (User user : this.users) {
                    if (user == null
                            || user.getRoomUnit() == null
                            || WiredMoveCarryHelper.shouldSuppressStatusComposer(user.getRoomUnit())
                            || WiredUserMovementHelper.shouldSuppressStatusComposer(user.getRoomUnit())) {
                        continue;
                    }

                    users.add(user);
                }

                this.response.appendInt(users.size());
                for (User user : users) {
                    this.response.appendInt(user.getRoomUnit().getId());
                    this.response.appendInt(user.getRoomUnit().getPreviousLocation().x);
                    this.response.appendInt(user.getRoomUnit().getPreviousLocation().y);
                    this.response.appendString(user.getRoomUnit().getPreviousLocationZ() + "");

                    this.response.appendInt(user.getRoomUnit().getHeadRotation().getValue());
                    this.response.appendInt(user.getRoomUnit().getBodyRotation().getValue());

                    StringBuilder status = new StringBuilder("/");

                    for (Map.Entry<RoomUnitStatus, String> entry :
                            user.getRoomUnit().getStatusMap().entrySet()) {
                        status.append(entry.getKey())
                                .append(" ")
                                .append(entry.getValue())
                                .append("/");
                    }
                    this.response.appendString(status.toString());
                    user.getRoomUnit().setPreviousLocation(user.getRoomUnit().getCurrentLocation());
                }
            }
        }
        return this.response;
    }

    public Collection<User> getUsers() {
        return users;
    }

    public Collection<RoomUnit> getRoomUnits() {
        return roomUnits;
    }

    public double getOverrideZ() {
        return overrideZ;
    }
}

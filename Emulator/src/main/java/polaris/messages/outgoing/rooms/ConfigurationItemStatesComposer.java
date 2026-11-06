package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChooserDisableSupport;
import polaris.hotel.rooms.RoomConfInvisSupport;
import polaris.hotel.rooms.RoomHanditemBlockSupport;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR 13 {@code ConfigurationItemStates} (official id 1508, parser
 * {@code class_3013}): four room flags the client forwards to the room engine
 * in {@code class_1902.onConfigurationItemStates}.
 *
 * <p>The flags are mapped onto the state this emulator really owns:
 *
 * <ul>
 *   <li>{@code isHanditemControlBlocked} - the room handitem-block controller
 *       ({@link RoomHanditemBlockSupport}),
 *   <li>{@code chooserDisabled} - a switched-on {@code :chooser} disabler
 *       ({@link RoomChooserDisableSupport}); the client then keeps the user
 *       chooser closed for visitors below rights,
 *   <li>{@code freeFurniMovementsEnabled} - the viewer's {@code acc_moverotate}
 *       permission, which is what lets them move furni freely here,
 *   <li>{@code invisibleFurni} - an active {@code conf_invis} controller
 *       ({@link RoomConfInvisSupport}).
 * </ul>
 *
 * <p>{@code freeFurniMovementsEnabled} is per viewer, so this is sent per
 * client and never broadcast to the room.
 */
public class ConfigurationItemStatesComposer extends MessageComposer {

    private final boolean handitemControlBlocked;
    private final boolean chooserDisabled;
    private final boolean freeFurniMovementsEnabled;
    private final boolean invisibleFurni;

    public ConfigurationItemStatesComposer(Room room, User user) {
        this.handitemControlBlocked = RoomHanditemBlockSupport.isHanditemBlocked(room);
        this.invisibleFurni = RoomConfInvisSupport.hasActiveController(room);
        this.chooserDisabled = RoomChooserDisableSupport.isChooserDisabled(room);
        this.freeFurniMovementsEnabled = RoomPlayTestSupport.hasFreeFurniMovementsMode(user);
    }

    public ConfigurationItemStatesComposer(
            boolean handitemControlBlocked,
            boolean chooserDisabled,
            boolean freeFurniMovementsEnabled,
            boolean invisibleFurni) {
        this.handitemControlBlocked = handitemControlBlocked;
        this.chooserDisabled = chooserDisabled;
        this.freeFurniMovementsEnabled = freeFurniMovementsEnabled;
        this.invisibleFurni = invisibleFurni;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ConfigurationItemStatesComposer);

        this.response.appendBoolean(this.handitemControlBlocked);
        this.response.appendBoolean(this.chooserDisabled);
        this.response.appendBoolean(this.freeFurniMovementsEnabled);
        this.response.appendBoolean(this.invisibleFurni);

        return this.response;
    }

    public boolean isHanditemControlBlocked() {
        return this.handitemControlBlocked;
    }

    public boolean isChooserDisabled() {
        return this.chooserDisabled;
    }

    public boolean isFreeFurniMovementsEnabled() {
        return this.freeFurniMovementsEnabled;
    }

    public boolean isInvisibleFurni() {
        return this.invisibleFurni;
    }
}

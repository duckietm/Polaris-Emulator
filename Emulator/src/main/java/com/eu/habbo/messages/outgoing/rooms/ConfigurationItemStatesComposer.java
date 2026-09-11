package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomConfInvisSupport;
import com.eu.habbo.habbohotel.rooms.RoomHanditemBlockSupport;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
 *   <li>{@code chooserDisabled} - whether the viewer may not open the furni /
 *       user choosers, i.e. has no room rights and no {@code acc_anyroomowner},
 *   <li>{@code freeFurniMovementsEnabled} - the viewer's {@code acc_moverotate}
 *       permission, which is what lets them move furni freely here,
 *   <li>{@code invisibleFurni} - an active {@code conf_invis} controller
 *       ({@link RoomConfInvisSupport}).
 * </ul>
 *
 * <p>Two of the flags are per viewer, so this is sent per client and never
 * broadcast to the room.
 */
public class ConfigurationItemStatesComposer extends MessageComposer {

    private final boolean handitemControlBlocked;
    private final boolean chooserDisabled;
    private final boolean freeFurniMovementsEnabled;
    private final boolean invisibleFurni;

    public ConfigurationItemStatesComposer(Room room, Habbo habbo) {
        this.handitemControlBlocked = RoomHanditemBlockSupport.isHanditemBlocked(room);
        this.invisibleFurni = RoomConfInvisSupport.hasActiveController(room);

        boolean mayUseChoosers = room != null
                && habbo != null
                && (room.hasRights(habbo) || habbo.hasPermission(Permission.ACC_ANYROOMOWNER));
        this.chooserDisabled = !mayUseChoosers;
        this.freeFurniMovementsEnabled = habbo != null && habbo.hasPermission(Permission.ACC_MOVEROTATE);
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

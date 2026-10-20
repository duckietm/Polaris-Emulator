package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomFloorItemLimit;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomWallItemLimit;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The room counters of the wired menu monitor, in the order of Habbo's room stats block: floor furni,
 * wall furni and the permanent furni, user and global variables, each with its limit.
 *
 * <p>Floor and wall furni count every item of the room. Floor furni stand against the floor cap, bounded by the
 * room item cap ({@link RoomFloorItemLimit#effectiveMaxFloorItems()}), wall furni against the wall cap
 * ({@link RoomWallItemLimit}). A permanent variable is a furni, user or global
 * variable box set to permanent (or permanent and shared); its limits default to the caps seen on Habbo and
 * are enforced when a box is saved as permanent ({@link #canBecomePermanent}) and when a box that already is permanent
 * is placed ({@link #hasPermanentSlotFor}).
 */
public final class WiredRoomStats {

    /** Habbo's refusal when the room holds as many permanent variables of the kind as it may. */
    public static final String PERMANENT_LIMIT_ERROR = "wiredfurni.error.variables.permanent_limit";

    /** Habbo's permanent variable limits: furni, user and global. */
    public static final int DEFAULT_MAX_PERMANENT_FURNI_VARIABLES = 23;

    public static final int DEFAULT_MAX_PERMANENT_USER_VARIABLES = 93;
    public static final int DEFAULT_MAX_PERMANENT_GLOBAL_VARIABLES = 70;

    private static final AtomicReference<Limits> LIMITS = new AtomicReference<>(new Limits(
            DEFAULT_MAX_PERMANENT_FURNI_VARIABLES,
            DEFAULT_MAX_PERMANENT_USER_VARIABLES,
            DEFAULT_MAX_PERMANENT_GLOBAL_VARIABLES));

    private final int floorCount;
    private final int floorLimit;
    private final int wallCount;
    private final int wallLimit;
    private final int permanentFurniVariables;
    private final int permanentFurniVariablesLimit;
    private final int permanentUserVariables;
    private final int permanentUserVariablesLimit;
    private final int permanentGlobalVariables;
    private final int permanentGlobalVariablesLimit;

    public WiredRoomStats(
            int floorCount,
            int floorLimit,
            int wallCount,
            int wallLimit,
            int permanentFurniVariables,
            int permanentFurniVariablesLimit,
            int permanentUserVariables,
            int permanentUserVariablesLimit,
            int permanentGlobalVariables,
            int permanentGlobalVariablesLimit) {
        this.floorCount = floorCount;
        this.floorLimit = floorLimit;
        this.wallCount = wallCount;
        this.wallLimit = wallLimit;
        this.permanentFurniVariables = permanentFurniVariables;
        this.permanentFurniVariablesLimit = permanentFurniVariablesLimit;
        this.permanentUserVariables = permanentUserVariables;
        this.permanentUserVariablesLimit = permanentUserVariablesLimit;
        this.permanentGlobalVariables = permanentGlobalVariables;
        this.permanentGlobalVariablesLimit = permanentGlobalVariablesLimit;
    }

    /** The stats of a loaded room; {@code null} when there is no room. */
    public static WiredRoomStats of(Room room) {
        if (room == null) {
            return null;
        }

        return from(
                room.getFloorItems().size(),
                RoomFloorItemLimit.effectiveMaxFloorItems(),
                room.getWallItems().size(),
                RoomWallItemLimit.maxWallItems(),
                extrasOf(room));
    }

    /** Counts the permanent variable boxes among the extras and adds the current limits; one cap for both furni lines. */
    public static WiredRoomStats from(
            int floorCount, int wallCount, int itemLimit, Collection<? extends InteractionWiredExtra> extras) {
        return from(floorCount, itemLimit, wallCount, itemLimit, extras);
    }

    /** Counts the permanent variable boxes among the extras and adds the current limits. */
    public static WiredRoomStats from(
            int floorCount,
            int floorLimit,
            int wallCount,
            int wallLimit,
            Collection<? extends InteractionWiredExtra> extras) {
        int furni = 0;
        int user = 0;
        int global = 0;

        if (extras != null) {
            for (InteractionWiredExtra extra : extras) {
                switch (permanentKind(extra)) {
                    case FURNI -> furni++;
                    case USER -> user++;
                    case GLOBAL -> global++;
                    default -> {}
                }
            }
        }

        Limits limits = LIMITS.get();

        return new WiredRoomStats(
                floorCount,
                Math.max(0, floorLimit),
                wallCount,
                Math.max(0, wallLimit),
                furni,
                limits.furni(),
                user,
                limits.user(),
                global,
                limits.global());
    }

    /**
     * Whether {@code box} may be saved as permanent (or permanent and shared). A box that already is permanent keeps
     * its slot, so a room over the cap from before keeps working; a box that becomes permanent needs a free slot
     * among the permanent variables of its kind (furni, user or global). Other boxes are never refused.
     */
    public static boolean canBecomePermanent(Room room, InteractionWiredExtra box) {
        return canBecomePermanent(extrasOf(room), box);
    }

    /** {@link #canBecomePermanent(Room, InteractionWiredExtra)} against the given extras of the room. */
    public static boolean canBecomePermanent(
            Collection<? extends InteractionWiredExtra> extras, InteractionWiredExtra box) {
        VariableKind kind = variableKind(box);

        if (kind == VariableKind.NONE || permanentKind(box) != VariableKind.NONE) {
            return true;
        }

        int permanent = 0;

        if (extras != null) {
            for (InteractionWiredExtra extra : extras) {
                if (extra != box && permanentKind(extra) == kind) {
                    permanent++;
                }
            }
        }

        return permanent < limitOf(kind);
    }

    /**
     * Whether {@code box}, placed into a room holding {@code extras}, finds a free permanent slot of its kind. Unlike
     * {@link #canBecomePermanent} a permanent box is not given its slot here: it is new to the room, so it needs one.
     * A box that is not permanent always fits.
     */
    public static boolean hasPermanentSlotFor(
            Collection<? extends InteractionWiredExtra> extras, InteractionWiredExtra box) {
        VariableKind kind = permanentKind(box);

        if (kind == VariableKind.NONE) {
            return true;
        }

        int permanent = 0;

        if (extras != null) {
            for (InteractionWiredExtra extra : extras) {
                if (extra != box && permanentKind(extra) == kind) {
                    permanent++;
                }
            }
        }

        return permanent < limitOf(kind);
    }

    private static Collection<InteractionWiredExtra> extrasOf(Room room) {
        RoomSpecialTypes specialTypes = room == null ? null : room.getRoomSpecialTypes();

        return specialTypes == null ? List.of() : specialTypes.getExtras();
    }

    private static VariableKind variableKind(InteractionWiredExtra extra) {
        if (extra instanceof WiredExtraFurniVariable) {
            return VariableKind.FURNI;
        }
        if (extra instanceof WiredExtraUserVariable) {
            return VariableKind.USER;
        }
        if (extra instanceof WiredExtraRoomVariable) {
            return VariableKind.GLOBAL;
        }
        return VariableKind.NONE;
    }

    private static VariableKind permanentKind(InteractionWiredExtra extra) {
        boolean permanent = (extra instanceof WiredExtraFurniVariable furni && furni.isPermanentAvailability())
                || (extra instanceof WiredExtraUserVariable user && user.isPermanentAvailability())
                || (extra instanceof WiredExtraRoomVariable global && global.isPermanentAvailability());

        return permanent ? variableKind(extra) : VariableKind.NONE;
    }

    private static int limitOf(VariableKind kind) {
        Limits limits = LIMITS.get();

        return switch (kind) {
            case FURNI -> limits.furni();
            case USER -> limits.user();
            case GLOBAL -> limits.global();
            case NONE -> Integer.MAX_VALUE;
        };
    }

    /** Sets the permanent variable limits, shown on the monitor and enforced on save; a negative limit counts as 0. */
    public static void configureLimits(int furni, int user, int global) {
        LIMITS.set(new Limits(Math.max(0, furni), Math.max(0, user), Math.max(0, global)));
    }

    public static int maxPermanentFurniVariables() {
        return LIMITS.get().furni();
    }

    public static int maxPermanentUserVariables() {
        return LIMITS.get().user();
    }

    public static int maxPermanentGlobalVariables() {
        return LIMITS.get().global();
    }

    public int getFloorCount() {
        return this.floorCount;
    }

    public int getFloorLimit() {
        return this.floorLimit;
    }

    public int getWallCount() {
        return this.wallCount;
    }

    public int getWallLimit() {
        return this.wallLimit;
    }

    public int getPermanentFurniVariables() {
        return this.permanentFurniVariables;
    }

    public int getPermanentFurniVariablesLimit() {
        return this.permanentFurniVariablesLimit;
    }

    public int getPermanentUserVariables() {
        return this.permanentUserVariables;
    }

    public int getPermanentUserVariablesLimit() {
        return this.permanentUserVariablesLimit;
    }

    public int getPermanentGlobalVariables() {
        return this.permanentGlobalVariables;
    }

    public int getPermanentGlobalVariablesLimit() {
        return this.permanentGlobalVariablesLimit;
    }

    private record Limits(int furni, int user, int global) {}

    private enum VariableKind {
        NONE,
        FURNI,
        USER,
        GLOBAL
    }
}

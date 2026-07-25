package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.wired.WiredRoomSettingsDataComposer;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RoomWiredAccessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomWiredAccessService.class);
    private final Room room;
    private final RoomRepository repository;
    private final Object lock = new Object();
    private volatile boolean loaded;
    private int inspectMask = Room.WIRED_ACCESS_DEFAULT_INSPECT_MASK;
    private int modifyMask = Room.WIRED_ACCESS_DEFAULT_MODIFY_MASK;

    RoomWiredAccessService(Room room, RoomRepository repository) {
        this.room = room;
        this.repository = repository;
    }

    int inspectMask() {
        this.ensureLoaded();
        return this.inspectMask;
    }

    int modifyMask() {
        this.ensureLoaded();
        return this.modifyMask;
    }

    boolean canInspect(Habbo habbo) {
        if (habbo == null) {
            return false;
        }
        if (this.canManage(habbo)) {
            return true;
        }

        this.ensureLoaded();
        return this.matches(habbo, this.inspectMask, true);
    }

    boolean canModify(Habbo habbo) {
        if (habbo == null) {
            return false;
        }
        if (this.canManage(habbo)) {
            return true;
        }

        this.ensureLoaded();
        return this.matches(habbo, this.modifyMask, false);
    }

    boolean canManage(Habbo habbo) {
        return habbo != null && this.room.isOwner(habbo);
    }

    boolean save(int requestedInspectMask, int requestedModifyMask) {
        int sanitizedModifyMask = sanitizeModifyMask(requestedModifyMask);
        int sanitizedInspectMask = sanitizeInspectMask(requestedInspectMask) | sanitizedModifyMask;

        synchronized (this.lock) {
            int previousInspectMask = this.inspectMask;
            int previousModifyMask = this.modifyMask;
            this.inspectMask = sanitizedInspectMask;
            this.modifyMask = sanitizedModifyMask;
            this.loaded = true;

            this.room
                    .threading()
                    .run(() -> this.persist(
                            sanitizedInspectMask, sanitizedModifyMask, previousInspectMask, previousModifyMask));
            this.publish();
            return true;
        }
    }

    void publish() {
        for (Habbo habbo : this.room.getCurrentHabbos().values()) {
            if (habbo != null && habbo.getClient() != null) {
                habbo.getClient().sendResponse(new WiredRoomSettingsDataComposer(this.room, habbo));
            }
        }
    }

    private void ensureLoaded() {
        if (this.loaded) {
            return;
        }

        synchronized (this.lock) {
            if (this.loaded) {
                return;
            }

            this.inspectMask = Room.WIRED_ACCESS_DEFAULT_INSPECT_MASK;
            this.modifyMask = Room.WIRED_ACCESS_DEFAULT_MODIFY_MASK;
            try {
                RoomRepository.WiredSettings settings = this.repository.findWiredSettings(this.room.getId());
                this.inspectMask = sanitizeInspectMask(settings.inspectMask());
                this.modifyMask = sanitizeModifyMask(settings.modifyMask());
            } catch (SQLException exception) {
                LOGGER.error("Caught SQL exception while loading wired room settings", exception);
            }
            this.loaded = true;
        }
    }

    private void persist(int savedInspectMask, int savedModifyMask, int previousInspectMask, int previousModifyMask) {
        try {
            this.repository.saveWiredSettings(this.room.getId(), savedInspectMask, savedModifyMask);
        } catch (SQLException exception) {
            synchronized (this.lock) {
                if (this.inspectMask == savedInspectMask && this.modifyMask == savedModifyMask) {
                    this.inspectMask = previousInspectMask;
                    this.modifyMask = previousModifyMask;
                }
            }
            LOGGER.error("Caught SQL exception while saving wired room settings", exception);
        }
    }

    private boolean matches(Habbo habbo, int mask, boolean allowEveryone) {
        if (allowEveryone && hasAccess(mask, Room.WIRED_ACCESS_EVERYONE)) {
            return true;
        }
        if (hasAccess(mask, Room.WIRED_ACCESS_USERS_WITH_RIGHTS) && this.room.hasExplicitRights(habbo)) {
            return true;
        }
        if (hasAccess(mask, Room.WIRED_ACCESS_GROUP_ADMINS) && this.isRoomGroupAdmin(habbo)) {
            return true;
        }
        return hasAccess(mask, Room.WIRED_ACCESS_GROUP_MEMBERS) && this.isRoomGroupMember(habbo);
    }

    private boolean isRoomGroupMember(Habbo habbo) {
        return this.room.getGuildId() > 0 && habbo.getHabboStats().hasGuild(this.room.getGuildId());
    }

    private boolean isRoomGroupAdmin(Habbo habbo) {
        if (!this.isRoomGroupMember(habbo)) {
            return false;
        }

        GuildMember member = this.room
                .gameEnvironment()
                .getGuildManager()
                .getGuildMember(this.room.getGuildId(), habbo.getHabboInfo().getId());
        if (member == null) {
            return false;
        }

        GuildRank rank = member.getRank();
        return rank == GuildRank.OWNER || rank == GuildRank.ADMIN;
    }

    private static boolean hasAccess(int mask, int permissionMask) {
        return (mask & permissionMask) != 0;
    }

    private static int sanitizeInspectMask(int mask) {
        int sanitized = mask & Room.WIRED_ACCESS_ALLOWED_INSPECT_MASK;
        if (hasAccess(sanitized, Room.WIRED_ACCESS_GROUP_MEMBERS)) {
            sanitized |= Room.WIRED_ACCESS_GROUP_ADMINS;
        }
        return sanitized;
    }

    private static int sanitizeModifyMask(int mask) {
        int sanitized = mask & Room.WIRED_ACCESS_ALLOWED_MODIFY_MASK;
        if (hasAccess(sanitized, Room.WIRED_ACCESS_GROUP_MEMBERS)) {
            sanitized |= Room.WIRED_ACCESS_GROUP_ADMINS;
        }
        return sanitized;
    }
}

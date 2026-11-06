package polaris.hotel.rooms;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.users.User;
import polaris.hotel.wired.label.WiredLabelSupport;
import polaris.messages.outgoing.wired.WiredRoomSettingsDataComposer;

final class RoomWiredAccessService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomWiredAccessService.class);
    private final Room room;
    private final RoomRepository repository;
    private final Object lock = new Object();
    /** Group-admin answers by user id, with the time they expire; the lookup is a database query. */
    private final Map<Integer, long[]> groupAdminCache = new ConcurrentHashMap<>();

    private static final long GROUP_ADMIN_CACHE_MS = 10_000L;
    private static final int GROUP_ADMIN_CACHE_MAX = 256;
    private volatile boolean loaded;
    private int inspectMask = Room.WIRED_ACCESS_DEFAULT_INSPECT_MASK;
    private int modifyMask = Room.WIRED_ACCESS_DEFAULT_MODIFY_MASK;
    private String timezone = "";

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

    String timezone() {
        this.ensureLoaded();
        return this.timezone;
    }

    boolean canInspect(User user) {
        if (user == null) {
            return false;
        }
        if (this.canManage(user)) {
            return true;
        }

        this.ensureLoaded();
        return this.matches(user, this.inspectMask, true);
    }

    boolean canModify(User user) {
        if (user == null) {
            return false;
        }
        if (this.canManage(user)) {
            return true;
        }

        this.ensureLoaded();
        return this.matches(user, this.modifyMask, false);
    }

    boolean canManage(User user) {
        return user != null && this.room.isOwner(user);
    }

    boolean save(int requestedInspectMask, int requestedModifyMask) {
        return this.save(requestedInspectMask, requestedModifyMask, this.timezone());
    }

    boolean save(int requestedInspectMask, int requestedModifyMask, String requestedTimezone) {
        int sanitizedModifyMask = sanitizeModifyMask(requestedModifyMask);
        int sanitizedInspectMask = sanitizeInspectMask(requestedInspectMask) | sanitizedModifyMask;
        String sanitizedTimezone = sanitizeTimezone(requestedTimezone);

        synchronized (this.lock) {
            int previousInspectMask = this.inspectMask;
            int previousModifyMask = this.modifyMask;
            String previousTimezone = this.timezone;
            this.inspectMask = sanitizedInspectMask;
            this.modifyMask = sanitizedModifyMask;
            this.timezone = sanitizedTimezone;
            this.loaded = true;

            this.room
                    .threading()
                    .run(() -> this.persist(
                            sanitizedInspectMask,
                            sanitizedModifyMask,
                            sanitizedTimezone,
                            previousInspectMask,
                            previousModifyMask,
                            previousTimezone));
            this.publish();
            return true;
        }
    }

    void publish() {
        for (User user : this.room.getCurrentUsers().values()) {
            if (user != null && user.getClient() != null) {
                user.getClient().sendResponse(new WiredRoomSettingsDataComposer(this.room, user));
                // Who may inspect wired decides who sees the inspectors-only labels.
                WiredLabelSupport.sendAll(this.room, user);
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
            this.timezone = "";
            try {
                RoomRepository.WiredSettings settings = this.repository.findWiredSettings(this.room.getId());
                this.inspectMask = sanitizeInspectMask(settings.inspectMask());
                this.modifyMask = sanitizeModifyMask(settings.modifyMask());
                this.timezone = sanitizeTimezone(settings.timezone());
            } catch (SQLException exception) {
                LOGGER.error("Caught SQL exception while loading wired room settings", exception);
            }
            this.loaded = true;
        }
    }

    private void persist(
            int savedInspectMask,
            int savedModifyMask,
            String savedTimezone,
            int previousInspectMask,
            int previousModifyMask,
            String previousTimezone) {
        try {
            this.repository.saveWiredSettings(this.room.getId(), savedInspectMask, savedModifyMask, savedTimezone);
        } catch (SQLException exception) {
            synchronized (this.lock) {
                if (this.inspectMask == savedInspectMask && this.modifyMask == savedModifyMask) {
                    this.inspectMask = previousInspectMask;
                    this.modifyMask = previousModifyMask;
                    this.timezone = previousTimezone;
                }
            }
            LOGGER.error("Caught SQL exception while saving wired room settings", exception);
        }
    }

    /** The timezone is a free-text picker value; keep it short and never null. */
    private static String sanitizeTimezone(String timezone) {
        if (timezone == null) {
            return "";
        }

        String trimmed = timezone.trim();
        return (trimmed.length() > 64) ? trimmed.substring(0, 64) : trimmed;
    }

    private boolean matches(User user, int mask, boolean allowEveryone) {
        if (allowEveryone && hasAccess(mask, Room.WIRED_ACCESS_EVERYONE)) {
            return true;
        }
        if (hasAccess(mask, Room.WIRED_ACCESS_USERS_WITH_RIGHTS) && this.room.hasExplicitRights(user)) {
            return true;
        }
        // Members first: it is an in-memory check, and every admin is a member.
        if (hasAccess(mask, Room.WIRED_ACCESS_GROUP_MEMBERS) && this.isRoomGroupMember(user)) {
            return true;
        }
        return hasAccess(mask, Room.WIRED_ACCESS_GROUP_ADMINS) && this.isRoomGroupAdmin(user);
    }

    private boolean isRoomGroupMember(User user) {
        return this.room.getGuildId() > 0 && user.getUserStats().hasGuild(this.room.getGuildId());
    }

    private boolean isRoomGroupAdmin(User user) {
        if (!this.isRoomGroupMember(user)) {
            return false;
        }

        int userId = user.getUserInfo().getId();
        long now = System.currentTimeMillis();
        long[] cached = this.groupAdminCache.get(userId);
        if (cached != null && cached[0] > now) {
            return cached[1] == 1L;
        }

        boolean admin = this.lookupRoomGroupAdmin(user);
        if (this.groupAdminCache.size() >= GROUP_ADMIN_CACHE_MAX) {
            this.groupAdminCache.clear();
        }
        this.groupAdminCache.put(userId, new long[] {now + GROUP_ADMIN_CACHE_MS, admin ? 1L : 0L});
        return admin;
    }

    private boolean lookupRoomGroupAdmin(User user) {
        GuildMember member = this.room
                .gameEnvironment()
                .getGuildManager()
                .getGuildMember(this.room.getGuildId(), user.getUserInfo().getId());
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

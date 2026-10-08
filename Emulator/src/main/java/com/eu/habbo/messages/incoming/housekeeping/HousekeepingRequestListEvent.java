package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.habbohotel.modtool.ModToolBanList;
import com.eu.habbo.habbohotel.modtool.ModToolChatLog;
import com.eu.habbo.habbohotel.modtool.ModToolRoomVisit;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.WordFilterWord;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.TemporaryRanks;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingListComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Read-only lists behind the user and room pages, each answered as a table:
 * chat, visits, accounts sharing an IP, past names and sanctions of a user,
 * and chat and visitors of a room. Every list reuses the query the mod tools
 * already run. Hotel-wide lists: bans in force, the word filter, who is
 * online, the rooms with people in them, and the dashboard chart series.
 */
public class HousekeepingRequestListEvent extends HousekeepingHandler {
    static final String USER_CHATLOG = "user.chatlog";
    static final String USER_VISITS = "user.visits";
    static final String USER_CLONES = "user.clones";
    static final String USER_NAMES = "user.names";
    static final String USER_SANCTIONS = "user.sanctions";
    static final String USER_NOTES = "user.notes";
    static final String USER_TEMP_RANK = "user.temp_rank";
    /** The user's login and register IPs in clear; needs acc_hk_view_private and is audited. */
    static final String USER_PRIVATE = "user.private";

    static final String ROOM_CHATLOG = "room.chatlog";
    static final String ROOM_VISITS = "room.visits";
    /** Hotel-wide lists take no target; the client sends 0. */
    static final String HOTEL_BANS = "hotel.bans";

    static final String HOTEL_WORDFILTER = "hotel.wordfilter";
    static final String HOTEL_ONLINE = "hotel.online";
    static final String HOTEL_ROOMS = "hotel.rooms";
    static final String HOTEL_STATS = "hotel.stats";
    static final String HOTEL_PERMISSIONS = "hotel.permissions";
    static final String HOTEL_SECURITY = "hotel.security";

    private static final int CLONE_LIMIT = 50;
    private static final int NAME_LIMIT = 50;
    private static final int VISIT_LIMIT = 100;
    private static final int BAN_LIMIT = 200;

    /** IPs in clear for this request: asked for, and held acc_hk_view_private. */
    private boolean showPrivate;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        String listKey = HousekeepingInputGuard.normalize(this.packet.readString());
        int targetId = this.packet.readInt();
        int reveal = 0;

        if (this.packet.bytesAvailable() > 0) {
            reveal = this.packet.readInt();
        }

        String area = HousekeepingAreas.forList(listKey);

        if (area != null && !this.client.getHabbo().hasPermission(area)) {
            this.client.sendResponse(
                    HousekeepingListComposer.failure(listKey, targetId, HousekeepingAccess.DENIED_MESSAGE));
            return;
        }

        boolean wantsPrivate = reveal == 1 || USER_PRIVATE.equals(listKey);

        if (wantsPrivate && !this.client.getHabbo().hasPermission(HousekeepingPrivacy.PERMISSION)) {
            this.client.sendResponse(
                    HousekeepingListComposer.failure(listKey, targetId, HousekeepingAccess.DENIED_MESSAGE));
            return;
        }

        this.showPrivate = wantsPrivate;

        if (this.showPrivate) {
            HousekeepingAuditLog.log(
                    this.client.getHabbo().getHabboInfo().getId(),
                    this.client.getHabbo().getHabboInfo().getUsername(),
                    "user.view_private",
                    targetId > 0 ? HousekeepingAuditLog.TARGET_USER : HousekeepingAuditLog.TARGET_HOTEL,
                    Math.max(0, targetId),
                    "",
                    "list=" + listKey,
                    this.client.getHabbo().getHabboInfo().getIpLogin());
        }

        if (HOTEL_BANS.equals(listKey)) {
            this.client.sendResponse(bans(listKey, this.showPrivate));
            return;
        }

        if (HOTEL_SECURITY.equals(listKey)) {
            this.client.sendResponse(new HousekeepingListComposer(
                    listKey,
                    0,
                    true,
                    "",
                    List.of("lockdown", "top_rank"),
                    List.of(List.of(
                            HousekeepingLockdown.isLocked() ? "1" : "0",
                            HousekeepingTargetRankGuard.isTopRank(this.client.getHabbo()) ? "1" : "0"))));
            return;
        }

        if (HOTEL_PERMISSIONS.equals(listKey)) {
            this.client.sendResponse(HousekeepingPermissionMatrix.list(listKey, this.client.getHabbo()));
            return;
        }

        if (HOTEL_STATS.equals(listKey)) {
            this.client.sendResponse(new HousekeepingListComposer(
                    listKey, 0, true, "", List.of("series", "bucket", "value"), HousekeepingHotelStats.rows((int)
                            (System.currentTimeMillis() / 1000L))));
            return;
        }

        GameEnvironment environment = Emulator.getGameEnvironment();

        if (HOTEL_WORDFILTER.equals(listKey)) {
            this.client.sendResponse(
                    wordFilter(listKey, environment.getWordFilter().getWords()));
            return;
        }

        if (HOTEL_ONLINE.equals(listKey)) {
            this.client.sendResponse(online(
                    listKey, environment.getHabboManager().getOnlineHabbos().values(), this.showPrivate));
            return;
        }

        if (HOTEL_ROOMS.equals(listKey)) {
            this.client.sendResponse(rooms(listKey, environment.getRoomManager().getActiveRooms()));
            return;
        }

        if (targetId <= 0) {
            this.client.sendResponse(
                    HousekeepingListComposer.failure(listKey, targetId, "housekeeping.error.invalid_input"));
            return;
        }

        HousekeepingListComposer answer =
                switch (listKey) {
                    case USER_CHATLOG ->
                        chatlog(
                                listKey,
                                targetId,
                                environment.getModToolManager().getUserChatlog(targetId),
                                false);
                    case ROOM_CHATLOG ->
                        chatlog(
                                listKey,
                                targetId,
                                environment.getModToolManager().getRoomChatlog(targetId),
                                true);
                    case USER_VISITS ->
                        userVisits(
                                listKey,
                                targetId,
                                environment.getModToolManager().getUserRoomVisits(targetId));
                    case ROOM_VISITS -> this.roomVisits(listKey, targetId, environment);
                    case USER_CLONES -> this.clones(listKey, targetId, environment);
                    case USER_NAMES ->
                        names(listKey, targetId, environment.getHabboManager().getNameChanges(targetId, NAME_LIMIT));
                    case USER_NOTES -> HousekeepingUserNotes.list(listKey, targetId);
                    case USER_PRIVATE -> privateData(listKey, targetId, environment);
                    case USER_TEMP_RANK -> temporaryRank(listKey, targetId, environment.getPermissionsManager());
                    case USER_SANCTIONS ->
                        sanctions(
                                listKey,
                                targetId,
                                environment.getModToolSanctions().getSanctions(targetId));
                    default -> HousekeepingListComposer.failure(listKey, targetId, "housekeeping.error.invalid_input");
                };

        this.client.sendResponse(answer);
    }

    /** A user's login and register IPs in clear (the handler already checked and audited the reveal). */
    private static HousekeepingListComposer privateData(String listKey, int userId, GameEnvironment environment) {
        HabboInfo info = environment.getHabboManager().getHabboInfo(userId);

        if (info == null) return HousekeepingListComposer.failure(listKey, userId, "housekeeping.error.user_not_found");

        return new HousekeepingListComposer(
                listKey,
                userId,
                true,
                "",
                List.of("ip_login", "ip_register"),
                List.of(List.of(
                        info.getIpLogin() == null ? "" : info.getIpLogin(),
                        info.getIpRegister() == null ? "" : info.getIpRegister())));
    }

    /** The user's temporary rank, if one runs: the rank, the one they go back to, when, who and why. */
    private static HousekeepingListComposer temporaryRank(String listKey, int userId, PermissionsManager permissions) {
        List<List<String>> rows = new ArrayList<>();

        TemporaryRanks.find(userId)
                .ifPresent(row -> rows.add(List.of(
                        String.valueOf(row.rankId()),
                        rankName(permissions, row.rankId()),
                        String.valueOf(row.previousRankId()),
                        rankName(permissions, row.previousRankId()),
                        String.valueOf(row.expiresAt()),
                        row.setByName() == null ? "" : row.setByName(),
                        row.reason() == null ? "" : row.reason())));

        return new HousekeepingListComposer(
                listKey,
                userId,
                true,
                "",
                List.of("rank", "rank_name", "previous_rank", "previous_rank_name", "expires", "staff", "reason"),
                rows);
    }

    private static String rankName(PermissionsManager permissions, int rankId) {
        return permissions.rankExists(rankId) ? permissions.getRank(rankId).getName() : "";
    }

    /** Everyone online, by name, with rank, current room, login IP and since when they are on. */
    private static HousekeepingListComposer online(String listKey, Collection<Habbo> habbos, boolean showPrivate) {
        List<List<String>> rows = new ArrayList<>();

        for (Habbo habbo : habbos) {
            if (habbo == null || habbo.getHabboInfo() == null) continue;

            HabboInfo info = habbo.getHabboInfo();
            Room room = info.getCurrentRoom();

            rows.add(List.of(
                    String.valueOf(info.getId()),
                    info.getUsername(),
                    info.getRank() == null ? "" : info.getRank().getName(),
                    room == null ? "" : String.valueOf(room.getId()),
                    room == null ? "" : room.getName(),
                    HousekeepingPrivacy.show(info.getIpLogin(), showPrivate),
                    String.valueOf(info.getLastOnline())));
        }

        rows.sort(Comparator.comparing(row -> row.get(1).toLowerCase()));

        return new HousekeepingListComposer(
                listKey, 0, true, "", List.of("id", "user", "rank", "room_id", "room", "ip", "since"), rows);
    }

    /** Loaded rooms with people in them, fullest first. */
    private static HousekeepingListComposer rooms(String listKey, Collection<Room> rooms) {
        List<Room> occupied = rooms.stream()
                .filter(room -> room != null && room.getUserCount() > 0)
                .sorted(Comparator.comparingInt(Room::getUserCount).reversed())
                .toList();
        List<List<String>> rows = new ArrayList<>();

        for (Room room : occupied) {
            rows.add(List.of(
                    String.valueOf(room.getId()),
                    room.getName(),
                    room.getOwnerName() == null ? "" : room.getOwnerName(),
                    String.valueOf(room.getUserCount()),
                    String.valueOf(room.getUsersMax()),
                    room.getState() == null ? "" : room.getState().name().toLowerCase()));
        }

        return new HousekeepingListComposer(
                listKey, 0, true, "", List.of("room_id", "name", "owner", "users", "max", "state"), rows);
    }

    private static HousekeepingListComposer wordFilter(String listKey, Collection<WordFilterWord> words) {
        List<List<String>> rows = new ArrayList<>();

        for (WordFilterWord word :
                words.stream().sorted(Comparator.comparing(entry -> entry.key)).toList()) {
            rows.add(List.of(
                    word.key,
                    word.replacement,
                    word.hideMessage ? "1" : "0",
                    word.autoReport ? "1" : "0",
                    String.valueOf(word.muteTime),
                    word.prefixOnly ? "1" : "0"));
        }

        return new HousekeepingListComposer(
                listKey, 0, true, "", List.of("word", "replacement", "hide", "report", "mute", "prefix"), rows);
    }

    private static HousekeepingListComposer bans(String listKey, boolean showPrivate) {
        List<List<String>> rows = new ArrayList<>();

        for (ModToolBanList.Entry ban : ModToolBanList.active((int) (System.currentTimeMillis() / 1000L), BAN_LIMIT)) {
            rows.add(List.of(
                    String.valueOf(ban.userId()),
                    ban.username(),
                    ban.type(),
                    ban.reason(),
                    String.valueOf(ban.expires()),
                    ban.staffName(),
                    String.valueOf(ban.timestamp()),
                    HousekeepingPrivacy.show(ban.ip(), showPrivate),
                    String.valueOf(ban.banId())));
        }

        return new HousekeepingListComposer(
                listKey,
                0,
                true,
                "",
                List.of("id", "user", "type", "reason", "expires", "staff", "time", "ip", "ban_id"),
                rows);
    }

    private static HousekeepingListComposer chatlog(
            String listKey, int targetId, List<ModToolChatLog> lines, boolean withUser) {
        List<List<String>> rows = new ArrayList<>();
        List<ModToolChatLog> sorted = new ArrayList<>(lines);
        sorted.sort(
                Comparator.comparingInt((ModToolChatLog line) -> line.timestamp).reversed());

        for (ModToolChatLog line : sorted) {
            rows.add(
                    withUser
                            ? List.of(String.valueOf(line.timestamp), line.username, line.message)
                            : List.of(String.valueOf(line.timestamp), line.message));
        }

        List<String> columns = withUser ? List.of("time", "user", "message") : List.of("time", "message");
        return new HousekeepingListComposer(listKey, targetId, true, "", columns, rows);
    }

    private static HousekeepingListComposer userVisits(
            String listKey, int targetId, Collection<ModToolRoomVisit> visits) {
        List<List<String>> rows = new ArrayList<>();

        for (ModToolRoomVisit visit : newestFirst(visits)) {
            rows.add(List.of(String.valueOf(visit.timestamp), visit.roomName, String.valueOf(visit.roomId)));
        }

        return new HousekeepingListComposer(listKey, targetId, true, "", List.of("enter", "room", "room_id"), rows);
    }

    private HousekeepingListComposer roomVisits(String listKey, int roomId, GameEnvironment environment) {
        Room room = environment.getRoomManager().getRoom(roomId);

        if (room == null) {
            room = environment.getRoomManager().loadRoom(roomId, false);
        }

        if (room == null) {
            return HousekeepingListComposer.failure(listKey, roomId, "housekeeping.error.room_not_found");
        }

        List<List<String>> rows = new ArrayList<>();

        // For a room the visit's name column carries the visitor's username.
        for (ModToolRoomVisit visit :
                newestFirst(environment.getModToolManager().getVisitsForRoom(room, VISIT_LIMIT, false, 0, 0))) {
            rows.add(List.of(String.valueOf(visit.timestamp), visit.roomName));
        }

        return new HousekeepingListComposer(listKey, roomId, true, "", List.of("enter", "user"), rows);
    }

    private HousekeepingListComposer clones(String listKey, int userId, GameEnvironment environment) {
        HabboInfo info = environment.getHabboManager().getHabboInfo(userId);

        if (info == null) {
            return HousekeepingListComposer.failure(listKey, userId, "housekeeping.error.user_not_found");
        }

        List<List<String>> rows = new ArrayList<>();

        for (HabboInfo clone : environment.getHabboManager().getCloneAccounts(info, CLONE_LIMIT)) {
            rows.add(List.of(
                    String.valueOf(clone.getId()),
                    clone.getUsername(),
                    String.valueOf(clone.getLastOnline()),
                    HousekeepingPrivacy.show(clone.getIpLogin(), this.showPrivate)));
        }

        return new HousekeepingListComposer(
                listKey, userId, true, "", List.of("id", "user", "last_online", "ip"), rows);
    }

    private static HousekeepingListComposer names(
            String listKey, int userId, List<Map.Entry<Integer, String>> changes) {
        List<List<String>> rows = new ArrayList<>();

        for (Map.Entry<Integer, String> change : changes) {
            rows.add(List.of(String.valueOf(change.getKey()), change.getValue()));
        }

        return new HousekeepingListComposer(listKey, userId, true, "", List.of("time", "name"), rows);
    }

    private static HousekeepingListComposer sanctions(
            String listKey, int userId, Map<Integer, ArrayList<ModToolSanctionItem>> byUser) {
        List<List<String>> rows = new ArrayList<>();
        List<ModToolSanctionItem> items = byUser == null ? List.of() : byUser.getOrDefault(userId, new ArrayList<>());

        for (ModToolSanctionItem item : items) {
            rows.add(List.of(
                    String.valueOf(item.sanctionLevel),
                    item.reason,
                    String.valueOf(item.probationTimestamp),
                    String.valueOf(item.tradeLockedUntil),
                    item.isMuted ? String.valueOf(item.muteDuration) : "0"));
        }

        return new HousekeepingListComposer(
                listKey,
                userId,
                true,
                "",
                List.of("level", "reason", "probation_until", "trade_locked_until", "mute_minutes"),
                rows);
    }

    private static List<ModToolRoomVisit> newestFirst(Collection<ModToolRoomVisit> visits) {
        List<ModToolRoomVisit> sorted = new ArrayList<>(visits);
        sorted.sort(Comparator.comparingInt((ModToolRoomVisit visit) -> visit.timestamp)
                .reversed());
        return sorted;
    }
}

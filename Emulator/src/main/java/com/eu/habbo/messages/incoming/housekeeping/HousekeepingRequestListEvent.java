package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.modtool.ModToolBanList;
import com.eu.habbo.habbohotel.modtool.ModToolChatLog;
import com.eu.habbo.habbohotel.modtool.ModToolRoomVisit;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.WordFilterWord;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
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
 * already run.
 */
public class HousekeepingRequestListEvent extends MessageHandler {
    static final String USER_CHATLOG = "user.chatlog";
    static final String USER_VISITS = "user.visits";
    static final String USER_CLONES = "user.clones";
    static final String USER_NAMES = "user.names";
    static final String USER_SANCTIONS = "user.sanctions";
    static final String ROOM_CHATLOG = "room.chatlog";
    static final String ROOM_VISITS = "room.visits";
    /** Hotel-wide lists take no target; the client sends 0. */
    static final String HOTEL_BANS = "hotel.bans";

    static final String HOTEL_WORDFILTER = "hotel.wordfilter";

    private static final int CLONE_LIMIT = 50;
    private static final int NAME_LIMIT = 50;
    private static final int VISIT_LIMIT = 100;
    private static final int BAN_LIMIT = 200;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!HousekeepingAccess.check(this.client)) {
            return;
        }

        String listKey = HousekeepingInputGuard.normalize(this.packet.readString());
        int targetId = this.packet.readInt();

        if (HOTEL_BANS.equals(listKey)) {
            this.client.sendResponse(bans(listKey));
            return;
        }

        GameEnvironment environment = Emulator.getGameEnvironment();

        if (HOTEL_WORDFILTER.equals(listKey)) {
            this.client.sendResponse(
                    wordFilter(listKey, environment.getWordFilter().getWords()));
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
                    case USER_SANCTIONS ->
                        sanctions(
                                listKey,
                                targetId,
                                environment.getModToolSanctions().getSanctions(targetId));
                    default -> HousekeepingListComposer.failure(listKey, targetId, "housekeeping.error.invalid_input");
                };

        this.client.sendResponse(answer);
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

    private static HousekeepingListComposer bans(String listKey) {
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
                    ban.ip(),
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
                    clone.getIpLogin()));
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

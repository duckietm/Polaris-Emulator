package polaris.messages.rcon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import polaris.WiredPlatform;
import polaris.database.SqlQueries;
import polaris.hotel.GameEnvironment;
import polaris.hotel.MaintenanceCountdown;
import polaris.hotel.MaintenanceMode;
import polaris.hotel.commands.UpdatePermissionsCommand;
import polaris.hotel.commands.UpdateWordFilterCommand;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolTicketState;
import polaris.hotel.users.User;
import polaris.messages.incoming.housekeeping.HousekeepingDeleteRoomEvent;
import polaris.messages.incoming.housekeeping.HousekeepingGetDashboardEvent;
import polaris.messages.incoming.housekeeping.HousekeepingGrantItemEvent;
import polaris.messages.incoming.housekeeping.HousekeepingKickAllFromRoomEvent;
import polaris.messages.incoming.housekeeping.HousekeepingKickUserEvent;
import polaris.messages.incoming.housekeeping.HousekeepingLockdownEvent;
import polaris.messages.incoming.housekeeping.HousekeepingMaintenanceEvent;
import polaris.messages.incoming.housekeeping.HousekeepingMuteRoomEvent;
import polaris.messages.incoming.housekeeping.HousekeepingOperator;
import polaris.messages.incoming.housekeeping.HousekeepingReloadEvent;
import polaris.messages.incoming.housekeeping.HousekeepingResetUserPasswordEvent;
import polaris.messages.incoming.housekeeping.HousekeepingResult;
import polaris.messages.incoming.housekeeping.HousekeepingRevokeBanEvent;
import polaris.messages.incoming.housekeeping.HousekeepingRoomStateEvent;
import polaris.messages.incoming.housekeeping.HousekeepingSaveRoomSettingsEvent;
import polaris.messages.incoming.housekeeping.HousekeepingSendHotelAlertEvent;
import polaris.messages.incoming.housekeeping.HousekeepingSetHcSubscriptionEvent;
import polaris.messages.incoming.housekeeping.HousekeepingSetPermissionEvent;
import polaris.messages.incoming.housekeeping.HousekeepingSetUserRankEvent;
import polaris.messages.incoming.housekeeping.HousekeepingTradeLockUserEvent;
import polaris.messages.incoming.housekeeping.HousekeepingTransferRoomOwnershipEvent;
import polaris.messages.incoming.housekeeping.HousekeepingUserNoteEvent;
import polaris.messages.incoming.housekeeping.HousekeepingWordFilterEvent;
import polaris.messages.outgoing.modtool.ModToolIssueResponseAlertComposer;

/**
 * RCON {@code housekeeping}: the in-game housekeeping panel's actions for the All-in-1 Manager.
 * {@code data.action} picks the action; the other fields of {@code data} are its arguments (see
 * Gameserver/docs/rcon-housekeeping.md, the contract the Manager is built against).
 *
 * <p>Each action runs the same static core as its panel handler (HousekeepingKickUserEvent.kick,
 * ...), as the system operator {@link HousekeepingOperator#system()}: audited as operator 0
 * "All-in-1 Manager" from "rcon", past the rank-of-operator guards (RCON is root-level), with every
 * input check and side effect of the panel. The support-ticket actions have no panel handler and
 * use the mod tool's ModToolManager methods, as the mod tool's own handlers do.
 *
 * <p>Reply: status 0 and an empty message on success, or a JSON document for the query actions;
 * on failure a non-zero status ({@link #statusFor}) and the handler's error key.
 */
public class Housekeeping extends RCONMessage<Housekeeping.JSON> {
    static final String UNKNOWN_ACTION = "Unknown action. Use one of: ";

    static final String USER_KICK = "user.kick";
    static final String USER_TRADE_LOCK = "user.trade_lock";
    static final String USER_RESET_PASSWORD = "user.reset_password";
    static final String USER_SET_RANK = "user.set_rank";
    static final String USER_GRANT_ITEM = "user.grant_item";
    static final String USER_SET_HC = "user.set_hc";
    static final String USER_NOTE_ADD = "user.note.add";
    static final String USER_NOTE_DELETE = "user.note.delete";
    static final String ROOM_OPEN = "room.open";
    static final String ROOM_CLOSE = "room.close";
    static final String ROOM_SETTINGS = "room.settings";
    static final String ROOM_MUTE = "room.mute";
    static final String ROOM_KICK_ALL = "room.kick_all";
    static final String ROOM_TRANSFER = "room.transfer";
    static final String ROOM_DELETE = "room.delete";
    static final String HOTEL_ALERT = "hotel.alert";
    static final String HOTEL_MAINTENANCE = "hotel.maintenance";
    static final String HOTEL_MAINTENANCE_STATUS = "hotel.maintenance.status";
    static final String HOTEL_LOCKDOWN = "hotel.lockdown";
    static final String HOTEL_LOCKDOWN_STATUS = "hotel.lockdown.status";
    static final String HOTEL_WORDFILTER_ADD = "hotel.wordfilter.add";
    static final String HOTEL_WORDFILTER_REMOVE = "hotel.wordfilter.remove";
    static final String HOTEL_PERMISSION_SET = "hotel.permission.set";
    static final String HOTEL_RELOAD = "hotel.reload";
    static final String HOTEL_DASHBOARD = "hotel.dashboard";
    static final String BAN_REVOKE = "ban.revoke";
    static final String TICKET_LIST = "ticket.list";
    static final String TICKET_CLOSE = "ticket.close";
    static final String TICKET_REPLY = "ticket.reply";

    /** hotel.maintenance's "now": a start with no countdown. */
    static final String MAINTENANCE_NOW = "now";

    static final String RESOLVED = "resolved";
    static final String USELESS = "useless";
    static final String ABUSIVE = "abusive";

    /** The mod tool's own limit on a message to a user (ModToolInputGuard). */
    static final int MAX_TICKET_REPLY_LENGTH = 1000;

    static final String INVALID_INPUT = "housekeeping.error.invalid_input";
    static final String TICKET_NOT_FOUND = "housekeeping.error.ticket_not_found";
    static final String USER_OFFLINE = "housekeeping.error.user_offline";

    @FunctionalInterface
    interface Action {
        HousekeepingResult run(JSON json) throws Exception;
    }

    private final Supplier<GameEnvironment> environment;
    private final Map<String, Action> actions = new LinkedHashMap<>();

    public Housekeeping() {
        this(WiredPlatform::gameEnvironment);
    }

    Housekeeping(Supplier<GameEnvironment> environment) {
        super(JSON.class);
        this.environment = environment;
        this.registerActions();
    }

    private void registerActions() {
        HousekeepingOperator system = HousekeepingOperator.system();

        this.actions.put(USER_KICK, json -> HousekeepingKickUserEvent.kick(system, json.user_id, json.reason, ALLOWED));
        this.actions.put(
                USER_TRADE_LOCK,
                json -> HousekeepingTradeLockUserEvent.tradeLock(
                        system, json.user_id, json.hours, json.reason, ALLOWED));
        this.actions.put(USER_RESET_PASSWORD, json -> {
            HousekeepingResult result = HousekeepingResetUserPasswordEvent.resetPassword(system, json.user_id, ALLOWED);

            if (!result.success()) return result;

            JsonObject reply = new JsonObject();
            reply.addProperty("password", result.message());
            return HousekeepingResult.ok(result.id(), reply.toString());
        });
        this.actions.put(
                USER_SET_RANK,
                json -> HousekeepingSetUserRankEvent.setRank(
                        system, json.user_id, json.rank, json.seconds, rank -> true, rankId -> true));
        this.actions.put(
                USER_GRANT_ITEM,
                json -> HousekeepingGrantItemEvent.grantItem(
                        system, json.user_id, json.item_id, json.quantity, ALLOWED));
        this.actions.put(
                USER_SET_HC,
                json -> HousekeepingSetHcSubscriptionEvent.setHc(system, json.user_id, json.days, ALLOWED));
        this.actions.put(
                USER_NOTE_ADD,
                json -> HousekeepingUserNoteEvent.edit(
                        system, HousekeepingUserNoteEvent.ADD, json.user_id, 0, json.note));
        this.actions.put(
                USER_NOTE_DELETE,
                json -> HousekeepingUserNoteEvent.edit(
                        system, HousekeepingUserNoteEvent.DELETE, json.user_id, json.note_id, null));
        this.actions.put(
                ROOM_OPEN, json -> HousekeepingRoomStateEvent.setState(system, json.room_id, true, room -> true));
        this.actions.put(
                ROOM_CLOSE, json -> HousekeepingRoomStateEvent.setState(system, json.room_id, false, room -> true));
        this.actions.put(
                ROOM_SETTINGS,
                json -> HousekeepingSaveRoomSettingsEvent.save(
                        system,
                        json.room_id,
                        json.name,
                        json.description,
                        json.users_max,
                        json.category_id,
                        json.trade_mode,
                        json.tags,
                        room -> true));
        this.actions.put(
                ROOM_MUTE, json -> HousekeepingMuteRoomEvent.mute(system, json.room_id, json.minutes, room -> true));
        this.actions.put(
                ROOM_KICK_ALL, json -> HousekeepingKickAllFromRoomEvent.kickAll(system, json.room_id, room -> true));
        this.actions.put(
                ROOM_TRANSFER,
                json -> HousekeepingTransferRoomOwnershipEvent.transfer(
                        system, json.room_id, json.owner_id, room -> true));
        this.actions.put(ROOM_DELETE, json -> HousekeepingDeleteRoomEvent.delete(system, json.room_id, room -> true));
        this.actions.put(
                HOTEL_ALERT,
                json -> HousekeepingSendHotelAlertEvent.send(
                        system, json.message, json.recipient == null ? "hotel" : json.recipient));
        this.actions.put(HOTEL_MAINTENANCE, json -> maintenance(system, json));
        this.actions.put(HOTEL_MAINTENANCE_STATUS, json -> HousekeepingResult.ok(0, maintenanceStatus()));
        this.actions.put(HOTEL_LOCKDOWN, json -> {
            if (json.enabled == null) return HousekeepingResult.fail(INVALID_INPUT);

            return HousekeepingLockdownEvent.set(system, json.enabled, ALLOWED);
        });
        this.actions.put(HOTEL_LOCKDOWN_STATUS, json -> {
            JsonObject reply = new JsonObject();
            reply.addProperty("enabled", HousekeepingLockdownEvent.isEnabled());
            return HousekeepingResult.ok(0, reply.toString());
        });
        this.actions.put(
                HOTEL_WORDFILTER_ADD,
                json -> HousekeepingWordFilterEvent.edit(
                        system,
                        HousekeepingWordFilterEvent.ADD,
                        json.word,
                        json.replacement,
                        UpdateWordFilterCommand::reload));
        this.actions.put(
                HOTEL_WORDFILTER_REMOVE,
                json -> HousekeepingWordFilterEvent.edit(
                        system, HousekeepingWordFilterEvent.REMOVE, json.word, "", UpdateWordFilterCommand::reload));
        this.actions.put(HOTEL_PERMISSION_SET, json -> {
            if (json.value == null) return HousekeepingResult.fail(INVALID_INPUT);

            return HousekeepingSetPermissionEvent.set(
                    system,
                    json.permission,
                    json.rank,
                    json.value,
                    (key, value) -> false,
                    () -> UpdatePermissionsCommand.reload(system.id(), system.name()));
        });
        this.actions.put(HOTEL_RELOAD, this::hotelReload);
        this.actions.put(HOTEL_DASHBOARD, json -> HousekeepingResult.ok(0, dashboard()));
        this.actions.put(BAN_REVOKE, json -> HousekeepingRevokeBanEvent.revoke(system, json.ban_id, owner -> true));
        this.actions.put(TICKET_LIST, json -> HousekeepingResult.ok(0, this.ticketList()));
        this.actions.put(TICKET_CLOSE, this::ticketClose);
        this.actions.put(TICKET_REPLY, this::ticketReply);
    }

    /** The rank-of-operator guard the system operator always passes. */
    private static final java.util.function.BooleanSupplier ALLOWED = HousekeepingOperator.ALLOWED;

    /** The action keys, in the order the unknown-action reply lists them. */
    List<String> actionKeys() {
        return List.copyOf(this.actions.keySet());
    }

    @Override
    public void handle(Gson gson, JSON json) {
        Action action = this.actions.get(json.action.trim());

        if (action == null) {
            this.status = STATUS_ERROR;
            this.message = UNKNOWN_ACTION + String.join(", ", this.actions.keySet());
            return;
        }

        HousekeepingResult result;

        try {
            result = action.run(json);
        } catch (Exception e) {
            this.status = SYSTEM_ERROR;
            this.message = "The action failed: " + e.getClass().getSimpleName();
            return;
        }

        this.status = result.success() ? STATUS_OK : statusFor(result.message());
        this.message = result.message();
    }

    /**
     * The RCON status of a failed action: USER_NOT_FOUND for an unknown user, ROOM_NOT_FOUND for an
     * unknown or inactive room, SYSTEM_ERROR for a database or reload failure, STATUS_ERROR otherwise.
     */
    static int statusFor(String error) {
        return switch (error) {
            case "housekeeping.error.user_not_found", "housekeeping.error.new_owner_not_found" -> USER_NOT_FOUND;
            case "housekeeping.error.room_not_found", "housekeeping.error.room_not_active" -> ROOM_NOT_FOUND;
            case "housekeeping.error.db_failed",
                    "housekeeping.error.hash_failed",
                    "housekeeping.error.economy_failed",
                    "housekeeping.error.note_failed",
                    "housekeeping.error.wordfilter_failed",
                    "housekeeping.error.permission_failed",
                    "housekeeping.error.reload_failed" -> SYSTEM_ERROR;
            default -> STATUS_ERROR;
        };
    }

    // ---- hotel ----

    /** start (minutes 0-120), now (start with no countdown), cancel or disable: HousekeepingMaintenanceEvent.apply. */
    static HousekeepingResult maintenance(HousekeepingOperator system, JSON json) throws Exception {
        String mode = json.mode == null ? "" : json.mode.trim();

        if (MAINTENANCE_NOW.equals(mode)) {
            return HousekeepingMaintenanceEvent.apply(system, HousekeepingMaintenanceEvent.START, json.message, 0);
        }

        if (HousekeepingMaintenanceEvent.STATUS.equals(mode)) {
            return HousekeepingResult.fail(INVALID_INPUT);
        }

        return HousekeepingMaintenanceEvent.apply(system, mode, json.message, json.minutes);
    }

    static String maintenanceStatus() {
        JsonObject reply = new JsonObject();
        reply.addProperty("enabled", MaintenanceMode.isEnabled());
        reply.addProperty("min_rank", MaintenanceMode.getMinRank());
        reply.addProperty("message", MaintenanceMode.getMessage());
        reply.addProperty("countdown_ends_at", MaintenanceCountdown.getEndsAt());
        return reply.toString();
    }

    /** One of the panel's reload targets (HousekeepingReloadEvent.TARGETS), reloaded as reloadcontent does. */
    private HousekeepingResult hotelReload(JSON json) throws Exception {
        String target = json.target == null ? "" : json.target.trim();

        if (!HousekeepingReloadEvent.TARGETS.contains(target)) {
            return HousekeepingResult.fail(ReloadContent.unknownTarget(HousekeepingReloadEvent.TARGETS));
        }

        String error = ReloadContent.reload(target, this.environment);
        return error == null ? HousekeepingResult.ok(0) : HousekeepingResult.fail(error);
    }

    static String dashboard() {
        HousekeepingGetDashboardEvent.Stats stats = HousekeepingGetDashboardEvent.stats();
        JsonObject reply = new JsonObject();
        reply.addProperty("online_users", stats.onlineUsers());
        reply.addProperty("total_users", stats.totalUsers());
        reply.addProperty("active_rooms", stats.activeRooms());
        reply.addProperty("total_rooms", stats.totalRooms());
        reply.addProperty("peak_online_today", stats.peakOnlineToday());
        reply.addProperty("peak_online_all_time", stats.peakOnlineAllTime());
        reply.addProperty("pending_tickets", stats.pendingTickets());
        reply.addProperty("server_uptime_seconds", stats.uptimeSeconds());
        reply.addProperty("server_version", stats.version());
        return reply.toString();
    }

    // ---- support tickets (the mod tool's calls for help) ----

    String ticketList() {
        List<ModToolIssue> open = new ArrayList<>();
        Map<Integer, ModToolIssue> tickets =
                this.environment.get().getModToolManager().getTickets();

        synchronized (tickets) {
            for (ModToolIssue issue : tickets.values()) {
                if (issue != null
                        && (issue.state == ModToolTicketState.OPEN || issue.state == ModToolTicketState.PICKED)) {
                    open.add(issue);
                }
            }
        }

        open.sort(Comparator.comparingInt(issue -> issue.id));
        JsonArray reply = new JsonArray();

        for (ModToolIssue issue : open) {
            JsonObject ticket = new JsonObject();
            ticket.addProperty("id", issue.id);
            ticket.addProperty("state", issue.state == ModToolTicketState.PICKED ? "picked" : "open");
            ticket.addProperty("category_id", issue.category);
            ticket.addProperty("reporter_id", issue.senderId);
            ticket.addProperty("reporter_name", text(issue.senderUsername));
            ticket.addProperty("reported_id", issue.reportedId);
            ticket.addProperty("reported_name", text(issue.reportedUsername));
            ticket.addProperty("moderator_id", issue.modId > 0 ? issue.modId : 0);
            ticket.addProperty("moderator_name", issue.state == ModToolTicketState.PICKED ? text(issue.modName) : "");
            ticket.addProperty("message", text(issue.message));
            ticket.addProperty("room_id", issue.roomId);
            ticket.addProperty("room_name", roomName(issue.roomId));
            ticket.addProperty("created_at", issue.timestamp);
            reply.add(ticket);
        }

        return reply.toString();
    }

    /** Closes an open or picked ticket as resolved, useless or abusive, as the mod tool's close does. */
    private HousekeepingResult ticketClose(JSON json) {
        String resolution = json.resolution == null ? "" : json.resolution.trim();

        if (!List.of(RESOLVED, USELESS, ABUSIVE).contains(resolution)) {
            return HousekeepingResult.fail(INVALID_INPUT);
        }

        ModToolIssue issue = this.openTicket(json.ticket_id);

        if (issue == null) {
            return HousekeepingResult.fail(TICKET_NOT_FOUND);
        }

        this.close(issue, resolution);
        audit(TICKET_CLOSE, issue, "ticket=" + issue.id + " resolution=" + resolution);
        return HousekeepingResult.ok(issue.id);
    }

    /**
     * Answers the reporter under their call for help, as ModToolAlertEvent does for the moderator
     * holding it, and closes the ticket as resolved when {@code close} is true. The reporter must be
     * online to receive it.
     */
    private HousekeepingResult ticketReply(JSON json) {
        String message = json.message == null ? "" : json.message.trim();

        if (message.isEmpty() || message.length() > MAX_TICKET_REPLY_LENGTH) {
            return HousekeepingResult.fail(INVALID_INPUT);
        }

        ModToolIssue issue = this.openTicket(json.ticket_id);

        if (issue == null) {
            return HousekeepingResult.fail(TICKET_NOT_FOUND);
        }

        User reporter = this.environment.get().getUserManager().getUser(issue.senderId);

        if (reporter == null || reporter.getClient() == null) {
            return HousekeepingResult.fail(USER_OFFLINE);
        }

        reporter.getClient().sendResponse(new ModToolIssueResponseAlertComposer(message));
        boolean close = Boolean.TRUE.equals(json.close);

        if (close) {
            this.close(issue, RESOLVED);
        }

        audit(TICKET_REPLY, issue, "ticket=" + issue.id + " closed=" + close + " length=" + message.length());
        return HousekeepingResult.ok(issue.id);
    }

    private ModToolIssue openTicket(int ticketId) {
        if (ticketId <= 0) return null;

        ModToolIssue issue = this.environment.get().getModToolManager().getTicket(ticketId);

        return issue != null && issue.state != ModToolTicketState.CLOSED ? issue : null;
    }

    /** The system operator takes the ticket, then closes it with the mod tool's verdict to the reporter. */
    private void close(ModToolIssue issue, String resolution) {
        GameEnvironment environment = this.environment.get();
        ModToolManager modTools = environment.getModToolManager();
        User reporter = environment.getUserManager().getUser(issue.senderId);

        modTools.pickTicket(issue, HousekeepingOperator.SYSTEM_ID, HousekeepingOperator.SYSTEM_NAME);

        switch (resolution) {
            case USELESS -> modTools.closeTicketAsUseless(issue, reporter);
            case ABUSIVE -> modTools.closeTicketAsAbusive(issue, reporter);
            default -> modTools.closeTicketAsHandled(issue, reporter);
        }
    }

    private static void audit(String action, ModToolIssue issue, String detail) {
        HousekeepingAuditLog.log(
                HousekeepingOperator.SYSTEM_ID,
                HousekeepingOperator.SYSTEM_NAME,
                action,
                HousekeepingAuditLog.TARGET_USER,
                issue.senderId,
                text(issue.senderUsername),
                detail,
                HousekeepingOperator.SYSTEM_IP);
    }

    private static String roomName(int roomId) {
        if (roomId <= 0) return "";

        try {
            return SqlQueries.queryOne(
                            "SELECT name FROM rooms WHERE id = ? LIMIT 1", set -> set.getString("name"), roomId)
                    .orElse("");
        } catch (SqlQueries.DataAccessException e) {
            return "";
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /**
     * The request's data: the action and every field any action reads. Integers left out are 0,
     * strings null; the room settings fields are null when left out (the room keeps that value).
     */
    static class JSON {
        @NotBlank(message = "action is required")
        public String action;

        public int user_id;
        public int room_id;
        public int ban_id;
        public int ticket_id;
        public int note_id;
        public int owner_id;
        public int rank;
        public int seconds;
        public int minutes;
        public int hours;
        public int days;
        public int item_id;
        public int quantity;
        public Integer value;

        public String reason;
        public String message;
        public String note;
        public String recipient;
        public String word;
        public String replacement;
        public String permission;
        public String target;
        public String mode;
        public String resolution;

        public Boolean enabled;
        public Boolean close;

        // room.settings
        public String name;
        public String description;
        public Integer users_max;
        public Integer category_id;
        public Integer trade_mode;
        public List<String> tags;
    }
}

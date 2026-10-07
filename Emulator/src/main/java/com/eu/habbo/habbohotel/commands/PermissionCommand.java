package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.PermissionAuditLog;
import com.eu.habbo.habbohotel.permissions.PermissionChange;
import com.eu.habbo.habbohotel.permissions.PermissionExplanation;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.permissions.TemporaryRanks;
import com.eu.habbo.habbohotel.permissions.UserPermissionOverrides;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.users.UserPerksComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Staff tool for permissions:
 * <ul>
 *   <li>:perm check &lt;user&gt; &lt;key&gt; - why a user has a key or not</li>
 *   <li>:perm log [user] - the newest permission audit rows</li>
 *   <li>:perm set &lt;user&gt; &lt;key&gt; &lt;0|1|2&gt; [duration] [reason] - the user's own value, before the rank</li>
 *   <li>:perm unset &lt;user&gt; &lt;key&gt; and :perm list &lt;user&gt;</li>
 * </ul>
 * Only lower ranks can be changed, and a key can only be granted by someone who holds it.
 */
public class PermissionCommand extends Command {
    private static final int LOG_ROWS = 15;
    private static final String USAGE = ":perm check <user> <key> | log [user] | list <user>"
            + " | set <user> <key> <0|1|2> [30m|12h|7d|2w|perm] [reason] | unset <user> <key>"
            + " | rank <user> <rank> <30m|12h|7d|2w> [reason]";

    public PermissionCommand() {
        super("cmd_perm", new String[] {"perm"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        String sub = params.length > 1 ? params[1].toLowerCase() : "";

        switch (sub) {
            case "check" -> {
                if (params.length >= 4) this.check(gameClient, params[2], params[3]);
                else this.usage(gameClient);
            }
            case "log" -> this.log(gameClient, params.length >= 3 ? params[2] : null);
            case "list" -> {
                if (params.length >= 3) this.list(gameClient, params[2]);
                else this.usage(gameClient);
            }
            case "set" -> {
                if (params.length >= 5) this.set(gameClient, params);
                else this.usage(gameClient);
            }
            case "unset" -> {
                if (params.length >= 4) this.unset(gameClient, params[2], params[3]);
                else this.usage(gameClient);
            }
            case "rank" -> {
                if (params.length >= 5) this.temporaryRank(gameClient, params);
                else this.usage(gameClient);
            }
            default -> this.usage(gameClient);
        }

        return true;
    }

    private void usage(GameClient gameClient) {
        gameClient.getHabbo().whisper(USAGE, RoomChatMessageBubbles.ALERT);
    }

    private static HabboInfo findUser(GameClient gameClient, String username) {
        Habbo online = WiredPlatform.gameEnvironment().getHabboManager().getHabbo(username);
        HabboInfo info = online != null ? online.getHabboInfo() : HabboManager.getOfflineHabboInfo(username);

        if (info == null) {
            gameClient.getHabbo().whisper("No user called " + username + ".", RoomChatMessageBubbles.ALERT);
        }

        return info;
    }

    private void check(GameClient gameClient, String username, String key) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        PermissionsManager permissions = environment.getPermissionsManager();
        HabboInfo info = findUser(gameClient, username);

        if (info == null) {
            return;
        }

        Habbo online = environment.getHabboManager().getHabbo(info.getId());
        Rank rank = info.getRank();
        PermissionExplanation.PluginCheck plugin;

        if (rank != null && rank.hasPermission(key, false)) {
            plugin = PermissionExplanation.PluginCheck.NOT_NEEDED;
        } else if (online == null) {
            plugin = PermissionExplanation.PluginCheck.NOT_ASKED_OFFLINE;
        } else {
            plugin = permissions.hasPermission(online, key)
                    ? PermissionExplanation.PluginCheck.GRANTED
                    : PermissionExplanation.PluginCheck.REFUSED;
        }

        List<String> lines = PermissionExplanation.explain(
                info.getUsername(),
                key,
                rank,
                permissions.isDefinedByAnyRank(key),
                plugin,
                permissions.getOverrides().findOverride(info.getId(), key));

        gameClient.getHabbo().alert(String.join("\r", lines));
    }

    private void log(GameClient gameClient, String username) {
        int userId = 0;

        if (username != null) {
            HabboInfo info = findUser(gameClient, username);

            if (info == null) {
                return;
            }

            userId = info.getId();
        }

        List<String> lines = new ArrayList<>();
        lines.add(username == null ? "Permission audit (newest first)" : "Permission audit for " + username);

        for (PermissionAuditLog.Entry entry : PermissionAuditLog.recent(userId, LOG_ROWS)) {
            lines.add(PermissionExplanation.line(entry));
        }

        if (lines.size() == 1) {
            lines.add("Nothing recorded yet.");
        }

        gameClient.getHabbo().alert(String.join("\r", lines));
    }

    private void list(GameClient gameClient, String username) {
        HabboInfo info = findUser(gameClient, username);

        if (info == null) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Own permission values of " + info.getUsername());

        for (UserPermissionOverrides.UserOverride override : WiredPlatform.gameEnvironment()
                .getPermissionsManager()
                .getOverrides()
                .active(info.getId())) {
            lines.add(override.key() + " = " + UserPermissionOverrides.valueOf(override.setting()) + ", "
                    + PermissionExplanation.until(override.expiresAt())
                    + (override.reason() == null || override.reason().isEmpty() ? "" : " (" + override.reason() + ")"));
        }

        if (lines.size() == 1) {
            lines.add("None: the rank decides everything.");
        }

        TemporaryRanks.find(info.getId())
                .ifPresent(row -> lines.add("Temporary rank " + row.rankId() + ", "
                        + PermissionExplanation.until(row.expiresAt()) + ", then back to rank " + row.previousRankId()
                        + (row.reason() == null || row.reason().isEmpty() ? "" : " (" + row.reason() + ")")));

        gameClient.getHabbo().alert(String.join("\r", lines));
    }

    private void set(GameClient gameClient, String[] params) {
        Habbo actor = gameClient.getHabbo();
        PermissionsManager permissions = WiredPlatform.gameEnvironment().getPermissionsManager();
        String key = params[3];
        PermissionSetting setting = parseValue(params[4]);

        if (setting == null) {
            actor.whisper(
                    "The value is 0 (deny), 1 (allow) or 2 (only with room rights).", RoomChatMessageBubbles.ALERT);
            return;
        }

        if (!permissions.isDefinedByAnyRank(key)) {
            actor.whisper("No rank defines " + key + ". Check the key name.", RoomChatMessageBubbles.ALERT);
            return;
        }

        // Nobody hands out a key they do not hold themselves.
        if (setting != PermissionSetting.DISALLOWED && !actor.hasPermission(key)) {
            actor.whisper("You can only grant keys you hold yourself.", RoomChatMessageBubbles.ALERT);
            return;
        }

        int duration = 0;
        int reasonFrom = 5;

        if (params.length >= 6) {
            int parsed = UserPermissionOverrides.parseDuration(params[5]);

            if (parsed >= 0) {
                duration = parsed;
                reasonFrom = 6;
            }
        }

        HabboInfo target = findUser(gameClient, params[2]);

        if (target == null) {
            return;
        }

        if (!CommandTargetGuard.canTarget(actor, target)) {
            actor.whisper("You can only change users below your rank.", RoomChatMessageBubbles.ALERT);
            return;
        }

        String reason = params.length > reasonFrom
                ? String.join(" ", Arrays.copyOfRange(params, reasonFrom, params.length))
                : "";
        int expiresAt = duration > 0 ? WiredPlatform.unixTimestamp() + duration : 0;
        UserPermissionOverrides overrides = permissions.getOverrides();
        UserPermissionOverrides.UserOverride previous = overrides.findOverride(target.getId(), key);

        try {
            UserPermissionOverrides.save(
                    target.getId(),
                    key,
                    setting,
                    expiresAt,
                    reason,
                    actor.getHabboInfo().getId(),
                    actor.getHabboInfo().getUsername());
        } catch (SqlQueries.DataAccessException e) {
            actor.whisper("Could not save it; see the emulator log.", RoomChatMessageBubbles.ALERT);
            return;
        }

        PermissionAuditLog.overrideChanged(
                actor.getHabboInfo().getId(),
                actor.getHabboInfo().getUsername(),
                PermissionAuditLog.OVERRIDE_SET,
                target.getId(),
                key,
                previous == null
                        ? PermissionChange.NONE
                        : String.valueOf(UserPermissionOverrides.valueOf(previous.setting())),
                String.valueOf(UserPermissionOverrides.valueOf(setting)),
                "perm " + (duration > 0 ? params[5] : "permanent"));

        this.refresh(overrides, target.getId());
        actor.whisper(
                target.getUsername() + ": " + key + " = " + UserPermissionOverrides.valueOf(setting) + ", "
                        + PermissionExplanation.until(expiresAt) + ".",
                RoomChatMessageBubbles.ALERT);
    }

    /** :perm rank <user> <rank> <duration> [reason]: the rank until the time is up, then the one before. */
    private void temporaryRank(GameClient gameClient, String[] params) {
        Habbo actor = gameClient.getHabbo();
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        PermissionsManager permissions = environment.getPermissionsManager();
        Rank rank = findRank(permissions, params[3]);

        if (rank == null) {
            actor.whisper("No rank " + params[3] + ".", RoomChatMessageBubbles.ALERT);
            return;
        }

        int duration = UserPermissionOverrides.parseDuration(params[4]);

        if (duration <= 0) {
            actor.whisper(
                    "Give a time like 30m, 12h, 7d or 2w. For a lasting rank use :give_rank.",
                    RoomChatMessageBubbles.ALERT);
            return;
        }

        HabboInfo target = findUser(gameClient, params[2]);

        if (target == null) {
            return;
        }

        if (!CommandTargetGuard.canTarget(actor, target) || !CommandTargetGuard.canAssignRank(actor, rank)) {
            actor.whisper(
                    "You can only give ranks below yours, to users below your rank.", RoomChatMessageBubbles.ALERT);
            return;
        }

        // A second temporary rank still ends on the rank from before the first, so whoever replaces it
        // must be able to give that rank too (else a short one would end someone else's demotion early).
        Optional<TemporaryRanks.Row> running = TemporaryRanks.find(target.getId());
        if (running.isPresent()
                && !CommandTargetGuard.canAssignRank(
                        actor,
                        environment
                                .getPermissionsManager()
                                .getRank(running.get().previousRankId()))) {
            actor.whisper(
                    "A temporary rank set by a higher rank is running; it ends on a rank above yours.",
                    RoomChatMessageBubbles.ALERT);
            return;
        }
        int previousRankId = running.map(TemporaryRanks.Row::previousRankId)
                .orElse(target.getRank() != null ? target.getRank().getId() : 1);
        int expiresAt = WiredPlatform.unixTimestamp() + duration;
        String reason = params.length > 5 ? String.join(" ", Arrays.copyOfRange(params, 5, params.length)) : "";

        try {
            TemporaryRanks.give(
                    target.getId(),
                    rank.getId(),
                    previousRankId,
                    expiresAt,
                    actor.getHabboInfo().getId(),
                    actor.getHabboInfo().getUsername(),
                    reason);
            environment
                    .getHabboManager()
                    .setRank(
                            target.getId(),
                            rank.getId(),
                            actor.getHabboInfo().getId(),
                            actor.getHabboInfo().getUsername(),
                            TemporaryRanks.VIA_PREFIX + " " + params[4]);
        } catch (Exception e) {
            TemporaryRanks.clear(target.getId());
            actor.whisper("Could not give the rank; see the emulator log.", RoomChatMessageBubbles.ALERT);
            return;
        }

        actor.whisper(
                target.getUsername() + " is " + rank.getName() + " " + PermissionExplanation.until(expiresAt)
                        + ", then rank " + previousRankId + " again.",
                RoomChatMessageBubbles.ALERT);
    }

    private static Rank findRank(PermissionsManager permissions, String value) {
        try {
            int id = Integer.parseInt(value);
            return permissions.rankExists(id) ? permissions.getRank(id) : null;
        } catch (NumberFormatException e) {
            return permissions.getRankByName(value);
        }
    }

    private void unset(GameClient gameClient, String username, String key) {
        Habbo actor = gameClient.getHabbo();
        HabboInfo target = findUser(gameClient, username);

        if (target == null) {
            return;
        }

        if (!CommandTargetGuard.canTarget(actor, target)) {
            actor.whisper("You can only change users below your rank.", RoomChatMessageBubbles.ALERT);
            return;
        }

        UserPermissionOverrides overrides =
                WiredPlatform.gameEnvironment().getPermissionsManager().getOverrides();
        UserPermissionOverrides.UserOverride previous = overrides.findOverride(target.getId(), key);
        boolean removed;

        try {
            removed = UserPermissionOverrides.delete(target.getId(), key);
        } catch (SqlQueries.DataAccessException e) {
            actor.whisper("Could not remove it; see the emulator log.", RoomChatMessageBubbles.ALERT);
            return;
        }

        if (!removed) {
            actor.whisper(target.getUsername() + " has no own value for " + key + ".", RoomChatMessageBubbles.ALERT);
            return;
        }

        PermissionAuditLog.overrideChanged(
                actor.getHabboInfo().getId(),
                actor.getHabboInfo().getUsername(),
                PermissionAuditLog.OVERRIDE_REMOVED,
                target.getId(),
                key,
                previous == null
                        ? PermissionChange.NONE
                        : String.valueOf(UserPermissionOverrides.valueOf(previous.setting())),
                PermissionChange.NONE,
                "perm");

        this.refresh(overrides, target.getId());
        actor.whisper(target.getUsername() + ": " + key + " follows the rank again.", RoomChatMessageBubbles.ALERT);
    }

    /** Drops the cached values and, for an online user, sends the new permissions and perks. */
    private void refresh(UserPermissionOverrides overrides, int userId) {
        overrides.invalidate(userId);

        Habbo online = WiredPlatform.gameEnvironment().getHabboManager().getHabbo(userId);

        if (online != null && online.getClient() != null) {
            online.getClient().sendResponse(new UserPermissionsComposer(online));
            online.getClient().sendResponse(new UserPerksComposer(online));
        }
    }

    static PermissionSetting parseValue(String value) {
        return switch (value) {
            case "0" -> PermissionSetting.DISALLOWED;
            case "1" -> PermissionSetting.ALLOWED;
            case "2" -> PermissionSetting.ROOM_OWNER;
            default -> null;
        };
    }
}

package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.plugin.HabboPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends the full per-user permission state to the connected client.
 *
 * Wire layout (each trailing block is guarded by `bytesAvailable` on
 * the client so older Nitro builds keep parsing the prefix and stop):
 *
 *   int     clubLevel
 *   int     rank.level                           // mapped to securityLevel on the client
 *   bool    isAmbassador                         // legacy ACC_AMBASSADOR flag
 *   --- rank metadata (Arcturus ≥ 4.2.10) ---
 *   int     rank.id
 *   string  rank.name                            // permission_ranks.rank_name
 *   string  rank.badge
 *   string  rank.prefix
 *   string  rank.prefixColor
 *   --- resolved permission map (Arcturus ≥ 4.2.10) ---
 *   int     count
 *   loop:   string permission_key + int value   // 1 = ALLOWED, 2 = ROOM_OWNER
 *
 * The map is the union of:
 *   • rank entries with `PermissionSetting != DISALLOWED` — same data
 *     `Rank.hasPermission(key, isRoomOwner)` reads server-side.
 *   • plugin grants — for each key the rank doesn't allow, every
 *     installed `HabboPlugin.hasPermission(habbo, key)` is consulted;
 *     if any plugin grants it, the key lands on the wire with value 1
 *     (plugins don't have a ROOM_OWNER concept).
 *
 * The React-side `useHasPermission(key)` / `useUserPermissions()`
 * consumers read the map directly so UI gates follow the same
 * semantics as `PermissionsManager.hasPermission(habbo, key)`
 * server-side — including plugin-granted permissions, which were
 * invisible to the client before this commit.
 *
 * Two send points:
 *   1. End of `SecureLoginEvent` — client receives the full state once.
 *   2. Inside `HabboManager.setRank` — runtime promote/demote refresh.
 *   3. Inside `UpdatePermissionsCommand` — broadcast after
 *      `:update_permissions` reloads the tables at runtime.
 */
public class UserPermissionsComposer extends MessageComposer {
    private final int clubLevel;

    private final Habbo habbo;

    public UserPermissionsComposer(Habbo habbo) {
        this.clubLevel = habbo.getHabboStats().hasActiveClub() ? 2 : 0;
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserPermissionsComposer);
        this.response.appendInt(this.clubLevel);

        Rank rank = this.habbo.getHabboInfo().getRank();

        this.response.appendInt(rank.getLevel());
        this.response.appendBoolean(this.habbo.hasPermission(Permission.ACC_AMBASSADOR));

        // Rank metadata
        this.response.appendInt(rank.getId());
        this.response.appendString(rank.getName());
        this.response.appendString(rank.getBadge());
        this.response.appendString(rank.getPrefix());
        this.response.appendString(rank.getPrefixColor());

        // Build the resolved permission map. Walk rank.getPermissions()
        // (Rank.permissions has every row from permission_definitions
        // because PermissionsManager.loadPermissionsNormalized() calls
        // rank.setPermission(key, …) for every key, including DISALLOWED
        // ones) and emit the final value per key:
        //   ALLOWED                 → 1
        //   ROOM_OWNER              → 2
        //   DISALLOWED + plugin yes → 1
        //   DISALLOWED + plugin no  → omit
        //
        // LinkedHashMap preserves the alphabetical order that the rank
        // table was populated with, which is helpful for snapshotting
        // and grep'ing wire dumps.
        Map<String, Permission> rankPermissions = rank.getPermissions();
        Map<String, Integer> resolved = new LinkedHashMap<>(rankPermissions.size());

        for (Map.Entry<String, Permission> entry : rankPermissions.entrySet()) {
            String key = entry.getKey();
            Permission rankPerm = entry.getValue();

            if (rankPerm.setting == PermissionSetting.ALLOWED) {
                resolved.put(key, 1);
            } else if (rankPerm.setting == PermissionSetting.ROOM_OWNER) {
                resolved.put(key, 2);
            } else if (this.anyPluginGrants(key)) {
                resolved.put(key, 1);
            }
        }

        // Plugins may also grant CUSTOM keys that aren't in
        // permission_definitions — rare but legal. There's no enumeration
        // API on HabboPlugin to discover them, so they stay invisible
        // here. Document the limitation rather than over-engineer.

        this.response.appendInt(resolved.size());

        for (Map.Entry<String, Integer> entry : resolved.entrySet()) {
            this.response.appendString(entry.getKey());
            this.response.appendInt(entry.getValue());
        }

        return this.response;
    }

    private boolean anyPluginGrants(String key) {
        for (HabboPlugin plugin : Emulator.getPluginManager().getPlugins()) {
            if (plugin.hasPermission(this.habbo, key)) return true;
        }
        return false;
    }

    public int getClubLevel() {
        return clubLevel;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}

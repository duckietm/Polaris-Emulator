package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.commands.UpdatePermissionsCommand;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.PermissionSetting;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import java.util.Optional;

/**
 * Sets one cell of the permission matrix (a permission for a rank) and reloads the permissions
 * through :update_permissions, which re-binds everyone online to the new rank table. Needs that
 * command's permission, and the rank must be one the operator may act on - the same policy as
 * changing a user's rank - so nobody widens their own rank or one above it.
 */
public class HousekeepingSetPermissionEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.PERMISSIONS;
    }

    static final String ACTION_KEY = "hotel.permission.set";
    static final String PERMISSION = "cmd_update_permissions";

    @Override
    public int getRatelimit() {
        return 300;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        String permissionKey = HousekeepingInputGuard.normalize(this.packet.readString());
        int rankId = this.packet.readInt();
        int value = this.packet.readInt();

        if (!this.client.getHabbo().hasPermission(PERMISSION)) {
            this.fail(HousekeepingAccess.DENIED_MESSAGE);
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetRank(this.client.getHabbo(), rankId)) {
            this.fail("housekeeping.error.rank_too_high");
            return;
        }

        try {
            Optional<Integer> maxValue = HousekeepingPermissionMatrix.maxValue(permissionKey);

            if (permissionKey.isEmpty() || maxValue.isEmpty() || !HousekeepingPermissionMatrix.rankExists(rankId)) {
                this.fail("housekeeping.error.invalid_input");
                return;
            }

            if (value < 0 || value > maxValue.get()) {
                this.fail("housekeeping.error.invalid_input");
                return;
            }

            // Below the top rank an operator grants only what their own rank holds: lowering a rank's
            // setting was checked, widening it past the operator's own was not, so an operator could
            // give a lower rank (and an alt moved onto it) what they did not have themselves.
            if (!HousekeepingTargetRankGuard.isTopRank(this.client.getHabbo())
                    && !holds(operatorSetting(this.client.getHabbo(), permissionKey), value)) {
                this.fail(HousekeepingAccess.DENIED_MESSAGE);
                return;
            }

            if (HousekeepingLockoutGuard.permissionChangeLocksOut(permissionKey, rankId, value)) {
                this.fail("housekeeping.error.lockout");
                return;
            }

            // The row exists (checked above); an unchanged value may report 0 rows and is still fine.
            HousekeepingPermissionMatrix.set(permissionKey, rankId, value);
        } catch (SqlQueries.DataAccessException e) {
            this.fail("housekeeping.error.permission_failed");
            return;
        }

        UpdatePermissionsCommand reload = new UpdatePermissionsCommand();
        reload.handle(this.client, new String[] {reload.keys.length > 0 ? reload.keys[0] : "update_permissions"});

        HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_HOTEL,
                rankId,
                "",
                "permission=" + HousekeepingInputGuard.auditValue(permissionKey) + " rank=" + rankId + " value="
                        + value,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, 0, ""));
    }

    /** The operator's own setting for this permission, as a matrix value. */
    static int operatorSetting(Habbo operator, String permissionKey) {
        Permission permission =
                operator.getHabboInfo().getRank().getPermissions().get(permissionKey);
        return permission == null ? 0 : permission.setting.ordinal();
    }

    /**
     * Whether an operator holding {@code own} may grant {@code value}: nothing (0) always, everywhere
     * (1) only when they have it everywhere, and in own rooms only (2) when they have it there or
     * everywhere. The values are {@link PermissionSetting}'s, so 2 is narrower than 1, not wider.
     */
    static boolean holds(int own, int value) {
        return value == PermissionSetting.DISALLOWED.ordinal()
                || own == PermissionSetting.ALLOWED.ordinal()
                || own == value;
    }

    private void fail(String message) {
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, message));
    }
}

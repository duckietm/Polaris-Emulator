package polaris.messages.incoming.housekeeping;

import java.util.Optional;
import java.util.function.BiPredicate;
import polaris.database.SqlQueries;
import polaris.hotel.commands.UpdatePermissionsCommand;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.permissions.Permission;
import polaris.hotel.permissions.PermissionSetting;
import polaris.hotel.users.User;

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

    public static final String ACTION_KEY = "hotel.permission.set";
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

        String requestedKey = this.packet.readString();
        int rankId = this.packet.readInt();
        int requestedValue = this.packet.readInt();

        if (!this.client.getUser().hasPermission(PERMISSION)) {
            this.reply(ACTION_KEY, HousekeepingResult.fail(HousekeepingAccess.DENIED_MESSAGE));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetRank(this.client.getUser(), rankId)) {
            this.reply(ACTION_KEY, HousekeepingResult.fail("housekeeping.error.rank_too_high"));
            return;
        }

        UpdatePermissionsCommand command = new UpdatePermissionsCommand();
        this.reply(
                ACTION_KEY,
                set(
                        this.operator(),
                        requestedKey,
                        rankId,
                        requestedValue,
                        (permissionKey, value) -> !HousekeepingTargetRankGuard.isTopRank(this.client.getUser())
                                && !holds(operatorSetting(this.client.getUser(), permissionKey), value),
                        () -> command.handle(
                                this.client,
                                new String[] {command.keys.length > 0 ? command.keys[0] : "update_permissions"})));
    }

    /**
     * Sets one cell of the permission matrix (0 to the permission's max_value) and runs
     * {@code reload} so everyone online gets the new table. A change that would leave nobody able to
     * reach the panel or edit permissions is refused.
     *
     * @param refuseGrant whether the operator may not grant this value of this permission: below the
     *     top rank an operator grants only what their own rank holds
     */
    public static HousekeepingResult set(
            HousekeepingOperator operator,
            String rawPermissionKey,
            int rankId,
            int value,
            BiPredicate<String, Integer> refuseGrant,
            HousekeepingOperator.Reload reload)
            throws Exception {
        String permissionKey = HousekeepingInputGuard.normalize(rawPermissionKey);

        try {
            Optional<Integer> maxValue = HousekeepingPermissionMatrix.maxValue(permissionKey);

            if (permissionKey.isEmpty() || maxValue.isEmpty() || !HousekeepingPermissionMatrix.rankExists(rankId)) {
                return HousekeepingResult.fail("housekeeping.error.invalid_input");
            }

            if (value < 0 || value > maxValue.get()) {
                return HousekeepingResult.fail("housekeeping.error.invalid_input");
            }

            // Below the top rank an operator grants only what their own rank holds: lowering a rank's
            // setting was checked, widening it past the operator's own was not, so an operator could
            // give a lower rank (and an alt moved onto it) what they did not have themselves.
            if (refuseGrant.test(permissionKey, value)) {
                return HousekeepingResult.fail(HousekeepingAccess.DENIED_MESSAGE);
            }

            if (HousekeepingLockoutGuard.permissionChangeLocksOut(permissionKey, rankId, value)) {
                return HousekeepingResult.fail("housekeeping.error.lockout");
            }

            // The row exists (checked above); an unchanged value may report 0 rows and is still fine.
            HousekeepingPermissionMatrix.set(permissionKey, rankId, value);
        } catch (SqlQueries.DataAccessException e) {
            return HousekeepingResult.fail("housekeeping.error.permission_failed");
        }

        reload.run();

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_HOTEL,
                rankId,
                "",
                "permission=" + HousekeepingInputGuard.auditValue(permissionKey) + " rank=" + rankId + " value="
                        + value,
                operator.ip());
        return HousekeepingResult.ok(0);
    }

    /** The operator's own setting for this permission, as a matrix value. */
    static int operatorSetting(User operator, String permissionKey) {
        Permission permission =
                operator.getUserInfo().getRank().getPermissions().get(permissionKey);
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
}

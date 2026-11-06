package polaris.hotel.items.interactions.wired;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;

/**
 * Who may configure a wired box that hands out value.
 *
 * <p>Six furni grant currency, score or a badge out of nothing: credits, diamonds, duckets,
 * experience, respects and badges. Their amounts are capped per execution by
 * {@link WiredNumericInputGuard}, but the guard bounds one firing, not a room: the execution guard
 * admits a hundred events every ten seconds and each firing pays every user the selector resolves.
 * A repeater wired to a give-credits box therefore mints on the order of ten thousand credits a
 * second, to everyone present.
 *
 * <p>Until now the only thing standing between that and a hotel's economy was the catalogue — which
 * of those furni it sells, and to whom. That is a data boundary: it lives outside the tests, outside
 * code review, and outside anything this repository can guarantee. {@link
 * polaris.hotel.items.interactions.wired.effects.WiredEffectGiveReward} already drew the
 * line in code; this is the same line, drawn once for the rest.
 *
 * <p>The badge box belongs here for a different reason: a staff badge is a badge code like any
 * other, so granting arbitrary codes is a route to impersonation rather than a cosmetic.
 *
 * <p><b>This changes the default.</b> A hotel that deliberately sells these to players can set
 * {@code hotel.wired.reward.require_permission = 0} and keep the old behaviour. The gate sits on
 * saving, not on firing, so furni configured before it keep working either way.
 */
public final class WiredRewardPolicy {
    private static final String REQUIRE_PERMISSION_KEY = "hotel.wired.reward.require_permission";

    private WiredRewardPolicy() {}

    /** Whether {@code gameClient} may save a value-granting wired box. */
    public static boolean canConfigure(GameClient gameClient) {
        if (!requiresPermission()) {
            return true;
        }

        if (gameClient == null) {
            return false;
        }

        User user = gameClient.getUser();
        return user != null && user.hasPermission(Permission.ACC_SUPERWIRED);
    }

    private static boolean requiresPermission() {
        return Emulator.getConfig() == null || Emulator.getConfig().getBoolean(REQUIRE_PERMISSION_KEY, true);
    }
}

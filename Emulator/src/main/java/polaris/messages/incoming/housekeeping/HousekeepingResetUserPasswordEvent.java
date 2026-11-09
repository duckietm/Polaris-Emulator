package polaris.messages.incoming.housekeeping;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;
import polaris.Emulator;
import polaris.networking.gameserver.auth.PasswordHasher;
import polaris.networking.gameserver.auth.RememberJwtService;

/**
 * Reset a user's password to a fresh random 12-character alphanumeric
 * string. Persists a BCrypt hash of the new password into
 * `users.password` (matches what `AuthHttpUtil.checkPassword` /
 * `SessionEndpoints` / `AccountChangeEndpoints` already write and read),
 * clears `auth_ticket` so any active session can't be re-used to bypass
 * the reset, and ships the PLAINTEXT new password back to the operator
 * in the action-result `message` so they can communicate it out-of-band.
 */
public class HousekeepingResetUserPasswordEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.USERS;
    }

    public static final String ACTION_KEY = "user.reset_password";
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final int BCRYPT_COST = 10;

    private static final SecureRandom RNG = new SecureRandom();

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int userId = this.packet.readInt();

        this.reply(
                ACTION_KEY,
                resetPassword(
                        this.operator(),
                        userId,
                        () -> HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)));
    }

    /**
     * Gives the user a fresh random password and ends their sessions. On success the result's message
     * is the new password in plain text, for the operator to pass on.
     *
     * @param mayTarget the operator's rank guard on the target
     */
    public static HousekeepingResult resetPassword(
            HousekeepingOperator operator, int userId, BooleanSupplier mayTarget) {
        if (userId <= 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        if (!mayTarget.getAsBoolean()) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        String plain = randomPassword();
        String hash;

        try {
            hash = PasswordHasher.hash(plain, BCRYPT_COST);
        } catch (IllegalArgumentException e) {
            return HousekeepingResult.fail("housekeeping.error.hash_failed");
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE users SET password = ?, auth_ticket = '', "
                                + "access_token_version = access_token_version + 1 WHERE id = ? LIMIT 1")) {
            statement.setString(1, hash);
            statement.setInt(2, userId);
            int rows = statement.executeUpdate();

            if (rows == 0) {
                return HousekeepingResult.fail("housekeeping.error.user_not_found");
            }
            RememberJwtService.revokeAllForUser(connection, userId);
        } catch (SQLException e) {
            return HousekeepingResult.fail("housekeeping.error.db_failed");
        }

        // Plaintext flows through `message` — the client surfaces it via the
        // status banner so the operator can read it once. SSL is on the
        // operator: the only secure transport for the WS is wss://.
        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(), operator.name(), ACTION_KEY, userId, "password_reset=1", operator.ip());
        return HousekeepingResult.ok(userId, plain);
    }

    private static String randomPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RNG.nextInt(PASSWORD_ALPHABET.length())));
        }

        return sb.toString();
    }
}

package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Reset a user's password to a fresh random 12-character alphanumeric
 * string. Persists a BCrypt `$2a$` hash of the new password into
 * `users.password` (matches what `AuthHttpUtil.checkPassword` /
 * `SessionEndpoints` / `AccountChangeEndpoints` already write and read),
 * clears `auth_ticket` so any active session can't be re-used to bypass
 * the reset, and ships the PLAINTEXT new password back to the operator
 * in the action-result `message` so they can communicate it out-of-band.
 */
public class HousekeepingResetUserPasswordEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.reset_password";
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
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        int userId = this.packet.readInt();

        if (userId <= 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        String plain = randomPassword();
        String hash;

        try {
            hash = BCrypt.hashpw(plain, BCrypt.gensalt(BCRYPT_COST));
        } catch (IllegalArgumentException e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.hash_failed"));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users SET password = ?, auth_ticket = '' WHERE id = ? LIMIT 1")) {
            statement.setString(1, hash);
            statement.setInt(2, userId);
            int rows = statement.executeUpdate();

            if (rows == 0) {
                this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
                return;
            }
        } catch (SQLException e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        // Plaintext flows through `message` — the client surfaces it via the
        // status banner so the operator can read it once. SSL is on the
        // operator: the only secure transport for the WS is wss://.
        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "password_reset=1",
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, plain));
    }

    private static String randomPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RNG.nextInt(PASSWORD_ALPHABET.length())));
        }

        return sb.toString();
    }
}

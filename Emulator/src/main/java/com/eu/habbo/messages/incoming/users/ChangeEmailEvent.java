package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.TalentTrackEmailFailedComposer;
import com.eu.habbo.messages.outgoing.unknown.TalentTrackEmailVerifiedComposer;
import com.eu.habbo.networking.gameserver.auth.AvailabilityCache;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Official {@code ChangeEmailMessageComposer} (3965): the talent-track task dialog submits a new
 * account e-mail. A rejection is reported with {@code ChangeEmailResult} (1815) and the codes
 * {@code TalentTrackController.setEmailErrorStatus} renders; on success the client is refreshed
 * with a new {@code EmailStatus} (612).
 *
 * <p>The dialog has no password field, so this packet can only ever <em>set</em> the address of an
 * account that has none — the talent-track task it exists for. Replacing an address that is already
 * on the account is what {@code /api/auth/change-email} is for, and that endpoint asks for the
 * current password: without that rule, anyone holding a live session (a stolen SSO ticket, a shared
 * PC) could point the account at their own inbox and then take it over for good through
 * {@code /forgot-password}. Operators who accept that risk can set
 * {@code hotel.email.change.requires_password=0}.
 */
public class ChangeEmailEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeEmailEvent.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s.]+(\\.[^@\\s.]+)+$");
    /** welcome.gift.email.error.2 says the maximum length is 48 characters. */
    private static final int MAX_EMAIL_LENGTH = 48;

    /**
     * ChangeEmailResult codes, named by the official texts welcome.gift.email.error.N:
     * 0 accepted, 1 empty or malformed, 2 too long, 3 already in use, 4 changed too often.
     */
    private static final int RESULT_OK = 0;

    private static final int RESULT_INVALID = 1;
    private static final int RESULT_TOO_LONG = 2;
    private static final int RESULT_TAKEN = 3;

    @Override
    public int getRatelimit() {
        return 5000;
    }

    @Override
    public void handle() throws Exception {
        String mail = this.packet.readString();

        String currentMail = this.client.getHabbo().getHabboInfo().getMail();

        if (!Emulator.getConfig().getBoolean("hotel.email.change.enabled", true)) {
            this.refuseWithoutErrorCode(currentMail);
            return;
        }

        // Replacing an address the account already has needs the password, which
        // only the HTTP endpoint can ask for.
        if (currentMail != null
                && !currentMail.trim().isEmpty()
                && Emulator.getConfig().getBoolean("hotel.email.change.requires_password", true)) {
            LOGGER.info(
                    "Refused in-game e-mail change for user {}: the account already has an address, "
                            + "so the change must go through /api/auth/change-email",
                    this.client.getHabbo().getHabboInfo().getId());
            this.refuseWithoutErrorCode(currentMail);
            return;
        }

        if (mail != null && mail.trim().length() > MAX_EMAIL_LENGTH) {
            this.client.sendResponse(new TalentTrackEmailFailedComposer(RESULT_TOO_LONG));
            return;
        }

        if (mail == null || !EMAIL.matcher(mail.trim()).matches()) {
            this.client.sendResponse(new TalentTrackEmailFailedComposer(RESULT_INVALID));
            return;
        }

        String normalized = mail.trim().toLowerCase();

        if (!this.persist(normalized)) {
            this.client.sendResponse(new TalentTrackEmailFailedComposer(RESULT_TAKEN));
            return;
        }

        this.client.getHabbo().getHabboInfo().setMail(normalized);
        this.client.getHabbo().getHabboInfo().setMailVerified(false);

        // The availability answers for both addresses are now stale: without this,
        // check-email keeps claiming the new address is free for a minute.
        if (currentMail != null && !currentMail.trim().isEmpty()) {
            AvailabilityCache.invalidateEmail(currentMail);
        }
        AvailabilityCache.invalidateEmail(normalized);

        this.client.sendResponse(new TalentTrackEmailFailedComposer(RESULT_OK));
        this.client.sendResponse(new TalentTrackEmailVerifiedComposer(normalized, false, true));
    }

    /** The client hides the change button when allowChange is false; no error code says "not allowed". */
    private void refuseWithoutErrorCode(String currentMail) {
        this.client.sendResponse(new TalentTrackEmailVerifiedComposer(
                currentMail == null ? "" : currentMail,
                this.client.getHabbo().getHabboInfo().isMailVerified(),
                false));
    }

    /** False when the address belongs to another account, or the write failed. */
    private boolean persist(String mail) {
        int userId = this.client.getHabbo().getHabboInfo().getId();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // users.mail has no unique index, and /forgot-password resolves an
            // address with LIMIT 1: two accounts sharing one address means the
            // reset mail can be answered for the wrong one.
            try (PreparedStatement taken =
                    connection.prepareStatement("SELECT id FROM users WHERE mail = ? AND id <> ? LIMIT 1")) {
                taken.setString(1, mail);
                taken.setInt(2, userId);
                try (ResultSet rs = taken.executeQuery()) {
                    if (rs.next()) return false;
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET mail = ?, mail_verified = '0' WHERE id = ? LIMIT 1")) {
                statement.setString(1, mail);
                statement.setInt(2, userId);
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to change the e-mail of user {}", userId, e);
            return false;
        }
    }
}

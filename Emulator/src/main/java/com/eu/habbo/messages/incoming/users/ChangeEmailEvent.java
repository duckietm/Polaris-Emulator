package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.TalentTrackEmailFailedComposer;
import com.eu.habbo.messages.outgoing.unknown.TalentTrackEmailVerifiedComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Official {@code ChangeEmailMessageComposer} (3965): the talent-track task dialog submits a new
 * account e-mail. A rejection is reported with {@code ChangeEmailResult} (1815) and the codes
 * {@code TalentTrackController.setEmailErrorStatus} renders; on success the client is refreshed
 * with a new {@code EmailStatus} (612).
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

        if (!Emulator.getConfig().getBoolean("hotel.email.change.enabled", true)) {
            // No error code says "not allowed"; the client hides the button when allowChange is false.
            this.client.sendResponse(new TalentTrackEmailVerifiedComposer(
                    this.client.getHabbo().getHabboInfo().getMail() == null
                            ? ""
                            : this.client.getHabbo().getHabboInfo().getMail(),
                    this.client.getHabbo().getHabboInfo().isMailVerified(),
                    false));
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

        this.client.sendResponse(new TalentTrackEmailFailedComposer(RESULT_OK));
        this.client.sendResponse(new TalentTrackEmailVerifiedComposer(normalized, false, true));
    }

    private boolean persist(String mail) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET mail = ?, mail_verified = '0' WHERE id = ? LIMIT 1")) {
            statement.setString(1, mail);
            statement.setInt(2, this.client.getHabbo().getHabboInfo().getId());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error(
                    "Failed to change the e-mail of user {}",
                    this.client.getHabbo().getHabboInfo().getId(),
                    e);
            return false;
        }
    }
}

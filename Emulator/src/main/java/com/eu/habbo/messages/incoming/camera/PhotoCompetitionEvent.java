package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.habbohotel.camera.CameraCompetitionManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.camera.CameraCompetitionStatusComposer;

/**
 * Submits the photo in the camera checkout to the hotel's photo competition. The packet carries
 * nothing: the photo is the one the sender just rendered, which the hotel is already holding for the
 * publish flow, so a checkout without a photo is refused the same way publishing would refuse it.
 *
 * <p>The three answers the client knows are a plain yes, {@code too-many-submits} when the day's
 * allowance is spent, and anything else as a generic failure. The verified-email refusal the client
 * also understands is not ours to send: this hotel does not verify e-mail addresses.
 */
public class PhotoCompetitionEvent extends MessageHandler {
    static final String TOO_MANY = "too-many-submits";

    static final String FAILED = "error";

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        HabboInfo info = habbo.getHabboInfo();
        int timestamp = info.getPhotoTimestamp();
        String photo = info.getPhotoJSON();

        if (timestamp == 0 || photo == null || photo.isEmpty() || !photo.contains(Integer.toString(timestamp))) {
            this.client.sendResponse(new CameraCompetitionStatusComposer(false, FAILED));
            return;
        }

        CameraCompetitionManager competition = CameraCompetitionManager.getInstance();

        if (competition.submissionsToday(info.getId()) >= competition.dailyLimit()) {
            this.client.sendResponse(new CameraCompetitionStatusComposer(false, TOO_MANY));
            return;
        }

        boolean stored = competition.submit(info.getId(), info.getPhotoRoomId(), info.getPhotoURL());

        this.client.sendResponse(new CameraCompetitionStatusComposer(stored, stored ? "" : FAILED));
    }
}

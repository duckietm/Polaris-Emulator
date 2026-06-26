package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.camera.CameraPublishWaitMessageComposer;
import com.eu.habbo.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import com.eu.habbo.plugin.events.users.UserPublishPictureEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CameraPublishToWebEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPublishToWebEvent.class);

    public static int CAMERA_PUBLISH_POINTS = 1;
    public static int CAMERA_PUBLISH_POINTS_TYPE = 5;
    public static int CAMERA_PUBLISH_DELAY = 180;

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        HabboInfo habboInfo = habbo.getHabboInfo();

        int points = habboInfo.getCurrencyAmount(CAMERA_PUBLISH_POINTS_TYPE);
        if (points < CAMERA_PUBLISH_POINTS) {
            String currencyName = Emulator.getTexts().getValue("seasonal.name." + CAMERA_PUBLISH_POINTS_TYPE, "currency");
            habbo.alert("You don't have enough " + currencyName + "!");
            this.client.sendResponse(new NotEnoughPointsTypeComposer(false, true, CAMERA_PUBLISH_POINTS_TYPE));
            return;
        }

        int photoTimestamp = habboInfo.getPhotoTimestamp();
        String photoJSON = habboInfo.getPhotoJSON();
        if (photoTimestamp == 0 || photoJSON.isEmpty() || !photoJSON.contains(Integer.toString(photoTimestamp)))
            return;

        int currentTimestamp = Emulator.getIntUnixTimestamp();
        int timeSinceLastPublish = currentTimestamp - habboInfo.getWebPublishTimestamp();

        if (timeSinceLastPublish < CAMERA_PUBLISH_DELAY) {
            int wait = CAMERA_PUBLISH_DELAY - timeSinceLastPublish;
            this.client.sendResponse(new CameraPublishWaitMessageComposer(false, wait, habboInfo.getPhotoURL()));
        } else {
            var publishPictureEvent = new UserPublishPictureEvent(habbo, habboInfo.getPhotoURL(), currentTimestamp, habboInfo.getPhotoRoomId());

            if (!Emulator.getPluginManager().fireEvent(publishPictureEvent).isCancelled()) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                     PreparedStatement statement = connection.prepareStatement("INSERT INTO camera_web (user_id, room_id, timestamp, url) VALUES (?, ?, ?, ?)")) {
                    statement.setInt(1, habboInfo.getId());
                    statement.setInt(2, publishPictureEvent.roomId);
                    statement.setInt(3, publishPictureEvent.timestamp);
                    statement.setString(4, publishPictureEvent.URL);
                    statement.execute();
                    habboInfo.setWebPublishTimestamp(currentTimestamp);
                    habbo.givePoints(CAMERA_PUBLISH_POINTS_TYPE, -CAMERA_PUBLISH_POINTS);
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            }
            this.client.sendResponse(new CameraPublishWaitMessageComposer(true, 0, ""));
        }
    }
}

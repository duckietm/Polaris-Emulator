package polaris.messages.incoming.camera;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.camera.CameraPublishWaitMessageComposer;
import polaris.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import polaris.plugin.PluginEventInputGuard;
import polaris.plugin.events.users.UserPublishPictureEvent;

public class CameraPublishToWebEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPublishToWebEvent.class);

    public static int CAMERA_PUBLISH_POINTS = 1;
    public static int CAMERA_PUBLISH_POINTS_TYPE = 5;
    public static int CAMERA_PUBLISH_DELAY = 180;

    @Override
    public void handle() {
        User user = this.client.getUser();
        if (user == null) return;

        UserInfo userInfo = user.getUserInfo();

        int points = userInfo.getCurrencyAmount(CAMERA_PUBLISH_POINTS_TYPE);
        if (points < CAMERA_PUBLISH_POINTS) {
            String currencyName =
                    Emulator.getTexts().getValue("seasonal.name." + CAMERA_PUBLISH_POINTS_TYPE, "currency");
            user.alert("You don't have enough " + currencyName + "!");
            this.client.sendResponse(new NotEnoughPointsTypeComposer(false, true, CAMERA_PUBLISH_POINTS_TYPE));
            return;
        }

        int photoTimestamp = userInfo.getPhotoTimestamp();
        String photoJSON = userInfo.getPhotoJSON();
        if (photoTimestamp == 0
                || photoJSON == null
                || photoJSON.isEmpty()
                || !photoJSON.contains(Integer.toString(photoTimestamp))) return;

        if (!CameraPurchaseEvent.isInPhotoRoom(userInfo)) {
            LOGGER.warn(
                    "Camera publish for {} aborted: the user left the room the photo was taken in.",
                    userInfo.getUsername());
            return;
        }

        int currentTimestamp = Emulator.getIntUnixTimestamp();
        int timeSinceLastPublish = currentTimestamp - userInfo.getWebPublishTimestamp();

        if (timeSinceLastPublish < CAMERA_PUBLISH_DELAY) {
            int wait = CAMERA_PUBLISH_DELAY - timeSinceLastPublish;
            this.client.sendResponse(new CameraPublishWaitMessageComposer(false, wait, userInfo.getPhotoURL()));
        } else {
            UserPublishPictureEvent publishPictureEvent = new UserPublishPictureEvent(
                    user, userInfo.getPhotoURL(), currentTimestamp, userInfo.getPhotoRoomId());

            if (!Emulator.getPluginManager().fireEvent(publishPictureEvent).isCancelled()) {
                if (!PluginEventInputGuard.isPositiveId(publishPictureEvent.roomId)
                        || Emulator.getGameEnvironment().getRoomManager().loadRoom(publishPictureEvent.roomId)
                                == null) {
                    return;
                }

                // Take the publish slot atomically: of two packets racing past the delay check
                // above, only one publishes and pays.
                int previousPublish = userInfo.claimWebPublish(currentTimestamp, CAMERA_PUBLISH_DELAY);
                if (previousPublish < 0) return;

                // The points are taken first and must really go: the void givePoints after the insert hid a refused
                // debit (a plugin, the ledger), and the photo was published for free.
                boolean charged = CAMERA_PUBLISH_POINTS > 0;
                if (charged && !user.tryTakePoints(CAMERA_PUBLISH_POINTS_TYPE, CAMERA_PUBLISH_POINTS)) {
                    userInfo.releaseWebPublish(currentTimestamp, previousPublish);
                    this.client.sendResponse(new NotEnoughPointsTypeComposer(false, true, CAMERA_PUBLISH_POINTS_TYPE));
                    return;
                }

                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO camera_web (user_id, room_id, timestamp, url) VALUES (?, ?, ?, ?)")) {
                    statement.setInt(1, userInfo.getId());
                    statement.setInt(2, publishPictureEvent.roomId);
                    statement.setInt(3, publishPictureEvent.timestamp);
                    statement.setString(4, publishPictureEvent.URL);
                    statement.execute();
                    polaris.hotel.quests.QuestProgressEvents.progress(
                            user, polaris.hotel.quests.QuestGoalType.PUBLISH_PICTURE, 1);
                } catch (SQLException e) {
                    userInfo.releaseWebPublish(currentTimestamp, previousPublish);
                    if (charged) {
                        user.givePoints(CAMERA_PUBLISH_POINTS_TYPE, CAMERA_PUBLISH_POINTS, "camera.publish.refund");
                    }
                    LOGGER.error("Caught SQL exception", e);
                }
            }
            this.client.sendResponse(new CameraPublishWaitMessageComposer(true, 0, ""));
        }
    }
}

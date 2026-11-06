package polaris.messages.incoming.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.camera.CameraPurchaseSuccesfullComposer;
import polaris.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.plugin.events.users.UserPurchasePictureEvent;

public class CameraPurchaseEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CameraPurchaseEvent.class);

    public static int CAMERA_PURCHASE_CREDITS = 2;
    public static int CAMERA_PURCHASE_POINTS = 0;
    public static int CAMERA_PURCHASE_POINTS_TYPE = 5;

    // A double click must not buy (and charge) twice: one purchase per second, as the other
    // purchase handlers do. A second packet for the same photo is refused by the claim below.
    @Override
    public int getRatelimit() {
        return 1000;
    }

    /** True when the user is still in the room the pending photo was taken in. */
    static boolean isInPhotoRoom(UserInfo userInfo) {
        Room room = userInfo.getCurrentRoom();
        return room != null && userInfo.getPhotoRoomId() > 0 && room.getId() == userInfo.getPhotoRoomId();
    }

    @Override
    public void handle() {
        this.packet.readString();

        User user = this.client.getUser();
        UserInfo userInfo = user.getUserInfo();

        if (userInfo.getCredits() < CAMERA_PURCHASE_CREDITS) {
            user.alert("You don't have enough credits!");
            this.client.sendResponse(new NotEnoughPointsTypeComposer(true, false, 0));
            return;
        }

        if (userInfo.getCurrencyAmount(CAMERA_PURCHASE_POINTS_TYPE) < CAMERA_PURCHASE_POINTS) {
            String alertMessage = "You don't have enough "
                    + Emulator.getTexts().getValue("seasonal.name." + CAMERA_PURCHASE_POINTS_TYPE, "currency") + "!";
            user.alert(alertMessage);
            this.client.sendResponse(new NotEnoughPointsTypeComposer(false, true, CAMERA_PURCHASE_POINTS_TYPE));
            return;
        }

        int photoTimestamp = userInfo.getPhotoTimestamp();
        String photoJSON = userInfo.getPhotoJSON();
        String photoURL = userInfo.getPhotoURL();
        int photoRoomId = userInfo.getPhotoRoomId();

        if (photoTimestamp == 0
                || photoJSON == null
                || photoJSON.isEmpty()
                || !photoJSON.contains(Integer.toString(photoTimestamp))) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: no valid rendered photo in state (timestamp={}, jsonEmpty={}). Take a photo again before buying.",
                    userInfo.getUsername(),
                    photoTimestamp,
                    photoJSON == null || photoJSON.isEmpty());
            return;
        }

        if (userInfo.getCurrentRoom() == null) {
            LOGGER.warn("Camera purchase for {} aborted: user is not in a room.", userInfo.getUsername());
            return;
        }

        if (!isInPhotoRoom(userInfo)) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: photo was taken in room {} but the user is in room {}.",
                    userInfo.getUsername(),
                    photoRoomId,
                    userInfo.getCurrentRoom().getId());
            user.alert("You can only buy a photo in the room where you took it.");
            return;
        }

        if (Emulator.getPluginManager()
                .fireEvent(new UserPurchasePictureEvent(user, photoURL, photoRoomId, photoTimestamp))
                .isCancelled()) return;

        int cameraItemId = Emulator.getConfig().getInt("camera.item_id");
        Item item = Emulator.getGameEnvironment().getItemManager().getItem(cameraItemId);
        if (item == null || !item.getInteractionType().getName().equals("external_image")) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: camera.item_id={} is {} (need a catalog item with interaction type 'external_image').",
                    userInfo.getUsername(),
                    cameraItemId,
                    item == null
                            ? "not found"
                            : "interaction '" + item.getInteractionType().getName() + "'");
            user.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        // Claim the photo before anything is charged or created: a second packet for the same
        // photo, even one racing this one, stops here.
        if (!userInfo.claimPhotoPurchase(photoTimestamp)) {
            LOGGER.warn(
                    "Camera purchase for {} ignored: photo {} was already bought.",
                    userInfo.getUsername(),
                    photoTimestamp);
            return;
        }

        if (!this.charge(user)) {
            userInfo.releasePhotoPurchase(photoTimestamp);
            user.alert("You don't have enough credits!");
            return;
        }

        UserItem photoItem =
                Emulator.getGameEnvironment().getItemManager().createItem(userInfo.getId(), item, 0, 0, photoJSON);
        if (photoItem == null) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: createItem returned null for camera.item_id={}.",
                    userInfo.getUsername(),
                    cameraItemId);
            this.refund(user);
            userInfo.releasePhotoPurchase(photoTimestamp);
            user.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        photoItem.setExtradata(photoItem.getExtradata().replace("%id%", Integer.toString(photoItem.getId())));
        photoItem.needsUpdate(true);
        user.getInventory().getItemsComponent().addItem(photoItem);

        this.client.sendResponse(new CameraPurchaseSuccesfullComposer());
        this.client.sendResponse(new AddUserItemComposer(photoItem));
        this.client.sendResponse(new InventoryRefreshComposer());

        AchievementManager.progressAchievement(
                user, Emulator.getGameEnvironment().getAchievementManager().getAchievement("CameraPhotoCount"));
    }

    /** Takes the price, all of it or nothing. */
    private boolean charge(User user) {
        if (CAMERA_PURCHASE_CREDITS > 0 && !user.tryTakeCredits(CAMERA_PURCHASE_CREDITS)) return false;

        if (CAMERA_PURCHASE_POINTS > 0 && !user.tryTakePoints(CAMERA_PURCHASE_POINTS_TYPE, CAMERA_PURCHASE_POINTS)) {
            user.giveCredits(CAMERA_PURCHASE_CREDITS);
            return false;
        }

        return true;
    }

    private void refund(User user) {
        user.giveCredits(CAMERA_PURCHASE_CREDITS);
        user.givePoints(CAMERA_PURCHASE_POINTS_TYPE, CAMERA_PURCHASE_POINTS);
    }
}

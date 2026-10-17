package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.camera.CameraPurchaseSuccesfullComposer;
import com.eu.habbo.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.plugin.events.users.UserPurchasePictureEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    static boolean isInPhotoRoom(HabboInfo habboInfo) {
        Room room = habboInfo.getCurrentRoom();
        return room != null && habboInfo.getPhotoRoomId() > 0 && room.getId() == habboInfo.getPhotoRoomId();
    }

    @Override
    public void handle() {
        this.packet.readString();

        Habbo habbo = this.client.getHabbo();
        HabboInfo habboInfo = habbo.getHabboInfo();

        if (habboInfo.getCredits() < CAMERA_PURCHASE_CREDITS) {
            habbo.alert("You don't have enough credits!");
            this.client.sendResponse(new NotEnoughPointsTypeComposer(true, false, 0));
            return;
        }

        if (habboInfo.getCurrencyAmount(CAMERA_PURCHASE_POINTS_TYPE) < CAMERA_PURCHASE_POINTS) {
            String alertMessage = "You don't have enough "
                    + Emulator.getTexts().getValue("seasonal.name." + CAMERA_PURCHASE_POINTS_TYPE, "currency") + "!";
            habbo.alert(alertMessage);
            this.client.sendResponse(new NotEnoughPointsTypeComposer(false, true, CAMERA_PURCHASE_POINTS_TYPE));
            return;
        }

        int photoTimestamp = habboInfo.getPhotoTimestamp();
        String photoJSON = habboInfo.getPhotoJSON();
        String photoURL = habboInfo.getPhotoURL();
        int photoRoomId = habboInfo.getPhotoRoomId();

        if (photoTimestamp == 0
                || photoJSON == null
                || photoJSON.isEmpty()
                || !photoJSON.contains(Integer.toString(photoTimestamp))) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: no valid rendered photo in state (timestamp={}, jsonEmpty={}). Take a photo again before buying.",
                    habboInfo.getUsername(),
                    photoTimestamp,
                    photoJSON == null || photoJSON.isEmpty());
            return;
        }

        if (habboInfo.getCurrentRoom() == null) {
            LOGGER.warn("Camera purchase for {} aborted: user is not in a room.", habboInfo.getUsername());
            return;
        }

        if (!isInPhotoRoom(habboInfo)) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: photo was taken in room {} but the user is in room {}.",
                    habboInfo.getUsername(),
                    photoRoomId,
                    habboInfo.getCurrentRoom().getId());
            habbo.alert("You can only buy a photo in the room where you took it.");
            return;
        }

        if (Emulator.getPluginManager()
                .fireEvent(new UserPurchasePictureEvent(habbo, photoURL, photoRoomId, photoTimestamp))
                .isCancelled()) return;

        int cameraItemId = Emulator.getConfig().getInt("camera.item_id");
        Item item = Emulator.getGameEnvironment().getItemManager().getItem(cameraItemId);
        if (item == null || !item.getInteractionType().getName().equals("external_image")) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: camera.item_id={} is {} (need a catalog item with interaction type 'external_image').",
                    habboInfo.getUsername(),
                    cameraItemId,
                    item == null
                            ? "not found"
                            : "interaction '" + item.getInteractionType().getName() + "'");
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        // Claim the photo before anything is charged or created: a second packet for the same
        // photo, even one racing this one, stops here.
        if (!habboInfo.claimPhotoPurchase(photoTimestamp)) {
            LOGGER.warn(
                    "Camera purchase for {} ignored: photo {} was already bought.",
                    habboInfo.getUsername(),
                    photoTimestamp);
            return;
        }

        if (!this.charge(habbo)) {
            habboInfo.releasePhotoPurchase(photoTimestamp);
            habbo.alert("You don't have enough credits!");
            return;
        }

        HabboItem photoItem =
                Emulator.getGameEnvironment().getItemManager().createItem(habboInfo.getId(), item, 0, 0, photoJSON);
        if (photoItem == null) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: createItem returned null for camera.item_id={}.",
                    habboInfo.getUsername(),
                    cameraItemId);
            this.refund(habbo);
            habboInfo.releasePhotoPurchase(photoTimestamp);
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        photoItem.setExtradata(photoItem.getExtradata().replace("%id%", Integer.toString(photoItem.getId())));
        photoItem.needsUpdate(true);
        habbo.getInventory().getItemsComponent().addItem(photoItem);

        this.client.sendResponse(new CameraPurchaseSuccesfullComposer());
        this.client.sendResponse(new AddHabboItemComposer(photoItem));
        this.client.sendResponse(new InventoryRefreshComposer());

        AchievementManager.progressAchievement(
                habbo, Emulator.getGameEnvironment().getAchievementManager().getAchievement("CameraPhotoCount"));
    }

    /** Takes the price, all of it or nothing. */
    private boolean charge(Habbo habbo) {
        if (CAMERA_PURCHASE_CREDITS > 0 && !habbo.tryTakeCredits(CAMERA_PURCHASE_CREDITS)) return false;

        if (CAMERA_PURCHASE_POINTS > 0 && !habbo.tryTakePoints(CAMERA_PURCHASE_POINTS_TYPE, CAMERA_PURCHASE_POINTS)) {
            habbo.giveCredits(CAMERA_PURCHASE_CREDITS);
            return false;
        }

        return true;
    }

    private void refund(Habbo habbo) {
        habbo.giveCredits(CAMERA_PURCHASE_CREDITS);
        habbo.givePoints(CAMERA_PURCHASE_POINTS_TYPE, CAMERA_PURCHASE_POINTS);
    }
}

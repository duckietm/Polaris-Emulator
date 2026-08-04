package com.eu.habbo.messages.incoming.camera;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.Item;
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

        if (habboInfo.getPhotoTimestamp() == 0
                || habboInfo.getPhotoJSON() == null
                || habboInfo.getPhotoJSON().isEmpty()
                || !habboInfo.getPhotoJSON().contains(Integer.toString(habboInfo.getPhotoTimestamp()))) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: no valid rendered photo in state (timestamp={}, jsonEmpty={}). Take a photo again before buying.",
                    habboInfo.getUsername(),
                    habboInfo.getPhotoTimestamp(),
                    habboInfo.getPhotoJSON() == null || habboInfo.getPhotoJSON().isEmpty());
            return;
        }

        if (habboInfo.getCurrentRoom() == null) {
            LOGGER.warn("Camera purchase for {} aborted: user is not in a room.", habboInfo.getUsername());
            return;
        }

        if (Emulator.getPluginManager()
                .fireEvent(new UserPurchasePictureEvent(
                        habbo,
                        habboInfo.getPhotoURL(),
                        habboInfo.getCurrentRoom().getId(),
                        habboInfo.getPhotoTimestamp()))
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

        HabboItem photoItem = Emulator.getGameEnvironment()
                .getItemManager()
                .createItem(habboInfo.getId(), item, 0, 0, habboInfo.getPhotoJSON());
        if (photoItem == null) {
            LOGGER.warn(
                    "Camera purchase for {} aborted: createItem returned null for camera.item_id={}.",
                    habboInfo.getUsername(),
                    cameraItemId);
            habbo.alert(Emulator.getTexts().getValue("camera.error.creation"));
            return;
        }

        photoItem.setExtradata(photoItem.getExtradata().replace("%id%", Integer.toString(photoItem.getId())));
        photoItem.needsUpdate(true);
        habbo.getInventory().getItemsComponent().addItem(photoItem);

        this.client.sendResponse(new CameraPurchaseSuccesfullComposer());
        this.client.sendResponse(new AddHabboItemComposer(photoItem));
        this.client.sendResponse(new InventoryRefreshComposer());

        habbo.giveCredits(-CAMERA_PURCHASE_CREDITS);
        habbo.givePoints(CAMERA_PURCHASE_POINTS_TYPE, -CAMERA_PURCHASE_POINTS);

        AchievementManager.progressAchievement(
                habbo, Emulator.getGameEnvironment().getAchievementManager().getAchievement("CameraPhotoCount"));
    }
}

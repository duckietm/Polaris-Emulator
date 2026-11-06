package polaris.messages.incoming.selfdonation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.selfdonation.SelfDonationResultComposer;

/**
 * AIR 13 SelfDonation (2499), sent by {@code SelfDonationTool.onDonate}:
 * {@code (isWallItem, typeId, legacyPosterId, amount)}.
 *
 * <p>The official tool only exists on the sandbox environments
 * ({@code ALLOWED_ENVIRONMENT_IDS}); here the equivalent gate is the setting
 * {@code hotel.selfdonation.enabled} plus the {@code acc_debug} permission, and
 * the amount keeps the official 1..{@code hotel.selfdonation.max.amount} range.
 * Everything else answers {@code NOT_ALLOWED} / {@code FAILED} exactly like the
 * official result codes.
 */
public class SelfDonationEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfDonationEvent.class);

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        boolean isWallItem = this.packet.readBoolean();
        int typeId = this.packet.readInt();
        String legacyPosterId = this.packet.readString();
        int amount = this.packet.readInt();

        User user = this.client.getUser();

        if (user == null) return;

        if (!Emulator.getConfig().getBoolean("hotel.selfdonation.enabled", false) || !user.hasPermission("acc_debug")) {
            this.client.sendResponse(new SelfDonationResultComposer(SelfDonationResultComposer.NOT_ALLOWED));
            return;
        }

        int maxAmount = Math.max(1, Emulator.getConfig().getInt("hotel.selfdonation.max.amount", 500));

        if (amount < 1 || amount > maxAmount) {
            this.client.sendResponse(new SelfDonationResultComposer(SelfDonationResultComposer.FAILED));
            return;
        }

        Item baseItem = findItem(typeId, isWallItem);

        if (baseItem == null) {
            this.client.sendResponse(new SelfDonationResultComposer(SelfDonationResultComposer.FAILED));
            return;
        }

        String extraData = legacyPosterId == null ? "" : legacyPosterId.trim();
        int created = 0;

        for (int i = 0; i < amount; i++) {
            UserItem item = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createItem(user.getUserInfo().getId(), baseItem, 0, 0, extraData);

            if (item == null) break;

            user.getInventory().getItemsComponent().addItem(item);
            this.client.sendResponse(new AddUserItemComposer(item));
            created++;
        }

        if (created == 0) {
            this.client.sendResponse(new SelfDonationResultComposer(SelfDonationResultComposer.FAILED));
            return;
        }

        this.client.sendResponse(new InventoryRefreshComposer());
        this.client.sendResponse(new SelfDonationResultComposer(SelfDonationResultComposer.SUCCESS));

        LOGGER.info("Self donation: {} received {}x {}", user.getUserInfo().getUsername(), created, baseItem.getName());
    }

    /** The client sends the furnidata sprite id, not the {@code items_base} id. */
    private static Item findItem(int spriteId, boolean isWallItem) {
        if (spriteId <= 0) return null;

        FurnitureType wanted = isWallItem ? FurnitureType.WALL : FurnitureType.FLOOR;

        for (Item item :
                Emulator.getGameEnvironment().getItemManager().getItems().values()) {
            if (item != null && item.getSpriteId() == spriteId && item.getType() == wanted) return item;
        }

        return null;
    }
}

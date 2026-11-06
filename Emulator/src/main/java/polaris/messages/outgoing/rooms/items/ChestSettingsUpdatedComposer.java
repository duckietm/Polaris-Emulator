package polaris.messages.outgoing.rooms.items;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * A chest's settings were saved (AIR {@code ChestSettingsUpdated}):
 * {@code int chestId, bool isNotificationPreferences}. Sent only for a save that went through; the
 * client closes the matching settings frame. The settings themselves arrive with the furni.
 */
public class ChestSettingsUpdatedComposer extends MessageComposer {
    private final int chestId;
    private final boolean notificationPreferences;

    public ChestSettingsUpdatedComposer(int chestId, boolean notificationPreferences) {
        this.chestId = chestId;
        this.notificationPreferences = notificationPreferences;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestSettingsUpdatedComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.chestId));
        this.response.appendBoolean(this.notificationPreferences);
        return this.response;
    }
}

package polaris.messages.incoming.wired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import polaris.hotel.items.FurnitureDirections;
import polaris.hotel.items.interactions.wired.interfaces.InteractionWiredMatchFurniSettings;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.rooms.items.FloorItemOnRollerComposer;

public class WiredApplySetConditionsEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        // Executing Habbo has to be in a Room
        if (!this.client.getUser().getRoomUnit().isInRoom()) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {

            // Executing Habbo should be able to edit wireds
            if (room.canModifyWired(this.client.getUser())) {

                // Official: the snapshot boxes are the match-to-snapshot condition and action, the
                // "furni state is changed" trigger and place furni.
                List<UserItem> wireds = new ArrayList<>();
                wireds.addAll(room.getRoomSpecialTypes().getTriggers());
                wireds.addAll(room.getRoomSpecialTypes().getConditions());
                wireds.addAll(room.getRoomSpecialTypes().getEffects());

                // Find the item with the given ID in the room
                Optional<UserItem> item =
                        wireds.stream().filter(wired -> wired.getId() == itemId).findFirst();

                // If the item exists
                if (item.isPresent()) {
                    UserItem wiredItem = item.get();

                    // The item should have settings to match furni state, position and rotation
                    if (wiredItem instanceof InteractionWiredMatchFurniSettings) {

                        InteractionWiredMatchFurniSettings wired = (InteractionWiredMatchFurniSettings) wiredItem;

                        // Try to apply the set settings to each item
                        wired.getMatchFurniSettings().forEach(setting -> {
                            UserItem matchItem = room.getUserItem(setting.item_id);

                            // The referenced furni may have been removed from the
                            // room since the match settings were saved; skip it
                            // instead of NPEing out of the packet handler.
                            if (matchItem == null) {
                                return;
                            }

                            // Match state
                            if (wired.shouldMatchState() && matchItem.allowWiredResetState()) {
                                if (!setting.state.equals(" ")
                                        && !matchItem.getExtradata().equals(setting.state)) {
                                    matchItem.setExtradata(setting.state);
                                    room.updateItemState(matchItem);
                                }
                            }

                            RoomTile oldLocation = room.getLayout().getTile(matchItem.getX(), matchItem.getY());
                            double oldZ = matchItem.getZ();

                            // Match Position & Rotation; a snapshot direction the furni's asset lacks
                            // snaps as in Habbo (FurnitureDirections#match).
                            int targetRotation =
                                    FurnitureDirections.of(matchItem).match(matchItem.getRotation(), setting.rotation);
                            if (wired.shouldMatchRotation() && !wired.shouldMatchPosition()) {
                                if (matchItem.getRotation() != targetRotation
                                        && room.furnitureFitsAt(oldLocation, matchItem, targetRotation, false)
                                                == FurnitureMovementError.NONE) {
                                    room.moveFurniTo(matchItem, oldLocation, targetRotation, null, true);
                                }
                            } else if (wired.shouldMatchAltitude() && !wired.shouldMatchPosition()) {
                                int newRotation =
                                        wired.shouldMatchRotation() ? targetRotation : matchItem.getRotation();
                                if (BigDecimal.valueOf(matchItem.getZ()).compareTo(BigDecimal.valueOf(setting.z)) != 0
                                        || newRotation != matchItem.getRotation()) {
                                    room.moveFurniTo(matchItem, oldLocation, newRotation, setting.z, null, true, false);
                                }
                            } else if (wired.shouldMatchPosition()) {
                                boolean slideAnimation =
                                        !wired.shouldMatchRotation() || matchItem.getRotation() == targetRotation;
                                RoomTile newLocation = room.getLayout().getTile((short) setting.x, (short) setting.y);
                                int newRotation =
                                        wired.shouldMatchRotation() ? targetRotation : matchItem.getRotation();
                                double newZ = wired.shouldMatchAltitude() ? setting.z : matchItem.getZ();

                                if (newLocation != null
                                        && newLocation.state != RoomTileState.INVALID
                                        && (newLocation != oldLocation || newRotation != matchItem.getRotation())
                                        && room.furnitureFitsAt(newLocation, matchItem, newRotation, true)
                                                == FurnitureMovementError.NONE) {
                                    if (room.moveFurniTo(
                                                    matchItem,
                                                    newLocation,
                                                    newRotation,
                                                    newZ,
                                                    null,
                                                    !slideAnimation,
                                                    true)
                                            == FurnitureMovementError.NONE) {
                                        if (slideAnimation) {
                                            room.sendComposer(new FloorItemOnRollerComposer(
                                                            matchItem,
                                                            null,
                                                            oldLocation,
                                                            oldZ,
                                                            newLocation,
                                                            matchItem.getZ(),
                                                            0,
                                                            room)
                                                    .compose());
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
    }
}

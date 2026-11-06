package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.FurnitureUsagePolicy;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionBadgeDisplay;
import polaris.hotel.items.interactions.InteractionClothing;
import polaris.hotel.items.interactions.InteractionCrackable;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.items.interactions.InteractionGymEquipment;
import polaris.hotel.items.interactions.InteractionHopper;
import polaris.hotel.items.interactions.InteractionMannequin;
import polaris.hotel.items.interactions.InteractionObstacle;
import polaris.hotel.items.interactions.InteractionOneWayGate;
import polaris.hotel.items.interactions.InteractionPressurePlate;
import polaris.hotel.items.interactions.InteractionPushable;
import polaris.hotel.items.interactions.InteractionPuzzleBox;
import polaris.hotel.items.interactions.InteractionQueueSpeedControl;
import polaris.hotel.items.interactions.InteractionRemoteSwitchControl;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.items.interactions.InteractionSwitch;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionTent;
import polaris.hotel.items.interactions.InteractionTrap;
import polaris.hotel.items.interactions.InteractionTrophy;
import polaris.hotel.items.interactions.InteractionVendingMachine;
import polaris.hotel.items.interactions.InteractionWater;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.games.InteractionGameGate;
import polaris.hotel.items.interactions.games.InteractionGameScoreboard;
import polaris.hotel.items.interactions.games.InteractionGameTimer;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import polaris.hotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTile;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeBlock;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import polaris.hotel.items.interactions.games.freeze.InteractionFreezeTile;
import polaris.hotel.items.interactions.games.tag.InteractionTagField;
import polaris.hotel.items.interactions.games.tag.InteractionTagPole;
import polaris.hotel.items.interactions.pets.InteractionMonsterPlantSeed;
import polaris.hotel.items.interactions.pets.InteractionPetBreedingNest;
import polaris.hotel.items.interactions.pets.InteractionPetDrink;
import polaris.hotel.items.interactions.pets.InteractionPetFood;
import polaris.hotel.items.interactions.pets.InteractionPetToy;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredLegacyDataGuard;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

public class WiredEffectToggleFurni extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectToggleFurni.class);
    private static final int TOGGLE_TYPE_NEXT = 0;
    private static final int TOGGLE_TYPE_PREVIOUS = 1;

    public static final WiredEffectType type = WiredEffectType.TOGGLE_STATE;

    private final Set<UserItem> items;
    private int toggleType = TOGGLE_TYPE_NEXT;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    /** Furni AIR's Toggle State never changes, whatever their state count; no packet is sent for them. */
    static final Set<String> UNTOUCHED_NAMES = Set.of(
            "wf_floor_switch1",
            "wf_floor_switch2",
            "wf_antenna1",
            "wf_antenna2",
            "wf_pressureplate",
            "wf_arrowplate",
            "wf_box",
            "wf_pyramid",
            "wf_tile1",
            "wf_tile2");

    private static final List<Class<? extends UserItem>> FORBIDDEN_TYPES = new ArrayList<Class<? extends UserItem>>() {
        {
            this.add(InteractionWired.class);
            this.add(InteractionTeleport.class);
            this.add(InteractionPushable.class);
            this.add(InteractionTagPole.class);
            this.add(InteractionTagField.class);
            this.add(InteractionCrackable.class);
            this.add(InteractionGameScoreboard.class);
            this.add(InteractionGameGate.class);
            this.add(InteractionFreezeTile.class);
            this.add(InteractionFreezeBlock.class);
            this.add(InteractionFreezeExitTile.class);
            this.add(InteractionBattleBanzaiTeleporter.class);
            this.add(InteractionBattleBanzaiTile.class);
            this.add(InteractionMonsterPlantSeed.class);
            this.add(InteractionPetBreedingNest.class);
            this.add(InteractionPetDrink.class);
            this.add(InteractionPetFood.class);
            this.add(InteractionPetToy.class);
            this.add(InteractionBadgeDisplay.class);
            this.add(InteractionClothing.class);
            this.add(InteractionVendingMachine.class);
            this.add(InteractionGift.class);
            this.add(InteractionPressurePlate.class);
            this.add(InteractionMannequin.class);
            this.add(InteractionGymEquipment.class);
            this.add(InteractionHopper.class);
            this.add(InteractionObstacle.class);
            this.add(InteractionOneWayGate.class);
            this.add(InteractionPuzzleBox.class);
            this.add(InteractionRoller.class);
            this.add(InteractionSwitch.class);
            this.add(InteractionTent.class);
            this.add(InteractionTrap.class);
            this.add(InteractionTrophy.class);
            this.add(InteractionWater.class);
        }
    };

    public WiredEffectToggleFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new CopyOnWriteArraySet<>();
    }

    public WiredEffectToggleFurni(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new CopyOnWriteArraySet<>();
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        // Snapshot items to avoid concurrent modification with execute() on room cycle thread
        List<UserItem> snapshot = new ArrayList<>(this.items);

        List<UserItem> invalidItems = new ArrayList<>();
        for (UserItem item : snapshot) {
            if (item.getRoomId() != this.getRoomId()
                    || Emulator.getGameEnvironment()
                                    .getRoomManager()
                                    .getRoom(this.getRoomId())
                                    .getUserItem(item.getId())
                            == null) invalidItems.add(item);
        }

        for (UserItem item : invalidItems) {
            this.items.remove(item);
        }

        List<UserItem> validItems = new ArrayList<>(snapshot);
        validItems.removeAll(invalidItems);

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(validItems.size());
        for (UserItem item : validItems) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.toggleType);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        // Official layout: [toggle type]; the furni source is the official source slot.
        this.toggleType = (params.length > 0) ? normalizeToggleType(params[0]) : TOGGLE_TYPE_NEXT;
        this.furniSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (itemsCount > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        // Picks are kept whatever the source, as in the official client: they are only used while the
        // source is "picked furni", and switching back to it finds them again.
        List<UserItem> newItems = new ArrayList<>();
        for (int i = 0; i < itemsCount; i++) {
            int itemId = settings.getFurniIds()[i];
            UserItem it = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .getRoom(this.getRoomId())
                    .getUserItem(itemId);

            if (it == null) throw new WiredSaveException(String.format("Item %s not found", itemId));

            newItems.add(it);
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        this.items.addAll(newItems);
        this.setDelay(delay);

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        User user = ctx.actor().map(unit -> room.getUser(unit)).orElse(null);

        // Snapshot this.items into a new list to avoid undefined behavior from concurrent access
        // (serializeWiredData can modify items from the network thread).
        List<UserItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        Set<UserItem> itemsToRemove = new HashSet<>();
        for (UserItem item : effectiveItems) {
            if (item == null || item.getRoomId() == 0) {
                itemsToRemove.add(item);
                continue;
            }

            // A wired box in the list flashes instead of changing state, and stays picked.
            if (item instanceof InteractionWired wired) {
                wired.flashBox(room);
                continue;
            }

            if (isUntouched(item) || isHeldByFurniOnTop(room, item)) {
                continue;
            }

            if (FORBIDDEN_TYPES.stream().anyMatch(a -> a.isAssignableFrom(item.getClass()))) {
                itemsToRemove.add(item);
                continue;
            }

            try {
                if (toggleStateCount(item) > 1 || item instanceof InteractionGameTimer) {
                    this.toggleItemState(room, user, item);
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeAll(itemsToRemove);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getDelay(),
                        new ArrayList<>(this.items)
                                .stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.toggleType,
                        this.furniSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredMovementPayloadGuard.delay(jsonData.delay));
            this.toggleType = normalizeToggleType(jsonData.toggleType);
            this.furniSource = WiredMovementPayloadGuard.furniSource(jsonData.furniSource);
            if (jsonData.itemIds != null)
                for (Integer id : jsonData.itemIds) {
                    if (id == null) continue;
                    UserItem item = room.getUserItem(id);

                    if (item instanceof InteractionFreezeBlock
                            || item instanceof InteractionFreezeTile
                            || item instanceof InteractionCrackable) {
                        continue;
                    }

                    if (item != null) {
                        this.items.add(item);
                    }
                }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] wiredDataOld = wiredData != null ? wiredData.split("\t") : new String[0];

            if (wiredDataOld.length >= 1) {
                this.setDelay(WiredLegacyDataGuard.parseDelay(wiredDataOld[0]));
            }
            if (wiredDataOld.length == 2) {
                if (wiredDataOld[1].contains(";")) {
                    for (UserItem item : WiredLegacyDataGuard.parseRoomItems(wiredDataOld[1], room)) {
                        if (item instanceof InteractionFreezeBlock
                                || item instanceof InteractionFreezeTile
                                || item instanceof InteractionCrackable) continue;

                        this.items.add(item);
                    }
                }
            }
            this.toggleType = TOGGLE_TYPE_NEXT;
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.toggleType = TOGGLE_TYPE_NEXT;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    private int normalizeToggleType(int value) {
        return (value == TOGGLE_TYPE_PREVIOUS) ? TOGGLE_TYPE_PREVIOUS : TOGGLE_TYPE_NEXT;
    }

    /**
     * The number of states the toggle cycles through. A room configuration switch is on/off, and the
     * queue speed controller steps through its own modes, whatever {@code interaction_modes_count}
     * their catalog row carries.
     */
    static int toggleStateCount(UserItem item) {
        if (item instanceof InteractionQueueSpeedControl) {
            return InteractionQueueSpeedControl.MODE_COUNT;
        }

        if (item instanceof InteractionRemoteSwitchControl) {
            return 2;
        }

        return item.getBaseItem().getStateCount();
    }

    static boolean isUntouched(UserItem item) {
        return UNTOUCHED_NAMES.contains(FurnitureUsagePolicy.baseName(item));
    }

    /**
     * A furni whose height follows its state does not change while furni stand higher on any of its tiles,
     * as AIR refuses it (the same rule as a double-click).
     */
    static boolean isHeldByFurniOnTop(Room room, UserItem item) {
        Item base = item.getBaseItem();

        if (room == null || room.getLayout() == null || base == null) {
            return false;
        }

        double[] heights = base.getMultiHeights();

        if (heights == null || heights.length <= 1 || toggleStateCount(item) <= 1) {
            return false;
        }

        RoomTile origin = room.getLayout().getTile(item.getX(), item.getY());

        if (origin == null) {
            return false;
        }

        for (RoomTile tile :
                room.getLayout().getTilesAt(origin, base.getWidth(), base.getLength(), item.getRotation())) {
            for (UserItem other : room.getItemsAt(tile)) {
                if (other != item && other.getZ() > item.getZ()) {
                    return true;
                }
            }
        }

        return false;
    }

    private void toggleItemState(Room room, User user, UserItem item) throws Exception {
        if (item instanceof InteractionQueueSpeedControl queueSpeed) {
            // Its state also carries the animation frame, so it steps a whole mode, never a frame.
            // AIR shows the next mode group, but the rollers keep the step time a user chose.
            queueSpeed.wiredStepMode(room, this.toggleType != TOGGLE_TYPE_PREVIOUS);
            WiredManager.triggerFurniStateUpdated(room, (user != null) ? user.getRoomUnit() : null, item, true);
            return;
        }

        int stateCount = toggleStateCount(item);

        if (stateCount <= 1) {
            return;
        }
        int currentState = 0;

        if (!item.getExtradata().isEmpty()) {
            try {
                currentState = Integer.parseInt(item.getExtradata());
            } catch (NumberFormatException ignored) {
                if (this.toggleType == TOGGLE_TYPE_NEXT) {
                    item.onClick(user != null ? user.getClient() : null, room, new Object[] {0, this.getType()});
                }
                return;
            }
        }

        int nextState = (this.toggleType == TOGGLE_TYPE_PREVIOUS)
                ? ((currentState - 1 + stateCount) % stateCount)
                : ((currentState + 1) % stateCount);

        if (currentState == nextState) {
            return;
        }

        item.setExtradata(Integer.toString(nextState));
        item.needsUpdate(true);
        room.updateItemState(item);
        WiredManager.triggerFurniStateUpdated(room, (user != null) ? user.getRoomUnit() : null, item, true);
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int toggleType;
        int furniSource;

        public JsonData(int delay, List<Integer> itemIds, int toggleType, int furniSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.toggleType = toggleType;
            this.furniSource = furniSource;
        }
    }
}

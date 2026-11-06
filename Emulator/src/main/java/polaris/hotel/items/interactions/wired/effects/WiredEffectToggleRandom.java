package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

public class WiredEffectToggleRandom extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectToggleRandom.class);

    public static final WiredEffectType type = WiredEffectType.TOGGLE_RANDOM;

    private final Set<UserItem> items = new LinkedHashSet<>();
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    /** Floor switches AIR does randomise, although their class is otherwise excluded. */
    static final Set<String> RANDOMISED_SWITCHES = Set.of("wf_floor_switch1", "wf_floor_switch2");

    /** Game furni AIR's Toggle Random never changes; they are skipped and stay picked. */
    static final Set<String> UNTOUCHED_NAMES = Set.of(
            "wf_tile1",
            "wf_tile2",
            "wf_pyramid",
            "wf_box",
            "wf_arrowplate",
            "wf_arrowplate_8dir",
            "wf_pressureplate",
            "wf_ringplate",
            "wf_antenna1",
            "wf_antenna2",
            "wf_blob",
            "wf_blob2",
            "wf_button",
            "wf_button_red",
            "wf_button_x");

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

    public WiredEffectToggleRandom(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectToggleRandom(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<UserItem> itemsSnapshot = new ArrayList<>(this.items);
        Set<UserItem> items = new HashSet<>();

        for (UserItem item : itemsSnapshot) {
            if (item.getRoomId() != this.getRoomId()
                    || Emulator.getGameEnvironment()
                                    .getRoomManager()
                                    .getRoom(this.getRoomId())
                                    .getUserItem(item.getId())
                            == null) items.add(item);
        }

        for (UserItem item : items) {
            this.items.remove(item);
        }

        itemsSnapshot = new ArrayList<>(this.items);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (UserItem item : itemsSnapshot) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
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
        // Official: no int params; the furni source is the official source slot.
        this.furniSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (itemsCount > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        List<UserItem> newItems = new ArrayList<>();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < itemsCount; i++) {
                int itemId = settings.getFurniIds()[i];
                UserItem it = Emulator.getGameEnvironment()
                        .getRoomManager()
                        .getRoom(this.getRoomId())
                        .getUserItem(itemId);

                if (it == null) throw new WiredSaveException(String.format("Item %s not found", itemId));

                newItems.add(it);
            }
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.addAll(newItems);
        }
        this.setDelay(delay);

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        List<UserItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        for (UserItem item : effectiveItems) {
            if (item.getRoomId() == 0) {
                if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
                    this.items.remove(item);
                }
                continue;
            }

            if (!isRandomised(item)) {
                continue;
            }

            try {
                // States run 0..stateCount-1; the old "+ 1" could write stateCount itself, a state
                // the furni does not have.
                int stateCount = item.getBaseItem().getStateCount();
                String next = Integer.toString(Emulator.getRandom().nextInt(Math.max(1, stateCount)));

                // AIR writes and sends nothing when the pick is the state the furni already shows.
                if (next.equals(item.getExtradata())) {
                    continue;
                }

                item.setExtradata(next);
                item.needsUpdate(true);
                room.updateItem(item);
                WiredManager.triggerFurniStateUpdated(room, ctx.actor().orElse(null), item, true);
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    static boolean isRandomised(UserItem item) {
        String name = FurnitureUsagePolicy.baseName(item);

        if (RANDOMISED_SWITCHES.contains(name)) {
            return true;
        }

        return !UNTOUCHED_NAMES.contains(name)
                && !(item instanceof InteractionGameTimer)
                && FORBIDDEN_TYPES.stream().noneMatch(a -> a.isAssignableFrom(item.getClass()));
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<UserItem> itemsSnapshot = new ArrayList<>(this.items);
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getDelay(),
                        itemsSnapshot.stream().map(UserItem::getId).collect(Collectors.toList()),
                        this.furniSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredMovementPayloadGuard.delay(jsonData.delay));
            this.furniSource = WiredMovementPayloadGuard.furniSource(jsonData.furniSource);
            if (jsonData.itemIds != null)
                for (Integer id : jsonData.itemIds) {
                    if (id == null) continue;
                    UserItem item = room.getUserItem(id);

                    if (item instanceof InteractionFreezeBlock
                            || item instanceof InteractionGameTimer
                            || item instanceof InteractionCrackable) continue;

                    if (item != null) this.items.add(item);
                }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] wiredDataOld = wiredData != null ? wiredData.split("\t") : new String[0];

            if (wiredDataOld.length >= 1) {
                this.setDelay(WiredMovementPayloadGuard.parseDelay(wiredDataOld[0]));
            }
            if (wiredDataOld.length == 2) {
                if (wiredDataOld[1].contains(";")) {
                    for (String s : wiredDataOld[1].split(";")) {
                        int itemId = WiredMovementPayloadGuard.parseInt(s, 0);
                        UserItem item = itemId > 0 ? room.getUserItem(itemId) : null;

                        if (item instanceof InteractionFreezeBlock
                                || item instanceof InteractionGameTimer
                                || item instanceof InteractionCrackable) continue;

                        if (item != null) this.items.add(item);
                    }
                }
            }
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int furniSource;

        public JsonData(int delay, List<Integer> itemIds, int furniSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.furniSource = furniSource;
        }
    }
}

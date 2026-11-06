package polaris.hotel.wired.variablefx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.bots.Bot;
import polaris.hotel.games.GamePlayer;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableFx;
import polaris.hotel.pets.Pet;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.variables.RoomWiredVariableCatalog;
import polaris.hotel.rooms.variables.UserVariableHolders;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredVariableAddonSharing;
import polaris.hotel.wired.core.WiredVariableFxLimit;
import polaris.hotel.wired.tick.WiredTickable;
import polaris.messages.outgoing.wired.WiredVariableFxConfigsComposer;
import polaris.messages.outgoing.wired.WiredVariableFxConfigsRemovedComposer;
import polaris.messages.outgoing.wired.WiredVariableFxStatusComposer;
import polaris.messages.outgoing.wired.WiredVariableFxStatusRemovedComposer;

/**
 * Shows wired variables over avatars and furni. The variable fx boxes in the room say how an fx
 * looks and who may see it; this works out, per player in the room, which values that player
 * should be seeing, and sends only what changed since it last told them.
 *
 * <p>Everything is recomputed from the room as it is on every flush, so there is no per-change
 * bookkeeping to go wrong; what was sent is remembered per viewer and the difference goes out as a
 * batch. It rides the wired tick service; a room without fx boxes and without viewers lets go of
 * itself.
 */
public final class WiredVariableFxService implements WiredTickable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVariableFxService.class);

    /** The tick service's ids are furni ids; this is not one, so it uses a seat no furni has. */
    private static final int SERVICE_ID = -7_000_000;
    /** How often the diffs go out; a quarter second reads as live without flooding the room. */
    private static final int FLUSH_MS = 250;
    /** Statuses per viewer beyond which the rest are ignored; the fx box cap is {@link WiredVariableFxLimit}. */
    private static final int MAX_STATUSES_PER_VIEWER = 500;

    private final int roomId;
    private final Map<Integer, String> configSignatures = new HashMap<>();
    private final Map<Integer, WiredVariableFxViewer> viewers = new HashMap<>();
    /** The room's own fx boxes past the cap at the last flush. */
    private int leftOutBoxes;
    /** Set from any thread by a Share box's Synchronize; the next flush starts every viewer over. */
    private volatile boolean resyncRequested;

    /**
     * An fx box and the variable box it shows: its own tile's, or the reference importing it. {@code variableIds}
     * turns the variable ids the box names (range overrides, audience) into this room's.
     */
    record Shown(WiredExtraVariableFx box, InteractionWiredExtra variableBox, IntUnaryOperator variableIds) {
        Shown(WiredExtraVariableFx box, InteractionWiredExtra variableBox) {
            this(box, variableBox, IntUnaryOperator.identity());
        }
    }

    public WiredVariableFxService(int roomId) {
        this.roomId = roomId;
    }

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        int ticksPerFlush = Math.max(1, flushMs() / Math.max(1, tickIntervalMs));
        if (tickCount % ticksPerFlush != 0) return;

        try {
            this.flush(room);
        } catch (Exception e) {
            LOGGER.error("Wired variable fx flush failed in room {}", this.roomId, e);
        }
    }

    @Override
    public void resetTimer() {}

    @Override
    public int getId() {
        return SERVICE_ID - this.roomId;
    }

    @Override
    public int getRoomId() {
        return this.roomId;
    }

    /** Every viewer gets the configs and a full status sync again on the next flush. */
    void requestResync() {
        this.resyncRequested = true;
    }

    /** One pass: the configs, then the statuses, then the diffs to every viewer. */
    void flush(Room room) {
        if (room == null) return;

        if (this.resyncRequested) {
            this.resyncRequested = false;
            this.configSignatures.clear();
            this.viewers.clear();
        }

        List<Shown> boxes = fxBoxes(room);
        // Boxes past the cap are left out; the monitor counts it once per box over (VARIABLE_FX_LIMIT).
        this.leftOutBoxes = boxes.size() < maxBoxes() ? 0 : WiredVariableFxLimit.noteLeftOut(room, this.leftOutBoxes);
        List<User> players = new ArrayList<>();
        for (User user : room.getUsers()) {
            if (user != null && user.getClient() != null && user.getRoomUnit() != null) players.add(user);
        }

        if (boxes.isEmpty() && this.viewers.isEmpty() && !WiredVariableAddonSharing.importsFx(room)) {
            WiredVariableFxSupport.release(room, this);
            return;
        }

        Set<Integer> resentConfigs = this.syncConfigs(boxes, players);
        this.syncStatuses(room, boxes, players, resentConfigs);
    }

    /** Sends the configs that changed; returns the ids of those resent, whose statuses go out again as initialized. */
    private Set<Integer> syncConfigs(List<Shown> boxes, List<User> players) {
        Map<Integer, WiredVariableFxConfig> configs = new LinkedHashMap<>();
        for (Shown shown : boxes) {
            configs.put(shown.box().getId(), shown.box().buildConfig());
        }

        List<Integer> removed = new ArrayList<>();
        for (Integer configId : this.configSignatures.keySet()) {
            if (!configs.containsKey(configId)) removed.add(configId);
        }
        List<WiredVariableFxConfig> changed = new ArrayList<>();
        for (WiredVariableFxConfig config : configs.values()) {
            String known = this.configSignatures.get(config.configId());
            if (known == null || !known.equals(config.signature())) changed.add(config);
        }
        for (Integer configId : removed) this.configSignatures.remove(configId);
        for (WiredVariableFxConfig config : configs.values())
            this.configSignatures.put(config.configId(), config.signature());

        // Viewers who left take their state with them; newcomers get every config first, since a
        // status for a config the client does not have draws nothing.
        Set<Integer> present = new HashSet<>();
        for (User user : players) {
            int userId = user.getUserInfo().getId();
            present.add(userId);

            if (!this.viewers.containsKey(userId)) {
                this.viewers.put(userId, new WiredVariableFxViewer());
                if (!configs.isEmpty())
                    user.getClient()
                            .sendResponse(new WiredVariableFxConfigsComposer(new ArrayList<>(configs.values())));
                continue;
            }

            if (!removed.isEmpty()) user.getClient().sendResponse(new WiredVariableFxConfigsRemovedComposer(removed));
            if (!changed.isEmpty()) user.getClient().sendResponse(new WiredVariableFxConfigsComposer(changed));
        }
        this.viewers.keySet().removeIf(userId -> !present.contains(userId));

        Set<Integer> resent = new HashSet<>();
        for (WiredVariableFxConfig config : changed) resent.add(config.configId());
        return resent;
    }

    private void syncStatuses(Room room, List<Shown> boxes, List<User> players, Set<Integer> resentConfigs) {
        Map<Integer, Map<WiredVariableFxStatus.Key, WiredVariableFxStatus>> wanted = new HashMap<>();
        for (User user : players) wanted.put(user.getUserInfo().getId(), new LinkedHashMap<>());

        int cap = maxStatusesPerViewer();

        for (Shown shown : boxes) {
            this.collect(room, shown, players, wanted, cap);
        }

        for (User user : players) {
            WiredVariableFxViewer viewer = this.viewers.get(user.getUserInfo().getId());
            if (viewer == null) continue;

            WiredVariableFxViewer.Batch batch =
                    viewer.diff(wanted.get(user.getUserInfo().getId()), resentConfigs);
            if (!batch.removed().isEmpty())
                user.getClient().sendResponse(new WiredVariableFxStatusRemovedComposer(batch.removed()));
            if (!batch.updates().isEmpty()) {
                user.getClient()
                        .sendResponse(new WiredVariableFxStatusComposer(batch.initializeAll(), batch.updates()));
            }
        }
    }

    /** Every holder of one fx's variable, handed to the viewers allowed to see it. */
    private void collect(
            Room room,
            Shown shown,
            List<User> players,
            Map<Integer, Map<WiredVariableFxStatus.Key, WiredVariableFxStatus>> wanted,
            int cap) {
        WiredExtraVariableFx box = shown.box();
        InteractionWiredExtra variableBox = shown.variableBox();
        if (variableBox == null) return;

        int definitionId = variableBox.getId();
        Set<Integer> audience = audienceOf(room, box, shown.variableIds(), players);

        if (box.isUserFx()) {
            String variableId = RoomWiredVariableCatalog.variableId(RoomWiredVariableCatalog.TARGET_USER, definitionId);

            for (User holder : players) {
                this.collectUser(
                        room,
                        shown,
                        variableId,
                        holder.getUserInfo().getId(),
                        holder.getRoomUnit(),
                        holder,
                        players,
                        audience,
                        wanted,
                        cap);
            }
            for (Pet pet : unitsOf(room.getCurrentPets())) {
                if (pet == null) continue;
                this.collectUser(
                        room,
                        shown,
                        variableId,
                        UserVariableHolders.ofPet(pet.getId()),
                        pet.getRoomUnit(),
                        null,
                        players,
                        audience,
                        wanted,
                        cap);
            }
            for (Bot bot : unitsOf(room.getCurrentBots())) {
                if (bot == null) continue;
                this.collectUser(
                        room,
                        shown,
                        variableId,
                        UserVariableHolders.ofBot(bot.getId()),
                        bot.getRoomUnit(),
                        null,
                        players,
                        audience,
                        wanted,
                        cap);
            }
            return;
        }

        // The client can only draw over floor furni.
        String variableId = RoomWiredVariableCatalog.variableId(RoomWiredVariableCatalog.TARGET_FURNI, definitionId);
        Collection<UserItem> items = room.getFloorItems();
        if (items == null) return;

        for (UserItem item : items) {
            if (item == null || !room.getFurniVariableManager().hasVariable(item.getId(), definitionId)) continue;

            long value = room.getFurniVariableManager().getCurrentValue(item.getId(), definitionId);
            WiredVariableFxStatus.Key key = new WiredVariableFxStatus.Key(box.getId(), variableId, false, item.getId());
            WiredVariableFxStatus status =
                    box.resolveStatus(room, variableBox, key, item.getId(), value, null, shown.variableIds());

            for (User viewer : players) {
                if (!this.canSee(box, audience, viewer, null)) continue;
                Map<WiredVariableFxStatus.Key, WiredVariableFxStatus> statuses =
                        wanted.get(viewer.getUserInfo().getId());
                if (statuses != null && statuses.size() < cap) statuses.put(key, status);
            }
        }
    }

    /**
     * The viewers a "has variable" audience lets in, worked out once per fx; null for the
     * audiences that depend on nothing or on the holder.
     */
    static Set<Integer> audienceOf(
            Room room, WiredExtraVariableFx box, IntUnaryOperator variableIds, List<User> players) {
        int visibility = box.getVisibility();
        if (visibility != WiredExtraVariableFx.VISIBILITY_HAS_VARIABLE
                && visibility != WiredExtraVariableFx.VISIBILITY_HAS_VARIABLE_WITH_VALUE) {
            return null;
        }

        Set<Integer> audience = new HashSet<>();
        // With no variable chosen there is nothing a viewer could have, so nobody sees it.
        if (box.getAudienceItemId() <= 0) return audience;

        int definitionId = variableIds.applyAsInt(box.getAudienceItemId());

        for (User viewer : players) {
            int userId = viewer.getUserInfo().getId();
            if (!room.getUserVariableManager().hasVariable(userId, definitionId)) continue;

            if (visibility == WiredExtraVariableFx.VISIBILITY_HAS_VARIABLE
                    || room.getUserVariableManager().getCurrentValue(userId, definitionId) == box.getAudienceValue()) {
                audience.add(userId);
            }
        }

        return audience;
    }

    /** One holder of a user fx; pets and bots have no Habbo, so only-user and team fx skip them. */
    private void collectUser(
            Room room,
            Shown shown,
            String variableId,
            int holderKey,
            RoomUnit unit,
            User user,
            List<User> players,
            Set<Integer> audience,
            Map<Integer, Map<WiredVariableFxStatus.Key, WiredVariableFxStatus>> wanted,
            int cap) {
        WiredExtraVariableFx box = shown.box();
        InteractionWiredExtra variableBox = shown.variableBox();
        int definitionId = variableBox.getId();
        if (unit == null || !room.getUserVariableManager().hasVariable(holderKey, definitionId)) return;

        long value = room.getUserVariableManager().getCurrentValue(holderKey, definitionId);
        WiredVariableFxStatus.Key key = new WiredVariableFxStatus.Key(box.getId(), variableId, true, unit.getId());
        WiredVariableFxStatus status = box.resolveStatus(
                room, variableBox, key, holderKey, value, user != null ? teamColorOf(user) : null, shown.variableIds());

        for (User viewer : players) {
            if (!this.canSee(box, audience, viewer, user)) continue;
            Map<WiredVariableFxStatus.Key, WiredVariableFxStatus> statuses =
                    wanted.get(viewer.getUserInfo().getId());
            if (statuses != null && statuses.size() < cap) statuses.put(key, status);
        }
    }

    private boolean canSee(WiredExtraVariableFx box, Set<Integer> audience, User viewer, User holder) {
        if (audience != null) return audience.contains(viewer.getUserInfo().getId());

        switch (box.getVisibility()) {
            case WiredExtraVariableFx.VISIBILITY_ONLY_USER:
                return holder != null
                        && holder.getUserInfo().getId() == viewer.getUserInfo().getId();
            case WiredExtraVariableFx.VISIBILITY_GAME_TEAM: {
                if (holder == null) return false;
                if (holder.getUserInfo().getId() == viewer.getUserInfo().getId()) return true;

                GamePlayer holderPlayer = holder.getUserInfo().getGamePlayer();
                GamePlayer viewerPlayer = viewer.getUserInfo().getGamePlayer();
                return holderPlayer != null
                        && viewerPlayer != null
                        && holderPlayer.getTeamColor() != null
                        && holderPlayer.getTeamColor() == viewerPlayer.getTeamColor();
            }
            default:
                return true;
        }
    }

    private static <T> Collection<T> unitsOf(it.unimi.dsi.fastutil.ints.Int2ObjectMap<T> units) {
        return units != null ? units.values() : List.of();
    }

    private static String teamColorOf(User user) {
        GamePlayer player = user.getUserInfo().getGamePlayer();
        return (player != null) ? WiredVariableFxStyles.teamColor(player.getTeamColor()) : null;
    }

    /**
     * The room's own fx boxes, then the fx its references import from other rooms (an imported box draws the
     * reference's values), together at most {@link #maxBoxes()}.
     */
    static List<Shown> fxBoxes(Room room) {
        List<Shown> boxes = new ArrayList<>();
        if (room.getRoomSpecialTypes() == null) return boxes;

        int max = maxBoxes();
        // The limit counts fx boxes; one box shows every variable of its kind on its tile.
        int fxBoxes = 0;
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableFx box) {
                List<InteractionWiredExtra> variables = box.getShownVariableBoxes(room);
                if (variables.isEmpty()) {
                    boxes.add(new Shown(box, null));
                } else {
                    for (InteractionWiredExtra variable : variables) boxes.add(new Shown(box, variable));
                }
                if (++fxBoxes >= max) return boxes;
            }
        }
        for (WiredVariableAddonSharing.ImportedFx imported :
                WiredVariableAddonSharing.importedFx(room, max - fxBoxes)) {
            boxes.add(new Shown(imported.box(), imported.reference(), imported::localId));
        }
        return boxes;
    }

    static int flushMs() {
        return FLUSH_MS;
    }

    static int maxBoxes() {
        return WiredVariableFxLimit.maxBoxes();
    }

    static int maxStatusesPerViewer() {
        return MAX_STATUSES_PER_VIEWER;
    }
}

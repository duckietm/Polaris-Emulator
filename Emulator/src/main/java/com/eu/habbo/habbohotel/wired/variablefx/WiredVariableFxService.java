package com.eu.habbo.habbohotel.wired.variablefx;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFx;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.outgoing.wired.WiredVariableFxConfigsComposer;
import com.eu.habbo.messages.outgoing.wired.WiredVariableFxConfigsRemovedComposer;
import com.eu.habbo.messages.outgoing.wired.WiredVariableFxStatusComposer;
import com.eu.habbo.messages.outgoing.wired.WiredVariableFxStatusRemovedComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    /** Fx boxes per room beyond which the rest are ignored, and statuses per viewer likewise. */
    private static final int MAX_BOXES = 25;

    private static final int MAX_STATUSES_PER_VIEWER = 500;

    private final int roomId;
    private final Map<Integer, String> configSignatures = new HashMap<>();
    private final Map<Integer, WiredVariableFxViewer> viewers = new HashMap<>();

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

    /** One pass: the configs, then the statuses, then the diffs to every viewer. */
    void flush(Room room) {
        if (room == null) return;

        List<WiredExtraVariableFx> boxes = fxBoxes(room);
        List<Habbo> players = new ArrayList<>();
        for (Habbo habbo : room.getHabbos()) {
            if (habbo != null && habbo.getClient() != null && habbo.getRoomUnit() != null) players.add(habbo);
        }

        if (boxes.isEmpty() && this.viewers.isEmpty()) {
            WiredVariableFxSupport.release(room, this);
            return;
        }

        this.syncConfigs(boxes, players);
        this.syncStatuses(room, boxes, players);
    }

    private void syncConfigs(List<WiredExtraVariableFx> boxes, List<Habbo> players) {
        Map<Integer, WiredVariableFxConfig> configs = new LinkedHashMap<>();
        for (WiredExtraVariableFx box : boxes) {
            configs.put(box.getId(), box.buildConfig());
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
        for (Habbo habbo : players) {
            int userId = habbo.getHabboInfo().getId();
            present.add(userId);

            if (!this.viewers.containsKey(userId)) {
                this.viewers.put(userId, new WiredVariableFxViewer());
                if (!configs.isEmpty())
                    habbo.getClient()
                            .sendResponse(new WiredVariableFxConfigsComposer(new ArrayList<>(configs.values())));
                continue;
            }

            if (!removed.isEmpty()) habbo.getClient().sendResponse(new WiredVariableFxConfigsRemovedComposer(removed));
            if (!changed.isEmpty()) habbo.getClient().sendResponse(new WiredVariableFxConfigsComposer(changed));
        }
        this.viewers.keySet().removeIf(userId -> !present.contains(userId));
    }

    private void syncStatuses(Room room, List<WiredExtraVariableFx> boxes, List<Habbo> players) {
        Map<Integer, Map<WiredVariableFxStatus.Key, WiredVariableFxStatus>> wanted = new HashMap<>();
        for (Habbo habbo : players) wanted.put(habbo.getHabboInfo().getId(), new LinkedHashMap<>());

        int cap = maxStatusesPerViewer();

        for (WiredExtraVariableFx box : boxes) {
            this.collect(room, box, players, wanted, cap);
        }

        for (Habbo habbo : players) {
            WiredVariableFxViewer viewer = this.viewers.get(habbo.getHabboInfo().getId());
            if (viewer == null) continue;

            WiredVariableFxViewer.Batch batch =
                    viewer.diff(wanted.get(habbo.getHabboInfo().getId()));
            if (!batch.removed().isEmpty())
                habbo.getClient().sendResponse(new WiredVariableFxStatusRemovedComposer(batch.removed()));
            if (!batch.updates().isEmpty()) {
                habbo.getClient()
                        .sendResponse(new WiredVariableFxStatusComposer(batch.initializeAll(), batch.updates()));
            }
        }
    }

    /** Every holder of one fx's variable, handed to the viewers allowed to see it. */
    private void collect(
            Room room,
            WiredExtraVariableFx box,
            List<Habbo> players,
            Map<Integer, Map<WiredVariableFxStatus.Key, WiredVariableFxStatus>> wanted,
            int cap) {
        InteractionWiredExtra variableBox = box.getShownVariableBox(room);
        if (variableBox == null) return;

        int definitionId = variableBox.getId();
        Set<Integer> audience = this.audienceOf(room, box, players);

        if (box.isUserFx()) {
            String variableId = RoomWiredVariableCatalog.variableId(RoomWiredVariableCatalog.TARGET_USER, definitionId);

            for (Habbo holder : players) {
                this.collectUser(
                        room,
                        box,
                        variableBox,
                        variableId,
                        holder.getHabboInfo().getId(),
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
                        box,
                        variableBox,
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
                        box,
                        variableBox,
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
        Collection<HabboItem> items = room.getFloorItems();
        if (items == null) return;

        for (HabboItem item : items) {
            if (item == null || !room.getFurniVariableManager().hasVariable(item.getId(), definitionId)) continue;

            long value = room.getFurniVariableManager().getCurrentValue(item.getId(), definitionId);
            WiredVariableFxStatus.Key key = new WiredVariableFxStatus.Key(box.getId(), variableId, false, item.getId());
            WiredVariableFxStatus status = box.resolveStatus(room, variableBox, key, item.getId(), value, null);

            for (Habbo viewer : players) {
                if (!this.canSee(box, audience, viewer, null)) continue;
                Map<WiredVariableFxStatus.Key, WiredVariableFxStatus> statuses =
                        wanted.get(viewer.getHabboInfo().getId());
                if (statuses != null && statuses.size() < cap) statuses.put(key, status);
            }
        }
    }

    /**
     * The viewers a "has variable" audience lets in, worked out once per fx; null for the
     * audiences that depend on nothing or on the holder.
     */
    private Set<Integer> audienceOf(Room room, WiredExtraVariableFx box, List<Habbo> players) {
        int visibility = box.getVisibility();
        if (visibility != WiredExtraVariableFx.VISIBILITY_HAS_VARIABLE
                && visibility != WiredExtraVariableFx.VISIBILITY_HAS_VARIABLE_WITH_VALUE) {
            return null;
        }

        Set<Integer> audience = new HashSet<>();
        int definitionId = box.getAudienceItemId();

        // With no variable chosen there is nothing a viewer could have, so nobody sees it.
        if (definitionId <= 0) return audience;

        for (Habbo viewer : players) {
            int userId = viewer.getHabboInfo().getId();
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
            WiredExtraVariableFx box,
            InteractionWiredExtra variableBox,
            String variableId,
            int holderKey,
            RoomUnit unit,
            Habbo habbo,
            List<Habbo> players,
            Set<Integer> audience,
            Map<Integer, Map<WiredVariableFxStatus.Key, WiredVariableFxStatus>> wanted,
            int cap) {
        int definitionId = variableBox.getId();
        if (unit == null || !room.getUserVariableManager().hasVariable(holderKey, definitionId)) return;

        long value = room.getUserVariableManager().getCurrentValue(holderKey, definitionId);
        WiredVariableFxStatus.Key key = new WiredVariableFxStatus.Key(box.getId(), variableId, true, unit.getId());
        WiredVariableFxStatus status =
                box.resolveStatus(room, variableBox, key, holderKey, value, habbo != null ? teamColorOf(habbo) : null);

        for (Habbo viewer : players) {
            if (!this.canSee(box, audience, viewer, habbo)) continue;
            Map<WiredVariableFxStatus.Key, WiredVariableFxStatus> statuses =
                    wanted.get(viewer.getHabboInfo().getId());
            if (statuses != null && statuses.size() < cap) statuses.put(key, status);
        }
    }

    private boolean canSee(WiredExtraVariableFx box, Set<Integer> audience, Habbo viewer, Habbo holder) {
        if (audience != null) return audience.contains(viewer.getHabboInfo().getId());

        switch (box.getVisibility()) {
            case WiredExtraVariableFx.VISIBILITY_ONLY_USER:
                return holder != null
                        && holder.getHabboInfo().getId()
                                == viewer.getHabboInfo().getId();
            case WiredExtraVariableFx.VISIBILITY_GAME_TEAM: {
                if (holder == null) return false;
                if (holder.getHabboInfo().getId() == viewer.getHabboInfo().getId()) return true;

                GamePlayer holderPlayer = holder.getHabboInfo().getGamePlayer();
                GamePlayer viewerPlayer = viewer.getHabboInfo().getGamePlayer();
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

    private static String teamColorOf(Habbo habbo) {
        GamePlayer player = habbo.getHabboInfo().getGamePlayer();
        return (player != null) ? WiredVariableFxStyles.teamColor(player.getTeamColor()) : null;
    }

    static List<WiredExtraVariableFx> fxBoxes(Room room) {
        List<WiredExtraVariableFx> boxes = new ArrayList<>();
        if (room.getRoomSpecialTypes() == null) return boxes;

        int max = maxBoxes();
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableFx box) {
                boxes.add(box);
                if (boxes.size() >= max) break;
            }
        }
        return boxes;
    }

    static int flushMs() {
        return FLUSH_MS;
    }

    static int maxBoxes() {
        return MAX_BOXES;
    }

    static int maxStatusesPerViewer() {
        return MAX_STATUSES_PER_VIEWER;
    }
}

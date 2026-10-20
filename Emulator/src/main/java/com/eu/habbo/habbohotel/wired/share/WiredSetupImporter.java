package com.eu.habbo.habbohotel.wired.share;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLargePayload;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.rooms.BuildersClubRoomSupport;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredDefinitionWire;
import com.eu.habbo.habbohotel.wired.protocol.WiredHolders;
import com.eu.habbo.habbohotel.wired.protocol.WiredSourceSaveRules;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plans and applies a wired setup import.
 *
 * <p>An import never creates furni. Each exported box is matched to a wired box of the same furni type
 * that the importer placed on the target tile, or, when asked, to one of the same type taken from the
 * importer's own inventory and placed there with the room's normal placement rules. The matched box is
 * then saved with the exported editor state through its own save path (source rules, the box's save
 * and its refusals), exactly as if the importer had opened and saved it. Picks of furni that cannot be
 * found in this room and variables of boxes that were not imported are dropped.
 */
final class WiredSetupImporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredSetupImporter.class);

    static final int ENTRY_CONFIGURE = 1;
    static final int ENTRY_PLACE = 2;
    static final int ENTRY_MISSING = 3;
    static final int ENTRY_REFUSED = 4;
    static final int ENTRY_BLOCKED = 5;
    static final int ENTRY_UNSUPPORTED = 6;
    static final int ENTRY_UNCHANGED = 7;

    private static final int MAX_REASON_LENGTH = 200;
    private static final Pattern CUSTOM_REFERENCE = Pattern.compile("custom:#(\\d{1,4})");
    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("(custom:|user:|furni:|room:|ctx:)?#(\\d{1,4})");

    /** Variables first (others reference them), then addons, then the stacks. */
    private static final List<WiredDefinitionWire.Holder> SAVE_ORDER = List.of(
            WiredDefinitionWire.Holder.VARIABLE,
            WiredDefinitionWire.Holder.ADDON,
            WiredDefinitionWire.Holder.SELECTOR,
            WiredDefinitionWire.Holder.TRIGGER,
            WiredDefinitionWire.Holder.CONDITION,
            WiredDefinitionWire.Holder.ACTION);

    private static final ClassValue<Boolean> LEGACY_CONDITION = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method typed = type.getMethod("saveData", WiredSettings.class);
                return Modifier.isAbstract(typed.getModifiers());
            } catch (NoSuchMethodException e) {
                return Boolean.TRUE;
            }
        }
    };

    /** The import report; sent to the client as JSON. */
    static final class Report {
        boolean confirmed;
        int status;
        int anchorX;
        int anchorY;
        int total;
        int configured;
        int placed;
        int missing;
        int refused;
        List<Entry> entries = new ArrayList<>();
    }

    static final class Entry {
        int index;
        String type;
        String holder;
        int x;
        int y;
        int status;
        int itemId;
        String reason = "";

        transient WiredSetupDocument.Box box;
        transient InteractionWired target;
        transient HabboItem inventoryItem;
        transient RoomTile tile;
    }

    private final Room room;
    private final GameClient client;
    private final Habbo habbo;
    private final WiredSetupDocument document;
    private final Report report = new Report();
    /** Exported box index to the box it was matched to (or placed as) in this room. */
    final Map<Integer, InteractionWired> resolved = new HashMap<>();

    WiredSetupImporter(Room room, GameClient client, WiredSetupDocument document) {
        this.room = room;
        this.client = client;
        this.habbo = client.getHabbo();
        this.document = document;
    }

    Report run(int anchorMode, boolean placeFromInventory, boolean confirm) {
        this.report.confirmed = confirm;
        int anchorX;
        int anchorY;
        if (anchorMode == WiredSetupShare.ANCHOR_AVATAR) {
            RoomTile location =
                    this.habbo.getRoomUnit() != null ? this.habbo.getRoomUnit().getCurrentLocation() : null;
            if (location == null) {
                this.report.status = WiredSetupShare.STATUS_NO_ANCHOR;
                return this.report;
            }
            anchorX = location.x;
            anchorY = location.y;
        } else {
            anchorX = this.document.anchorX;
            anchorY = this.document.anchorY;
        }
        this.report.anchorX = anchorX;
        this.report.anchorY = anchorY;
        this.report.total = this.document.boxes.size();

        this.plan(anchorX, anchorY, placeFromInventory);
        if (confirm) this.apply(anchorX, anchorY);
        this.count();
        return this.report;
    }

    private void plan(int anchorX, int anchorY, boolean placeFromInventory) {
        Map<Long, List<InteractionWired>> ownBoxes = this.ownBoxesByTile();
        Set<Integer> claimed = new HashSet<>();
        List<HabboItem> inventory = placeFromInventory ? this.inventoryBoxes() : List.of();
        boolean mayPlace = placeFromInventory && this.room.hasRights(this.habbo);

        for (int index = 0; index < this.document.boxes.size(); index++) {
            WiredSetupDocument.Box box = this.document.boxes.get(index);
            Entry entry = new Entry();
            entry.index = index;
            entry.box = box;
            this.report.entries.add(entry);

            String invalid = validate(box);
            if (invalid != null) {
                entry.type =
                        box == null || box.type == null ? "" : truncate(box.type, WiredSetupDocument.MAX_TYPE_LENGTH);
                entry.holder = box == null || box.holder == null ? "" : truncate(box.holder, 20);
                refuse(entry, ENTRY_REFUSED, invalid);
                continue;
            }
            entry.type = box.type;
            entry.holder = box.holder;
            entry.x = anchorX + box.dx;
            entry.y = anchorY + box.dy;

            RoomTile tile = entry.x >= 0 && entry.y >= 0 && entry.x <= Short.MAX_VALUE && entry.y <= Short.MAX_VALUE
                    ? this.room.getLayout().getTile((short) entry.x, (short) entry.y)
                    : null;
            if (tile == null || tile.state == RoomTileState.INVALID) {
                refuse(entry, ENTRY_BLOCKED, "wiredmenu.share.import.reason.tile");
                continue;
            }
            entry.tile = tile;

            InteractionWired existing = null;
            for (InteractionWired candidate : ownBoxes.getOrDefault(tileKey(entry.x, entry.y), List.of())) {
                if (!claimed.contains(candidate.getId())
                        && box.type.equals(candidate.getBaseItem().getName())) {
                    existing = candidate;
                    break;
                }
            }

            if (existing != null) {
                claimed.add(existing.getId());
                String unsupported = this.unsupported(existing, box);
                if (unsupported != null) {
                    refuse(entry, ENTRY_UNSUPPORTED, unsupported);
                    continue;
                }
                entry.target = existing;
                entry.itemId = existing.getId();
                entry.status = box.fresh ? ENTRY_UNCHANGED : ENTRY_CONFIGURE;
                this.resolved.put(index, existing);
                continue;
            }

            HabboItem fromInventory = null;
            if (placeFromInventory) {
                for (HabboItem candidate : inventory) {
                    if (!claimed.contains(candidate.getId())
                            && box.type.equals(candidate.getBaseItem().getName())) {
                        fromInventory = candidate;
                        break;
                    }
                }
            }
            if (fromInventory == null) {
                refuse(entry, ENTRY_MISSING, "");
                continue;
            }
            if (!mayPlace) {
                refuse(entry, ENTRY_BLOCKED, "wiredmenu.share.import.reason.rights");
                continue;
            }
            String unsupported = this.unsupported((InteractionWired) fromInventory, box);
            if (unsupported != null) {
                refuse(entry, ENTRY_UNSUPPORTED, unsupported);
                continue;
            }
            claimed.add(fromInventory.getId());
            entry.inventoryItem = fromInventory;
            entry.itemId = fromInventory.getId();
            entry.status = ENTRY_PLACE;
        }
    }

    private void apply(int anchorX, int anchorY) {
        boolean changed = false;

        for (Entry entry : this.report.entries) {
            if (entry.status != ENTRY_PLACE) continue;
            String placementError = this.place(entry);
            if (placementError != null) {
                refuse(entry, ENTRY_BLOCKED, placementError);
                continue;
            }
            changed = true;
            entry.target = (InteractionWired) entry.inventoryItem;
            this.resolved.put(entry.index, entry.target);
        }

        List<Entry> toSave = new ArrayList<>();
        for (Entry entry : this.report.entries) {
            if ((entry.status == ENTRY_CONFIGURE || entry.status == ENTRY_PLACE) && !entry.box.fresh) toSave.add(entry);
        }
        toSave.sort(Comparator.comparingInt(entry -> SAVE_ORDER.indexOf(WiredHolders.of(entry.target))));

        List<Entry> retry = new ArrayList<>();
        for (Entry entry : toSave) {
            String refusal = this.save(entry, anchorX, anchorY);
            if (refusal == null) {
                changed = true;
            } else {
                entry.reason = truncate(refusal, MAX_REASON_LENGTH);
                retry.add(entry);
            }
        }
        // A box that references another one can be refused while that one is not saved yet: try once more.
        for (Entry entry : retry) {
            String refusal = this.save(entry, anchorX, anchorY);
            if (refusal == null) {
                entry.reason = "";
                changed = true;
            } else {
                // A placed box stays in the room (its item id is in the entry); only its settings were refused.
                entry.reason = truncate(refusal, MAX_REASON_LENGTH);
                entry.status = ENTRY_REFUSED;
            }
        }

        if (changed) WiredManager.invalidateRoom(this.room);
    }

    /** Puts an inventory box down with the room's placement rules; returns the placement error key or null. */
    private String place(Entry entry) {
        HabboItem item = entry.inventoryItem;
        HabboItem stillOwned = this.habbo.getInventory().getItemsComponent().getHabboItem(item.getId());
        if (stillOwned != item || item.getRoomId() != 0 || !this.room.hasRights(this.habbo)) {
            return "wiredmenu.share.import.reason.rights";
        }
        int rotation = Math.floorMod(entry.box.rot, 8);
        FurnitureMovementError error = this.room.canPlaceFurnitureAt(item, this.habbo, entry.tile, rotation);
        if (error == FurnitureMovementError.NONE) {
            error = this.room.placeFloorFurniAt(item, entry.tile, rotation, this.habbo);
        }
        if (error != FurnitureMovementError.NONE) {
            return "room.error.cant_set_item";
        }
        this.client.sendResponse(new RemoveHabboItemComposer(item.getGiftAdjustedId()));
        this.habbo.getInventory().getItemsComponent().removeHabboItem(item.getId());
        item.setFromGift(false);
        return null;
    }

    /** Saves one box through its own save path; returns the refusal or null. */
    private String save(Entry entry, int anchorX, int anchorY) {
        InteractionWired box = entry.target;
        WiredSetupDocument.Box source = entry.box;
        int maximumText = box instanceof WiredLargePayload
                ? WiredLargePayload.MAX_STRING_PARAM_LENGTH
                : WiredInputGuard.MAX_STRING_PARAM_LENGTH;
        String text = this.remapText(source.text);
        if (text.length() > maximumText) return "wiredmenu.share.import.reason.invalid";

        WiredSettings settings = new WiredSettings(
                source.ints == null ? new int[0] : source.ints.clone(),
                text,
                this.remapPicks(source.picks, anchorX, anchorY),
                -1,
                WiredInputGuard.normalizeDelay(source.delay));
        settings.setFurniIds2(this.remapPicks(source.picks2, anchorX, anchorY));
        settings.setFurniSourceTypes(source.furniSources == null ? new int[0] : source.furniSources.clone());
        settings.setUserSourceTypes(source.userSources == null ? new int[0] : source.userSources.clone());
        settings.setVariableIds(this.remapVariables(source.variables));
        settings.setQuantifierCode(source.quantifier);
        settings.setSelectorFilter(source.filter);
        settings.setSelectorInvert(source.invert);
        settings.setRoom(this.room);

        try {
            String sourceRefusal = WiredSourceSaveRules.apply(box, settings, this.room);
            if (sourceRefusal != null) return sourceRefusal;

            boolean saved;
            if (box instanceof InteractionWiredTrigger trigger) {
                saved = trigger.saveData(settings, this.client);
            } else if (box instanceof InteractionWiredCondition condition) {
                saved = condition.saveData(settings);
            } else if (box instanceof InteractionWiredEffect effect) {
                saved = effect.saveData(settings, this.client);
            } else if (box instanceof InteractionWiredExtra extra) {
                saved = extra.saveData(settings, this.client);
            } else {
                return "wiredmenu.share.import.reason.unsupported";
            }
            if (!saved) return "wiredmenu.share.import.reason.refused";
        } catch (Exception refused) {
            LOGGER.debug("Wired setup import refused box {}", box.getId(), refused);
            String message = refused.getMessage();
            return message == null || message.isBlank() ? "wiredmenu.share.import.reason.refused" : message;
        }

        box.markEditorSaved();
        if (box instanceof InteractionWiredEffect effect && effect.isSelector()) {
            // As a manual save: a selector rests at 3 with Filter, 0 without.
            effect.setExtradata(effect.usesExistingSelectorTargets() ? "3" : "0");
            this.room.sendComposer(new ItemStateComposer(effect).compose());
        }
        box.needsUpdate(true);
        ThreadPooling threading = WiredPlatform.threading();
        if (threading != null) threading.run(box);
        return null;
    }

    /** {@code custom:#<index>} becomes the imported box's {@code custom:<id>}, or {@code custom:0} when it was not imported. */
    String remapText(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = CUSTOM_REFERENCE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            InteractionWired target = this.resolved.get(WiredSetupExporter.parseId(matcher.group(1)));
            matcher.appendReplacement(out, target != null ? "custom:" + target.getId() : "custom:0");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    String[] remapVariables(List<String> variables) {
        List<String> out = new ArrayList<>();
        if (variables == null) return new String[0];
        // Slot by slot: a variable that cannot be resolved here stays as "none", so the slots after it keep their
        // place.
        for (String variable : variables) {
            if (variable == null || variable.isEmpty()) {
                out.add(WiredVariableIds.NONE);
            } else {
                Matcher matcher = VARIABLE_REFERENCE.matcher(variable);
                if (matcher.matches()) {
                    InteractionWired target = this.resolved.get(WiredSetupExporter.parseId(matcher.group(2)));
                    String prefix = matcher.group(1) == null ? "" : matcher.group(1);
                    out.add(target != null ? prefix + target.getId() : WiredVariableIds.NONE);
                } else if (!WiredSetupExporter.isDigits(variable) && !variable.startsWith("custom:")) {
                    // Built-in variables, and ids of other rooms' shared variables; a raw item id from a code is
                    // never trusted.
                    out.add(variable);
                } else {
                    out.add(WiredVariableIds.NONE);
                }
            }
            if (out.size() >= WiredInputGuard.MAX_VARIABLE_IDS) break;
        }
        return out.toArray(new String[0]);
    }

    int[] remapPicks(List<String> picks, int anchorX, int anchorY) {
        if (picks == null || picks.isEmpty()) return new int[0];
        Set<Integer> ids = new LinkedHashSet<>();
        int limit = WiredInputGuard.maxFurniSelectionCount();
        for (String pick : picks) {
            if (ids.size() >= limit) break;
            int boxIndex = WiredSetupDocument.boxReference(pick);
            if (boxIndex >= 0) {
                InteractionWired target = this.resolved.get(boxIndex);
                if (target != null) ids.add(target.getId());
                continue;
            }
            int furniIndex = WiredSetupDocument.furniReference(pick);
            if (furniIndex < 0 || furniIndex >= this.document.furni.size()) continue;
            HabboItem furni = this.findFurni(this.document.furni.get(furniIndex), anchorX, anchorY);
            if (furni != null) ids.add(furni.getId());
        }
        int[] out = new int[ids.size()];
        int i = 0;
        for (int id : ids) out[i++] = id;
        return out;
    }

    /** The furni of this room at the reference's tile and stack place, or null. */
    private HabboItem findFurni(WiredSetupDocument.Furni reference, int anchorX, int anchorY) {
        if (reference == null
                || reference.type == null
                || reference.type.length() > WiredSetupDocument.MAX_TYPE_LENGTH
                || Math.abs(reference.dx) > WiredSetupDocument.MAX_OFFSET
                || Math.abs(reference.dy) > WiredSetupDocument.MAX_OFFSET
                || reference.n < 0) {
            return null;
        }
        List<HabboItem> items = WiredSetupExporter.sameTypeOnTile(
                this.room, anchorX + reference.dx, anchorY + reference.dy, reference.type);
        return reference.n < items.size() ? items.get(reference.n) : null;
    }

    /** Why the matched box cannot take this entry, or null. */
    private String unsupported(InteractionWired box, WiredSetupDocument.Box entry) {
        if (box instanceof WiredExtraVariableWebApi) return "wiredmenu.share.import.reason.unsupported";
        if (box instanceof InteractionWiredCondition && LEGACY_CONDITION.get(box.getClass())) {
            return "wiredmenu.share.import.reason.unsupported";
        }
        if (!entry.fresh && !WiredHolders.of(box).name().equals(entry.holder)) {
            return "wiredmenu.share.import.reason.invalid";
        }
        return null;
    }

    /** The importer's own wired boxes in this room, per root tile, lowest first. */
    private Map<Long, List<InteractionWired>> ownBoxesByTile() {
        Map<Long, List<InteractionWired>> byTile = new HashMap<>();
        int userId = this.habbo.getHabboInfo().getId();
        for (InteractionWired box : WiredSetupExporter.exportableBoxes(this.room)) {
            if (box.getUserId() != userId) continue;
            byTile.computeIfAbsent(tileKey(box.getX(), box.getY()), key -> new ArrayList<>())
                    .add(box);
        }
        return byTile;
    }

    /** Wired boxes in the importer's inventory that may be placed by an import. */
    private List<HabboItem> inventoryBoxes() {
        List<HabboItem> boxes = new ArrayList<>();
        int userId = this.habbo.getHabboInfo().getId();
        for (HabboItem item : this.habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (item instanceof InteractionWired
                    && item.getRoomId() == 0
                    && item.getUserId() == userId
                    && item.getBaseItem() != null
                    && item.getBaseItem().getType() == FurnitureType.FLOOR
                    && !BuildersClubRoomSupport.isTrackedItem(item.getId())) {
                boxes.add(item);
            }
        }
        boxes.sort(Comparator.comparingInt(HabboItem::getId));
        return boxes;
    }

    /** Bounds every field of an entry; returns the refusal key or null. */
    static String validate(WiredSetupDocument.Box box) {
        String invalid = "wiredmenu.share.import.reason.invalid";
        if (box == null
                || box.type == null
                || box.type.isBlank()
                || box.type.length() > WiredSetupDocument.MAX_TYPE_LENGTH) {
            return invalid;
        }
        if (box.holder == null || box.holder.length() > 20) return invalid;
        if (Math.abs(box.dx) > WiredSetupDocument.MAX_OFFSET || Math.abs(box.dy) > WiredSetupDocument.MAX_OFFSET) {
            return invalid;
        }
        if (box.fresh) return null;
        if (!isHolder(box.holder)) return invalid;
        if (box.ints != null && box.ints.length > WiredInputGuard.MAX_INT_PARAMS) return invalid;
        if (box.text != null && box.text.length() > WiredLargePayload.MAX_STRING_PARAM_LENGTH) return invalid;
        if (box.picks != null && box.picks.size() > WiredInputGuard.MAX_ABSOLUTE_FURNI_IDS) return invalid;
        if (box.picks2 != null && box.picks2.size() > WiredInputGuard.MAX_ABSOLUTE_FURNI_IDS) return invalid;
        if (box.furniSources != null && box.furniSources.length > WiredInputGuard.MAX_SOURCE_TYPES) return invalid;
        if (box.userSources != null && box.userSources.length > WiredInputGuard.MAX_SOURCE_TYPES) return invalid;
        if (box.variables != null) {
            if (box.variables.size() > WiredInputGuard.MAX_VARIABLE_IDS) return invalid;
            for (String variable : box.variables) {
                if (variable != null && variable.length() > WiredInputGuard.MAX_VARIABLE_ID_LENGTH) return invalid;
            }
        }
        return null;
    }

    private static boolean isHolder(String holder) {
        for (WiredDefinitionWire.Holder value : WiredDefinitionWire.Holder.values()) {
            if (value.name().equals(holder)) return true;
        }
        return false;
    }

    private void count() {
        for (Entry entry : this.report.entries) {
            switch (entry.status) {
                case ENTRY_CONFIGURE, ENTRY_UNCHANGED -> this.report.configured++;
                case ENTRY_PLACE -> this.report.placed++;
                case ENTRY_MISSING -> this.report.missing++;
                default -> this.report.refused++;
            }
        }
    }

    private static void refuse(Entry entry, int status, String reason) {
        entry.status = status;
        entry.reason = truncate(reason, MAX_REASON_LENGTH);
        entry.target = null;
    }

    private static long tileKey(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max) : value;
    }
}

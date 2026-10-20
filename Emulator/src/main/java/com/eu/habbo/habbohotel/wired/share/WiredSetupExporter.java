package com.eu.habbo.habbohotel.wired.share;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.protocol.WiredEditorState;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the {@link WiredSetupDocument} of a room's wired boxes. */
final class WiredSetupExporter {
    static final Pattern CUSTOM_TOKEN = Pattern.compile("custom:(\\d{1,10})");
    /** A box's variable id: the token, the variable list's {@code <target>:<box id>}, or the bare box id. */
    static final Pattern BOX_VARIABLE_ID = Pattern.compile("(custom:|user:|furni:|room:|ctx:)?(\\d{1,10})");

    /** Boxes are read in a stable order: by tile, then from the bottom of the stack up. */
    static final Comparator<HabboItem> STACK_ORDER = Comparator.comparingInt(HabboItem::getX)
            .thenComparingInt(HabboItem::getY)
            .thenComparingDouble(HabboItem::getZ)
            .thenComparingInt(HabboItem::getId);

    private final Room room;
    final Map<Integer, Integer> boxIndexes = new HashMap<>();
    private final Map<Integer, Integer> furniIndexes = new LinkedHashMap<>();
    private WiredSetupDocument document;

    WiredSetupExporter(Room room) {
        this.room = room;
    }

    /** Every wired box of the room that may leave it, in stack order. The Web API box holds a secret. */
    static List<InteractionWired> exportableBoxes(Room room) {
        RoomSpecialTypes types = room.getRoomSpecialTypes();
        List<InteractionWired> boxes = new ArrayList<>();
        if (types == null) return boxes;
        boxes.addAll(types.getTriggers());
        boxes.addAll(types.getEffects());
        boxes.addAll(types.getConditions());
        boxes.addAll(types.getExtras());
        boxes.removeIf(box -> box instanceof WiredExtraVariableWebApi
                || box.getBaseItem() == null
                || box.getBaseItem().getType() != FurnitureType.FLOOR);
        boxes.sort(STACK_ORDER);
        return boxes;
    }

    /** The document, or null when the room has more boxes than a setup may hold. */
    WiredSetupDocument build(List<InteractionWired> boxes) {
        if (boxes.size() > WiredSetupDocument.MAX_BOXES) return null;

        this.document = new WiredSetupDocument();
        int anchorX = Integer.MAX_VALUE;
        int anchorY = Integer.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            InteractionWired box = boxes.get(i);
            this.boxIndexes.put(box.getId(), i);
            anchorX = Math.min(anchorX, box.getX());
            anchorY = Math.min(anchorY, box.getY());
        }
        if (boxes.isEmpty()) {
            anchorX = 0;
            anchorY = 0;
        }
        this.document.anchorX = anchorX;
        this.document.anchorY = anchorY;

        for (InteractionWired box : boxes) {
            WiredEditorState state = WiredEditorState.of(box, this.room);
            WiredSetupDocument.Box entry = new WiredSetupDocument.Box();
            entry.type = box.getBaseItem().getName();
            entry.dx = box.getX() - anchorX;
            entry.dy = box.getY() - anchorY;
            entry.rot = box.getRotation();
            if (state == null) {
                // A definition that cannot be read still keeps its place in the layout.
                entry.holder = "";
                entry.fresh = true;
            } else {
                entry.holder = state.holder().name();
                entry.fresh = state.fresh();
                entry.ints = state.intParams().clone();
                entry.text = this.exportText(state.stringParam());
                entry.picks = this.exportPicks(state.stuffIds());
                entry.picks2 = this.exportPicks(state.stuffIds2());
                entry.furniSources = state.furniSources().clone();
                entry.userSources = state.userSources().clone();
                entry.variables = this.exportVariables(state.variableIds());
                entry.delay = state.delay();
                entry.quantifier = state.quantifierCode();
                entry.filter = state.selectorFilter();
                entry.invert = state.selectorInvert();
            }
            this.document.boxes.add(entry);
        }
        return this.document;
    }

    /** {@code custom:<id>} of an exported box becomes {@code custom:#<index>}; any other id is dropped. */
    String exportText(String text) {
        if (text == null || text.isEmpty()) return "";
        Matcher matcher = CUSTOM_TOKEN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Integer index = this.boxIndexes.get(parseId(matcher.group(1)));
            matcher.appendReplacement(out, index != null ? "custom:#" + index : "custom:0");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * The variable ids slot by slot. A box's variable becomes {@code <prefix>#<index>} when the box is exported;
     * otherwise a bare or {@code custom:} id is dropped to "none" ({@code n}), while a {@code <target>:<id>} stays (a
     * reference names a shared variable of another room). Built-in ids are the same in every room.
     */
    List<String> exportVariables(String[] variableIds) {
        List<String> out = new ArrayList<>();
        if (variableIds == null) return out;
        for (String variableId : variableIds) {
            if (variableId == null || variableId.isEmpty()) {
                out.add(WiredVariableIds.NONE);
                continue;
            }
            Matcher matcher = BOX_VARIABLE_ID.matcher(variableId);
            if (!matcher.matches()) {
                out.add(variableId);
                continue;
            }
            String prefix = matcher.group(1) == null ? "" : matcher.group(1);
            Integer index = this.boxIndexes.get(parseId(matcher.group(2)));
            if (index != null) {
                out.add(prefix + "#" + index);
            } else if (prefix.isEmpty() || prefix.equals("custom:")) {
                out.add(WiredVariableIds.NONE);
            } else {
                out.add(variableId);
            }
        }
        return out;
    }

    List<String> exportPicks(int[] itemIds) {
        List<String> out = new ArrayList<>();
        if (itemIds == null) return out;
        for (int itemId : itemIds) {
            Integer boxIndex = this.boxIndexes.get(itemId);
            if (boxIndex != null) {
                out.add("b" + boxIndex);
                continue;
            }
            Integer furniIndex = this.furniIndex(itemId);
            if (furniIndex != null) out.add("f" + furniIndex);
        }
        return out;
    }

    private Integer furniIndex(int itemId) {
        Integer known = this.furniIndexes.get(itemId);
        if (known != null) return known;
        if (this.furniIndexes.size() >= WiredSetupDocument.MAX_FURNI_REFERENCES) return null;

        HabboItem item = this.room.getHabboItem(itemId);
        // Wall furni have no tile to find them by in another room.
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.FLOOR) {
            return null;
        }

        int n = 0;
        for (HabboItem other : sameTypeOnTile(
                this.room, item.getX(), item.getY(), item.getBaseItem().getName())) {
            if (other.getId() == item.getId()) break;
            n++;
        }

        WiredSetupDocument.Furni furni = new WiredSetupDocument.Furni();
        furni.type = item.getBaseItem().getName();
        furni.dx = item.getX() - this.document.anchorX;
        furni.dy = item.getY() - this.document.anchorY;
        furni.n = n;
        int index = this.document.furni.size();
        this.document.furni.add(furni);
        this.furniIndexes.put(itemId, index);
        return index;
    }

    /** The floor furni of {@code type} whose root tile is ({@code x}, {@code y}), lowest first. */
    static List<HabboItem> sameTypeOnTile(Room room, int x, int y, String type) {
        List<HabboItem> items = new ArrayList<>();
        for (HabboItem item : room.getItemsAt(x, y)) {
            if (item.getX() == x
                    && item.getY() == y
                    && item.getBaseItem() != null
                    && item.getBaseItem().getType() == FurnitureType.FLOOR
                    && type.equals(item.getBaseItem().getName())) {
                items.add(item);
            }
        }
        items.sort(STACK_ORDER);
        return items;
    }

    static boolean isDigits(String value) {
        if (value == null || value.isEmpty() || value.length() > 10) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i)) || value.charAt(i) > '9') return false;
        }
        return true;
    }

    static int parseId(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

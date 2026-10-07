package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.furnieditor.FurniDataManager;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The colour of recolourable Builders Club furni as wired variables ({@code ~recolorable_furni.color.*}).
 * Reading gives the furni's colour from furnidata; writing swaps it to the nearest colour of its Builders
 * Club palette, keeping id, place and state, as the recolor window does. Only Builders Club furni change.
 */
public final class BuildersClubPalette {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildersClubPalette.class);

    public static final String RGB = "~recolorable_furni.color.rgb";
    public static final String RED = "~recolorable_furni.color.rgb.r";
    public static final String GREEN = "~recolorable_furni.color.rgb.g";
    public static final String BLUE = "~recolorable_furni.color.rgb.b";

    /** Each recolor re-sends the furni to the room, so a room gets a limited number per second. */
    static final int MAX_RECOLORS_PER_SECOND = 100;

    static final int NO_COLOR = -1;
    private static final int WHITE = 0xFFFFFF;
    private static final long OFFERS_TTL_MS = 60_000L;
    private static final long ROOM_ITEMS_TTL_MS = 5_000L;
    private static final long ROOM_ITEMS_MIN_RELOAD_MS = 500L;
    private static final int MAX_TRACKED_ROOMS = 10_000;

    private static final ConcurrentHashMap<Integer, Integer> COLOR_BY_BASE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, RoomItems> ROOM_ITEMS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, long[]> ROOM_BUDGET = new ConcurrentHashMap<>();
    /** When each refusal reason was last logged, so a hotel owner sees why without a flooded log. */
    private static final ConcurrentHashMap<String, Long> LAST_REFUSAL_LOG = new ConcurrentHashMap<>();

    private static final long REFUSAL_LOG_MS = 60_000L;
    private static final Object PALETTE_LOCK = new Object();
    private static final AtomicReference<Palettes> PALETTES = new AtomicReference<>(new Palettes(Map.of(), 0L));

    private record Palettes(Map<String, List<Item>> byFamily, long loadedAt) {}

    private record RoomItems(Set<Integer> ids, long loadedAt) {}

    private BuildersClubPalette() {}

    public static boolean isKey(String key) {
        return RGB.equals(key) || RED.equals(key) || GREEN.equals(key) || BLUE.equals(key);
    }

    /** True when the furni is a colour variant with a known colour. */
    public static boolean hasColor(HabboItem item) {
        Item base = item == null ? null : item.getBaseItem();
        if (base == null
                || base.getType() != FurnitureType.FLOOR
                || BuildersClubRecolorService.familyOf(base.getName()) == null) {
            return false;
        }
        if (colorOf(base) == NO_COLOR) {
            logRefusal(
                    "no-furnidata",
                    "no colour for {} in furnidata: check that the furnidata source is found at startup"
                            + " (FurnitureTextProvider) and has partcolors for it",
                    base.getName());
            return false;
        }
        return true;
    }

    public static Integer read(HabboItem item, String key) {
        if (!hasColor(item)) {
            return null;
        }
        return component(colorOf(item.getBaseItem()), key);
    }

    /** Recolours a placed Builders Club furni to the palette colour nearest the value written. */
    public static boolean write(Room room, HabboItem item, String key, int value) {
        if (room == null || !hasColor(item)) {
            return false;
        }

        Item base = item.getBaseItem();
        int wanted = withComponent(colorOf(base), key, value);
        Item target = nearest(
                base,
                wanted,
                paletteOf(BuildersClubRecolorService.familyOf(base.getName())),
                BuildersClubPalette::colorOf);
        if (target == null || target.getId() == base.getId()) {
            logRefusal(
                    "no-target-" + room.getId(),
                    "no other Builders Club colour of {} is nearer to #{} (the colours come from catalog_items_bc)",
                    base.getName(),
                    String.format("%06X", wanted));
            return false;
        }
        if (!isBuildersClubItem(room, item)) {
            logRefusal(
                    "not-bc-" + room.getId(),
                    "furni {} in room {} is not Builders Club furni (builders_club_items), so it keeps its colour",
                    item.getId(),
                    room.getId());
            return false;
        }
        if (!spend(room.getId(), System.currentTimeMillis())) {
            logRefusal(
                    "budget-" + room.getId(),
                    "room {} reached {} recolors this second",
                    room.getId(),
                    MAX_RECOLORS_PER_SECOND);
            return false;
        }

        item.setBaseItem(target);
        room.sendComposer(new RemoveFloorItemComposer(item, true).compose());
        room.sendComposer(new AddFloorItemComposer(item, room.getFurniOwnerName(item.getUserId())).compose());
        persist(item.getId(), target.getId(), room.getId());
        return true;
    }

    private static void logRefusal(String reason, String message, Object... arguments) {
        long now = System.currentTimeMillis();
        if (LAST_REFUSAL_LOG.size() >= MAX_TRACKED_ROOMS) {
            LAST_REFUSAL_LOG.clear();
        }
        Long last = LAST_REFUSAL_LOG.get(reason);
        if (last != null && now - last < REFUSAL_LOG_MS) {
            return;
        }
        LAST_REFUSAL_LOG.put(reason, now);
        LOGGER.warn("Recolor refused: " + message, arguments);
    }

    /** The value of a variable for a colour: the colour itself or one of its parts. */
    static int component(int rgb, String key) {
        if (RED.equals(key)) return (rgb >> 16) & 0xFF;
        if (GREEN.equals(key)) return (rgb >> 8) & 0xFF;
        if (BLUE.equals(key)) return rgb & 0xFF;
        return rgb & WHITE;
    }

    /** The colour asked for: the value as colour, or the current colour with one part replaced. */
    static int withComponent(int rgb, String key, int value) {
        int part = Math.max(0, Math.min(0xFF, value));
        if (RED.equals(key)) return (rgb & 0x00FFFF) | (part << 16);
        if (GREEN.equals(key)) return (rgb & 0xFF00FF) | (part << 8);
        if (BLUE.equals(key)) return (rgb & 0xFFFF00) | part;
        return Math.max(0, Math.min(WHITE, value));
    }

    /** The palette colour closest to the one wanted; only colours the furni can turn into count. */
    static Item nearest(Item base, int wanted, List<Item> palette, ToIntFunction<Item> colorOf) {
        Item best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Item candidate : palette) {
            if (candidate == null
                    || (candidate.getId() != base.getId()
                            && !BuildersClubRecolorService.interchangeable(base, candidate))) {
                continue;
            }
            int color = colorOf.applyAsInt(candidate);
            if (color == NO_COLOR) {
                continue;
            }
            long distance = distance(color, wanted);
            // On a tie the current colour stays.
            if (distance < bestDistance || (distance == bestDistance && candidate.getId() == base.getId())) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    static long distance(int a, int b) {
        long dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        long dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        long db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    /** The swatch of a colour variant, as the client shows it: its last part colour that is not white. */
    static int swatchOf(String furnidataJson, String className) {
        try {
            JsonElement parsed = JsonParser.parseString(furnidataJson);
            if (!parsed.isJsonObject()) {
                return NO_COLOR;
            }
            JsonObject entry = parsed.getAsJsonObject();
            // Only the variant's own entry: a fallback to the colourless base would give a wrong colour.
            if (!entry.has("classname") || !entry.get("classname").getAsString().equalsIgnoreCase(className)) {
                return NO_COLOR;
            }
            if (!entry.has("partcolors") || !entry.get("partcolors").isJsonObject()) {
                return NO_COLOR;
            }
            JsonElement colors = entry.getAsJsonObject("partcolors").get("color");
            if (colors == null || !colors.isJsonArray()) {
                return NO_COLOR;
            }
            int swatch = NO_COLOR;
            for (JsonElement element : (JsonArray) colors) {
                int color = parseColor(element.getAsString());
                if (color == NO_COLOR) {
                    continue;
                }
                if (swatch == NO_COLOR || color != WHITE) {
                    swatch = color;
                }
            }
            return swatch;
        } catch (RuntimeException e) {
            return NO_COLOR;
        }
    }

    static int parseColor(String value) {
        if (value == null) {
            return NO_COLOR;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return NO_COLOR;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return NO_COLOR;
        }
    }

    /** True when {@code now} still fits the room's recolors for this second; counts it if so. */
    static boolean spend(int roomId, long now) {
        if (ROOM_BUDGET.size() >= MAX_TRACKED_ROOMS) {
            ROOM_BUDGET.clear();
        }
        boolean[] allowed = {false};
        ROOM_BUDGET.compute(roomId, (key, window) -> {
            if (window == null || now - window[0] >= 1_000L) {
                window = new long[] {now, 0};
            }
            if (window[1] < MAX_RECOLORS_PER_SECOND) {
                window[1]++;
                allowed[0] = true;
            }
            return window;
        });
        return allowed[0];
    }

    static int colorOf(Item base) {
        if (base == null) {
            return NO_COLOR;
        }
        refreshIfStale();
        return COLOR_BY_BASE.computeIfAbsent(base.getId(), id -> lookUpColor(base));
    }

    private static int lookUpColor(Item base) {
        try {
            String json = FurniDataManager.getItemJson(base.getSpriteId(), base.getName());
            return swatchOf(json, base.getName());
        } catch (RuntimeException e) {
            return NO_COLOR;
        }
    }

    private static List<Item> paletteOf(String family) {
        if (family == null) {
            return List.of();
        }
        refreshIfStale();
        return PALETTES.get().byFamily().getOrDefault(family.toLowerCase(), List.of());
    }

    /** Colours and palettes are reloaded once a minute, so catalog and furnidata edits come through. */
    private static void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - PALETTES.get().loadedAt() < OFFERS_TTL_MS) {
            return;
        }
        synchronized (PALETTE_LOCK) {
            if (now - PALETTES.get().loadedAt() < OFFERS_TTL_MS) {
                return;
            }
            COLOR_BY_BASE.clear();
            PALETTES.set(new Palettes(loadPalettes(), now));
        }
    }

    private static Map<String, List<Item>> loadPalettes() {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        Database database = WiredPlatform.database();
        if (environment == null || environment.getItemManager() == null || database == null) {
            return Map.of();
        }

        Set<Integer> offered = new HashSet<>();
        try (Connection connection = database.getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT item_ids FROM catalog_items_bc");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                try {
                    offered.add(Integer.parseInt(set.getString(1).trim()));
                } catch (RuntimeException ignored) {
                    // Bundles ("1;2") are no colour variants.
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not read the Builders Club catalog for furni palettes", e);
            return Map.of();
        }

        Map<String, List<Item>> byFamily = new HashMap<>();
        synchronized (environment.getItemManager().getItems()) {
            for (Item item : environment.getItemManager().getItems().values()) {
                String family = item == null ? null : BuildersClubRecolorService.familyOf(item.getName());
                if (family != null && offered.contains(item.getId())) {
                    byFamily.computeIfAbsent(family.toLowerCase(), key -> new ArrayList<>())
                            .add(item);
                }
            }
        }
        byFamily.replaceAll((family, items) -> Collections.unmodifiableList(items));
        return Collections.unmodifiableMap(byFamily);
    }

    /** Builders Club furni of the room, from a cache a few seconds old at most. */
    private static boolean isBuildersClubItem(Room room, HabboItem item) {
        long now = System.currentTimeMillis();
        RoomItems cached = ROOM_ITEMS.get(room.getId());
        boolean stale = cached == null || now - cached.loadedAt() >= ROOM_ITEMS_TTL_MS;
        // A Builders Club furni placed after the last load shows the virtual owner: look again, but not too often.
        boolean maybeNew = cached != null
                && !cached.ids().contains(item.getId())
                && item.getUserId() == BuildersClubRoomSupport.VIRTUAL_OWNER_ID
                && now - cached.loadedAt() >= ROOM_ITEMS_MIN_RELOAD_MS;
        if (stale || maybeNew) {
            if (ROOM_ITEMS.size() >= MAX_TRACKED_ROOMS) {
                ROOM_ITEMS.clear();
            }
            cached = new RoomItems(loadRoomItems(room.getId()), now);
            ROOM_ITEMS.put(room.getId(), cached);
        }
        return cached.ids().contains(item.getId());
    }

    private static Set<Integer> loadRoomItems(int roomId) {
        Database database = WiredPlatform.database();
        if (database == null) {
            return Set.of();
        }
        try (Connection connection = database.getDataSource().getConnection()) {
            return BuildersClubRecolorService.buildersClubItemsIn(connection, roomId);
        } catch (SQLException e) {
            LOGGER.error("Could not read the Builders Club furni of room {}", roomId, e);
            return Set.of();
        }
    }

    /** The new colour is stored off the wired thread; only a row still in the room changes. */
    private static void persist(int itemId, int baseItemId, int roomId) {
        Database database = WiredPlatform.database();
        if (database == null || WiredPlatform.threading() == null) {
            return;
        }
        WiredPlatform.threading().run(() -> {
            try (Connection connection = database.getDataSource().getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("UPDATE items SET item_id = ? WHERE id = ? AND room_id = ?")) {
                statement.setInt(1, baseItemId);
                statement.setInt(2, itemId);
                statement.setInt(3, roomId);
                statement.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Could not store the new colour of Builders Club furni {}", itemId, e);
            }
        });
    }
}

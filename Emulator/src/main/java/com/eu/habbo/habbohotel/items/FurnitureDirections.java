package com.eu.habbo.habbohotel.items;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Random;

/**
 * The directions a floor furni can face, as the client knows them, and the rules Habbo uses to
 * turn a furni between them.
 *
 * <p>The client keeps the list in {@code furniture_allowed_directions}: the {@code
 * logic.model.directions} of the furni's asset library, in degrees, sorted (AIR {@code
 * room/object/logic/furniture} {@code initialize}). Rotating a furni steps to the next or previous
 * entry of that list, a direction missing from it counting as the first entry at or after it (AIR
 * {@code room/RoomObjectEventHandler} rotate). Moving a furni whose direction is not in the list
 * starts it at the first entry. Values here are in rotation units (degrees / 45, 0..7).
 *
 * <p>An instance without directions ({@link #isKnown()} false) means the asset could not be read:
 * every caller then keeps its old rule.
 */
public final class FurnitureDirections {
    private static final int[] NONE = new int[0];

    /** No asset data: callers keep their old rules. */
    public static final FurnitureDirections UNKNOWN = new FurnitureDirections(NONE, -1);

    private final int[] directions;
    private final int defaultDirection;

    private FurnitureDirections(int[] directions, int defaultDirection) {
        this.directions = directions;
        this.defaultDirection = defaultDirection;
    }

    /**
     * @param directions rotation units 0..7, any order; others are dropped
     * @param defaultDirection the furnidata {@code defaultdir}, or -1 when unknown
     */
    public static FurnitureDirections of(int[] directions, int defaultDirection) {
        int[] clean = normalize(directions);
        return clean.length == 0 ? UNKNOWN : new FurnitureDirections(clean, defaultDirection);
    }

    /** The directions of a room item, from the hotel's furniture bundles; {@link #UNKNOWN} without them. */
    public static FurnitureDirections of(HabboItem item) {
        Item base = item == null ? null : item.getBaseItem();
        if (base == null || base.getType() != FurnitureType.FLOOR) {
            return UNKNOWN;
        }
        return FurnitureDirectionsRuntime.lookup(base.getName());
    }

    /**
     * The directions an asset library json lists: {@code logic.model.directions} (degrees, as AIR
     * and the Octane renderer read them); when the logic has none, the direction keys of the size-64
     * visualization. Empty when neither is present.
     */
    public static int[] parse(JsonObject library) {
        if (library == null) {
            return NONE;
        }
        JsonObject logic = object(library, "logic");
        JsonObject model = logic == null ? null : object(logic, "model");
        JsonElement listed = model == null ? null : model.get("directions");
        if (listed != null && listed.isJsonArray()) {
            JsonArray array = listed.getAsJsonArray();
            int[] values = new int[array.size()];
            int count = 0;
            for (JsonElement element : array) {
                Double degrees = number(element);
                if (degrees == null && element.isJsonObject()) {
                    degrees = number(element.getAsJsonObject().get("id"));
                }
                if (degrees != null && Double.isFinite(degrees)) {
                    values[count++] = Math.floorMod(Math.round(degrees / 45.0), 8);
                }
            }
            int[] parsed = normalize(Arrays.copyOf(values, count));
            if (parsed.length > 0) {
                return parsed;
            }
        }
        return visualizationDirections(library, 64);
    }

    private static int[] visualizationDirections(JsonObject library, int size) {
        JsonElement visualizations = library.get("visualizations");
        if (visualizations == null) {
            return NONE;
        }
        Iterable<JsonElement> entries;
        if (visualizations.isJsonArray()) {
            entries = visualizations.getAsJsonArray();
        } else if (visualizations.isJsonObject()) {
            entries = visualizations.getAsJsonObject().entrySet().stream()
                    .map(java.util.Map.Entry::getValue)
                    .toList();
        } else {
            return NONE;
        }
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject visualization = entry.getAsJsonObject();
            Double visualizationSize = number(visualization.get("size"));
            JsonObject keys = object(visualization, "directions");
            if (visualizationSize == null || visualizationSize.intValue() != size || keys == null) {
                continue;
            }
            int[] values = new int[keys.size()];
            int count = 0;
            for (String key : keys.keySet()) {
                try {
                    values[count++] = Integer.parseInt(key.trim());
                } catch (NumberFormatException ignored) {
                    // not a direction key
                }
            }
            return normalize(Arrays.copyOf(values, count));
        }
        return NONE;
    }

    /**
     * The directions a variable counts @rotation in: the furni's own, else 0, 2, 4, 6, or all eight for a furni
     * that turns in eighths.
     */
    public int[] variableDirections(int rotations) {
        if (this.isKnown()) return this.directions.clone();
        return rotations >= 8 ? new int[] {0, 1, 2, 3, 4, 5, 6, 7} : new int[] {0, 2, 4, 6};
    }

    /**
     * Where a rotation sits in {@code directions}: its index, or for a rotation the furni lacks the first direction
     * at or after it, wrapping to 0 (observed on AIR: ktchn_fridge, directions 2 and 4, facing 4 reads 1).
     */
    public static int indexOf(int[] directions, int rotation) {
        int index = Arrays.binarySearch(directions, rotation);
        if (index < 0) {
            index = -index - 1;
            if (index >= directions.length) index = 0;
        }
        return index;
    }

    /** True when the asset listed directions; false means "use the old rule". */
    public boolean isKnown() {
        return this.directions.length > 0;
    }

    public int count() {
        return this.directions.length;
    }

    /** The directions, sorted, in rotation units. */
    public int[] values() {
        return this.directions.clone();
    }

    public int defaultDirection() {
        return this.defaultDirection;
    }

    public boolean has(int rotation) {
        return Arrays.binarySearch(this.directions, rotation) >= 0;
    }

    /**
     * The next direction clockwise (or counterclockwise) in 45 degree order, as the client rotates:
     * a rotation the furni lacks counts as the first direction at or after it. A furni with one
     * direction stays on it.
     */
    public int turn(int rotation, boolean clockwise) {
        if (!this.isKnown()) {
            return rotation;
        }
        int index = Arrays.binarySearch(this.directions, rotation);
        if (index < 0) {
            index = -index - 1;
            if (index >= this.directions.length) {
                index = 0;
            }
        }
        int size = this.directions.length;
        return this.directions[clockwise ? (index + 1) % size : (index - 1 + size) % size];
    }

    /** A random direction the furni has other than its current one; the current one when it has no other. */
    public int random(int rotation, Random random) {
        if (!this.isKnown()) {
            return rotation;
        }
        int[] others = Arrays.stream(this.directions).filter(d -> d != rotation).toArray();
        if (others.length == 0) {
            return rotation;
        }
        return others[random == null ? 0 : random.nextInt(others.length)];
    }

    /**
     * The rotation a written value stores (@rotation, Match to Snapshot), or null to refuse it: the
     * value itself when the furni has it, else the nearest direction within 45 degrees, clockwise
     * first; anything further is refused. Out-of-range values are refused.
     */
    public Integer write(int value) {
        if (value < 0 || value > 7) {
            return null;
        }
        if (!this.isKnown()) {
            return value;
        }
        for (int candidate : new int[] {value, (value + 1) % 8, (value + 7) % 8}) {
            if (this.has(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The rotation a snapshot match (Match to Snapshot, applying a snapshot) stores for a furni at
     * {@code current}: {@link #write} of the snapshot's rotation, or {@code current} when that is
     * refused. Without directions, the snapshot's rotation as before.
     */
    public int match(int current, int snapshotRotation) {
        if (!this.isKnown()) {
            return snapshotRotation;
        }
        Integer rotation = this.write(snapshotRotation);
        return rotation == null ? current : rotation;
    }

    /**
     * The rotation a user's move or placement stores: the requested one when the furni has it,
     * else the furnidata {@code defaultdir} when that is one of its directions, else its first
     * direction (where the client starts a move whose direction the furni lacks).
     */
    public int place(int rotation) {
        if (!this.isKnown() || this.has(rotation)) {
            return rotation;
        }
        return this.has(this.defaultDirection) ? this.defaultDirection : this.directions[0];
    }

    /**
     * The rotation a projectile furni lands on for a heading, or null to keep its rotation: the
     * heading when the furni has it, else its furnidata {@code defaultdir} when that is one of its
     * directions.
     */
    public Integer heading(int heading) {
        int normalized = Math.floorMod(heading, 8);
        if (!this.isKnown() || this.has(normalized)) {
            return normalized;
        }
        return this.has(this.defaultDirection) ? this.defaultDirection : null;
    }

    private static int[] normalize(int[] directions) {
        if (directions == null || directions.length == 0) {
            return NONE;
        }
        return Arrays.stream(directions)
                .filter(d -> d >= 0 && d <= 7)
                .distinct()
                .sorted()
                .toArray();
    }

    private static JsonObject object(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static Double number(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsJsonPrimitive().isNumber()
                    ? element.getAsDouble()
                    : Double.parseDouble(element.getAsString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "FurnitureDirections" + Arrays.toString(this.directions) + " default " + this.defaultDirection;
    }
}

package com.eu.habbo.habbohotel.games.snowwar.mapping;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of item collision properties (README 5.6).
 * walkableHeight is used by the pathfinder, collisionHeight by snowball physics.
 */
public final class SnowWarItemProperties {

    private static final Map<String, int[]> PROPERTIES = new HashMap<>();

    static {
        // name -> { walkableHeight, collisionHeight }
        // Trees: not walkable + stack height 1.0 (2300), so they behave like a
        // height-1.0 furni - a straight/lob snowball is stopped, a long (curved)
        // throw still arcs over them.
        PROPERTIES.put("sw_tree1", new int[] {1, 2300});
        PROPERTIES.put("sw_tree2", new int[] {1, 2300});
        PROPERTIES.put("sw_tree3", new int[] {1, 2300});
        PROPERTIES.put("sw_tree4", new int[] {1, 2300});

        // Flat floor tiles: walkable (height 0) and non-blocking, so a snowball
        // flies over them. The N x N footprint is purely visual and handled
        // client-side; the server just marks the origin tile as walkable floor.
        PROPERTIES.put("block_basic", new int[] {0, 0});
        PROPERTIES.put("block_basic2", new int[] {0, 0});
        PROPERTIES.put("block_basic3", new int[] {0, 0});

        PROPERTIES.put("block_ice", new int[] {0, 0});
        PROPERTIES.put("block_ice2", new int[] {0, 0});
        PROPERTIES.put("block_ice3", new int[] {0, 0});

        // Water: flat but NOT walkable (you'd fall in) - a snowball still skims
        // over it (handled in SnowWarMapsManager). walkableHeight 1 = blocked.
        PROPERTIES.put("block_water1", new int[] {1, 0});
        PROPERTIES.put("block_water2", new int[] {1, 0});
        PROPERTIES.put("block_water3", new int[] {1, 0});

        PROPERTIES.put("block_arch1b", new int[] {3, 6900});
        PROPERTIES.put("block_arch2b", new int[] {3, 6900});
        PROPERTIES.put("block_arch3b", new int[] {3, 6900});

        PROPERTIES.put("block_arch1", new int[] {3, 2300});
        PROPERTIES.put("block_arch2", new int[] {0, 2300});
        PROPERTIES.put("block_arch3", new int[] {3, 2300});

        PROPERTIES.put("obst_duck", new int[] {1, 2300});
        // Snowman: not walkable + stack height 1.5 (3450), so a straight/lob
        // snowball is stopped when it hits (a long curved throw still arcs over).
        PROPERTIES.put("obst_snowman", new int[] {2, 3450});

        // Fence: not walkable (can't cross) + low stack height 0.5 (1150), and a
        // snowball passes straight through (handled in SnowWarMapsManager).
        // sw_fence2 is just the other-angle variant.
        PROPERTIES.put("sw_fence", new int[] {1, 1150});
        PROPERTIES.put("sw_fence2", new int[] {1, 1150});

        PROPERTIES.put("snowball_machine", new int[] {1, 2400});
        PROPERTIES.put("snowball_machine_hidden", new int[] {1, 0});
    }

    private SnowWarItemProperties() {}

    public static int getWalkableHeight(String itemName) {
        int[] props = PROPERTIES.get(itemName);
        return props != null ? props[0] : 0;
    }

    public static int getCollisionHeight(String itemName) {
        int[] props = PROPERTIES.get(itemName);
        return props != null ? props[1] : -1;
    }

    public static boolean isKnownItem(String itemName) {
        return PROPERTIES.containsKey(itemName);
    }
}

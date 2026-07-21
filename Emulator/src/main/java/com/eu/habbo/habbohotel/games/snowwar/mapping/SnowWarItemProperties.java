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
        PROPERTIES.put("sw_tree1", new int[]{3, 4600});
        PROPERTIES.put("sw_tree2", new int[]{3, 4600});
        PROPERTIES.put("sw_tree3", new int[]{3, 4600});
        PROPERTIES.put("sw_tree4", new int[]{3, 4600});

        PROPERTIES.put("block_basic", new int[]{1, 2300});
        PROPERTIES.put("block_basic2", new int[]{2, 4600});
        PROPERTIES.put("block_basic3", new int[]{3, 6900});
        PROPERTIES.put("block_small", new int[]{0, 1150});

        PROPERTIES.put("block_ice", new int[]{1, 2300});
        PROPERTIES.put("block_ice2", new int[]{2, 4600});

        PROPERTIES.put("block_arch1b", new int[]{3, 6900});
        PROPERTIES.put("block_arch2b", new int[]{3, 6900});
        PROPERTIES.put("block_arch3b", new int[]{3, 6900});

        PROPERTIES.put("block_arch1", new int[]{3, 2300});
        PROPERTIES.put("block_arch2", new int[]{0, 2300});
        PROPERTIES.put("block_arch3", new int[]{3, 2300});

        PROPERTIES.put("obst_duck", new int[]{1, 2300});
        PROPERTIES.put("obst_snowman", new int[]{3, 4600});

        PROPERTIES.put("sw_fence", new int[]{1, 2500});

        PROPERTIES.put("snowball_machine", new int[]{1, 2400});
        PROPERTIES.put("snowball_machine_hidden", new int[]{1, 0});
    }

    private SnowWarItemProperties() {
    }

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

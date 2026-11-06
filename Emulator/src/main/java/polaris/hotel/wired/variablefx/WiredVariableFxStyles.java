package polaris.hotel.wired.variablefx;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import polaris.hotel.games.GameTeamColors;

/**
 * The styles the client knows per variable fx category: which renderer each draws with and the
 * extras its renderer reads from the config (icon, colour, number design). The client looks a
 * config's (category, style) pair up, so an unknown pair falls back to the category's first style.
 */
public final class WiredVariableFxStyles {
    public static final int CATEGORY_HEALTH_POINTS = 0;
    public static final int CATEGORY_PROGRESS_BAR = 1;
    public static final int CATEGORY_LEVELLING_PROGRESS = 2;
    public static final int CATEGORY_STATUS_BAR = 3;
    public static final int CATEGORY_BOSS_BAR = 4;
    public static final int CATEGORY_NUMBER_DISPLAY = 5;

    public static final String EXTRA_ICON = "icon";
    public static final String EXTRA_ICON_ALIGNMENT = "icon_alignment";
    public static final String EXTRA_DESIGN = "design";
    public static final String EXTRA_COLOR = "color";
    public static final String EXTRA_METALLIC = "metallic";
    public static final String EXTRA_SUB_RENDERER = "sub_renderer";
    public static final String EXTRA_SEGMENTS = "segments";

    public static final int RENDERER_PLAIN = 0;
    public static final int RENDERER_CLASSIC_MINI = 1;
    public static final int RENDERER_BLOCK = 2;
    public static final int RENDERER_STRIPED = 3;
    public static final int RENDERER_ARROW = 4;
    public static final int RENDERER_HEARTS = 10;
    public static final int RENDERER_HEALTH_BAR = 11;
    public static final int RENDERER_HEALTH_CROSS = 12;
    public static final int RENDERER_THERMOMETER = 13;
    public static final int RENDERER_LEVEL_WITH_PROGRESS = 20;
    public static final int RENDERER_LEVEL_DETAILS = 21;
    public static final int RENDERER_BOSS = 100;
    public static final int RENDERER_NUMBER_STYLED = 200;
    public static final int RENDERER_NUMBER_FREEZE = 201;

    /** One style: the renderers the client registers for it (the first is the default) and its config extras. */
    public record Style(int styleId, int[] rendererIds, Map<String, String> extra) {
        public int defaultRendererId() {
            return this.rendererIds[0];
        }

        /** The renderer asked for when this style has it, its default otherwise. */
        public int resolveRenderer(int rendererId) {
            for (int candidate : this.rendererIds) {
                if (candidate == rendererId) return rendererId;
            }
            return this.defaultRendererId();
        }
    }

    private static final Map<Integer, Map<Integer, Style>> STYLES = build();

    /** The icons a number display may carry; an unknown icon is not sent. */
    private static final Set<String> ICONS = Set.of(
            "battery",
            "burning",
            "cash",
            "cooldown",
            "droplet",
            "energy",
            "eye",
            "fish",
            "food",
            "freezing",
            "gems",
            "gold",
            "health",
            "honor",
            "magic",
            "mana",
            "poison",
            "repairing",
            "reputation",
            "shield",
            "stamina",
            "star_power",
            "stealth",
            "timeleft",
            "upgrading",
            "wooden_logs",
            "misc_heart",
            "misc_skull",
            "misc_star",
            "misc_coin");

    private WiredVariableFxStyles() {}

    /** The style, or the category's first one when the id is not in the table. */
    public static Style get(int category, int styleId) {
        Map<Integer, Style> styles = STYLES.getOrDefault(category, STYLES.get(CATEGORY_PROGRESS_BAR));
        Style style = styles.get(styleId);
        return (style != null) ? style : styles.get(0);
    }

    public static int styleCount(int category) {
        Map<Integer, Style> styles = STYLES.get(category);
        return (styles != null) ? styles.size() : 0;
    }

    /** Renderers that draw a fixed number of segments instead of a continuous bar. */
    public static boolean supportsSegments(int rendererId) {
        return rendererId == RENDERER_BLOCK || rendererId == RENDERER_ARROW || rendererId == RENDERER_THERMOMETER;
    }

    public static boolean isKnownIcon(String icon) {
        return icon != null && !icon.isEmpty() && ICONS.contains(icon);
    }

    /** The colour the team colour option paints a holder's fx, null for a holder on no team. */
    public static String teamColor(GameTeamColors team) {
        if (team == null) return null;
        return switch (team) {
            case RED -> "#df291e";
            case GREEN -> "#36b24a";
            case BLUE -> "#3b7de3";
            case YELLOW -> "#ffd83d";
            default -> null;
        };
    }

    private static Map<Integer, Map<Integer, Style>> build() {
        Map<Integer, Map<Integer, Style>> table = new HashMap<>();

        add(table, CATEGORY_HEALTH_POINTS, 0, new int[] {RENDERER_HEARTS}, EXTRA_ICON, "misc_heart");
        add(table, CATEGORY_HEALTH_POINTS, 1, new int[] {RENDERER_HEALTH_CROSS});
        add(table, CATEGORY_HEALTH_POINTS, 2, new int[] {RENDERER_THERMOMETER});
        add(table, CATEGORY_HEALTH_POINTS, 3, new int[] {RENDERER_HEALTH_BAR});

        add(table, CATEGORY_PROGRESS_BAR, 0, new int[] {RENDERER_PLAIN});
        add(table, CATEGORY_PROGRESS_BAR, 1, new int[] {RENDERER_BLOCK});
        add(table, CATEGORY_PROGRESS_BAR, 2, new int[] {RENDERER_STRIPED});
        add(table, CATEGORY_PROGRESS_BAR, 3, new int[] {RENDERER_ARROW});
        add(table, CATEGORY_PROGRESS_BAR, 4, new int[] {RENDERER_CLASSIC_MINI});

        add(table, CATEGORY_LEVELLING_PROGRESS, 0, new int[] {RENDERER_LEVEL_WITH_PROGRESS});
        add(table, CATEGORY_LEVELLING_PROGRESS, 1, new int[] {RENDERER_LEVEL_DETAILS});

        // Status bars: a themed icon and a baked colour. The battery runs red to green instead.
        String[][] statusBars = {
            {"energy", "#ffd83d", "false"},
            {"shield", "#4aa9f6", "false"},
            {"magic", "#8751d1", "false"},
            {"food", "#ff9f24", "false"},
            {"stamina", "#86d213", "false"},
            {"poison", "#8ddc35", "false"},
            {"mana", "#268fff", "false"},
            {"health", "#7dce35", "false"},
            {"gold", "#ffc83d", "true"},
            {"gems", "#416bdd", "true"},
            {"honor", "#fac384", "false"},
            {"reputation", "#ffd83d", "false"},
            {"cooldown", "#b8c3cc", "false"},
            {"timeleft", "#74b9e8", "false"},
            {"burning", "#ff5a1f", "false"},
            {"freezing", "#82cfff", "false"},
            {"battery", null, "false"},
            {"repairing", "#c9c5b8", "true"},
            {"stealth", "#6254a8", "false"},
            {"upgrading", "#6bdc34", "false"},
            {"star_power", "#ffd900", "true"},
            {"droplet", "#4aabf5", "false"},
        };
        int[] statusRenderers = {RENDERER_BLOCK, RENDERER_STRIPED, RENDERER_ARROW};
        for (int styleId = 0; styleId < statusBars.length; styleId++) {
            String[] bar = statusBars[styleId];
            if (bar[1] == null) {
                add(table, CATEGORY_STATUS_BAR, styleId, statusRenderers, EXTRA_ICON, bar[0]);
            } else {
                add(
                        table,
                        CATEGORY_STATUS_BAR,
                        styleId,
                        statusRenderers,
                        EXTRA_ICON,
                        bar[0],
                        EXTRA_COLOR,
                        bar[1],
                        EXTRA_METALLIC,
                        bar[2]);
            }
        }

        add(
                table,
                CATEGORY_BOSS_BAR,
                0,
                new int[] {RENDERER_BOSS},
                EXTRA_ICON,
                "misc_skull",
                EXTRA_ICON_ALIGNMENT,
                "double");
        add(table, CATEGORY_BOSS_BAR, 1, new int[] {RENDERER_BOSS});

        add(table, CATEGORY_NUMBER_DISPLAY, 0, new int[] {RENDERER_NUMBER_FREEZE}, EXTRA_DESIGN, "freeze_style");
        add(table, CATEGORY_NUMBER_DISPLAY, 1, new int[] {RENDERER_NUMBER_STYLED}, EXTRA_DESIGN, "shalimar");
        add(table, CATEGORY_NUMBER_DISPLAY, 2, new int[] {RENDERER_NUMBER_STYLED}, EXTRA_DESIGN, "blocky");

        return Collections.unmodifiableMap(table);
    }

    private static void add(
            Map<Integer, Map<Integer, Style>> table, int category, int styleId, int[] renderers, String... extraPairs) {
        Map<String, String> extra = new LinkedHashMap<>();
        for (int i = 0; i + 1 < extraPairs.length; i += 2) {
            extra.put(extraPairs[i], extraPairs[i + 1]);
        }
        table.computeIfAbsent(category, ignored -> new LinkedHashMap<>())
                .put(styleId, new Style(styleId, renderers, Collections.unmodifiableMap(extra)));
    }
}

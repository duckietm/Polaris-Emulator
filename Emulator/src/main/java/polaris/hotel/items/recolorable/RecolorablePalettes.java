package polaris.hotel.items.recolorable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The colours a recolourable Builders Club furni may take, as the official client reads them from
 * {@code recolorable_bc.palette_to_furni} and {@code recolorable_bc.palette.<name>}: the furni's palette
 * by classname, or "default". Colours are packed 0xRRGGBB in display order.
 *
 * <p>The values ship with the emulator in {@code recolorable/recolorable_bc.properties}, copied from
 * Habbo's external variables, so the server checks the same colours the client offers.
 */
public final class RecolorablePalettes {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecolorablePalettes.class);

    public static final String RESOURCE = "recolorable/recolorable_bc.properties";
    public static final String PALETTE_TO_FURNI = "recolorable_bc.palette_to_furni";
    public static final String PALETTE_PREFIX = "recolorable_bc.palette.";
    public static final String DEFAULT_PALETTE = "default";

    private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]{6}$");
    private static final Pattern RGB =
            Pattern.compile("^\\s*([0-9]{1,3})\\s*,\\s*([0-9]{1,3})\\s*,\\s*([0-9]{1,3})\\s*$");
    private static final RecolorablePalettes SHIPPED = load();

    private final Map<String, String> paletteByFurni;
    private final Map<String, List<Integer>> palettes;

    RecolorablePalettes(Map<String, String> settings) {
        this.paletteByFurni = parsePaletteToFurni(settings.getOrDefault(PALETTE_TO_FURNI, ""));
        Map<String, List<Integer>> parsed = new HashMap<>();
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            if (entry.getKey().startsWith(PALETTE_PREFIX)) {
                parsed.put(entry.getKey().substring(PALETTE_PREFIX.length()), parseColors(entry.getValue()));
            }
        }
        this.palettes = Map.copyOf(parsed);
    }

    /** The palettes shipped with the emulator. */
    public static RecolorablePalettes shipped() {
        return SHIPPED;
    }

    /** The colours of a furni by its classname; empty when its palette is unknown. */
    public List<Integer> colorsFor(String className) {
        String name = (className == null) ? null : this.paletteByFurni.get(className.toLowerCase(Locale.ROOT));
        return this.palettes.getOrDefault(name != null ? name : DEFAULT_PALETTE, List.of());
    }

    public boolean allows(String className, int rgb) {
        return this.colorsFor(className).contains(rgb & 0xFFFFFF);
    }

    /** The palette colour closest to the wanted one; -1 when the furni has no palette. */
    public int nearest(String className, int wanted) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int color : this.colorsFor(className)) {
            long distance = distance(color, wanted);
            if (distance < bestDistance) {
                best = color;
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

    /** "R,G,B" (decimal, 0-255) as the client sends it; -1 when it is not one. */
    public static int parseRgb(String text) {
        if (text == null) return -1;

        java.util.regex.Matcher matcher = RGB.matcher(text);
        if (!matcher.matches()) return -1;

        int r = Integer.parseInt(matcher.group(1));
        int g = Integer.parseInt(matcher.group(2));
        int b = Integer.parseInt(matcher.group(3));
        if (r > 255 || g > 255 || b > 255) return -1;

        return (r << 16) | (g << 8) | b;
    }

    public static String formatRgb(int rgb) {
        return ((rgb >> 16) & 0xFF) + "," + ((rgb >> 8) & 0xFF) + "," + (rgb & 0xFF);
    }

    static Map<String, String> parsePaletteToFurni(String value) {
        Map<String, String> byFurni = new HashMap<>();
        for (String group : value.split(";")) {
            String[] parts = group.split("=", 2);
            if (parts.length != 2) continue;

            String palette = parts[0].trim();
            for (String furni : parts[1].split(",")) {
                String name = furni.trim().toLowerCase(Locale.ROOT);
                // As the official client: when a furni is listed twice, the last palette wins.
                if (!name.isEmpty()) byFurni.put(name, palette);
            }
        }
        return byFurni;
    }

    static List<Integer> parseColors(String value) {
        List<Integer> colors = new ArrayList<>();
        for (String hex : value.split(",")) {
            String trimmed = hex.trim();
            if (HEX.matcher(trimmed).matches()) colors.add(Integer.parseInt(trimmed, 16));
        }
        return Collections.unmodifiableList(colors);
    }

    private static RecolorablePalettes load() {
        Properties properties = new Properties();
        try (InputStream stream = RecolorablePalettes.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } else {
                LOGGER.warn("{} is missing; recolourable furni have no colours", RESOURCE);
            }
        } catch (IOException e) {
            LOGGER.error("Could not read {}", RESOURCE, e);
        }
        Map<String, String> settings = new HashMap<>();
        for (String key : properties.stringPropertyNames()) settings.put(key, properties.getProperty(key));
        return new RecolorablePalettes(settings);
    }
}

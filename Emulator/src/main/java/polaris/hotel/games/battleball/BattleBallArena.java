package polaris.hotel.games.battleball;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A BattleBall arena of Habbo's original game: its heights (where one can bounce), its game tiles (those that take a
 * colour) and the spawn points of each team, as Kepler keeps them (games_maps, games_player_spawns), in
 * resources/battleball/arenas.json. Rows are y, columns x.
 */
public final class BattleBallArena {

    private static final Map<Integer, BattleBallArena> ARENAS = load();

    private final int id;
    private final int width;
    private final int height;
    private final int[][] heights;
    private final boolean[][] gameTiles;
    private final List<List<int[]>> spawns;

    private BattleBallArena(int id, List<String> heightRows, List<String> tileRows, List<List<int[]>> spawns) {
        this.id = id;
        this.height = heightRows.size();
        this.width = heightRows.get(0).length();
        this.heights = new int[this.width][this.height];
        this.gameTiles = new boolean[this.width][this.height];
        this.spawns = spawns;

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                char cell = x < heightRows.get(y).length() ? heightRows.get(y).charAt(x) : 'x';
                char tile = y < tileRows.size() && x < tileRows.get(y).length()
                        ? tileRows.get(y).charAt(x)
                        : '0';

                this.heights[x][y] = Character.isDigit(cell) ? cell - '0' : -1;
                this.gameTiles[x][y] = tile == '1' && this.heights[x][y] >= 0;
            }
        }

        // Kepler opens two more tiles of Sky Peak by hand.
        if (id == 1 && this.width > 24 && this.height > 18) {
            this.gameTiles[24][17] = this.heights[24][17] >= 0;
            this.gameTiles[24][18] = this.heights[24][18] >= 0;
        }
    }

    public static BattleBallArena get(int id) {
        BattleBallArena arena = ARENAS.get(id);

        if (arena == null) throw new BattleBallException("no_arena");

        return arena;
    }

    private static Map<Integer, BattleBallArena> load() {
        Map<Integer, BattleBallArena> arenas = new HashMap<>();

        try (InputStream input = BattleBallArena.class.getClassLoader().getResourceAsStream("battleball/arenas.json")) {
            if (input == null) return Map.of();

            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject data = entry.getValue().getAsJsonObject();
                List<String> heights = strings(data.getAsJsonArray("heights"));
                List<String> tiles = strings(data.getAsJsonArray("tiles"));
                List<List<int[]>> spawns = new ArrayList<>();

                for (int team = 0; team < 4; team++) {
                    List<int[]> points = new ArrayList<>();
                    JsonObject byTeam = data.getAsJsonObject("spawns");

                    if (byTeam.has(String.valueOf(team))) {
                        for (JsonElement point : byTeam.getAsJsonArray(String.valueOf(team))) {
                            JsonArray values = point.getAsJsonArray();

                            points.add(new int[] {
                                values.get(0).getAsInt(),
                                values.get(1).getAsInt(),
                                values.get(2).getAsInt()
                            });
                        }
                    }

                    spawns.add(points);
                }

                int id = Integer.parseInt(entry.getKey());

                arenas.put(id, new BattleBallArena(id, heights, tiles, spawns));
            }
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }

        return Map.copyOf(arenas);
    }

    private static List<String> strings(JsonArray array) {
        List<String> list = new ArrayList<>();

        for (JsonElement element : array) list.add(element.getAsString());

        return list;
    }

    public int id() {
        return this.id;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < this.width && y < this.height;
    }

    /** Whether a ball can bounce there. */
    public boolean walkable(int x, int y) {
        return this.inside(x, y) && this.heights[x][y] >= 0;
    }

    public int heightAt(int x, int y) {
        return this.inside(x, y) ? this.heights[x][y] : -1;
    }

    /** Whether the tile takes a colour. */
    public boolean gameTile(int x, int y) {
        return this.inside(x, y) && this.gameTiles[x][y];
    }

    /** The spawn points of a team: x, y, direction. */
    public List<int[]> spawns(int team) {
        return team >= 0 && team < this.spawns.size() ? this.spawns.get(team) : List.of();
    }
}

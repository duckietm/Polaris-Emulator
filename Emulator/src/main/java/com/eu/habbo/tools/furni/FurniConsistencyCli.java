package com.eu.habbo.tools.furni;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FurniConsistencyCli {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private FurniConsistencyCli() {
    }

    public static void main(String[] arguments) {
        System.exit(run(arguments, System.out, System.err));
    }

    static int run(String[] arguments, PrintStream out, PrintStream error) {
        try {
            Map<String, String> options = options(arguments);
            if (options.containsKey("help")) {
                usage(out);
                return 0;
            }
            require(options, "furniture-data", "bundles", "icons", "report");
            if (!options.containsKey("items") && !options.containsKey("items-sql-dump")
                    && !options.containsKey("jdbc-url")) {
                throw new IllegalArgumentException("provide --items, --items-sql-dump, or --jdbc-url");
            }

            List<FurniConsistencyValidator.ItemBase> items;
            if (options.containsKey("items")) {
                items = readItems(Path.of(options.get("items")));
            } else if (options.containsKey("items-sql-dump")) {
                items = ItemsBaseSqlDumpReader.read(Files.readString(Path.of(options.get("items-sql-dump"))));
            } else {
                items = readItemsFromDatabase(options);
            }
            JsonObject furnitureData = JsonParser.parseString(
                    Files.readString(Path.of(options.get("furniture-data")))).getAsJsonObject();
            Path swf = options.containsKey("swf") ? Path.of(options.get("swf")) : null;
            FurniConsistencyValidator.Report report = FurniConsistencyValidator.validate(
                    items,
                    furnitureData,
                    Path.of(options.get("bundles")),
                    Path.of(options.get("icons")),
                    swf);

            Path reportPath = Path.of(options.get("report"));
            if (reportPath.getParent() != null) Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, GSON.toJson(report));
            out.printf("Furni audit: %d items, %d FurnitureData entries, %d bundles, %d findings%n",
                    report.summary().itemsChecked(), report.summary().furnitureDataEntries(),
                    report.summary().bundlesChecked(), report.findings().size());
            if (!options.containsKey("quiet")) {
                report.findings().forEach(finding -> out.printf("[%s] item=%s classname=%s %s%n",
                        finding.code(), finding.itemId(), finding.classname(), finding.message()));
            }
            out.println("Report: " + reportPath.toAbsolutePath());
            return report.valid() ? 0 : 1;
        } catch (Exception exception) {
            error.println("Furni audit failed: " + exception.getMessage());
            usage(error);
            return 2;
        }
    }

    private static List<FurniConsistencyValidator.ItemBase> readItems(Path path) throws Exception {
        JsonElement root = JsonParser.parseString(Files.readString(path));
        JsonArray array = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("items");
        if (array == null) throw new IllegalArgumentException("items JSON must be an array or contain an items array");
        List<FurniConsistencyValidator.ItemBase> items = new ArrayList<>();
        for (JsonElement element : array) {
            items.add(GSON.fromJson(element, FurniConsistencyValidator.ItemBase.class));
        }
        return List.copyOf(items);
    }

    private static List<FurniConsistencyValidator.ItemBase> readItemsFromDatabase(Map<String, String> options)
            throws Exception {
        String user = options.getOrDefault("db-user", "root");
        String passwordEnvironment = options.getOrDefault("db-password-env", "POLARIS_DB_PASSWORD");
        String password = System.getenv(passwordEnvironment);
        if (password == null) throw new IllegalArgumentException("missing database password environment: " + passwordEnvironment);

        List<FurniConsistencyValidator.ItemBase> items = new ArrayList<>();
        try (var connection = DriverManager.getConnection(options.get("jdbc-url"), user, password);
             var statement = connection.prepareStatement(
                     "SELECT id, sprite_id, item_name, type, interaction_type FROM items_base ORDER BY id");
             var result = statement.executeQuery()) {
            while (result.next()) {
                items.add(new FurniConsistencyValidator.ItemBase(
                        result.getInt("id"), result.getInt("sprite_id"), result.getString("item_name"),
                        result.getString("type"), result.getString("interaction_type")));
            }
        }
        return List.copyOf(items);
    }

    private static Map<String, String> options(String[] arguments) {
        Map<String, String> result = new HashMap<>();
        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            if (!argument.startsWith("--")) throw new IllegalArgumentException("unexpected argument: " + argument);
            String key = argument.substring(2);
            if (key.equals("help") || key.equals("quiet")) {
                result.put(key, "true");
                continue;
            }
            if (++index >= arguments.length) throw new IllegalArgumentException("missing value for --" + key);
            result.put(key, arguments[index]);
        }
        return result;
    }

    private static void require(Map<String, String> options, String... names) {
        for (String name : names) {
            if (!options.containsKey(name) || options.get(name).isBlank()) {
                throw new IllegalArgumentException("missing --" + name);
            }
        }
    }

    private static void usage(PrintStream out) {
        out.println("Usage: FurniConsistencyCli --items items-base.json OR --items-sql-dump database.sql OR --jdbc-url jdbc:mariadb://... ");
        out.println("       --furniture-data FurnitureData.json --bundles DIR --icons DIR [--swf DIR] --report report.json [--quiet]");
        out.println("Database mode also accepts --db-user USER --db-password-env ENV_NAME.");
    }
}

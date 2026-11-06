package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CatalogPageLayoutsSchemaTest {
    /** Layouts V20260814210000 took out of the database; pages using them became default_3x3. */
    private static final Set<String> RETIRED = Set.of("custom_prefix");

    @Test
    void migrationContainsEverySupportedCatalogPageLayout() throws Exception {
        String migration = Files.readString(latestPageLayoutMigration());

        for (CatalogPageLayouts layout : CatalogPageLayouts.values()) {
            if (RETIRED.contains(layout.name())) continue;
            assertTrue(
                    migration.contains("'" + layout.name() + "'"),
                    () -> "Missing database enum value for " + layout.name());
        }
    }

    /** The newest migration that redefines the page_layout enum holds the full list. */
    private static Path latestPageLayoutMigration() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            return files.filter(file -> file.getFileName().toString().endsWith(".sql"))
                    .filter(file -> {
                        try {
                            return Files.readString(file).contains("`page_layout` ENUM");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .max(Comparator.comparing(file -> file.getFileName().toString()))
                    .orElseThrow();
        }
    }
}

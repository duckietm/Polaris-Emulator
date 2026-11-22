package polaris.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.HikariConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import polaris.core.ConfigurationManager;

class DatabasePoolSizingTest {

    @TempDir
    Path tempDirectory;

    @Test
    void appliesConfiguredPoolSizes() throws Exception {
        Path configFile = this.tempDirectory.resolve("config.ini");
        Files.writeString(configFile, """
                db.pool.maxsize=37
                db.pool.minsize=7
                """);
        ConfigurationManager configuration = new ConfigurationManager(configFile.toString());
        HikariConfig hikari = new HikariConfig();

        DatabasePool.applyPoolSizing(hikari, configuration);

        assertEquals(37, hikari.getMaximumPoolSize());
        assertEquals(7, hikari.getMinimumIdle());
    }

    @Test
    void appliesDocumentedDefaultsWhenSizesAreAbsent() throws Exception {
        Path configFile = this.tempDirectory.resolve("config.ini");
        Files.writeString(configFile, "");
        ConfigurationManager configuration = new ConfigurationManager(configFile.toString());
        HikariConfig hikari = new HikariConfig();

        DatabasePool.applyPoolSizing(hikari, configuration);

        assertEquals(50, hikari.getMaximumPoolSize());
        assertEquals(10, hikari.getMinimumIdle());
    }

    @Test
    void thePoolRunsAtReadCommittedUnlessToldOtherwise() throws Exception {
        assertEquals("TRANSACTION_READ_COMMITTED", this.isolation(""));
        assertEquals("TRANSACTION_REPEATABLE_READ", this.isolation("db.pool.transaction_isolation=repeatable_read\n"));
        assertEquals(null, this.isolation("db.pool.transaction_isolation=default\n"));
        assertEquals("TRANSACTION_READ_COMMITTED", this.isolation("db.pool.transaction_isolation=nonsense\n"));
    }

    private String isolation(String settings) throws Exception {
        Path configFile = this.tempDirectory.resolve("isolation.ini");
        Files.writeString(configFile, settings);
        HikariConfig hikari = new HikariConfig();

        DatabasePool.applyTransactionIsolation(hikari, new ConfigurationManager(configFile.toString()));

        return hikari.getTransactionIsolation();
    }
}

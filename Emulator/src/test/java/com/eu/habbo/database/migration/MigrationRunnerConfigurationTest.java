package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class MigrationRunnerConfigurationTest {

    @Test
    void allowsLateVersionedMigrationsFromMergedBranches() {
        DataSource dataSource = mock(DataSource.class);

        assertTrue(MigrationRunner.flyway(dataSource).getConfiguration().isOutOfOrder());
    }
}

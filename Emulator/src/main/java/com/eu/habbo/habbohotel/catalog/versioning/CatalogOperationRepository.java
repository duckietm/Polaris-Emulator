package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface CatalogOperationRepository {
    Optional<CatalogOperationRecord> findForUpdate(Connection connection, String operationId, int actorId)
            throws SQLException;

    void insert(Connection connection, CatalogOperationRecord record) throws SQLException;
}

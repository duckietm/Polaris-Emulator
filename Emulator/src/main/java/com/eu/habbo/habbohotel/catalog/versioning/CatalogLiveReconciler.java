package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface CatalogLiveReconciler {
    CatalogLiveReconciliationResult reconcile(
            Connection connection, CatalogVersionSnapshot active, CatalogVersionSnapshot draft, int actorId)
            throws SQLException;
}

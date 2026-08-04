package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface CatalogValidationDataRepository {
    CatalogValidationReferenceData load(Connection connection) throws SQLException;
}

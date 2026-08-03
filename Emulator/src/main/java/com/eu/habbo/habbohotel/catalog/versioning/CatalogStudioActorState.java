package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;

public record CatalogStudioActorState(int id, String username) {
    public CatalogStudioActorState {
        username = Objects.requireNonNull(username, "username");
    }
}

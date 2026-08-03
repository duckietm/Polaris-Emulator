package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import java.util.Objects;

public record CatalogStudioActor(int id, String username) {
    public CatalogStudioActor {
        username = Objects.requireNonNull(username, "username");
    }
}

package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogAdminAccessTest {
    private static GameClient client(boolean allowed) {
        GameClient client = mock(GameClient.class);
        Habbo habbo = mock(Habbo.class);

        when(client.getHabbo()).thenReturn(habbo);
        when(habbo.hasPermission(Permission.ACC_CATALOGFURNI)).thenReturn(allowed);

        return client;
    }

    @Test
    void aUserWithThePermissionPassesAndIsNotAnswered() {
        GameClient client = client(true);

        assertTrue(CatalogAdminAccess.check(client));

        verify(client, never()).sendResponse(org.mockito.ArgumentMatchers.any(MessageComposer.class));
    }

    @Test
    void aUserWithoutThePermissionIsRefusedAtOnce() {
        GameClient client = client(false);

        assertFalse(CatalogAdminAccess.check(client));

        ArgumentCaptor<MessageComposer> answer = ArgumentCaptor.forClass(MessageComposer.class);
        verify(client).sendResponse(answer.capture());
        assertInstanceOf(CatalogAdminResultComposer.class, answer.getValue());
    }

    @Test
    void everyCatalogAdminHandlerStartsWithTheGate() throws IOException {
        Path directory = Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/catalogadmin");
        List<Path> handlers;

        try (Stream<Path> files = Files.list(directory)) {
            handlers = files.filter(path -> path.getFileName().toString().endsWith("Event.java"))
                    .toList();
        }

        assertFalse(handlers.isEmpty());

        for (Path handler : handlers) {
            String source = Files.readString(handler, StandardCharsets.UTF_8);

            assertTrue(
                    source.contains("CatalogAdminAccess.check(this.client)"),
                    handler.getFileName() + " does not check the catalog permission");
            assertEquals(
                    -1,
                    source.indexOf("Permission.ACC_CATALOGFURNI"),
                    handler.getFileName() + " checks the permission itself instead of CatalogAdminAccess");
        }
    }
}

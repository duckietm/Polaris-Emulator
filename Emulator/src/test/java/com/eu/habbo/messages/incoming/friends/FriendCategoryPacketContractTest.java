package com.eu.habbo.messages.incoming.friends;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendCategoryPacketContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void categoryPacketHeadersMatchRendererContract() throws Exception {
        String incoming = source("com/eu/habbo/messages/incoming/Incoming.java");

        assertTrue(incoming.contains("AddFriendCategoryEvent = 4081"));
        assertTrue(incoming.contains("RenameFriendCategoryEvent = 4082"));
        assertTrue(incoming.contains("RemoveFriendCategoryEvent = 4083"));
        assertTrue(incoming.contains("MoveFriendToCategoryEvent = 4084"));
    }

    @Test
    void packetManagerRegistersAllCategoryHandlers() throws Exception {
        String registry = source("com/eu/habbo/messages/PacketManager.java");

        assertTrue(registry.contains("Incoming.AddFriendCategoryEvent, AddFriendCategoryEvent.class"));
        assertTrue(registry.contains("Incoming.RenameFriendCategoryEvent, RenameFriendCategoryEvent.class"));
        assertTrue(registry.contains("Incoming.RemoveFriendCategoryEvent, RemoveFriendCategoryEvent.class"));
        assertTrue(registry.contains("Incoming.MoveFriendToCategoryEvent, MoveFriendToCategoryEvent.class"));
    }

    @Test
    void categoryMutationsValidateOwnedCategoriesAndFriends() throws Exception {
        String rename = source("com/eu/habbo/messages/incoming/friends/RenameFriendCategoryEvent.java");
        String remove = source("com/eu/habbo/messages/incoming/friends/RemoveFriendCategoryEvent.java");
        String move = source("com/eu/habbo/messages/incoming/friends/MoveFriendToCategoryEvent.java");

        assertTrue(rename.contains("getMessengerCategory(categoryId)"));
        assertTrue(remove.contains("getMessengerCategory(categoryId)"));
        assertTrue(move.contains("getMessengerCategory(categoryId)"));
        assertTrue(move.contains("getMessenger().getFriend(friendId)"));
    }
}
